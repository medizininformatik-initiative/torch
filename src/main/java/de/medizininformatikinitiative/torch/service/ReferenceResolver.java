package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.diagnostics.BatchDiagnosticsAcc;
import de.medizininformatikinitiative.torch.diagnostics.CriterionKeys;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionId;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ReferenceWrapper;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import de.medizininformatikinitiative.torch.util.ReferenceExtractor;
import de.medizininformatikinitiative.torch.util.ReferenceHandler;
import de.medizininformatikinitiative.torch.util.ResourceUtils;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Resolves inter-resource references for patient and core bundles.
 *
 * <p>References are extracted, fetched if missing, cached into bundles,
 * and validated according to attribute-group and compartment rules.</p>
 *
 * <p>This implementation mutates bundles in place (as before), but additionally records diagnostics
 * (missing references, processing-mode mismatches, etc.) into a {@link BatchDiagnosticsAcc}.</p>
 */
@Component
public class ReferenceResolver {
    private static final Logger logger = LoggerFactory.getLogger(ReferenceResolver.class);

    private final ReferenceExtractor referenceExtractor;
    private final CompartmentManager compartmentManager;
    private final ReferenceHandler referenceHandler;
    private final ReferenceBundleLoader bundleLoader;

    public ReferenceResolver(CompartmentManager compartmentManager,
                             ReferenceHandler referenceHandler,
                             ReferenceExtractor referenceExtractor,
                             ReferenceBundleLoader bundleLoader) {
        this.referenceExtractor = referenceExtractor;
        this.compartmentManager = compartmentManager;
        this.referenceHandler = referenceHandler;
        this.bundleLoader = bundleLoader;
    }

    // -------------------------------------------------------------------------
    // Public API (core + patient)
    // -------------------------------------------------------------------------

    Mono<ResourceBundle> resolveCoreBundle(ResourceBundle coreBundle, Map<String, AnnotatedAttributeGroup> groupMap) {
        return Mono.just(coreBundle.getValidResourceGroups())
                .map(groups -> groups.stream()
                        .filter(resourceGroup -> !compartmentManager.isInCompartment(resourceGroup))
                        .collect(Collectors.toSet()))
                .expand(currentGroupSet ->
                        resolveUnknownCoreRefs(currentGroupSet, coreBundle, groupMap))
                .then().thenReturn(coreBundle);
    }

    Mono<ResourceBundle> resolveCoreBundle(ResourceBundle coreBundle,
                                           Map<String, AnnotatedAttributeGroup> groupMap,
                                           BatchDiagnosticsAcc acc) {
        return Mono.just(coreBundle.getValidResourceGroups())
                .map(groups -> groups.stream()
                        .filter(resourceGroup -> !compartmentManager.isInCompartment(resourceGroup))
                        .collect(Collectors.toSet()))
                .expand(currentGroupSet ->
                        resolveUnknownCoreRefs(currentGroupSet, coreBundle, groupMap, acc))
                .then().thenReturn(coreBundle);
    }

