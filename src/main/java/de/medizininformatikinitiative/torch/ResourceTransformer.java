package de.medizininformatikinitiative.torch;

import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.management.ConsentHandler;
import de.medizininformatikinitiative.torch.management.StructureDefinitionHandler;
import de.medizininformatikinitiative.torch.model.PatientBatch;
import de.medizininformatikinitiative.torch.model.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.ResourceBundle;
import de.medizininformatikinitiative.torch.model.ResourceGroupWrapper;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;
import de.medizininformatikinitiative.torch.service.DataStore;
import de.medizininformatikinitiative.torch.util.ElementCopier;
import de.medizininformatikinitiative.torch.util.ProfileMustHaveChecker;
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
import java.util.concurrent.atomic.AtomicReference;

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
    private final ProfileMustHaveChecker profileMustHaveChecker;

    @Autowired
    public ResourceTransformer(DataStore dataStore, ConsentHandler handler, ElementCopier copier, Redaction redaction, DseMappingTreeBase dseMappingTreeBase, StructureDefinitionHandler structureDefinitionHandler, ProfileMustHaveChecker profileMustHaveChecker) {
        this.dataStore = dataStore;
        this.copier = copier;
        this.redaction = redaction;
        this.handler = handler;
        this.dseMappingTreeBase = dseMappingTreeBase;
        this.structureDefinitionsHandler = structureDefinitionHandler;
        this.profileMustHaveChecker = profileMustHaveChecker;
    }

    /**
     * @param attributeGroups CRTDL to be applied on batch
     * @param batch           Batch of PatIDs
     * @param consentKey      string key encoding the applicable consent code (optional)
     * @return extracted Resources grouped by PatientID
     */
    public Mono<PatientBatchWithConsent> directLoadPatientCompartment(List<AnnotatedAttributeGroup> attributeGroups, PatientBatch batch, Optional<String> consentKey) {
        logger.trace("Starting collectResourcesByPatientReference");
        logger.trace("Patients Received: {}", batch);
        return consentKey.map(s -> handler.fetchAndBuildConsentInfo(s, batch).flatMap(patientBatchWithConsent -> processBatchWithConsent(attributeGroups, patientBatchWithConsent))).orElseGet(() -> processBatchWithConsent(attributeGroups, PatientBatchWithConsent.fromBatch(batch)));
    }

    private Mono<PatientBatchWithConsent> processBatchWithConsent(List<AnnotatedAttributeGroup> attributeGroups, PatientBatchWithConsent patientBatchWithConsent) {

        Set<String> safeSet = new ConcurrentSkipListSet<>(patientBatchWithConsent.patientBatch().ids());

        return processPatientAttributeGroups(attributeGroups, patientBatchWithConsent, safeSet).flatMap(bundle -> {
            // Ensure the updated safeSet is applied before returning
            PatientBatchWithConsent filteredBatch = patientBatchWithConsent.keep(safeSet);
            return Mono.just(filteredBatch);
        });
    }


    public Flux<Resource> fetchResourcesDirect(Optional<PatientBatchWithConsent> batch, AnnotatedAttributeGroup group) {
        List<Query> queries = group.queries(dseMappingTreeBase, structureDefinitionsHandler.getResourceType(group.groupReference()));

        if (batch.isPresent()) {
            PatientBatch queryBatch = batch.get().patientBatch();
            return Flux.fromIterable(queries).flatMap(query -> executeQueryWithBatch(queryBatch, query), queryConcurrency).flatMap(resource -> applyConsent((DomainResource) resource, batch.get()));
        } else {
            return Flux.fromIterable(queries).flatMap(dataStore::search, queryConcurrency);
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


    public Mono<PatientBatchWithConsent> processPatientAttributeGroups(List<AnnotatedAttributeGroup> attributeGroups, PatientBatchWithConsent batch, Set<String> safeSet) {

        return Flux.fromIterable(attributeGroups).flatMap(group -> processPatientSingleAttributeGroup(group, batch, safeSet)).then(Mono.just(batch));
    }


    public Mono<ResourceBundle> proccessCoreAttributeGroups(List<AnnotatedAttributeGroup> attributeGroups) {
        ResourceBundle bundle = new ResourceBundle();
        return Flux.fromIterable(attributeGroups).flatMap(group -> proccessCoreAttributeGroup(group, bundle)).then(Mono.just(bundle));
    }

    private Mono<Void> proccessCoreAttributeGroup(AnnotatedAttributeGroup group, ResourceBundle resourceBundle) {
        logger.trace("Processing attribute group: {}", group);
        AtomicReference<Boolean> atLeastOneResource = new AtomicReference<>(!group.hasMustHave());
        return fetchResourcesDirect(Optional.empty(), group).doOnNext(resource -> {
            String resourceId = resource.getId();
            logger.trace("Storing resource {} under ID: {}", resourceId, resourceId);
            if (profileMustHaveChecker.fulfilled((DomainResource) resource, group)) {
                atLeastOneResource.set(true);
                resourceBundle.put(new ResourceGroupWrapper(resource, Set.of(group)));
            }
        }).then(Mono.defer(() -> {
            if (atLeastOneResource.get()) {
                return Mono.empty();

            } else {
                logger.error("MustHave violated for group: {}", group.groupReference());
                return Mono.error(new MustHaveViolatedException("MustHave requirement violated for group: " + group.id()));
            }
        }));
    }


    /**
     * Fetches all resources for a batch and adds them to it, if
     *
     * @param group   Annotated Attribute Group to be processed
     * @param batch   Patientbatch containid the PatientResourceBundles to be filled
     * @param safeSet resources that have survived so far.
     * @return Patient batch containing a bundle per Patient Resource
     */
    private Mono<PatientBatchWithConsent> processPatientSingleAttributeGroup(AnnotatedAttributeGroup group,
                                                                             PatientBatchWithConsent batch,
                                                                             Set<String> safeSet) {
        logger.trace("Processing patient attribute group: {}", group);
        Set<String> safeGroup = new HashSet<>();

        if (!group.hasMustHave()) {
            safeGroup.addAll(batch.patientBatch().ids());
            logger.trace("Group has no must-have constraints, initial safe group: {}", safeGroup);
        }

        // Extract a mutable copy of the patient bundles
        Map<String, PatientResourceBundle> mutableBundles = batch.bundles();

        return fetchResourcesDirect(Optional.of(batch), group)
                .filter(resource -> !resource.isEmpty())
                .doOnNext(resource -> {
                    try {
                        String id = ResourceUtils.patientId((DomainResource) resource);

                        if (profileMustHaveChecker.fulfilled((DomainResource) resource, group)) {
                            safeGroup.add(id);
                            PatientResourceBundle bundle = mutableBundles.get(id);
                            bundle.put(new ResourceGroupWrapper(resource, Set.of(group)));
                        }

                    } catch (PatientIdNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                })
                .doOnTerminate(() -> {
                    safeSet.retainAll(safeGroup);
                })
                .then(Mono.just(new PatientBatchWithConsent(batch.applyConsent(), mutableBundles))); // Convert back to immutable
    }

}


