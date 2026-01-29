package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ReferenceWrapper;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import de.medizininformatikinitiative.torch.util.ReferenceExtractor;
import de.medizininformatikinitiative.torch.util.ReferenceHandler;
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
 * Service class responsible for resolving references within a PatientResourceBundle and the CoreBundle.
 */
@Component
public class ReferenceResolver {
    private static final Logger logger = LoggerFactory.getLogger(ReferenceResolver.class);

    private final ReferenceExtractor referenceExtractor;
    private final CompartmentManager compartmentManager;
    private final ReferenceHandler referenceHandler;
    private final ReferenceBundleLoader bundleLoader;

    /**
     * Constructs a ReferenceResolver with the necessary dependencies.
     *
     * @param compartmentManager for deciding if Resources are in the
     * @param referenceHandler   for handling extracted references
     * @param referenceExtractor for extracting references from cache or loading them from server
     * @param bundleLoader       for fetching Resources into the processed Bundles
     */
    public ReferenceResolver(CompartmentManager compartmentManager,
                             ReferenceHandler referenceHandler, ReferenceExtractor referenceExtractor, ReferenceBundleLoader bundleLoader) {
        this.referenceExtractor = referenceExtractor;
        this.compartmentManager = compartmentManager;
        this.referenceHandler = referenceHandler;
        this.bundleLoader = bundleLoader;
    }

    Mono<ResourceBundle> resolveCoreBundle(ResourceBundle coreBundle, Map<String, AnnotatedAttributeGroup> groupMap) {
        return Mono.just(coreBundle.getValidResourceGroups())
                .map(groups -> groups.stream()
                        .filter(resourceGroup -> !compartmentManager.isInCompartment(resourceGroup))
                        .collect(Collectors.toSet()))
                .expand(currentGroupSet ->
                        resolveUnknownCoreRefs(currentGroupSet, coreBundle, groupMap))
                .then(Mono.just(coreBundle));


    }

