package de.medizininformatikinitiative.torch;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.model.crtdl.Attribute;
import de.medizininformatikinitiative.torch.model.crtdl.AttributeGroup;
import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.fhir.QueryParams;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;
import de.medizininformatikinitiative.torch.service.DataStore;
import de.medizininformatikinitiative.torch.util.ElementCopier;
import de.medizininformatikinitiative.torch.util.Redaction;
import de.medizininformatikinitiative.torch.util.ResourceUtils;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Collectors;

import static de.medizininformatikinitiative.torch.util.BatchUtils.queryElements;

@Component
public class ResourceTransformer {

    private static final Logger logger = LoggerFactory.getLogger(ResourceTransformer.class);

    private final DataStore dataStore;
    private final ElementCopier copier;
    private final Redaction redaction;
    private final ConsentHandler handler;
    private final FhirContext context;
    private final DseMappingTreeBase dseMappingTreeBase;

    @Autowired
    public ResourceTransformer(DataStore dataStore, ConsentHandler handler, ElementCopier copier, Redaction redaction, FhirContext context, DseMappingTreeBase dseMappingTreeBase) {
        this.dataStore = dataStore;
        this.copier = copier;
        this.redaction = redaction;
        this.handler = handler;
        this.context = context;
        this.dseMappingTreeBase = dseMappingTreeBase;
    }

    public Flux<Resource> transformResources(String batch, AttributeGroup group, Map<String, Map<String, List<Period>>> consentmap) {
        List<Query> queryList = group.queries(dseMappingTreeBase);

        // Process each query in the list
        return Flux.fromIterable(queryList)
                .flatMap(query -> executeQueryWithBatch(batch, query)
                        .flatMap(resource -> applyConsentAndTransform(resource, group, consentmap)));
    }

