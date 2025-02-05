package de.medizininformatikinitiative.torch;

import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.management.ConsentHandler;
import de.medizininformatikinitiative.torch.management.StructureDefinitionHandler;
import de.medizininformatikinitiative.torch.model.PatientBatch;
import de.medizininformatikinitiative.torch.model.consent.ConsentInfo;
import de.medizininformatikinitiative.torch.model.crtdl.Attribute;
import de.medizininformatikinitiative.torch.model.crtdl.AttributeGroup;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;
import de.medizininformatikinitiative.torch.service.DataStore;
import de.medizininformatikinitiative.torch.service.StandardAttributeGenerator;
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
import java.util.concurrent.ConcurrentSkipListSet;
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
    private final DseMappingTreeBase dseMappingTreeBase;
    private final int queryConcurrency = 4;
    private final StructureDefinitionHandler structueDefinitionHandler;

    @Autowired
    public ResourceTransformer(DataStore dataStore, ConsentHandler handler, ElementCopier copier, Redaction redaction, DseMappingTreeBase dseMappingTreeBase, StructureDefinitionHandler structureDefinitionHandler) {
        this.dataStore = dataStore;
        this.copier = copier;
        this.redaction = redaction;
        this.handler = handler;
        this.dseMappingTreeBase = dseMappingTreeBase;
        this.structueDefinitionHandler = structureDefinitionHandler;
    }

    /**
     * @param attributeGroups CRTDL to be applied on batch
     * @param batch           Batch of PatIDs
     * @param consentKey
     * @return extracted Resources grouped by PatientID
     */
    public Mono<Map<String, Collection<Resource>>> collectResourcesByPatientReference(List<AttributeGroup> attributeGroups, PatientBatch batch, Optional<String> consentKey) {
        logger.trace("Starting collectResourcesByPatientReference");
        logger.trace("Patients Received: {}", batch);
        Optional<String> key = consentKey;
        return key.map(s -> handler.fetchAndBuildConsentInfo(s, batch)
                        .flatMap(consentInfo -> processBatchWithConsent(attributeGroups, consentInfo)))
                .orElseGet(() -> processBatchWithConsent(attributeGroups, ConsentInfo.fromBatch(batch)));
    }

    private Mono<Map<String, Collection<Resource>>> processBatchWithConsent(List<AttributeGroup> attributeGroups, ConsentInfo consentInfo) {

        Set<String> safeSet = new ConcurrentSkipListSet<>(consentInfo.patientBatch().ids());
        return processAttributeGroups(attributeGroups, Optional.of(consentInfo), Optional.of(safeSet)).collectList()
                .map(resourceLists -> flattenAndFilterResourceLists(resourceLists, safeSet))
                .doOnSuccess(result -> logger.trace("Successfully collected resources {}", result.size()))
                .doOnError(error -> logger.error("Error collecting resources: {}", error.getMessage()));
    }


    /**
     * Extracts for the Patient {@code batch}, by fetching the adequate resources from the FHIR Server defined in the
     * {@code group}, checking them for consent using the {@code consentmap} and applying
     * the transformation according to all attributes defined in the {@code group}.
     *
     * @param batch Batch of PatIDs
     * @param group Attribute Group
     * @return Flux of transformed Resources with attribute, consent and batch conditions applied
     */
    public Flux<Resource> fetchAndTransformResources(Optional<ConsentInfo> batch, AttributeGroup group) {
        List<Query> queries = group.queries(dseMappingTreeBase, structueDefinitionHandler.getResourceType(group.groupReference()));
        if (batch.isPresent()) {

            PatientBatch queryBatch = batch.get().patientBatch();
            return Flux.fromIterable(queries)
                    .flatMap(query -> executeQueryWithBatch(queryBatch, query), queryConcurrency)
                    .flatMap(resource -> applyConsentAndTransform((DomainResource) resource, group, batch.get()));
        } else {
            return Flux.fromIterable(queries)
                    .flatMap(dataStore::search, queryConcurrency);
        }
    }


    private Flux<Resource> executeQueryWithBatch(PatientBatch batch, Query query) {
        Query finalQuery = Query.of(query.type(), query.params().appendParams(batch.compartmentSearchParam(query.type())));
        logger.debug("Query for Patients {}", finalQuery);

        return dataStore.search(finalQuery);
    }

    private Mono<Resource> applyConsentAndTransform(DomainResource resource, AttributeGroup group, ConsentInfo consentInfo) {
        try {
            if (!consentInfo.applyConsent() || handler.checkConsent(resource, consentInfo)) {
                return Mono.just(transform(resource, group, resource.getClass().asSubclass(DomainResource.class)));
            } else {
                logger.warn("Consent Violated for Resource {} {}", resource.getResourceType(), resource.getId());
                return Mono.empty();
            }
        } catch (PatientIdNotFoundException | TargetClassCreationException e) {
            return Mono.error(e);
        } catch (MustHaveViolatedException e) {
            logger.warn("Must Have Violated resulting in dropped Resource {} {} {}", resource.getResourceType(), resource.getId(), e.getMessage());
            return Mono.empty();
        }
    }

    public <T extends DomainResource> T transform(T resourceSrc, AttributeGroup group, Class<T> resourceClass) throws MustHaveViolatedException, TargetClassCreationException, PatientIdNotFoundException {
        T tgt = createTargetResource(resourceClass);
        logger.trace("Handling resource {} for patient {} and attributegroup {}", resourceSrc.getId(), ResourceUtils.patientId(resourceSrc), group.groupReference());
        group = StandardAttributeGenerator.generate(group, resourceClass.getSimpleName());
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


    private Flux<Map<String, Collection<Resource>>> processAttributeGroups(List<AttributeGroup> attributeGroups,
                                                                           Optional<ConsentInfo> batch,
                                                                           Optional<Set<String>> safeSet) {
        return Flux.fromIterable(attributeGroups)
                .flatMap(group -> processSingleAttributeGroup(group, batch, safeSet));
    }

    private Mono<Map<String, Collection<Resource>>> processSingleAttributeGroup(AttributeGroup group,
                                                                                Optional<ConsentInfo> batch,
                                                                                Optional<Set<String>> safeSet) {
        logger.trace("Processing attribute group: {}", group);

        // If batch is empty, collect all resources under "core" and return
        if (batch.isEmpty() && safeSet.isEmpty()) {
            return fetchAndTransformResources(Optional.empty(), group)
                    .collectMultimap(resource -> "core") // Everything goes under "core"
                    .map(map -> {
                        logger.trace("Collected resources under 'core' for group {}: {}", group.id(), map.size());
                        return new HashMap<>(map);
                    });
        }

        if (batch.isPresent() && safeSet.isPresent()) {
            Set<String> safeGroup = new HashSet<>();
            if (!group.hasMustHave()) {
                safeGroup.addAll(batch.get().patientBatch().ids());
                logger.trace("Group has no must-have constraints, initial safe group: {}", safeGroup);
            }

            return fetchAndTransformResources(batch, group)
                    .filter(resource -> {
                        boolean isNonEmpty = !resource.isEmpty();
                        logger.trace("Resource is non-isEmpty: {}", isNonEmpty);
                        return isNonEmpty;
                    })
                    .collectMultimap(resource -> {
                        try {
                            String id = ResourceUtils.patientId((DomainResource) resource);
                            safeGroup.add(id);
                            logger.trace("Adding patient ID to safe group: {}", id);
                            return id;
                        } catch (PatientIdNotFoundException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .doOnNext(map -> {
                        logger.trace("Collected map for group {}: {}", group, map);
                        safeSet.get().retainAll(safeGroup); // Retain only patients present in both sets
                        logger.trace("Updated safe set after retention: {}", safeSet);
                    });
        }
        return Mono.empty();
    }


    private Map<String, Collection<Resource>> flattenAndFilterResourceLists(
            List<Map<String, Collection<Resource>>> resourceLists, Set<String> safeSet) {
        return resourceLists.stream()
                .flatMap(map -> map.entrySet().stream())
                .filter(entry -> {
                    boolean isInSafeSet = safeSet.contains(entry.getKey());
                    logger.trace("Filtering entry with key: {} (in safe set: {}) and value: {}", entry.getKey(), isInSafeSet, entry.getValue());
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
