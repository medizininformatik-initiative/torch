package de.medizininformatikinitiative.torch;

import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.management.ConsentHandler;
import de.medizininformatikinitiative.torch.management.ResourceStore;
import de.medizininformatikinitiative.torch.management.StructureDefinitionHandler;
import de.medizininformatikinitiative.torch.model.PatientBatch;
import de.medizininformatikinitiative.torch.model.ResourceGroupWrapper;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
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
    private final StructureDefinitionHandler structureDefinitionsHandler;

    @Autowired
    public ResourceTransformer(DataStore dataStore, ConsentHandler handler, ElementCopier copier, Redaction redaction, DseMappingTreeBase dseMappingTreeBase, StructureDefinitionHandler structureDefinitionHandler) {
        this.dataStore = dataStore;
        this.copier = copier;
        this.redaction = redaction;
        this.handler = handler;
        this.dseMappingTreeBase = dseMappingTreeBase;
        this.structureDefinitionsHandler = structureDefinitionHandler;
    }

    /**
     * @param attributeGroups CRTDL to be applied on batch
     * @param batch           Batch of PatIDs
     * @param consentKey
     * @return extracted Resources grouped by PatientID
     */
    public Mono<ResourceStore> collectResourcesByPatientReference(List<AnnotatedAttributeGroup> attributeGroups,
                                                                  PatientBatch batch,
                                                                  Optional<String> consentKey) {
        logger.trace("Starting collectResourcesByPatientReference");
        logger.trace("Patients Received: {}", batch);


        return consentKey.map(s -> handler.fetchAndBuildConsentInfo(s, batch)
                        .flatMap(patientBatchWithConsent -> processBatchWithConsent(attributeGroups, patientBatchWithConsent)))
                .orElseGet(() -> processBatchWithConsent(attributeGroups, PatientBatchWithConsent.fromBatch(batch)));
    }

    private Mono<ResourceStore> processBatchWithConsent(List<AnnotatedAttributeGroup> attributeGroups,
                                                        PatientBatchWithConsent patientBatchWithConsent) {

        Set<String> safeSet = new ConcurrentSkipListSet<>(patientBatchWithConsent.patientBatch().ids());

        return processAttributeGroups(attributeGroups, Optional.of(patientBatchWithConsent), Optional.of(safeSet));
    }


    public Flux<Resource> fetchResourcesDirect(Optional<PatientBatchWithConsent> batch, AnnotatedAttributeGroup group) {
        List<Query> queries = group.queries(dseMappingTreeBase, structureDefinitionsHandler.getResourceType(group.groupReference()));

        if (batch.isPresent()) {
            PatientBatch queryBatch = batch.get().patientBatch();
            return Flux.fromIterable(queries)
                    .flatMap(query -> executeQueryWithBatch(queryBatch, query), queryConcurrency)
                    .flatMap(resource -> applyConsent((DomainResource) resource, batch.get()));
        } else {
            return Flux.fromIterable(queries)
                    .flatMap(dataStore::search, queryConcurrency);
        }
    }


    Flux<Resource> executeQueryWithBatch(PatientBatch batch, Query query) {
        Query finalQuery = Query.of(query.type(), query.params().appendParams(batch.compartmentSearchParam(query.type())));
        logger.debug("Query for Patients {}", finalQuery);

        return dataStore.search(finalQuery);
    }

    private Mono<Resource> applyConsent(DomainResource resource, PatientBatchWithConsent patientBatchWithConsent) {
        if (!patientBatchWithConsent.applyConsent() || handler.checkConsent(resource, patientBatchWithConsent)) {
            return Mono.just(resource);
        } else {
            logger.debug("Consent Violated for Resource {} {}", resource.getResourceType(), resource.getId());
            return Mono.empty();
        }
    }

    public <T extends DomainResource> T transform(T resourceSrc, AnnotatedAttributeGroup group, Class<T> resourceClass) throws MustHaveViolatedException, TargetClassCreationException, PatientIdNotFoundException {
        T tgt = createTargetResource(resourceClass);
        logger.trace("Handling resource {} for patient {} and attributegroup {}", resourceSrc.getId(), ResourceUtils.patientId(resourceSrc), group.groupReference());
        for (AnnotatedAttribute attribute : group.attributes()) {
            copier.copy(resourceSrc, tgt, attribute, group.groupReference());
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


    public Mono<ResourceStore> processAttributeGroups(List<AnnotatedAttributeGroup> attributeGroups,
                                                      Optional<PatientBatchWithConsent> batch,
                                                      Optional<Set<String>> safeSet) {
        ResourceStore resourceStore = new ResourceStore();

        return Flux.fromIterable(attributeGroups)
                .flatMap(group -> processSingleAttributeGroup(group, batch, safeSet, resourceStore))
                .then(Mono.just(resourceStore));
    }

    private Mono<Void> processSingleAttributeGroup(AnnotatedAttributeGroup group,
                                                   Optional<PatientBatchWithConsent> batch,
                                                   Optional<Set<String>> safeSet,
                                                   ResourceStore resourceStore) {
        logger.trace("Processing attribute group: {}", group);

        if (batch.isEmpty() && safeSet.isEmpty()) {
            return fetchResourcesDirect(Optional.empty(), group)
                    .doOnNext(resource -> {
                        String resourceId = resource.getId();
                        logger.trace("Storing resource {} under ID: {}", resourceId, resourceId);
                        resourceStore.put(new ResourceGroupWrapper(resource, Set.of(group)));
                    })
                    .then();
        }

        if (batch.isPresent() && safeSet.isPresent()) {
            Set<String> safeGroup = new HashSet<>();
            if (!group.hasMustHave()) {
                safeGroup.addAll(batch.get().patientBatch().ids());
                logger.trace("Group has no must-have constraints, initial safe group: {}", safeGroup);
            }

            return fetchResourcesDirect(batch, group)
                    .filter(resource -> !resource.isEmpty())
                    .doOnNext(resource -> {
                        try {
                            String id = ResourceUtils.patientId((DomainResource) resource);
                            safeGroup.add(id);
                            resourceStore.put(new ResourceGroupWrapper(resource, Set.of(group))); // Auto-merges!
                        } catch (PatientIdNotFoundException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .doOnTerminate(() -> {
                        logger.trace("Updated safe set after retention: {}", safeSet);
                        safeSet.get().retainAll(safeGroup);
                    })
                    .then();
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
