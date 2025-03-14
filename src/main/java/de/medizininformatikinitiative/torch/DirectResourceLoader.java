package de.medizininformatikinitiative.torch;

import de.medizininformatikinitiative.torch.consent.ConsentValidator;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.management.ConsentHandler;
import de.medizininformatikinitiative.torch.management.StructureDefinitionHandler;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;
import de.medizininformatikinitiative.torch.service.DataStore;
import de.medizininformatikinitiative.torch.util.ProfileMustHaveChecker;
import de.medizininformatikinitiative.torch.util.ResourceUtils;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Loader class, that handles the fetching of Resources from the datastore in batches and applying consent.
 */
@Component
public class DirectResourceLoader {

    private static final Logger logger = LoggerFactory.getLogger(DirectResourceLoader.class);

    private final DataStore dataStore;

    private final ConsentHandler handler;
    private final ConsentValidator consentValidator;
    private final DseMappingTreeBase dseMappingTreeBase;
    private final int queryConcurrency = 4;
    private final StructureDefinitionHandler structureDefinitionsHandler;
    private final ProfileMustHaveChecker profileMustHaveChecker;
    private final AnnotatedAttribute genericAttribute = new AnnotatedAttribute("direct", "direct", "direct", false);

    @Autowired
    public DirectResourceLoader(DataStore dataStore, ConsentHandler handler, DseMappingTreeBase dseMappingTreeBase, StructureDefinitionHandler structureDefinitionHandler, ProfileMustHaveChecker profileMustHaveChecker, ConsentValidator validator) {
        this.dataStore = dataStore;
        this.consentValidator = validator;
        this.handler = handler;
        this.dseMappingTreeBase = dseMappingTreeBase;
        this.structureDefinitionsHandler = structureDefinitionHandler;
        this.profileMustHaveChecker = profileMustHaveChecker;
    }

    /**
     * Extracts resources grouped by Patient ID for a given batch.
     *
     * @param attributeGroups CRTDL to be applied on batch
     * @param batch           Batch of Patient IDs
     * @param consentKey      Optional string key encoding the applicable consent code
     * @return Mono containing processed PatientBatchWithConsent
     */
    public Mono<PatientBatchWithConsent> directLoadPatientCompartment(
            List<AnnotatedAttributeGroup> attributeGroups,
            PatientBatchWithConsent batch,
            Optional<String> consentKey) {

        logger.trace("Starting collectResourcesByPatientReference");
        logger.trace("Patients Received: {}", batch);

        return consentKey.map(s -> handler.fetchAndBuildConsentInfo(s, batch)
                .flatMap(updatedBatch -> processBatchWithConsent(attributeGroups, updatedBatch))).orElseGet(() -> processBatchWithConsent(attributeGroups, batch));
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
        if (!patientBatchWithConsent.applyConsent() || consentValidator.checkConsent(resource, patientBatchWithConsent)) {
            return Mono.just(resource);
        } else {
            logger.debug("Consent Violated for Resource {} {}", resource.getResourceType(), resource.getId());
            return Mono.empty();
        }
    }


    public Mono<PatientBatchWithConsent> processPatientAttributeGroups(List<AnnotatedAttributeGroup> attributeGroups, PatientBatchWithConsent batch, Set<String> safeSet) {

        return Flux.fromIterable(attributeGroups).flatMap(group -> processPatientSingleAttributeGroup(group, batch, safeSet)).then(Mono.just(batch));
    }


    public Mono<ResourceBundle> proccessCoreAttributeGroups(List<AnnotatedAttributeGroup> attributeGroups, ResourceBundle coreResourceBundle) {
        return Flux.fromIterable(attributeGroups).flatMap(group -> proccessCoreAttributeGroup(group, coreResourceBundle)).then(Mono.just(coreResourceBundle));
    }

    public Mono<Void> proccessCoreAttributeGroup(AnnotatedAttributeGroup group, ResourceBundle resourceBundle) {
        logger.trace("Processing attribute group: {}", group);
        AtomicReference<Boolean> atLeastOneResource = new AtomicReference<>(!group.hasMustHave());
        return fetchResourcesDirect(Optional.empty(), group).doOnNext(resource -> {
            String id = ResourceUtils.getRelativeURL(resource);
            logger.trace("Storing resource {} under ID: {}", id, id);
            if (profileMustHaveChecker.fulfilled((DomainResource) resource, group)) {

                atLeastOneResource.set(true);
                resourceBundle.put(resource, group.id(), true);
            } else {
                resourceBundle.put(resource, group.id(), false);
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
                        String patientId = ResourceUtils.patientId((DomainResource) resource);
                        PatientResourceBundle bundle = mutableBundles.get(patientId);

                        if (profileMustHaveChecker.fulfilled(resource, group)) {

                            safeGroup.add(patientId);
                            bundle.put(resource, group.id(), true);
                        } else {
                            bundle.put(resource, group.id(), false);
                        }

                    } catch (PatientIdNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                })
                .doOnTerminate(() -> {
                    safeSet.retainAll(safeGroup);
                })
                .then(Mono.just(new PatientBatchWithConsent(mutableBundles, batch.applyConsent()))); // Convert back to immutable
    }

}