    Mono<PatientBatchWithConsent> resolvePatientBatch(
            PatientBatchWithConsent batch, Map<String, AnnotatedAttributeGroup> groupMap) {
        var RGsPerPat = batch.bundles().entrySet().stream().map(entry ->
                        Map.entry(entry.getKey(), entry.getValue()
                                .getValidResourceGroups().stream()
                                .filter(compartmentManager::isInCompartment)
                                .collect(Collectors.toSet())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // use expand only to recursively resolve in place (mutating the bundles) -> ignoring the return value
        return Mono.just(RGsPerPat).expand(f -> resolveUnknownPatientBatchRefs(f, batch, groupMap))
                .then(Mono.just(new PatientBatchWithConsent(batch.bundles(), batch.applyConsent(), batch.coreBundle())));
    }

    public Flux<Set<ResourceGroup>> resolveUnknownCoreRefs(
            Set<ResourceGroup> coreRGs,
            ResourceBundle coreBundle,
            Map<String, AnnotatedAttributeGroup> groupMap) {

        var refsPerRG = loadReferencesByResourceGroup(coreRGs, null, coreBundle, groupMap);
        var unresolvedRefsPerLinkedGroup = refsPerRG.entrySet().stream()
                .flatMap(e -> e.getValue().stream().flatMap(wrapper -> wrapper.refAttribute().linkedGroups().stream().map(linkedGroupId -> Map.entry(linkedGroupId, wrapper)))
                ).collect(Collectors.toMap(Map.Entry::getKey, e -> List.of(e.getValue()), (a, b) -> {
                    List<ReferenceWrapper> merged = new ArrayList<>(a);
                    merged.addAll(b);
                    return merged;
                }));

        return Flux.fromIterable(unresolvedRefsPerLinkedGroup.entrySet()).concatMap(e -> {
            var linkedGroupID = e.getKey();
            var unknownWrappers = e.getValue();
            var unknownRefs = getRefsFromWrappers(unknownWrappers);

            return bundleLoader.fetchUnknownResources(unknownRefs, linkedGroupID, groupMap)
                    .map(fetchedResources -> cacheNewCoreResources(fetchedResources, coreBundle))
                    .map(fetchedResources -> setUnloadedAsInvalidCore(fetchedResources, unknownRefs, linkedGroupID, coreBundle))
                    .doOnNext(this::logMissingRefs);
        }).thenMany(Flux.fromIterable(refsPerRG.entrySet()).concatMap(refsOfRg ->
                referenceHandler.handleReferences(
                        refsOfRg.getValue(),
                        null,
                        coreBundle,
                        groupMap,
                        coreBundle.getValidResourceGroups())
        ).collect(Collectors.toSet())).filter(map -> !map.isEmpty());

    }

    /**
     * Puts the newly fetched resources in the correct patient bundles or core bundle.
     *
     * @param resources      newly fetched resources to put into the patient bundles
     * @param refToPatHelper tells by which patients the resources is references
     * @param batch          the batch containing the patient bundles and core bundle to put cache the resources in
     * @return the unchanged resources for further processing
     */
    private List<Resource> cacheNewResourcesFromPatient(List<Resource> resources,
                                                        Map<ReferenceWrapper, String> refToPatHelper,
                                                        PatientBatchWithConsent batch) {
        return resources.stream().peek(resource -> {
            refToPatHelper.forEach((wrapper, patID) -> wrapper.references().forEach(unknownRef -> {
                if (unknownRef.equals(resource.getResourceType() + "/" + resource.getIdPart())) {
                    bundleLoader.cacheSearchResults(batch.bundles().get(patID), batch.coreBundle(), batch.applyConsent(), resource);
                }

            }));
        }).toList();
    }

    /**
     * Marks each resource that was not fetched as invalid.
     * <p>
     * The validity is later used in {@code ReferenceHandler.collectValidGroups()} to collect only the referenced
     * resources for further processing that have not been filtered out. If the resource was not fetched here, it
     * might still be in the bundle later because it might have been fetched by another attribute group (/ linked group).
     * The {@code ReferenceHandler.collectValidGroups()} would then find this resource and think it is part of this
     * linked group just because it exists in the bundle. Therefore, the combination of resource and linked group ID,
     * i.e. the resource group, is set as invalid here so the reference handler will know that the resource is "not part
     * of" this linked group.
     *
     * @param fetchedResources resources fetched using FHIRSearch with the filter of the linked group
     * @param expectedRefs     all resources that should have been fetched
     * @param linkedGroupID    ID of the linked group (used to apply the filter during the fetch)
     * @param batch            batch containing the patient bundles and core bundle to mark the resources as invalid in
     * @return the missing reference strings to be further processed
     */
    private Set<String> setUnloadedAsInvalid(List<Resource> fetchedResources,
                                             Map<ReferenceWrapper, String> refToPatHelper,
                                             List<String> expectedRefs,
                                             String linkedGroupID,
                                             PatientBatchWithConsent batch) {
        Set<String> notLoaded = new HashSet<>(expectedRefs);
        fetchedResources.stream().map(r -> r.getResourceType() + "/" + r.getIdPart()).forEach(notLoaded::remove);

        refToPatHelper.forEach((wrapper, patID) -> wrapper.references().forEach(unknownRef -> {
            if (notLoaded.contains(unknownRef)) {
                ResourceGroup resourceGroup = new ResourceGroup(unknownRef, linkedGroupID);

                // Set validity in patient bundle even if the referenced resource is a core resource. This is because
                // the (potential) core resource is invalid as a reference of a patient resource, meaning that later
                // this invalidity concerns the parent patient resource. And when cascading delete us done on a patient
                // resource, the patient bundle is used as "processingBundle".
                batch.bundles().get(patID).bundle().addResourceGroupValidity(resourceGroup, false);

            }
        }));

        return notLoaded;
    }

    /**
     * Marks each resource that was not fetched as invalid.
     * <p>
     * Works the same as {@link #setUnloadedAsInvalid(List, Map, List, String, PatientBatchWithConsent)} but here with
     * core resources only.
     *
     * @param fetchedResources resources fetched using FHIRSearch with the filter of the linked group
     * @param expectedRefs     all resources that should have been fetched
     * @param linkedGroupID    ID of the linked group (used to apply the filter during the fetch)
     * @param coreBundle       the core bundle to mark the resources as invalid in
     * @return the missing reference strings to be further processed
     */
    private Set<String> setUnloadedAsInvalidCore(List<Resource> fetchedResources,
                                                 List<String> expectedRefs,
                                                 String linkedGroupID,
                                                 ResourceBundle coreBundle) {
        Set<String> notLoaded = new HashSet<>(expectedRefs);
        fetchedResources.stream().map(r -> r.getResourceType() + "/" + r.getIdPart()).forEach(notLoaded::remove);

        notLoaded.forEach(missingRef -> {
            ResourceGroup resourceGroup = new ResourceGroup(missingRef, linkedGroupID);
            coreBundle.addResourceGroupValidity(resourceGroup, false);
        });

        return notLoaded;
    }

    private List<Resource> cacheNewCoreResources(List<Resource> fetchedResources, ResourceBundle coreBundle) {
        return fetchedResources.stream().peek(resource -> bundleLoader.cacheSearchResults(null, coreBundle, false, resource)).toList();
    }

    private void logMissingRefs(Set<String> missingRefs) {
        if (!missingRefs.isEmpty()) {
            logger.warn("Some references were not loaded: {}", missingRefs);
        }
    }

    /**
     * Calls the {@link ReferenceHandler} to set attribute- and group-validity and other information needed for cascading
     * delete later.
     *
     * @param patID           ID of the patient of the parent resource
     * @param refsPerParentRG map from parent resource group to references of that resource to handle
     * @param batch           batch containing the patient bundle and a core bundle
     * @param groupMap        map from groupID to corresponding {@link AnnotatedAttributeGroup}
     * @return map entry from patID and newly validated resources (as ResourceGroups) that were originally referenced
     */
    private Mono<Map.Entry<String, Set<ResourceGroup>>> handleReferencesForPatient(String patID, Map<ResourceGroup, List<ReferenceWrapper>> refsPerParentRG,
                                                                                   PatientBatchWithConsent batch, Map<String, AnnotatedAttributeGroup> groupMap) {
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

        // - the `.collect(Collectors.toSet())` makes sure a linked RG is later not processed multiple times
        // - because if two RGs reference the same linked RG, then it is sufficient to recursively resolve this referenced
        //   RG only once
        // - without the `toSet` operation, this linked RG would appear twice in the output due to how
        //   'referenceHandler.handleReferences()' works
        return newValidRGs.map(RGs -> Map.entry(patID, RGs));
    }

    private List<String> getRefsFromWrappers(List<ReferenceWrapper> wrappers) {
        return wrappers.stream().flatMap(wrapper -> wrapper.references().stream()).toList();
    }

    /**
     * Resolves references of each resource in {@code RGsPerPat}.
     * <p>
     * Reference strings are extracted and grouped by linked attribute group. All references of one group and one FHIR type are
     * fetched for all patient at once. Being grouped by attribute group enables to apply the filter of the attribute
     * group through FHIRSearch.
     * This method by itself is not recursive, so the returned resources are not yet resolved.
     * <p>
     * Since this method is called recursively, {@code RGsPerPat} might also contain core resources that were referenced
     * by a patient resource. Therefore, when two patients reference the same core resource, the core resource might
     * appear twice in the output, meaning that in the next recursive call it would be resolved twice. This should
     * not lead to problems though, other than potential performance impacts.
     *
     * @param RGsPerPat map from patient ID to a set of resources of which the references should be resolved
     * @param batch     batch containing compartment and core bundles for all patients of the batch
     * @param groupMap  map from attribute-group ID to the corresponding {@link AnnotatedAttributeGroup}
     * @return map from patient ID to set of newly fetched (i.e. still unresolved) resources to be recursively resolved
     */
    public Mono<Map<String, Set<ResourceGroup>>> resolveUnknownPatientBatchRefs(
            Map<String, Set<ResourceGroup>> RGsPerPat,
            PatientBatchWithConsent batch,
            Map<String, AnnotatedAttributeGroup> groupMap) {
        // When grouping by linked group, the connection from reference to patients will be lost.
        // So `refToPatHelper` is later used to find out which newly fetched resource is referenced by which patient in
        // order to put it into the correct patient bundle.
        // => unresolvedRefsPerLinkedGroup = likedGroupID -> [ReferenceWrapper1Pat1, ReferenceWrapper2Pat1, ReferenceWrapper1Pat2,...]
        // => refToPatHelper = likedGroupID -> ReferenceWrapper -> patID
        Map<String, Map<ReferenceWrapper, String>> refToPatHelper = new HashMap<>();
        Map<String, Map<ResourceGroup, List<ReferenceWrapper>>> refsPerPatPerRG = new HashMap<>();
        Stream<Map.Entry<String, ReferenceWrapper>> entryStream = RGsPerPat.entrySet().stream().flatMap(patEntry -> {
            var patID = patEntry.getKey();
            var patRGs = patEntry.getValue();
            var patientBundle = batch.bundles().get(patID);
            var refs = loadReferencesByResourceGroup(patRGs, patientBundle, batch.coreBundle(), groupMap);
            refsPerPatPerRG.put(patID, refs);
            return refs.entrySet().stream().flatMap(rgEntry ->
                    rgEntry.getValue().stream().flatMap(wrapper -> wrapper.refAttribute().linkedGroups().stream().map(linkedGroupId -> {
                        refToPatHelper
                                .computeIfAbsent(linkedGroupId, x -> new HashMap<>())
                                .computeIfAbsent(wrapper, x -> patID);

                        return Map.entry(linkedGroupId, wrapper);
                    }))
            );
        });
        Map<String, List<ReferenceWrapper>> unresolvedRefsPerLinkedGroup = entryStream.collect(Collectors.toMap(
                e -> e.getKey(),
                e -> List.of(e.getValue()),
                (a, b) -> {
                    List<ReferenceWrapper> merged = new ArrayList<>(a);
                    merged.addAll(b);
                    return merged;
                }));

        Flux<Map.Entry<String, Set<ResourceGroup>>> newRGsPerPat = Flux.fromIterable(unresolvedRefsPerLinkedGroup.entrySet()).concatMap(e -> {
            String linkedGroupID = e.getKey();
            List<ReferenceWrapper> unknownWrappers = e.getValue();
            var unknownRefs = getRefsFromWrappers(unknownWrappers);
            var refsToPat = refToPatHelper.get(linkedGroupID);

            return bundleLoader.fetchUnknownResources(unknownRefs, linkedGroupID, groupMap)
                    .map(fetchedResources -> cacheNewResourcesFromPatient(fetchedResources, refsToPat, batch))
                    .map(fetchedResources -> setUnloadedAsInvalid(fetchedResources, refsToPat, unknownRefs, linkedGroupID, batch))
                    .doOnNext(this::logMissingRefs);
        }).thenMany(
                Flux.fromIterable(refsPerPatPerRG.entrySet())
                        .concatMap(e -> handleReferencesForPatient(e.getKey(), e.getValue(), batch, groupMap)
                        ));

        return newRGsPerPat.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> {
            Set<ResourceGroup> merged = new HashSet<>(a);
            merged.addAll(b);
            return merged;
        })).filter(map -> !map.isEmpty());
    }