    Mono<PatientBatchWithConsent> resolvePatientBatch(
            PatientBatchWithConsent batch,
            Map<String, AnnotatedAttributeGroup> groupMap,
            BatchDiagnosticsAcc acc
    ) {
        var RGsPerPat = batch.bundles().entrySet().stream()
                .map(entry -> Map.entry(
                        entry.getKey(),
                        entry.getValue().getValidResourceGroups().stream()
                                .filter(compartmentManager::isInCompartment)
                                .collect(Collectors.toSet())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // expand is used only to recursively resolve in place (mutating the bundles)
        return Mono.just(RGsPerPat)
                .expand(f -> resolveUnknownPatientBatchRefs(f, batch, groupMap, acc))
                .then().thenReturn(new PatientBatchWithConsent(batch.bundles(), batch.applyConsent(), batch.coreBundle(), batch.id()));
    }

    // -------------------------------------------------------------------------
    // Recursive resolution (core)
    // -------------------------------------------------------------------------

    public Flux<Set<ResourceGroup>> resolveUnknownCoreRefs(
            Set<ResourceGroup> coreRGs,
            ResourceBundle coreBundle,
            Map<String, AnnotatedAttributeGroup> groupMap
    ) {
        var refsPerRG = loadReferencesByResourceGroup(coreRGs, null, coreBundle, groupMap);
        var unresolvedRefsPerLinkedGroup = refsPerRG.entrySet().stream()
                .flatMap(e -> e.getValue().stream()
                        .flatMap(wrapper -> wrapper.refAttribute().linkedGroups().stream()
                                .map(linkedGroupId -> Map.entry(linkedGroupId, wrapper))))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> List.of(e.getValue()),
                        (a, b) -> {
                            List<ReferenceWrapper> merged = new ArrayList<>(a);
                            merged.addAll(b);
                            return merged;
                        }
                ));

        return Flux.fromIterable(unresolvedRefsPerLinkedGroup.entrySet())
                .concatMap(e -> {
                    var linkedGroupID = e.getKey();
                    var unknownWrappers = e.getValue();
                    var unknownRefs = getRefsFromWrappers(unknownWrappers);

                    return bundleLoader.fetchUnknownResources(unknownRefs, linkedGroupID, groupMap)
                            .map(fetchedResources -> cacheNewCoreResources(fetchedResources, coreBundle))
                            .map(fetchedResources -> setUnloadedAsInvalidCore(
                                    fetchedResources, unknownRefs, linkedGroupID, coreBundle))
                            .doOnNext(this::logMissingRefs);
                })
                .thenMany(
                        Flux.fromIterable(refsPerRG.entrySet())
                                .concatMap(refsOfRg -> referenceHandler.handleReferences(
                                        refsOfRg.getValue(),
                                        null,
                                        coreBundle,
                                        groupMap,
                                        coreBundle.getValidResourceGroups()))
                                .collect(Collectors.toSet())
                )
                .filter(map -> !map.isEmpty());
    }