    // Step 1: Execute Query with Batch Parameter
    Flux<Resource> executeQueryWithBatch(String batch, Query query) {
        Query finalQuery = new Query(query.type(), query.params().appendParam(queryElements(query.type()), QueryParams.stringValue(batch)));
        logger.debug("Query for Patients {}", finalQuery);

        return dataStore.getResources(finalQuery)
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    logger.error("Error fetching resources for parameters: {}", finalQuery, e);
                    return Flux.empty();
                });
    }

    // Step 2: Apply Consent Check and Transform Resource
    private Mono<Resource> applyConsentAndTransform(Resource resource, AttributeGroup group, Map<String, Map<String, List<Period>>> consentmap) {
        try {
            if (consentmap.isEmpty() || handler.checkConsent((DomainResource) resource, consentmap)) {
                return Mono.just(transform((DomainResource) resource, group));
            } else {
                logger.warn("Consent Violated for Resource {} {}", resource.getResourceType(), resource.getId());
                return Mono.just(new Patient());  // Return empty patient if consent violated
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException |
                 InstantiationException e) {
            logger.error("Transform error: ", e);
            return Mono.error(new RuntimeException(e));
        } catch (MustHaveViolatedException e) {
            logger.error("Must Have Violated resulting in dropped Resource {} {}", resource.getResourceType(), resource.getId());
            return Mono.just(new Patient());
        }
    }


    public Resource transform(DomainResource resourcesrc, AttributeGroup group) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException, MustHaveViolatedException {
        Class<? extends DomainResource> resourceClass = resourcesrc.getClass().asSubclass(DomainResource.class);
        DomainResource tgt = resourceClass.getDeclaredConstructor().newInstance();

        try {
            logger.trace("Handling resource {} for patient {} and attributegroup {}", resourcesrc.getId(), ResourceUtils.getPatientId(resourcesrc), group.groupReference());
            for (Attribute attribute : group.attributes()) {

                copier.copy(resourcesrc, tgt, attribute);

            }
            //TODO define technically required in all Ressources
            copier.copy(resourcesrc, tgt, new Attribute("meta.profile", true));
            copier.copy(resourcesrc, tgt, new Attribute("id", true));
            //TODO Handle Custom ENUM Types like Status, since it has its Error in the valuesystem.
            if (resourcesrc.getClass() == org.hl7.fhir.r4.model.Observation.class) {
                copier.copy(resourcesrc, tgt, new Attribute("status", true));
            }
            if (resourcesrc.getClass() != org.hl7.fhir.r4.model.Patient.class && resourcesrc.getClass() != org.hl7.fhir.r4.model.Consent.class) {
                copier.copy(resourcesrc, tgt, new Attribute("subject.reference", true));
            }
            if (resourcesrc.getClass() == org.hl7.fhir.r4.model.Consent.class) {
                copier.copy(resourcesrc, tgt, new Attribute("patient.reference", true));
            }
            logger.trace("Resource after Copy {}", this.context.newJsonParser().encodeResourceToString(tgt));

            redaction.redact(tgt);
            logger.trace("Resource after Redact {}", this.context.newJsonParser().encodeResourceToString(tgt));

            logger.debug("Sucessfully transformed and redacted {}", resourcesrc.getId());
        } catch (PatientIdNotFoundException e) {
            throw new RuntimeException(e);
        }
        return tgt;
    }

    public Mono<Map<String, Collection<Resource>>> collectResourcesByPatientReference(Crtdl crtdl, List<String> batch) {
        logger.trace("Starting collectResourcesByPatientReference");
        logger.trace("Patients Received: {}", batch);

        // Step 1: Fetch consent map
        Flux<Map<String, Map<String, List<Period>>>> consentmap = fetchConsentMap(crtdl, batch);

        // Step 2: Initialize the safe set with the batch of patients
        Set<String> safeSet = new HashSet<>(batch);
        logger.trace("Initial safe set: {}", safeSet);

        return consentmap.switchIfEmpty(Flux.just(Collections.emptyMap()))
                .flatMap(finalConsentmap -> processAttributeGroups(crtdl, batch, finalConsentmap, safeSet))
                .collectList()
                .map(resourceLists -> flattenAndFilterResourceLists(resourceLists, safeSet))
                .doOnSuccess(result -> logger.debug("Successfully collected resources {}", result))
                .doOnError(error -> logger.error("Error collecting resources: {}", error.getMessage()));
    }

    // Step 1: Fetch consent map based on consent key
    private Flux<Map<String, Map<String, List<Period>>>> fetchConsentMap(Crtdl crtdl, List<String> batch) {
        String key = crtdl.consentKey();
        logger.trace("Consent key: {}", key);
        return key.isEmpty() ? Flux.empty()
                : handler.updateConsentPeriodsByPatientEncounters(handler.buildingConsentInfo(key, batch), batch);
    }

    // Step 2: Process each attribute group and collect resources
    private Flux<Map<String, Collection<Resource>>> processAttributeGroups(Crtdl crtdl, List<String> batch,
                                                                           Map<String, Map<String, List<Period>>> finalConsentmap,
                                                                           Set<String> safeSet) {
        return Flux.fromIterable(crtdl.dataExtraction().attributeGroups())
                .flatMap(group -> processSingleAttributeGroup(batch, group, finalConsentmap, safeSet));
    }

    // Helper method to process a single attribute group
    private Mono<Map<String, Collection<Resource>>> processSingleAttributeGroup(List<String> batch, AttributeGroup group,
                                                                                Map<String, Map<String, List<Period>>> consentMap,
                                                                                Set<String> safeSet) {
        logger.trace("Processing attribute group: {}", group);

        Set<String> safeGroup = new HashSet<>();
        if (!group.hasMustHave()) {
            safeGroup.addAll(batch);
            logger.trace("Group has no must-have constraints, initial safe group: {}", safeGroup);
        }

        return transformResources(String.join(",", batch), group, consentMap)
                .filter(resource -> {
                    boolean isNonEmpty = !resource.isEmpty();
                    logger.trace("Resource is non-empty: {}", isNonEmpty);
                    return isNonEmpty;
                })
                .collectMultimap(resource -> {
                    try {
                        String id = ResourceUtils.getPatientId((DomainResource) resource);
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

    // Step 3: Flatten, filter, and merge the collected resource lists
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
