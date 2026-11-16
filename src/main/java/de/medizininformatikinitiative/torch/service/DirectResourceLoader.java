package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.consent.ConsentCheckResult;
import de.medizininformatikinitiative.torch.consent.ConsentValidator;
import de.medizininformatikinitiative.torch.diagnostics.MustHaveEvaluation;
import de.medizininformatikinitiative.torch.diagnostics.consent.ConsentConsideredResourceEvent;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.BatchExclusions;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.PatientExclusionStage;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.management.PatientLinkResolver;
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
import org.hl7.fhir.r4.model.Patient;
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
    private final PatientLinkResolver patientLinkResolver;

    @Autowired
    public DirectResourceLoader(DataStore dataStore,
                                DseMappingTreeBase dseMappingTreeBase,
                                ProfileMustHaveChecker profileMustHaveChecker,
                                ConsentValidator validator,
                                PatientLinkResolver patientLinkResolver) {
        this.dataStore = requireNonNull(dataStore);
        this.patientLinkResolver = requireNonNull(patientLinkResolver);
        this.consentValidator = requireNonNull(validator);
        this.dseMappingTreeBase = requireNonNull(dseMappingTreeBase);
        this.profileMustHaveChecker = requireNonNull(profileMustHaveChecker);
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

    private Mono<PatientBatchWithConsent> processBatchWithConsent(
            List<AnnotatedAttributeGroup> attributeGroups,
            PatientBatchWithConsent patientBatchWithConsent) {

        Set<String> safeSet = new ConcurrentSkipListSet<>(patientBatchWithConsent.patientBatch().ids());

        return processPatientAttributeGroups(attributeGroups, patientBatchWithConsent, safeSet)
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
        return Flux.defer(() -> dataStore.search(
                Query.of(
                        query.type(),
                        batch.compartmentSearchParam(patientLinkResolver.searchParam(query.type()))
                                .appendParams(query.params())
                ),
                DomainResource.class
        ));
    }

    private Mono<ResourceWithPatientId> applyConsent(ResourceWithPatientId tuple,
                                                     PatientBatchWithConsent patientBatchWithConsent,
                                                     AnnotatedAttributeGroup group) {
        if (!patientBatchWithConsent.applyConsent()) {
            return Mono.just(tuple);
        }
        DomainResource resource = tuple.resource();

        ConsentCheckResult checkResult = consentValidator.checkConsent(resource, tuple.patientId(), patientBatchWithConsent);

        // on the common included+diagnostics-disabled path, avoid deriving the resourceUrl purely to feed a
        // considered-resource event that would no-op
        boolean diagnosticsEnabled = patientBatchWithConsent.diagnostics().consentDiagnostics().isEnabled();
        if (checkResult.included() && !diagnosticsEnabled) {
            return Mono.just(tuple);
        }

        String resourceUrl = ResourceUtils.getRelativeURL(resource).toRelativeUrl();

        if (diagnosticsEnabled) {
            patientBatchWithConsent.diagnostics().consentDiagnostics().addConsideredResource(
                    new ConsentConsideredResourceEvent(tuple.patientId(), resourceUrl, checkResult.included(),
                            checkResult.consideredDate().map(Object::toString).orElse("")));
        }

        if (checkResult.included()) {
            return Mono.just(tuple);
        }

        logger.trace("Consent Violated for Resource {} {}", resource.getResourceType(), resource.getId());
        patientBatchWithConsent.batchExclusions().addConsentExclusion(group.id(), resourceUrl, tuple.patientId(),
                checkResult.outcome().toString());
        return Mono.empty();
    }

    public Mono<PatientBatchWithConsent> processPatientAttributeGroups(
            List<AnnotatedAttributeGroup> groups,
            PatientBatchWithConsent batch,
            Set<String> safeSet) {

        logger.debug("Process {} patient attribute groups over {} patients...",
                groups.size(), batch.patientBatch().ids().size());

        return Flux.fromIterable(groups)
                .concatMap(group -> processPatientSingleAttributeGroup(group, batch, safeSet))
                .then().thenReturn(batch);
    }

    public Mono<ResourceBundle> processCoreAttributeGroups(
            List<AnnotatedAttributeGroup> attributeGroups,
            ResourceBundle coreResourceBundle,
            BatchExclusions batchExclusions) {

        return Flux.fromIterable(attributeGroups)
                .concatMap(group -> processCoreAttributeGroup(group, coreResourceBundle, batchExclusions))
                .then().thenReturn(coreResourceBundle);
    }

    public Mono<Void> processCoreAttributeGroup(AnnotatedAttributeGroup group,
                                                ResourceBundle resourceBundle,
                                                BatchExclusions batchExclusions) {
        logger.debug("Process core attribute group {}...", group.id());

        AtomicBoolean fulfillsMustHave = new AtomicBoolean(!group.hasMustHave());

        return groupQueries(group)
                .flatMap(query -> dataStore.search(query, DomainResource.class), 1)
                .flatMap(resource -> {
                    MustHaveEvaluation eval = profileMustHaveChecker.evaluateFirst(resource, group);
                    boolean valid = eval instanceof MustHaveEvaluation.Fulfilled;

                    if (valid) {
                        fulfillsMustHave.set(true);
                    } else if (eval instanceof MustHaveEvaluation.Violated v) {
                        batchExclusions.addMustHaveExclusionCore(group.id(),
                                ResourceUtils.getRelativeURL(resource).toRelativeUrl(), v.firstViolated().attributeRef());
                    }

                    resourceBundle.put(resource, group.id(), valid);
                    return Mono.empty();
                })
                .then(Mono.defer(() -> {
                    if (fulfillsMustHave.get()) {
                        return Mono.empty();
                    }
                    logger.trace("MustHave violated for group: {}", group.id());
                    return Mono.error(new MustHaveViolatedException.GroupViolated(
                            "MustHave requirement violated for group: " + group.id()));
                }));
    }

    /**
     * Assigns a loaded resource to the single patient it references. A resource referencing no patient or several
     * distinct patients is recorded as a {@code PATIENT_NOT_FOUND} or {@code MULTIPLE_PATIENTS} exclusion and dropped
     * (returns {@code null}).
     */
    private ResourceWithPatientId extractPatientId(DomainResource resource, PatientBatchWithConsent batch,
                                                   AnnotatedAttributeGroup group) {
        List<String> patientIds = patientLinkResolver.patientIds(resource);
        if (patientIds.size() == 1) {
            return new ResourceWithPatientId(resource, patientIds.getFirst());
        }
        String resourceUrl = ResourceUtils.getRelativeURL(resource).toRelativeUrl();
        if (patientIds.isEmpty()) {
            logger.debug("Ignoring resource {} not referencing a patient", resource.getId());
            batch.batchExclusions().addPatientNotFoundExclusion(group.id(), resourceUrl);
        } else {
            logger.debug("Ignoring resource {} referencing several patients", resource.getId());
            batch.batchExclusions().addMultiplePatientsExclusion(group.id(), resourceUrl);
        }
        return null;
    }

    private record ResourceWithPatientId(DomainResource resource, String patientId) {
        private ResourceWithPatientId {
            requireNonNull(resource);
            requireNonNull(patientId);
        }
    }

    /**
     * Fetches all resources for a single attribute group in a batch and adds them to the patient bundles.
     *
     * @param group   Annotated Attribute Group to be processed
     * @param batch   patient batch containing the PatientResourceBundles to be filled
     * @param safeSet patients that have survived must-have checks so far
     * @return Patient batch containing a bundle per Patient Resource
     */
    private Mono<PatientBatchWithConsent> processPatientSingleAttributeGroup(AnnotatedAttributeGroup group,
                                                                             PatientBatchWithConsent batch,
                                                                             Set<String> safeSet) {
        logger.debug("Process patient attribute group {}...", group.id());

        Set<String> safeGroup = new HashSet<>();

        // snapshot of who is currently alive before this group executes
        Set<String> aliveBeforeGroup = new HashSet<>(safeSet);

        if (!group.hasMustHave()) {
            safeGroup.addAll(batch.patientBatch().ids());
            logger.trace("Group has no must-have constraints, initial safe group: {}", safeGroup);
        }

        Map<String, PatientResourceBundle> mutableBundles = batch.bundles();

        var resourceFlux = groupQueries(group)
                .concatMap(query -> executeQueryWithBatch(batch.patientBatch(), query))
                .mapNotNull(resource -> extractPatientId(resource, batch, group))
                .concatMap(tuple -> applyConsent(tuple, batch, group));

        if (AnnotatedAttributeGroup.PATIENT.equals(group.resourceType())) {
            String targetProfile = group.groupReference();
            resourceFlux = resourceFlux.doOnNext(tuple -> {
                ((Patient) tuple.resource()).getMeta().getProfile().clear();
                ((Patient) tuple.resource()).getMeta().addProfile(targetProfile);
            });
        }

        return resourceFlux
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
                        batch.batchExclusions().addMustHaveExclusion(group.id(),
                                ResourceUtils.getRelativeURL(tuple.resource).toRelativeUrl(), v.firstViolated().attributeRef(),
                                tuple.patientId);
                    }
                })
                .then(Mono.fromRunnable(() -> {
                    if (group.hasMustHave()) {
                        Set<String> removed = new HashSet<>(aliveBeforeGroup);
                        removed.removeAll(safeGroup);

                        if (!removed.isEmpty()) {
                            removed.forEach(pat -> batch.batchExclusions().addPatientExclusion(PatientExclusionStage.DIRECT_LOAD, pat));
                        }
                    }
                    safeSet.retainAll(safeGroup);
                })).thenReturn(new PatientBatchWithConsent(
                        mutableBundles,
                        batch.applyConsent(),
                        batch.coreBundle(),
                        batch.id(),
                        batch.diagnostics()
                ));
    }

}