    /**
     * Extracts for every ResourceGroup the ReferenceWrappers and collects them ordered by
     *
     * @param resourceGroups valid ResourceGroups to be handled
     * @param patientBundle  bundle containing patient resources
     * @param coreBundle     bundle containing core resources
     * @param groupMap       map of known attribute groups
     * @return extracted ReferenceWrappers ordered by their respective ResourceGroup
     */
    public Map<ResourceGroup, List<ReferenceWrapper>> loadReferencesByResourceGroup(
            Set<ResourceGroup> resourceGroups,
            @Nullable PatientResourceBundle patientBundle,
            ResourceBundle coreBundle,
            Map<String, AnnotatedAttributeGroup> groupMap) {

        return resourceGroups.stream()
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

    /**
     * Extracts references for a single ResourceGroup
     *
     * @param resourceGroup resourceGroup to be handled
     * @param patientBundle bundle containing patient resources
     * @param coreBundle    bundle containing core resources
     * @param groupMap      all known attribute Groups
     * @return extracted References for a ResourceGroup
     */
    private Map.Entry<ResourceGroup, List<ReferenceWrapper>> processResourceGroup(
            ResourceGroup resourceGroup,
            @Nullable PatientResourceBundle patientBundle,
            ResourceBundle coreBundle,
            Map<String, AnnotatedAttributeGroup> groupMap) {
        ResourceBundle processingBundle = (patientBundle == null) ? coreBundle : patientBundle.bundle();

        boolean isPatientResource = compartmentManager.isInCompartment(resourceGroup);

        if (isPatientResource && patientBundle == null) {
            return skipDueToMissingPatientBundle(resourceGroup, coreBundle);
        }

        Optional<Resource> resource = isPatientResource
                ? patientBundle.get(resourceGroup.resourceId())
                : coreBundle.get(resourceGroup.resourceId());

        return resource.map(value -> extractReferences(resourceGroup, value, groupMap, processingBundle)).orElseGet(() -> handleMissingResource(resourceGroup, processingBundle));
    }

    /**
     * Invalidates a patient ResourceGroup in core handle processing.
     *
     * <p>Handles the case that a patient resource was called from in coreBundle Processing.
     *
     * @param resourceGroup resourceGroup to be handled
     * @param coreBundle    bundle containing core resources
     * @return empty map entry to be handled later
     */
    private Map.Entry<ResourceGroup, List<ReferenceWrapper>> skipDueToMissingPatientBundle(
            ResourceGroup resourceGroup, ResourceBundle coreBundle) {

        logger.warn("Skipping resourceGroup {}: Patient resource requires a PatientResourceBundle", resourceGroup);
        coreBundle.addResourceGroupValidity(resourceGroup, false);
        return Map.entry(resourceGroup, Collections.emptyList());
    }

    private Map.Entry<ResourceGroup, List<ReferenceWrapper>> extractReferences(
            ResourceGroup resourceGroup,
            Resource resource,
            Map<String, AnnotatedAttributeGroup> groupMap,
            ResourceBundle processingBundle) {

        try {
            List<ReferenceWrapper> extracted = referenceExtractor.extract(resource, groupMap, resourceGroup.groupId());
            return Map.entry(resourceGroup, extracted);
        } catch (MustHaveViolatedException e) {
            processingBundle.addResourceGroupValidity(resourceGroup, false);
            return Map.entry(resourceGroup, Collections.emptyList());
        }
    }

    /**
     * Corrects resourceGroup Validity for resource not found in cache.
     *
     * @param resourceGroup    resourceGroup to be handled
     * @param processingBundle bundle currently processed
     * @return empty map entry to be handled later
     */
    private Map.Entry<ResourceGroup, List<ReferenceWrapper>> handleMissingResource(
            ResourceGroup resourceGroup,
            ResourceBundle processingBundle) {
        logger.warn("Empty resource marked as valid for group {}", resourceGroup);
        processingBundle.addResourceGroupValidity(resourceGroup, false);
        return Map.entry(resourceGroup, Collections.emptyList());
    }


}
