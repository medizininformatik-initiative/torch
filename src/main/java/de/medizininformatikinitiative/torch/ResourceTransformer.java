package de.medizininformatikinitiative.torch;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.model.PatientBatch;
import de.medizininformatikinitiative.torch.model.crtdl.Attribute;
import de.medizininformatikinitiative.torch.model.crtdl.AttributeGroup;
import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;
import de.medizininformatikinitiative.torch.service.DataStore;
import de.medizininformatikinitiative.torch.util.ElementCopier;
import de.medizininformatikinitiative.torch.util.Redaction;
import de.medizininformatikinitiative.torch.util.ResourceUtils;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Transformer class, that handles the collecting of Resources from the datastore and the transformation of them according to the crtdl.
 */
@Component
public class ResourceTransformer {

    private static final Logger logger = LoggerFactory.getLogger(ResourceTransformer.class);

    private final DataStore dataStore;
    private final ElementCopier copier;
    private final Redaction redaction;
    private final ConsentHandler handler;
    private final FhirContext context;
    private final DseMappingTreeBase dseMappingTreeBase;
    private final int queryConcurrency = 4;

    @Autowired
    public ResourceTransformer(DataStore dataStore, ConsentHandler handler, ElementCopier copier, Redaction redaction, FhirContext context, DseMappingTreeBase dseMappingTreeBase) {
        this.dataStore = dataStore;
        this.copier = copier;
        this.redaction = redaction;
        this.handler = handler;
        this.context = context;
        this.dseMappingTreeBase = dseMappingTreeBase;
    }

    /**
     * @param crtdl
     * @param batch Batch of PatIDs
     * @return extracted Resources grouped by PatientID
     */
    public Mono<Map<String, Collection<Resource>>> collectResourcesByPatientReference(Crtdl crtdl, PatientBatch batch) {
        logger.trace("Starting collectResourcesByPatientReference");
        logger.trace("Patients Received: {}", batch);
        Optional<String> key = crtdl.consentKey();
        if (key.isPresent()) {
            return fetchConsentMap(key.get(), batch)
                    .flatMap(consentMap -> foo(crtdl, consentMap));
        } else {
            return foo(crtdl, batch.ids().stream().collect(
                    Collectors.toMap(Function.identity(),
                            id -> Map.of()
                    )
            ));
        }
    }

    private Mono<Map<String, Collection<Resource>>> foo(Crtdl crtdl, Map<String, Map<String, ConsentInfo.NonContinousPeriod>> consentMap) {

        Set<String> safeSet = new HashSet<>(consentMap.keySet());

        return processAttributeGroups(crtdl, consentMap, safeSet).collectList()
                .map(resourceLists -> flattenAndFilterResourceLists(resourceLists, safeSet))
                .doOnSuccess(result -> logger.debug("Successfully collected resources {}", result))
                .doOnError(error -> logger.error("Error collecting resources: {}", error.getMessage()));


    }

    private Mono<Map<String, Map<String, ConsentInfo.NonContinousPeriod>>> fetchConsentMap(String key, PatientBatch batch) {
        Flux<ConsentInfo> consentInfoFlux = handler.buildingConsentInfo(key, batch);
        Mono<List<ConsentInfo>> collectedConsentinfo = collectConsentInfo(consentInfoFlux);
        return handler.updateConsentPeriodsByPatientEncounters(collectedConsentinfo, batch).map(consentInfos -> consentInfos.stream().collect(
                Collectors.toMap(ConsentInfo::patientId, ConsentInfo::periods)
        ));
    }

    private Mono<List<ConsentInfo>> collectConsentInfo(Flux<ConsentInfo> consentInfoFlux) {
        return consentInfoFlux.collectMultimap(ConsentInfo::patientId, ConsentInfo::periods)
                .map(map ->
                        map.entrySet().stream().map(
                                entry -> new ConsentInfo(entry.getKey(), merge(entry.getValue()))
                        ).toList()
                );
    }

