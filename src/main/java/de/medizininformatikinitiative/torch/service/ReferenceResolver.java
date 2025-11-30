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
import org.apache.jena.base.Sys;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service class responsible for resolving references within a PatientResourceBundle and the CoreBundle.
 */
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

    /**
     * Extracts all known valid ResourceGroups from direct loading and then resolves references
     * until no new ResourceGroups could be found.
     *
     * @param coreBundle bundle containing core resources and ResourceGroups to be processed
     * @param groupMap   map of known attribute groups
     * @return newly added resourceGroups to be fed back into reference handling pipeline.
     */
    public Mono<ResourceBundle> resolveCoreBundle(ResourceBundle coreBundle, Map<String, AnnotatedAttributeGroup> groupMap) {
        return Mono.just(coreBundle.getValidResourceGroups())
                .map(groups -> groups.stream()
                        .filter(resourceGroup -> !compartmentManager.isInCompartment(resourceGroup)) // your custom filter logic
                        .collect(Collectors.toSet()))
                .expand(currentGroupSet ->
                        processResourceGroups(currentGroupSet, null, coreBundle, false, groupMap)
                                .onErrorResume(e -> {
                                    return Mono.empty(); // Skip this resource group on error
                                }))
                .then(Mono.just(coreBundle));
    }

    Mono<ResourceBundle> resolveCoreBundle_new(ResourceBundle coreBundle, Map<String, AnnotatedAttributeGroup> groupMap) {

        System.out.println("resolving core bundle...");
        return Mono.just(coreBundle.getValidResourceGroups())
                .map(groups -> groups.stream()
                        .filter(resourceGroup -> !compartmentManager.isInCompartment(resourceGroup)) // your custom filter logic
                        .collect(Collectors.toSet()))
                .expand(currentGroupSet ->
                        resolveUnknownCoreRefs(currentGroupSet, coreBundle, groupMap)
                                .onErrorResume(e -> {
                                    e.printStackTrace();
                                    return Mono.empty(); // Skip this resource group on error
                                }))
                .then(Mono.just(coreBundle));


        // use expand only to recursively resolve in place (mutating the bundles) -> ignoring the return value
        //return Mono.just(RGsPerPat).expand(f -> resolveUnknownBatchRefs(f, batch.bundles(), coreBundle, batch.applyConsent(), groupMap))
        //        .then(Mono.just(batch));


    }

    /**
     * Extracts all known valid ResourceGroups from direct loading and then resolves references
     * until no new ResourceGroups could be found.
     *
     * @param batch      patientBatch containing Patientbundles with patient resources and ResourceGroups to be processed
     * @param coreBundle bundle containing core resources
     * @param groupMap   map of known attribute groups
     * @return newly added resourceGroups to be fed back into reference handling pipeline.
     */
    Mono<PatientBatchWithConsent> processSinglePatientBatch(
            PatientBatchWithConsent batch, ResourceBundle coreBundle, Map<String, AnnotatedAttributeGroup> groupMap) {
        return Flux.fromIterable(batch.bundles().entrySet())
                .concatMap(entry -> resolvePatient(entry.getValue(), coreBundle, batch.applyConsent(), groupMap)
                        .map(updatedBundle -> Map.entry(entry.getKey(), updatedBundle)))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .map(updatedBundles -> new PatientBatchWithConsent(updatedBundles, batch.applyConsent()));
    }
    /*
    return Mono.just(patientBundle.getValidResourceGroups())
                .map(groups -> groups.stream()
                        .filter(compartmentManager::isInCompartment) // your custom filter logic
                        .collect(Collectors.toSet()))
                .expand(currentGroupSet ->
                        processResourceGroups(currentGroupSet, patientBundle, coreBundle, applyConsent, groupMap)
                                .onErrorResume(e -> {
                                    logger.warn("Error processing resource group set {} in PatientBundle: {}", currentGroupSet, e.getMessage());
                                    return Mono.empty(); // Skip this group on error
                                })
                )
                .then(Mono.just(patientBundle));
     */

    Mono<PatientBatchWithConsent> processSinglePatientBatch_new(
            PatientBatchWithConsent batch, ResourceBundle coreBundle, Map<String, AnnotatedAttributeGroup> groupMap) {
        var RGsPerPat = batch.bundles().entrySet().stream().map(entry ->
                Map.entry(entry.getKey(), entry.getValue()
                        .getValidResourceGroups().stream()
                        .filter(compartmentManager::isInCompartment)
                        .collect(Collectors.toSet())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // use expand only to recursively resolve in place (mutating the bundles) -> ignoring the return value
        return Mono.just(RGsPerPat).expand(f -> resolveUnknownBatchRefs(f, batch.bundles(), coreBundle, batch.applyConsent(), groupMap))
                .then(Mono.just(new PatientBatchWithConsent(batch.bundles(), batch.applyConsent())));


    }

    /**
     * Extracts all known valid ResourceGroups from direct loading and then resolves references
     * until no new ResourceGroups could be found.
     *
     * @param patientBundle bundle containing patient resources
     * @param coreBundle    bundle containing core resources
     * @param applyConsent  if consent is to be applied to patient resources
     * @param groupMap      map of known attribute groups
     * @return newly added resourceGroups to be fed back into reference handling pipeline.
     */
    public Mono<PatientResourceBundle> resolvePatient(
            PatientResourceBundle patientBundle,
            ResourceBundle coreBundle,
            boolean applyConsent,
            Map<String, AnnotatedAttributeGroup> groupMap) {
        int groupValidity = patientBundle.getValidResourceGroups().size();
        logger.trace("Resolving Patient Resource Bundle {} with {} valid groups", patientBundle.patientId(), groupValidity);
        return Mono.just(patientBundle.getValidResourceGroups())
                .map(groups -> groups.stream()
                        .filter(compartmentManager::isInCompartment) // your custom filter logic
                        .collect(Collectors.toSet()))
                .expand(currentGroupSet ->
                        processResourceGroups(currentGroupSet, patientBundle, coreBundle, applyConsent, groupMap)
                                .onErrorResume(e -> {
                                    logger.warn("Error processing resource group set {} in PatientBundle: {}", currentGroupSet, e.getMessage());
                                    return Mono.empty(); // Skip this group on error
                                })
                )
                .then(Mono.just(patientBundle));
    }

    /**
     * For a given List of ResourceGroup it extracts the references if a reference attribute is in the attribute group.
     * For every found attribute it updates the parent to attribute relation and then tries to handle the reference.
     *
     * @param validResourceGroups valid not yet processed ResourceGroup to be handled
     * @param patientBundle       bundle containing patient resources
     * @param coreBundle          bundle containing core resources
     * @param applyConsent        if consent is to applied to patient resources
     * @param groupMap            map of known attribute groups
     * @return newly added resourceGroups to be fed back into reference handling pipeline.
     */
    public Mono<Set<ResourceGroup>> processResourceGroups(
            Set<ResourceGroup> validResourceGroups,
            @Nullable PatientResourceBundle patientBundle,
            ResourceBundle coreBundle,
            boolean applyConsent,
            Map<String, AnnotatedAttributeGroup> groupMap) {
        Map<ResourceGroup, List<ReferenceWrapper>> referencesGroupedByResourceGroup =
                loadReferencesByResourceGroup(validResourceGroups, patientBundle, coreBundle, groupMap);

        System.out.println("valid resources for patient: " + validResourceGroups.size());
        AtomicInteger i = new AtomicInteger();
        referencesGroupedByResourceGroup.forEach((key, value) -> {
            value.forEach(l -> i.addAndGet(l.references().size()));
        });
        System.out.println("extracted references: " + i);

        // Get knownGroups ONCE before processing to avoid O(n²) copy operations
        ResourceBundle processingBundle = (patientBundle != null) ? patientBundle.bundle() : coreBundle;
        Set<ResourceGroup> knownGroups = processingBundle.getKnownResourceGroups();

        // TODO:
        //      - currently:
        //          - search strings are formed as: ResourceType+id1,id2,... (e.g. Medication?id=id1,id1,...)
        //          -> search is currently by resource type
        //      - should:
        //          - group by attributeGroup/ linkedGroup
        //          - maybe take the current grouping system (group by resource type) and group each group into attributegroups?
        //          - IDs from all attribute groups without filters can all be fetched at once
        //          - remaining: IDs from attribute groups with filters
        //              -> IDs from one attribute group can be fetched at one

        // - fetchUnknownResources klopft wrapper flach und packt heruntergeladene resourcen in bundle -> man zugehöriges patBundle pro flacher ref wissen
        // - brauche aber ganze wrapper, weil die in handleReferences für attributeValidity und so genutzt werden
        // - außer meine neue struktur gibt die infos, die in den wrappern stehen, auch so schon her

        // - handleReferences agiert pro ResourceGroup
        // - nutzt wrapper zum "identifizieren" und bundles zum Holen der neu heruntergeladenen resourcen

        // - wenn ich diese Struktur beibehalten will und gleichzeitig effizient herunterladen will,
        //   muss ich refs erst flachklopfen (effizientes fhirsearch) und dann wieder nach patient gruppieren (und innerhalb patient nach RG gruppieren?)
        // Beispiel:
        // - 5 pats mit je einer condition ref wären aktuell seperat
        // - ich will die in eine fhirserarch packen (solange sie die gleichen Filter haben)
        // - und danach wieder nach patient gruppieren

        // - erst iwie flachklopfen
        //      - ref wrapper pro RG bauen
        //      - dann flachklopfen?
        // - herunterladen und in passende bundles packen
        // - handleReferences

        // neue Erkenntnis?
        // - ich glaube beim fetchen ist die patient info komplett egal
        // - die patient info brauche ich nur, um es in das richtige bundle zu packen
        // -> input: gleiche map wie jetzt aber zusätzlich pro patID
        // -> referenceHandler.handleReferences kann ich dann aufrufen, indem ich über diese patIDs mappe
        // -> seperate methode für pat und core bundles
        // -> auch gleich seperates handleReferences?
        return bundleLoader.fetchUnknownResources(referencesGroupedByResourceGroup, patientBundle, coreBundle, applyConsent, groupMap)
                .thenMany(
                        Flux.fromIterable(referencesGroupedByResourceGroup.entrySet())
                                .concatMap(entry ->
                                        {
                                            try {
                                                return referenceHandler.handleReferences(
                                                        entry.getValue(),
                                                        patientBundle,
                                                        coreBundle,
                                                        groupMap,
                                                        knownGroups
                                                );
                                            } catch (MustHaveViolatedException e) {
                                                return Flux.empty();
                                            }
                                        }
                                )
                )
                .collect(Collectors.toSet())
                .flatMap(set -> set.isEmpty() ? Mono.empty() : Mono.just(set));
    }

    public Mono<Set<ResourceGroup>> resolveUnknownCoreRefs(
            Set<ResourceGroup> coreRGs,
            ResourceBundle coreBundle,
            Map<String, AnnotatedAttributeGroup> groupMap) {
        System.out.println("resolving core RG set");
        var refsPerRG = loadReferencesByResourceGroup(coreRGs, null, coreBundle, groupMap);
        var unresolvedRefsPerGroup = refsPerRG.entrySet().stream()
                .map(e ->
                    Map.entry(e.getKey().groupId(), e.getValue().stream().flatMap(wrapper -> wrapper.references().stream()).toList())
                ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> {
                    List<String> merged = new ArrayList<>(a);
                    merged.addAll(b);
                    return merged;
                }));


        var receivedResourcesMono = bundleLoader.fetchUnknownResources_new(unresolvedRefsPerGroup, groupMap)
                .map(resource -> {
                    bundleLoader.cacheSearchResults(null, coreBundle, false, resource);
                    return resource;
                }); // TODO remove blocking call/ explicitly block?

        return receivedResourcesMono.flatMap(receivedResources -> {
            // check missing refs
            System.out.println("test");
            var expectedRefs = unresolvedRefsPerGroup.values().stream().flatMap(Collection::stream).toList();
            var notReceived = expectedRefs.stream().filter(expected -> !receivedResources.stream().map(Resource::getId).toList().contains(expected)).toList();
            if (!notReceived.isEmpty()) {
                logger.warn("Some references were not loaded: {}", notReceived); // TODO does this properly pretty-print the list?
            }
            notReceived.forEach(coreBundle::put);

            return Flux.fromIterable(refsPerRG.entrySet()).flatMap(entry -> {
                        try {
                            return referenceHandler.handleReferences(
                                    entry.getValue(),
                                    null,
                                    coreBundle,
                                    groupMap,
                                    coreBundle.getKnownResourceGroups()
                            );
                        } catch (MustHaveViolatedException e) {
                            e.printStackTrace();
                            return Flux.empty(); // TODO properly handle error
                        }
                    }).collect(Collectors.toSet())
                    .mapNotNull(set -> set.isEmpty() ? null : set);
        } );






        /*
       return Flux.fromStream(bundles)
                .concatMap(datastore::executeBundle)
                .map(resources -> cacheSearchResults(patientBundle, coreBundle, applyConsent, resources))
                .doOnNext(loadedReferences -> {
                    checkMissingReferences(refsPerGroup, loadedReferences, patientBundle, coreBundle);
                }).then();
         */

        // TODO should i wait until all are returned, so instead returning a mono?
        //      -> depends if any of the other resources might be referenced by resource that is emitted here
        //      -> probably a good idea anyway because to avoid parallel mutations

    }


    // old equivalent method: processResourceGroups
    // TODO rename when done? (and make private?)
    // TODO docs: returns newly fetched resources to be recursively resolved again
    public Mono<Map<String, Set<ResourceGroup>>> resolveUnknownBatchRefs(
            Map<String, Set<ResourceGroup>> RGsPerPat,
            Map<String, PatientResourceBundle> batchBundles,
            ResourceBundle coreBundle,
            boolean applyConsent,
            Map<String, AnnotatedAttributeGroup> groupMap) {


        //////////////
        // BIG TODO:
        // - one RG references resources of DIFFERENT types
        // - i must sort by group id of REFERENCED resource
        //////////////

        // map from patID to valid RGs to be processed
        // --> i can return map from patID to newly added valid RGs
       // var testParam = batchBundles.entrySet().stream().map(entry -> Map.entry(entry.getKey(), entry.getValue().getValidResourceGroups().stream().filter(compartmentManager::isInCompartment).collect(Collectors.toSet()))).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Map<String, Map<ResourceGroup, List<ReferenceWrapper>>> refsPerPat = RGsPerPat.entrySet().stream().map(entry -> {
            var patID = entry.getKey();
            var patRGs = entry.getValue();
            var patientBundle = batchBundles.get(patID);
            var refs = loadReferencesByResourceGroup(patRGs, patientBundle, coreBundle, groupMap);
            return Map.entry(patientBundle.patientId(), refs);
        }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        /*Map<String, Map<ResourceGroup, List<ReferenceWrapper>>> refsPerPat = batchBundles.values().stream().map(patientBundle -> {
            var validRGs = patientBundle.getValidResourceGroups().stream().filter(compartmentManager::isInCompartment).collect(Collectors.toSet());
            var refs = loadReferencesByResourceGroup(validRGs, patientBundle, coreBundle, groupMap);
            return Map.entry(patientBundle.patientId(), refs);
        }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));*/


        // - one batch may contain 500 pats
        // - leading to 500 resources from which we have to extract and resolve references
        // - so now i have e.g. 500 compartment resources (e.g. Encounters) -> 500 RGs
        // - refsPerPat maps from patID to map <from resource to reference strings>
        // - from this i can flatten to map <group to reference strings>
        // - each group can be fetched at once (but must also be chunked)
        // TODO fetch resources
        // one reference can be referenced by the two patients TODO can it? not for compartment resources i guess -> throw error?
        Map<String, List<String>> refToPatHelper = new HashMap<>();
        Stream<Map.Entry<String, List<String>>> entryStream = refsPerPat.entrySet().stream().flatMap(e -> {
            var patID = e.getKey();
            var refsPerResource = e.getValue();
            return refsPerResource.entrySet().stream().flatMap(entry ->{
                var refsOfResource = entry.getValue().stream().flatMap(wrapper -> wrapper.references().stream()).toList();
                refsOfResource.forEach(ref -> refToPatHelper.computeIfAbsent(ref, x -> new ArrayList<>()).add(patID));
                return entry.getValue().stream().flatMap(wrapper -> {

                    return wrapper.refAttribute().linkedGroups().stream().map(linkedGroupId -> {
                        return Map.entry(linkedGroupId, wrapper.references()); // TODO is this the right way to handle multiple linekd groups??
                    });
                });

               // return Map.entry(entry.getKey().groupId(), refsOfResource);
            });
        });

        var unresolvedRefsPerGroup = entryStream.collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (a, b) -> {
                    List<String> merged = new ArrayList<>(a);
                    merged.addAll(b);
                    return merged;
                }));

        // new param:
        // - currently e.g. 500 patients, everyone mapping to 10 RGs (ie. 10 resources) which each might map to 2 references
        // - 500 -> 10 -> 2 -> 2 (old)
        // - 10 -> 500 -> 2 -> 2 (new?)
        // - map from group to List<RefWrapper> but here this list contains refWrappers from mutliple resources instead of from just one

        var receivedResourcesMono = bundleLoader.fetchUnknownResources_new(unresolvedRefsPerGroup, groupMap)
                .map(fetchedResources -> fetchedResources.stream().map(resource -> {
                    System.out.println("fetched resource: " + resource.getIdPart());
                    System.out.println("id element part: " + resource.getIdElement().getIdPart());
                    System.out.println("id base: " + resource.getIdBase());
            // put each resource in the patBundles to which they belong
            // TODO does this put core resources multiple times into the core bundle? -> check if core/ compartment outside of cacheSearchResults?
                    System.out.println("resource id: " + resource.getIdPart());

                    System.out.println(refToPatHelper.get(resource.getResourceType() + "/" + resource.getIdPart()));
            var patIDs = refToPatHelper.get(resource.getResourceType() + "/" + resource.getIdPart()); // TODO does this getId() work?
            patIDs.forEach(patID -> {
                bundleLoader.cacheSearchResults(batchBundles.get(patID), coreBundle, applyConsent, resource);
            });
            return resource;
        }).toList()); // TODO remove blocking call/ explicitly block?

        System.out.println("asdf");
        // check missing refs
        return receivedResourcesMono.flatMap(receivedResources -> {
            var expectedRefs = unresolvedRefsPerGroup.values().stream().flatMap(Collection::stream).toList();
            var receivedRefs = receivedResources.stream().map(r -> r.getResourceType() + "/" + r.getIdPart()).toList();
            var notReceived = expectedRefs.stream().filter(expected -> !receivedRefs.contains(expected)).toList();
            if (!notReceived.isEmpty()) {
                logger.warn("Some references were not loaded: {}", notReceived); // TODO does this properly pretty-print the list?
            }
            notReceived.forEach(missingRef -> {
                var patIDs = refToPatHelper.get(missingRef);
                patIDs.forEach(patID -> {
                    var patientBundle = batchBundles.get(patID);
                    if (compartmentManager.isInCompartment(missingRef) && patientBundle != null) {
                        patientBundle.put(missingRef);
                    } else {
                        coreBundle.put(missingRef);
                    }
                });

            });



            return Flux.fromIterable(refsPerPat.entrySet()).flatMap(entry -> {
                        var patID = entry.getKey();
                        var refsPerRG = entry.getValue();
                        var newValidRGs = Flux.fromIterable(refsPerRG.values()).flatMap(refs -> {
                            try {
                                var patientBundle = batchBundles.get(patID);
                                return referenceHandler.handleReferences(
                                        refs,
                                        patientBundle,
                                        coreBundle,
                                        groupMap,
                                        patientBundle.bundle().getKnownResourceGroups()
                                );
                            } catch (MustHaveViolatedException e) {
                                e.printStackTrace();
                                return Flux.empty(); // TODO handle better?
                            }
                        }).collectList(); // TODO explicitly block?
                        return newValidRGs.flatMap(RGs -> RGs.isEmpty() ? Mono.empty() : Mono.just(Map.entry(patID, RGs.stream().collect(Collectors.toSet()))));
                        //var temp = newValidRGs.map(RGs -> RGs.isEmpty() ? null : Map.entry(patID, newValidRGs));
                        //return newValidRGs.map(RGs -> RGs.isEmpty() ? Mono.empty() : Mono.just(Map.entry(patID, newValidRGs)));
                        //return newValidRGs.

                    })
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                    .mapNotNull(newMap -> newMap.isEmpty() ? null : newMap);
        });






        /*
       return Flux.fromStream(bundles)
                .concatMap(datastore::executeBundle)
                .map(resources -> cacheSearchResults(patientBundle, coreBundle, applyConsent, resources))
                .doOnNext(loadedReferences -> {
                    checkMissingReferences(refsPerGroup, loadedReferences, patientBundle, coreBundle);
                }).then();
         */

        // TODO should i wait until all are returned, so instead returning a mono?
        //      -> depends if any of the other resources might be referenced by resource that is emitted here
        //      -> probably a good idea anyway because to avoid parallel mutations


    }


    // TODO might it happen that there is a ref to a patient that is not in the cohort?
    private static List<String> getUnloadedReferences(
            List<ReferenceWrapper> referenceWrappers,
            PatientResourceBundle patientBundle) {
        return referenceWrappers.stream().flatMap(wrapper -> wrapper.references().stream()
                .filter(Objects::nonNull)
                .filter(ref -> patientBundle.bundle().get(ref) == null)).collect(Collectors.toList());
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
