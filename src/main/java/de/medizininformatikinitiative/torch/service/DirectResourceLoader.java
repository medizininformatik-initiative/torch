package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.consent.ConsentValidator;
import de.medizininformatikinitiative.torch.diagnostics.BatchDiagnosticsAcc;
import de.medizininformatikinitiative.torch.diagnostics.CriterionKeys;
import de.medizininformatikinitiative.torch.diagnostics.MustHaveEvaluation;
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
    public DirectResourceLoader(DataStore dataStore,
                                DseMappingTreeBase dseMappingTreeBase,
                                ProfileMustHaveChecker profileMustHaveChecker,
                                ConsentValidator validator) {
        this.dataStore = requireNonNull(dataStore);
        this.consentValidator = requireNonNull(validator);
        this.dseMappingTreeBase = requireNonNull(dseMappingTreeBase);
        this.profileMustHaveChecker = requireNonNull(profileMustHaveChecker);
    }

    /**
     * Extracts resources grouped by Patient ID for a given batch.
     *
     * @param attributeGroups CRTDL to be applied on batch
     * @param batch           Batch of Patient IDs
     * @param acc             diagnostics accumulator (per batch)
     * @return Mono containing processed PatientBatchWithConsent
     */
    public Mono<PatientBatchWithConsent> directLoadPatientCompartment(
            List<AnnotatedAttributeGroup> attributeGroups,
            PatientBatchWithConsent batch,
            BatchDiagnosticsAcc acc) {

        logger.trace("Starting collectResourcesByPatientReference");
        logger.trace("Patients Received: {}", batch);

        return processBatchWithConsent(attributeGroups, batch, acc);
    }

    private Mono<PatientBatchWithConsent> processBatchWithConsent(
            List<AnnotatedAttributeGroup> attributeGroups,
            PatientBatchWithConsent patientBatchWithConsent,
            BatchDiagnosticsAcc acc) {

        Set<String> safeSet = new ConcurrentSkipListSet<>(patientBatchWithConsent.patientBatch().ids());

        return processPatientAttributeGroups(attributeGroups, patientBatchWithConsent, safeSet, acc)
                .doOnNext(__ -> {
                    logger.debug("{} out of {} patients passed checks",
                            safeSet.size(),
                            patientBatchWithConsent.patientBatch().ids().size());
                    logger.trace("Surviving patient IDs: {}", String.join(", ", safeSet));
                })
                .map(__ -> patientBatchWithConsent.keep(safeSet));
    }

    private Flux<Query> groupQueries(AnnotatedAttributeGroup group) {
        return Flux.fromIterable(group.queries(dseMappingTreeBase, group.resourceType()));
    }

    Flux<DomainResource> executeQueryWithBatch(PatientBatch batch, Query query) {
        logger.debug("Execute query {} over {} patients", query, batch.ids().size());
        return dataStore.search(
                Query.of(
                        query.type(),
                        batch.compartmentSearchParam(query.type()).appendParams(query.params())
                ),
                DomainResource.class
        );
    }

    private Mono<DomainResource> applyConsent(DomainResource resource,
                                              PatientBatchWithConsent patientBatchWithConsent,
                                              BatchDiagnosticsAcc acc) {
        if (!patientBatchWithConsent.applyConsent() || consentValidator.checkConsent(resource, patientBatchWithConsent)) {
            return Mono.just(resource);
        }
        logger.debug("Consent Violated for Resource {} {}", resource.getResourceType(), resource.getId());
        acc.incResourcesExcluded(CriterionKeys.consentResourceBlocked(), 1);
        return Mono.empty();
    }

    public Mono<PatientBatchWithConsent> processPatientAttributeGroups(
            List<AnnotatedAttributeGroup> groups,
            PatientBatchWithConsent batch,
            Set<String> safeSet,
            BatchDiagnosticsAcc acc) {

        logger.debug("Process {} patient attribute groups over {} patients...",
                groups.size(), batch.patientBatch().ids().size());

        return Flux.fromIterable(groups)
                .concatMap(group -> processPatientSingleAttributeGroup(group, batch, safeSet, acc))
                .then().thenReturn(batch);
    }

    public Mono<ResourceBundle> processCoreAttributeGroups(
            List<AnnotatedAttributeGroup> attributeGroups,
            ResourceBundle coreResourceBundle,
            BatchDiagnosticsAcc acc) {

        return Flux.fromIterable(attributeGroups)
                .concatMap(group -> processCoreAttributeGroup(group, coreResourceBundle, acc))
                .then().thenReturn(coreResourceBundle);
    }

    public Mono<Void> processCoreAttributeGroup(AnnotatedAttributeGroup group,
                                                ResourceBundle resourceBundle,
                                                BatchDiagnosticsAcc acc) {
        logger.debug("Process core attribute group {}...", group.id());

        AtomicBoolean atLeastOneResource = new AtomicBoolean(!group.hasMustHave());

        return groupQueries(group)
                .flatMap(query -> dataStore.search(query, DomainResource.class), 1)
                .flatMap(resource -> {
                    MustHaveEvaluation eval = profileMustHaveChecker.evaluateFirst(resource, group);
                    boolean valid = eval instanceof MustHaveEvaluation.Fulfilled;

                    if (valid) {
                        atLeastOneResource.set(true);
                    } else if (eval instanceof MustHaveEvaluation.Violated v) {
                        acc.incResourcesExcluded(CriterionKeys.mustHaveAttribute(group, v.firstViolated()), 1);
                    }

                    resourceBundle.put(resource, group.id(), valid);
                    return Mono.empty();
                })
                .then(Mono.defer(() -> {
                    if (atLeastOneResource.get()) {
                        return Mono.empty();
                    }
                    logger.trace("MustHave violated for group: {}", group.groupReference());
                    return Mono.error(new MustHaveViolatedException(
                            "MustHave requirement violated for group: " + group.id()));
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

    private Mono<PatientBatchWithConsent> processPatientSingleAttributeGroup(AnnotatedAttributeGroup group,
                                                                             PatientBatchWithConsent batch,
                                                                             Set<String> safeSet,
                                                                             BatchDiagnosticsAcc acc) {
        logger.debug("Process patient attribute group {}...", group.id());

        Set<String> safeGroup = new HashSet<>();

        // snapshot of who is currently alive before this group executes
        Set<String> aliveBeforeGroup = new HashSet<>(safeSet);

        if (!group.hasMustHave()) {
            safeGroup.addAll(batch.patientBatch().ids());
            logger.trace("Group has no must-have constraints, initial safe group: {}", safeGroup);
        }

        Map<String, PatientResourceBundle> mutableBundles = batch.bundles();

        return groupQueries(group)
                .concatMap(query -> executeQueryWithBatch(batch.patientBatch(), query))
                .concatMap(resource -> applyConsent(resource, batch, acc))
                .mapNotNull(DirectResourceLoader::extractPatientId)
                .filter(tuple -> batch.bundles().containsKey(tuple.patientId))
                .doOnDiscard(ResourceWithPatientId.class, tuple ->
                        logger.warn("Ignoring resource {} referencing patient {} not in batch",
                                tuple.resource.getId(), tuple.patientId))
                .doOnNext(tuple -> {
                    PatientResourceBundle bundle = mutableBundles.get(tuple.patientId);

                    MustHaveEvaluation eval = profileMustHaveChecker.evaluateFirst(tuple.resource, group);

                    // Not applicable (profile mismatch etc.) -> not counted as must-have failure
                    if (eval instanceof MustHaveEvaluation.NotApplicable) {
                        bundle.put(tuple.resource, group.id(), false);
                        return;
                    }

                    if (eval instanceof MustHaveEvaluation.Fulfilled) {
                        safeGroup.add(tuple.patientId);
                        bundle.put(tuple.resource, group.id(), true);
                    } else if (eval instanceof MustHaveEvaluation.Violated v) {
                        bundle.put(tuple.resource, group.id(), false);
                        acc.incResourcesExcluded(CriterionKeys.mustHaveAttribute(group, v.firstViolated()), 1);
                    }
                })
                .doFinally(signal -> {
                    if (group.hasMustHave()) {
                        Set<String> removed = new HashSet<>(aliveBeforeGroup);
                        removed.removeAll(safeGroup);

                        if (!removed.isEmpty()) {
                            acc.incPatientsExcluded(CriterionKeys.mustHaveGroup(group), removed.size());
                        }
                    }
                    safeSet.retainAll(safeGroup);
                })
                .then().thenReturn(new PatientBatchWithConsent(
                        mutableBundles,
                        batch.applyConsent(),
                        batch.coreBundle(),
                        batch.id()
                ));
    }
}
