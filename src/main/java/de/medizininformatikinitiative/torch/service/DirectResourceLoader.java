package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.consent.ConsentValidator;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;
import de.medizininformatikinitiative.torch.util.ProfileMustHaveChecker;
import de.medizininformatikinitiative.torch.util.ResourceUtils;
import org.hl7.fhir.r4.model.DomainResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Objects.requireNonNull;

/**
 * Loader class, that handles the fetching of Resources from the datastore in batches and applying consent.
 */
@Component
public class DirectResourceLoader {

    private static final Logger logger = LoggerFactory.getLogger(DirectResourceLoader.class);

    private final DataStore dataStore;

    private final ConsentValidator consentValidator;
    private final DseMappingTreeBase dseMappingTreeBase;
    private final ProfileMustHaveChecker profileMustHaveChecker;

    @Autowired
    public DirectResourceLoader(DataStore dataStore, DseMappingTreeBase dseMappingTreeBase, ProfileMustHaveChecker profileMustHaveChecker, ConsentValidator validator) {
        this.dataStore = dataStore;
        this.consentValidator = validator;
        this.dseMappingTreeBase = dseMappingTreeBase;
        this.profileMustHaveChecker = profileMustHaveChecker;
    }

    /**
     * Extracts resources grouped by Patient ID for a given batch.
     *
     * @param attributeGroups CRTDL to be applied on batch
     * @param batch           Batch of Patient IDs
     * @return Mono containing processed PatientBatchWithConsent
     */
    public Mono<PatientBatchWithConsent> directLoadPatientCompartment(
            List<AnnotatedAttributeGroup> attributeGroups,
            PatientBatchWithConsent batch) {

        logger.trace("Starting collectResourcesByPatientReference");
        logger.trace("Patients Received: {}", batch);

        return processBatchWithConsent(attributeGroups, batch);
    }

    private Mono<PatientBatchWithConsent> processBatchWithConsent(List<AnnotatedAttributeGroup> attributeGroups, PatientBatchWithConsent patientBatchWithConsent) {
        Set<String> safeSet = new ConcurrentSkipListSet<>(patientBatchWithConsent.patientBatch().ids());
        return processPatientAttributeGroups(attributeGroups, patientBatchWithConsent, safeSet)
                .doOnNext(bundle -> {
                    logger.debug(" {} out of {} patients passed consent checks",
                            safeSet.size(),
                            patientBatchWithConsent.patientBatch().ids().size());
                    logger.trace("Surviving patient IDs: {}", String.join(", ", safeSet));
                })
                .map(bundle -> patientBatchWithConsent.keep(safeSet));
    }

    private Flux<Query> groupQueries(AnnotatedAttributeGroup group) {
        return Flux.fromIterable(group.queries(dseMappingTreeBase, group.resourceType()));
    }

    Flux<DomainResource> executeQueryWithBatch(PatientBatch batch, Query query) {
        logger.debug("Execute query {} over {} patients", query, batch.ids().size());
        return dataStore.search(Query.of(query.type(), batch.compartmentSearchParam(query.type()).appendParams(query.params())), DomainResource.class);
    }

    private Mono<DomainResource> applyConsent(DomainResource resource, PatientBatchWithConsent patientBatchWithConsent) {
        if (!patientBatchWithConsent.applyConsent() || consentValidator.checkConsent(resource, patientBatchWithConsent)) {
            return Mono.just(resource);
        } else {
            logger.debug("Consent Violated for Resource {} {}", resource.getResourceType(), resource.getId());
            return Mono.empty();
        }
    }

    public Mono<PatientBatchWithConsent> processPatientAttributeGroups(List<AnnotatedAttributeGroup> groups, PatientBatchWithConsent batch, Set<String> safeSet) {
        logger.debug("Process {} patient attribute groups over {} patients...", groups.size(), batch.patientBatch().ids().size());
        return Flux.fromIterable(groups)
                .concatMap(group -> processPatientSingleAttributeGroup(group, batch, safeSet))
                .then(Mono.just(batch));
    }

    public Mono<ResourceBundle> processCoreAttributeGroups(List<AnnotatedAttributeGroup> attributeGroups, ResourceBundle coreResourceBundle) {
        return Flux.fromIterable(attributeGroups).concatMap(group -> processCoreAttributeGroup(group, coreResourceBundle)).then(Mono.just(coreResourceBundle));
    }

    public Mono<Void> processCoreAttributeGroup(AnnotatedAttributeGroup group, ResourceBundle resourceBundle) {
        logger.debug("Process core attribute group {}...", group.id());

        AtomicBoolean atLeastOneResource = new AtomicBoolean(!group.hasMustHave());
        return groupQueries(group).flatMap(query -> dataStore.search(query, DomainResource.class), 1).doOnNext(resource -> {
            String id = ResourceUtils.getRelativeURL(resource);
            logger.trace("Storing resource {} under ID: {}", id, id);
            if (profileMustHaveChecker.fulfilled(resource, group)) {

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

    private static ResourceWithPatientId extractPatientId(DomainResource resource) {
        try {
            return new ResourceWithPatientId(resource, ResourceUtils.patientId(resource));
        } catch (PatientIdNotFoundException e) {
            logger.warn("Ignoring resource {} not referencing a patient", resource.getId());
            return null;
        }
    }

    private record ResourceWithPatientId(DomainResource resource, String patientId) {
        private ResourceWithPatientId {
            requireNonNull(resource);
            requireNonNull(patientId);
        }
    }

    /**
     * Fetches all resources for a batch and adds them to it, if
     *
     * @param group   Annotated Attribute Group to be processed
     * @param batch   patient batch containing the PatientResourceBundles to be filled
     * @param safeSet resources that have survived so far.
     * @return Patient batch containing a bundle per Patient Resource
     */
    private Mono<PatientBatchWithConsent> processPatientSingleAttributeGroup(AnnotatedAttributeGroup group,
                                                                             PatientBatchWithConsent batch,
                                                                             Set<String> safeSet) {
        logger.debug("Process patient attribute group {}...", group.id());
        Set<String> safeGroup = new HashSet<>();

        if (!group.hasMustHave()) {
            safeGroup.addAll(batch.patientBatch().ids());
            logger.trace("Group has no must-have constraints, initial safe group: {}", safeGroup);
        }

        // Extract a mutable copy of the patient bundles
        Map<String, PatientResourceBundle> mutableBundles = batch.bundles();

        return groupQueries(group)
                .concatMap(query -> executeQueryWithBatch(batch.patientBatch(), query))
                .concatMap(resource -> applyConsent(resource, batch))
                .mapNotNull(DirectResourceLoader::extractPatientId)
                .filter(tuple -> batch.bundles().containsKey(tuple.patientId))
                .doOnDiscard(ResourceWithPatientId.class, tuple ->
                        logger.warn("Ignoring resource {} referencing patient {} not in batch", tuple.resource.getId(), tuple.patientId))
                .doOnNext(tuple -> {
                    PatientResourceBundle bundle = mutableBundles.get(tuple.patientId);
                    if (profileMustHaveChecker.fulfilled(tuple.resource, group)) {
                        safeGroup.add(tuple.patientId);
                        bundle.put(tuple.resource, group.id(), true);
                    } else {
                        logger.trace("Resource {} has not fulfilled must have checks for group {}", tuple.resource.getId(), group.id());
                        bundle.put(tuple.resource, group.id(), false);
                    }
                })
                .doOnTerminate(() -> safeSet.retainAll(safeGroup))
                .then(Mono.just(new PatientBatchWithConsent(mutableBundles, batch.applyConsent()))); // Convert back to immutable
    }
}
