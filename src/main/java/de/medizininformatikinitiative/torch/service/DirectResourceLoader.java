package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.consent.ConsentValidator;
import de.medizininformatikinitiative.torch.diagnostics.ExclusionAcc;
import de.medizininformatikinitiative.torch.diagnostics.ExclusionKind;
import de.medizininformatikinitiative.torch.diagnostics.ExclusionRecord;
import de.medizininformatikinitiative.torch.diagnostics.MustHaveEvaluation;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
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
     * Returns the semicolon-joined attributeRef values of must-have attributes in the group.
     */
    private static String mustHaveAttributeRefs(AnnotatedAttributeGroup group) {
        return group.attributes().stream()
                .filter(AnnotatedAttribute::mustHave)
                .map(AnnotatedAttribute::attributeRef)
                .sorted()
                .reduce((a, b) -> a + ";" + b)
                .orElse(null);
    }

    private Mono<PatientBatchWithConsent> processBatchWithConsent(
            List<AnnotatedAttributeGroup> attributeGroups,
            PatientBatchWithConsent patientBatchWithConsent,
            ExclusionAcc writer) {

        Set<String> safeSet = new ConcurrentSkipListSet<>(patientBatchWithConsent.patientBatch().ids());

        return processPatientAttributeGroups(attributeGroups, patientBatchWithConsent, safeSet, writer)
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

    /**
     * Extracts resources grouped by Patient ID for a given batch.
     *
     * @param attributeGroups CRTDL to be applied on batch
     * @param batch           Batch of Patient IDs
     * @param writer          exclusionwriter
     * @return Mono containing processed PatientBatchWithConsent
     */
    public Mono<PatientBatchWithConsent> directLoadPatientCompartment(
            List<AnnotatedAttributeGroup> attributeGroups,
            PatientBatchWithConsent batch,
            ExclusionAcc writer) {

        logger.trace("Starting collectResourcesByPatientReference");
        logger.trace("Patients Received: {}", batch);

        return processBatchWithConsent(attributeGroups, batch, writer);
    }

    public Mono<PatientBatchWithConsent> processPatientAttributeGroups(
            List<AnnotatedAttributeGroup> groups,
            PatientBatchWithConsent batch,
            Set<String> safeSet,
            ExclusionAcc writer) {

        logger.debug("Process {} patient attribute groups over {} patients...",
                groups.size(), batch.patientBatch().ids().size());

        return Flux.fromIterable(groups)
                .concatMap(group -> processPatientSingleAttributeGroup(group, batch, safeSet, writer))
                .then().thenReturn(batch);
    }

    public Mono<ResourceBundle> processCoreAttributeGroups(
            List<AnnotatedAttributeGroup> attributeGroups,
            ResourceBundle coreResourceBundle,
            ExclusionAcc writer) {

        return Flux.fromIterable(attributeGroups)
                .concatMap(group -> processCoreAttributeGroup(group, coreResourceBundle, writer))
                .then().thenReturn(coreResourceBundle);
    }

    public Mono<Void> processCoreAttributeGroup(AnnotatedAttributeGroup group,
                                                ResourceBundle resourceBundle,
                                                ExclusionAcc writer) {
        logger.debug("Process core attribute group {}...", group.id());

        AtomicBoolean atLeastOneResource = new AtomicBoolean(!group.hasMustHave());

        return groupQueries(group)
                .flatMap(query -> dataStore.search(query, DomainResource.class), 1)
                .flatMap(resource -> {
                    MustHaveEvaluation eval = profileMustHaveChecker.evaluateFirst(resource, group);
                    boolean valid = eval instanceof MustHaveEvaluation.Fulfilled;

                    if (valid) {
                        atLeastOneResource.set(true);
                    } else if (eval instanceof MustHaveEvaluation.Violated(AnnotatedAttribute firstViolated)) {
                        String resourceId = resource.getResourceType() + "/" + resource.getIdPart();
                        writer.record(new ExclusionRecord(null, ExclusionKind.MUST_HAVE_FIELD,
                                group.groupReference(), resourceId, firstViolated.attributeRef()));
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

    private Mono<DomainResource> applyConsent(DomainResource resource,
                                              PatientBatchWithConsent patientBatchWithConsent,
                                              ExclusionAcc writer) {
        boolean allowed = !patientBatchWithConsent.applyConsent()
                || consentValidator.checkConsent(resource, patientBatchWithConsent);
        if (allowed) {
            return Mono.just(resource);
        }
        logger.debug("Consent Violated for Resource {} {}", resource.getResourceType(), resource.getId());
        String patientId = null;
        try {
            patientId = ResourceUtils.patientId(resource);
        } catch (PatientIdNotFoundException ignored) {
            //patient is present anyways making this catch effectively just a compiler level required catch,
            // our business logic catches it before.
        }
        String resourceId = resource.getResourceType() + "/" + resource.getIdPart();
        writer.record(new ExclusionRecord(patientId, ExclusionKind.CONSENT, null, resourceId, null));
        return Mono.empty();
    }

    /**
     * Fetches all resources for a single attribute group in a batch and adds them to the patient bundles.
     *
     * @param group   Annotated Attribute Group to be processed
     * @param batch   patient batch containing the PatientResourceBundles to be filled
     * @param safeSet patients that have survived must-have checks so far
     * @param writer  ExclusionWriter
     * @return Patient batch containing a bundle per Patient Resource
     */
    private Mono<PatientBatchWithConsent> processPatientSingleAttributeGroup(
            AnnotatedAttributeGroup group,
            PatientBatchWithConsent batch,
            Set<String> safeSet,
            ExclusionAcc writer) {

        logger.debug("Process patient attribute group {}...", group.id());

        Set<String> safeGroup = new HashSet<>();
        Set<String> hadAnyResource = new HashSet<>();

        // snapshot of who is currently alive before this group executes
        Set<String> aliveBeforeGroup = new HashSet<>(safeSet);

        if (!group.hasMustHave()) {
            safeGroup.addAll(batch.patientBatch().ids());
            logger.trace("Group has no must-have constraints, initial safe group: {}", safeGroup);
        }

        Map<String, PatientResourceBundle> mutableBundles = batch.bundles();

        var resourceFlux = groupQueries(group)
                .concatMap(query -> executeQueryWithBatch(batch.patientBatch(), query))
                .concatMap(resource -> applyConsent(resource, batch, writer));

        if (AnnotatedAttributeGroup.PATIENT.equals(group.resourceType())) {
            String targetProfile = group.groupReference();
            resourceFlux = resourceFlux.map(resource -> {
                resource.getMeta().getProfile().clear();
                resource.getMeta().addProfile(targetProfile);
                return resource;
            });
        }

        return resourceFlux
                .mapNotNull(DirectResourceLoader::extractPatientId)
                .filter(tuple -> batch.bundles().containsKey(tuple.patientId))
                .doOnDiscard(ResourceWithPatientId.class, tuple ->
                        logger.warn("Ignoring resource {} referencing patient {} not in batch",
                                tuple.resource.getId(), tuple.patientId))
                .doOnNext(tuple -> {
                    PatientResourceBundle bundle = mutableBundles.get(tuple.patientId);

                    MustHaveEvaluation eval = profileMustHaveChecker.evaluateFirst(tuple.resource, group);

                    if (eval instanceof MustHaveEvaluation.NotApplicable) {
                        bundle.put(tuple.resource, group.id(), false);
                        return;
                    }

                    hadAnyResource.add(tuple.patientId);

                    if (eval instanceof MustHaveEvaluation.Fulfilled) {
                        safeGroup.add(tuple.patientId);
                        bundle.put(tuple.resource, group.id(), true);
                    } else if (eval instanceof MustHaveEvaluation.Violated(AnnotatedAttribute firstViolated)) {
                        bundle.put(tuple.resource, group.id(), false);
                        String resourceId = tuple.resource.getResourceType() + "/" + tuple.resource.getIdPart();
                        writer.record(new ExclusionRecord(tuple.patientId, ExclusionKind.MUST_HAVE_FIELD,
                                group.groupReference(), resourceId, firstViolated.attributeRef()));
                    }
                })
                .doFinally(signal -> {
                    if (group.hasMustHave()) {
                        Set<String> removed = new HashSet<>(aliveBeforeGroup);
                        removed.removeAll(safeGroup);

                        for (String patientId : removed) {
                            ExclusionKind kind = hadAnyResource.contains(patientId)
                                    ? ExclusionKind.MUST_HAVE_FIELD
                                    : ExclusionKind.MUST_HAVE_RESOURCE;
                            writer.record(new ExclusionRecord(patientId, kind,
                                    group.groupReference(), null, mustHaveAttributeRefs(group)));
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