    private static Map<String, ConsentInfo.NonContinousPeriod> merge(Collection<Map<String, ConsentInfo.NonContinousPeriod>> values) {
        return values.stream().flatMap(map -> map.entrySet().stream()).collect(
                Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        ConsentInfo.NonContinousPeriod::merge
                )
        );
    }


    /**
     * Extracts for the Patient {@code batch}, by fetching the adequate resources from the FHIR Server defined in the
     * {@code group}, checking them for consent using the {@code consentmap} and applying
     * the transformation according to all attributes defined in the {@code group}.
     *
     * @param batch      Batch of PatIDs
     * @param group      Attribute Group
     * @param consentmap Map of Codes for Consent Processor
     * @return Flux of transformed Resources with attribute, consent and batch conditions applied
     */
    public Flux<Resource> fetchAndTransformResources(Map<String, Map<String, ConsentInfo.NonContinousPeriod>> batch, AttributeGroup group) {
        List<Query> queries = group.queries(dseMappingTreeBase);
        PatientBatch queryBatch = PatientBatch.of(batch.keySet().stream().toList());

        return Flux.fromIterable(queries)
                .flatMap(query -> executeQueryWithBatch(queryBatch, query), queryConcurrency)
                .flatMap(resource -> applyConsentAndTransform((DomainResource) resource, group, batch));
    }


    private Flux<Resource> executeQueryWithBatch(PatientBatch batch, Query query) {
        Query finalQuery = Query.of(query.type(), query.params().appendParams(batch.compartmentSearchParam(query.type())));
        logger.debug("Query for Patients {}", finalQuery);

        return dataStore.search(finalQuery);
    }

    private Mono<Resource> applyConsentAndTransform(DomainResource resource, AttributeGroup group, Map<String, Map<String, ConsentInfo.NonContinousPeriod>> consentmap) {
        try {
            if (consentmap.isEmpty() || handler.checkConsent(resource, consentmap)) {
                return Mono.just(transform(resource, group, resource.getClass().asSubclass(DomainResource.class)));
            } else {
                logger.warn("Consent Violated for Resource {} {}", resource.getResourceType(), resource.getId());
                return Mono.empty();  // Return empty patient if consent violated
            }
        } catch (PatientIdNotFoundException | TargetClassCreationException e) {
            return Mono.error(e);
        } catch (MustHaveViolatedException e) {
            logger.warn("Must Have Violated resulting in dropped Resource {} {}", resource.getResourceType(), resource.getId());
            return Mono.empty();
        }
    }

    //TODO Auslagern
    public <T extends DomainResource> T transform(T resourceSrc, AttributeGroup group, Class<T> resourceClass) throws MustHaveViolatedException, TargetClassCreationException, PatientIdNotFoundException {
        T tgt = createTargetResource(resourceClass);
        logger.trace("Handling resource {} for patient {} and attributegroup {}", resourceSrc.getId(), ResourceUtils.patientId(resourceSrc), group.groupReference());

        //TODO define technically required in all Ressources/extract
        copier.copy(resourceSrc, tgt, new Attribute("id", true));
        copier.copy(resourceSrc, tgt, new Attribute("meta.profile", true));
        if (resourceSrc.getClass() != org.hl7.fhir.r4.model.Patient.class && resourceSrc.getClass() != org.hl7.fhir.r4.model.Consent.class) {
            copier.copy(resourceSrc, tgt, new Attribute("subject.reference", true));
        }
        if (resourceSrc.getClass() == org.hl7.fhir.r4.model.Consent.class) {
            copier.copy(resourceSrc, tgt, new Attribute("patient.reference", true));
        }
        //TODO Can be removed when modifier elements are always copied
        if (resourceSrc.getClass() == org.hl7.fhir.r4.model.Observation.class) {
            copier.copy(resourceSrc, tgt, new Attribute("status", true));
        }

        for (Attribute attribute : group.attributes()) {
            copier.copy(resourceSrc, tgt, attribute);
        }

        redaction.redact(tgt);

        return tgt;
    }

    private static <T extends DomainResource> T createTargetResource(Class<T> resourceClass) throws TargetClassCreationException {
        T tgt;
        try {
            tgt = resourceClass.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                 NoSuchMethodException e) {
            throw new TargetClassCreationException(resourceClass);
        }
        return tgt;
    }


    private Flux<Map<String, Collection<Resource>>> processAttributeGroups(Crtdl crtdl,
                                                                           Map<String, Map<String, ConsentInfo.NonContinousPeriod>> batch,
                                                                           Set<String> safeSet) {
        return Flux.fromIterable(crtdl.dataExtraction().attributeGroups())
                .flatMap(group -> processSingleAttributeGroup(group, batch, safeSet));
    }

    private Mono<Map<String, Collection<Resource>>> processSingleAttributeGroup(AttributeGroup group,
                                                                                Map<String, Map<String, ConsentInfo.NonContinousPeriod>> batch,
                                                                                Set<String> safeSet) {
        logger.trace("Processing attribute group: {}", group);

        Set<String> safeGroup = new HashSet<>();
        if (!group.hasMustHave()) {
            safeGroup.addAll(batch.keySet());
            logger.trace("Group has no must-have constraints, initial safe group: {}", safeGroup);
        }

        return fetchAndTransformResources(batch, group)
                .filter(resource -> {
                    boolean isNonEmpty = !resource.isEmpty();
                    logger.trace("Resource is non-empty: {}", isNonEmpty);
                    return isNonEmpty;
                })
                .collectMultimap(resource -> {
                    try {
                        String id = ResourceUtils.patientId((DomainResource) resource);
                        safeGroup.add(id);
                        logger.trace("Adding patient ID to safe group: {}", id);
                        return id;
                    } catch (PatientIdNotFoundException e) {
                        logger.error("PatientIdNotFoundException: {}", e.getMessage());
                        throw new RuntimeException(e);
                    }
                })
                .doOnNext(map -> {
                    logger.trace("Collected map for group {}: {}", group, map);
                    safeSet.retainAll(safeGroup); // Retain only patients present in both sets
                    logger.trace("Updated safe set after retention: {}", safeSet);
                });
    }


    private Map<String, Collection<Resource>> flattenAndFilterResourceLists(
            List<Map<String, Collection<Resource>>> resourceLists, Set<String> safeSet) {
        logger.trace("Collected resource lists: {}", resourceLists);

        return resourceLists.stream()
                .flatMap(map -> map.entrySet().stream())
                .filter(entry -> {
                    boolean isInSafeSet = safeSet.contains(entry.getKey());
                    logger.debug("Filtering entry with key: {} (in safe set: {}) and value: {}", entry.getKey(), isInSafeSet, entry.getValue());
                    return isInSafeSet;
                })
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (existing, replacement) -> {
                            Collection<Resource> merged = new ArrayList<>(existing);
                            merged.addAll(replacement);
                            logger.trace("Merging values: existing = {}, replacement = {}", existing, replacement);
                            return merged;
                        }
                ));
    }


}