    public Flux<Set<ResourceGroup>> resolveUnknownCoreRefs(
            Set<ResourceGroup> coreRGs,
            ResourceBundle coreBundle,
            Map<String, AnnotatedAttributeGroup> groupMap,
            BatchDiagnosticsAcc acc
    ) {
        var refsPerRG = loadReferencesByResourceGroup(coreRGs, null, coreBundle, groupMap, acc);
        var unresolvedRefsPerLinkedGroup = refsPerRG.entrySet().stream()
                .flatMap(e -> e.getValue().stream()
                        .flatMap(wrapper -> wrapper.refAttribute().linkedGroups().stream()
                                .map(linkedGroupId -> Map.entry(linkedGroupId, wrapper))))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> List.of(e.getValue()),
                        (a, b) -> {
                            List<ReferenceWrapper> merged = new ArrayList<>(a);
                            merged.addAll(b);
                            return merged;
                        }
                ));

        return Flux.fromIterable(unresolvedRefsPerLinkedGroup.entrySet())
                .concatMap(e -> {
                    var linkedGroupID = e.getKey();
                    var unknownWrappers = e.getValue();
                    var unknownRefs = getRefsFromWrappers(unknownWrappers);

                    return bundleLoader.fetchUnknownResources(unknownRefs, linkedGroupID, groupMap)
                            .map(fetchedResources -> cacheNewCoreResources(fetchedResources, coreBundle))
                            .map(fetchedResources -> setUnloadedAsInvalidCore(
                                    fetchedResources, unknownRefs, linkedGroupID, coreBundle, acc))
                            .doOnNext(this::logMissingRefs);
                })
                .thenMany(
                        Flux.fromIterable(refsPerRG.entrySet())
                                .concatMap(refsOfRg -> referenceHandler.handleReferences(
                                        refsOfRg.getValue(),
                                        null,
                                        coreBundle,
                                        groupMap,
                                        coreBundle.getValidResourceGroups()))
                                .collect(Collectors.toSet())
                )
                .filter(map -> !map.isEmpty());
    }

    // -------------------------------------------------------------------------
    // Recursive resolution (patient batch)
    // -------------------------------------------------------------------------

    public Mono<Map<String, Set<ResourceGroup>>> resolveUnknownPatientBatchRefs(
            Map<String, Set<ResourceGroup>> RGsPerPat,
            PatientBatchWithConsent batch,
            Map<String, AnnotatedAttributeGroup> groupMap,
            BatchDiagnosticsAcc acc
    ) {
        Map<String, Map<ReferenceWrapper, String>> refToPatHelper = new HashMap<>();
        Map<String, Map<ResourceGroup, List<ReferenceWrapper>>> refsPerPatPerRG = new HashMap<>();

        Stream<Map.Entry<String, ReferenceWrapper>> entryStream = RGsPerPat.entrySet().stream().flatMap(patEntry -> {
            var patID = patEntry.getKey();
            var patRGs = patEntry.getValue();
            var patientBundle = batch.bundles().get(patID);

            var refs = loadReferencesByResourceGroup(patRGs, patientBundle, batch.coreBundle(), groupMap, acc);
            refsPerPatPerRG.put(patID, refs);

            return refs.entrySet().stream().flatMap(rgEntry ->
                    rgEntry.getValue().stream().flatMap(wrapper ->
                            wrapper.refAttribute().linkedGroups().stream().map(linkedGroupId -> {
                                refToPatHelper
                                        .computeIfAbsent(linkedGroupId, x -> new HashMap<>())
                                        .putIfAbsent(wrapper, patID);
                                return Map.entry(linkedGroupId, wrapper);
                            })
                    )
            );
        });

        Map<String, List<ReferenceWrapper>> unresolvedRefsPerLinkedGroup =
                entryStream.collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> List.of(e.getValue()),
                        (a, b) -> {
                            List<ReferenceWrapper> merged = new ArrayList<>(a);
                            merged.addAll(b);
                            return merged;
                        }));

        Flux<Map.Entry<String, Set<ResourceGroup>>> newRGsPerPat =
                Flux.fromIterable(unresolvedRefsPerLinkedGroup.entrySet())
                        .concatMap(e -> {
                            String linkedGroupID = e.getKey();
                            List<ReferenceWrapper> unknownWrappers = e.getValue();
                            var unknownRefs = getRefsFromWrappers(unknownWrappers);
                            var refsToPat = refToPatHelper.get(linkedGroupID);

                            return bundleLoader.fetchUnknownResources(unknownRefs, linkedGroupID, groupMap)
                                    .map(fetchedResources -> cacheNewResourcesFromPatient(fetchedResources, refsToPat, batch))
                                    .map(fetchedResources -> setUnloadedAsInvalid(
                                            fetchedResources, refsToPat, unknownRefs, linkedGroupID, batch, acc))
                                    .doOnNext(this::logMissingRefs);
                        })
                        .thenMany(
                                Flux.fromIterable(refsPerPatPerRG.entrySet())
                                        .concatMap(e -> handleReferencesForPatient(e.getKey(), e.getValue(), batch, groupMap))
                        );

        return newRGsPerPat.collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> {
                            Set<ResourceGroup> merged = new HashSet<>(a);
                            merged.addAll(b);
                            return merged;
                        }
                ))
                .filter(map -> !map.isEmpty());
    }

    // -------------------------------------------------------------------------
    // Cache + invalidation helpers
    // -------------------------------------------------------------------------

    private List<Resource> cacheNewResourcesFromPatient(
            List<Resource> resources,
            Map<ReferenceWrapper, String> refToPatHelper,
            PatientBatchWithConsent batch
    ) {
        Map<ExtractionId, String> refToPatient = refToPatHelper.entrySet().stream()
                .flatMap(e -> e.getKey().references().stream().map(ref -> Map.entry(ref, e.getValue())))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a
                ));

        resources.forEach(resource -> {
            ExtractionId ref = ResourceUtils.getRelativeURL(resource);
            String patID = refToPatient.get(ref);
            if (patID != null) {
                bundleLoader.cacheSearchResults(
                        batch.bundles().get(patID),
                        batch.coreBundle(),
                        batch.applyConsent(),
                        resource
                );
            }
        });

        return resources;
    }

    private Set<ExtractionId> setUnloadedAsInvalid(
            List<Resource> fetchedResources,
            Map<ReferenceWrapper, String> refToPatHelper,
            List<ExtractionId> expectedRefs,
            String linkedGroupID,
            PatientBatchWithConsent batch,
            BatchDiagnosticsAcc acc
    ) {
        Set<ExtractionId> notLoaded = new HashSet<>(expectedRefs);
        fetchedResources.stream()
                .map(r -> new ExtractionId(r.getResourceType().toString(), r.getIdPart()))
                .forEach(notLoaded::remove);

        // diagnostics: count missing ref targets
        notLoaded.forEach(missingRef -> acc.incResourcesExcluded(
                CriterionKeys.referenceNotFound(missingRef.resourceType()), 1));

        // invalidate RGs for all missing refs per patient
        refToPatHelper.forEach((wrapper, patID) -> wrapper.references().forEach(unknownRef -> {
            if (notLoaded.contains(unknownRef)) {
                ResourceGroup resourceGroup = new ResourceGroup(unknownRef, linkedGroupID);
                batch.bundles().get(patID).bundle().addResourceGroupValidity(resourceGroup, false);
            }
        }));

        return notLoaded;
    }

    private Set<ExtractionId> setUnloadedAsInvalidCore(
            List<Resource> fetchedResources,
            List<ExtractionId> expectedRefs,
            String linkedGroupID,
            ResourceBundle coreBundle
    ) {
        Set<ExtractionId> notLoaded = new HashSet<>(expectedRefs);
        fetchedResources.stream()
                .map(r -> new ExtractionId(r.getResourceType().toString(), r.getIdPart()))
                .forEach(notLoaded::remove);

        notLoaded.forEach(missingRef -> {
            ResourceGroup resourceGroup = new ResourceGroup(missingRef, linkedGroupID);
            coreBundle.addResourceGroupValidity(resourceGroup, false);
        });

        return notLoaded;
    }

    private Set<ExtractionId> setUnloadedAsInvalidCore(
            List<Resource> fetchedResources,
            List<ExtractionId> expectedRefs,
            String linkedGroupID,
            ResourceBundle coreBundle,
            BatchDiagnosticsAcc acc
    ) {
        Set<ExtractionId> notLoaded = new HashSet<>(expectedRefs);
        fetchedResources.stream()
                .map(r -> new ExtractionId(r.getResourceType().toString(), r.getIdPart()))
                .forEach(notLoaded::remove);

        notLoaded.forEach(missingRef -> {
            ResourceGroup resourceGroup = new ResourceGroup(missingRef, linkedGroupID);
            coreBundle.addResourceGroupValidity(resourceGroup, false);

            acc.incResourcesExcluded(CriterionKeys.referenceNotFound(missingRef.resourceType()), 1);
        });

        return notLoaded;
    }

    private List<Resource> cacheNewCoreResources(List<Resource> fetchedResources, ResourceBundle coreBundle) {
        fetchedResources.forEach(r -> bundleLoader.cacheSearchResults(null, coreBundle, false, r));
        return fetchedResources;
    }

    private void logMissingRefs(Set<ExtractionId> missingRefs) {
        if (!missingRefs.isEmpty()) {
            logger.warn("Some references were not loaded: {}", missingRefs);
        }
    }

    // -------------------------------------------------------------------------
    // Reference handling
    // -------------------------------------------------------------------------

    private Mono<Map.Entry<String, Set<ResourceGroup>>> handleReferencesForPatient(
            String patID,
            Map<ResourceGroup, List<ReferenceWrapper>> refsPerParentRG,
            PatientBatchWithConsent batch,
            Map<String, AnnotatedAttributeGroup> groupMap
    ) {
        var patientBundle = batch.bundles().get(patID);

        var newValidRGs = Flux.fromIterable(refsPerParentRG.values())
                .concatMap(refsOfParentRg -> referenceHandler.handleReferences(
                        refsOfParentRg,
                        patientBundle,
                        batch.coreBundle(),
                        groupMap,
                        patientBundle.getValidResourceGroups()))
                .collect(Collectors.toSet())
                .filter(s -> !s.isEmpty());

        return newValidRGs.map(RGs -> Map.entry(patID, RGs));
    }

    private List<ExtractionId> getRefsFromWrappers(List<ReferenceWrapper> wrappers) {
        return wrappers.stream().flatMap(wrapper -> wrapper.references().stream()).toList();
    }

    // -------------------------------------------------------------------------
    // Reference extraction per RG (acc-aware)
    // -------------------------------------------------------------------------

    public Map<ResourceGroup, List<ReferenceWrapper>> loadReferencesByResourceGroup(
            Set<ResourceGroup> resourceGroups,
            @Nullable PatientResourceBundle patientBundle,
            ResourceBundle coreBundle,
            Map<String, AnnotatedAttributeGroup> groupMap
    ) {
        return resourceGroups.parallelStream()
                .map(resourceGroup -> processResourceGroup(resourceGroup, patientBundle, coreBundle, groupMap))
                .filter(entry -> !entry.getValue().isEmpty())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (list1, list2) -> {
                            List<ReferenceWrapper> merged = new ArrayList<>(list1);
                            merged.addAll(list2);
                            return merged;
                        }
                ));
    }

    public Map<ResourceGroup, List<ReferenceWrapper>> loadReferencesByResourceGroup(
            Set<ResourceGroup> resourceGroups,
            @Nullable PatientResourceBundle patientBundle,
            ResourceBundle coreBundle,
            Map<String, AnnotatedAttributeGroup> groupMap,
            BatchDiagnosticsAcc acc
    ) {
        return resourceGroups.parallelStream()
                .map(resourceGroup -> processResourceGroup(resourceGroup, patientBundle, coreBundle, groupMap, acc))
                .filter(entry -> !entry.getValue().isEmpty())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (list1, list2) -> {
                            List<ReferenceWrapper> merged = new ArrayList<>(list1);
                            merged.addAll(list2);
                            return merged;
                        }
                ));
    }

    private Map.Entry<ResourceGroup, List<ReferenceWrapper>> processResourceGroup(
            ResourceGroup resourceGroup,
            @Nullable PatientResourceBundle patientBundle,
            ResourceBundle coreBundle,
            Map<String, AnnotatedAttributeGroup> groupMap
    ) {
        ResourceBundle processingBundle = (patientBundle == null) ? coreBundle : patientBundle.bundle();
        boolean isPatientResource = compartmentManager.isInCompartment(resourceGroup);

        if (isPatientResource && patientBundle == null) {
            return skipDueToMissingPatientBundle(resourceGroup, coreBundle);
        }

        Optional<Resource> resource = isPatientResource
                ? patientBundle.get(resourceGroup.resourceId())
                : coreBundle.get(resourceGroup.resourceId());

        return resource
                .map(value -> extractReferences(resourceGroup, value, groupMap, processingBundle))
                .orElseGet(() -> handleMissingResource(resourceGroup, processingBundle));
    }

    private Map.Entry<ResourceGroup, List<ReferenceWrapper>> processResourceGroup(
            ResourceGroup resourceGroup,
            @Nullable PatientResourceBundle patientBundle,
            ResourceBundle coreBundle,
            Map<String, AnnotatedAttributeGroup> groupMap,
            BatchDiagnosticsAcc acc
    ) {
        ResourceBundle processingBundle = (patientBundle == null) ? coreBundle : patientBundle.bundle();
        boolean isPatientResource = compartmentManager.isInCompartment(resourceGroup);

        if (isPatientResource && patientBundle == null) {
            return skipDueToMissingPatientBundle(resourceGroup, coreBundle, acc);
        }

        Optional<Resource> resource = isPatientResource
                ? patientBundle.get(resourceGroup.resourceId())
                : coreBundle.get(resourceGroup.resourceId());

        return resource
                .map(value -> extractReferences(resourceGroup, value, groupMap, processingBundle, acc))
                .orElseGet(() -> handleMissingResource(resourceGroup, processingBundle, acc));
    }

    private Map.Entry<ResourceGroup, List<ReferenceWrapper>> skipDueToMissingPatientBundle(
            ResourceGroup resourceGroup,
            ResourceBundle coreBundle
    ) {
        logger.warn("Skipping resourceGroup {}: Patient resource requires a PatientResourceBundle", resourceGroup);
        coreBundle.addResourceGroupValidity(resourceGroup, false);
        return Map.entry(resourceGroup, Collections.emptyList());
    }

    private Map.Entry<ResourceGroup, List<ReferenceWrapper>> skipDueToMissingPatientBundle(
            ResourceGroup resourceGroup,
            ResourceBundle coreBundle,
            BatchDiagnosticsAcc acc
    ) {
        logger.warn("Skipping resourceGroup {}: Patient resource requires a PatientResourceBundle", resourceGroup);
        coreBundle.addResourceGroupValidity(resourceGroup, false);

        acc.incResourcesExcluded(
                CriterionKeys.referenceOutsideBatch(resourceGroup.resourceId().resourceType()),
                1
        );

        return Map.entry(resourceGroup, Collections.emptyList());
    }

    private Map.Entry<ResourceGroup, List<ReferenceWrapper>> extractReferences(
            ResourceGroup resourceGroup,
            Resource resource,
            Map<String, AnnotatedAttributeGroup> groupMap,
            ResourceBundle processingBundle
    ) {
        try {
            List<ReferenceWrapper> extracted = referenceExtractor.extract(resource, groupMap, resourceGroup.groupId());
            return Map.entry(resourceGroup, extracted);
        } catch (MustHaveViolatedException e) {
            processingBundle.addResourceGroupValidity(resourceGroup, false);
            return Map.entry(resourceGroup, Collections.emptyList());
        }
    }

    private Map.Entry<ResourceGroup, List<ReferenceWrapper>> extractReferences(
            ResourceGroup resourceGroup,
            Resource resource,
            Map<String, AnnotatedAttributeGroup> groupMap,
            ResourceBundle processingBundle,
            BatchDiagnosticsAcc acc
    ) {
        try {
            List<ReferenceWrapper> extracted = referenceExtractor.extract(resource, groupMap, resourceGroup.groupId());
            return Map.entry(resourceGroup, extracted);
        } catch (MustHaveViolatedException e) {
            processingBundle.addResourceGroupValidity(resourceGroup, false);

            // NOTE: if MustHaveViolatedException later exposes the failing attribute, you can key it precisely.
            // For now, we only count "resource excluded due to must-have during ref extraction".
            acc.incResourcesExcluded(
                    CriterionKeys.mustHaveGroup(groupMap.get(resourceGroup.groupId())),
                    1
            );

            return Map.entry(resourceGroup, Collections.emptyList());
        }
    }

    private Map.Entry<ResourceGroup, List<ReferenceWrapper>> handleMissingResource(
            ResourceGroup resourceGroup,
            ResourceBundle processingBundle
    ) {
        logger.warn("Empty resource marked as valid for group {}", resourceGroup);
        processingBundle.addResourceGroupValidity(resourceGroup, false);
        return Map.entry(resourceGroup, Collections.emptyList());
    }

    private Map.Entry<ResourceGroup, List<ReferenceWrapper>> handleMissingResource(
            ResourceGroup resourceGroup,
            ResourceBundle processingBundle,
            BatchDiagnosticsAcc acc
    ) {
        logger.warn("Empty resource marked as valid for group {}", resourceGroup);
        processingBundle.addResourceGroupValidity(resourceGroup, false);

        acc.incResourcesExcluded(
                CriterionKeys.referenceNotFound(resourceGroup.resourceId().resourceType()),
                1
        );

        return Map.entry(resourceGroup, Collections.emptyList());
    }
}
