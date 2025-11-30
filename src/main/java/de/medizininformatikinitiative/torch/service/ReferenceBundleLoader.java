package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.consent.ConsentValidator;
import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.exceptions.ReferenceToPatientException;
import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.fhir.QueryParams;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ReferenceWrapper;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;
import de.medizininformatikinitiative.torch.util.ResourceUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.multiStringValue;

public class ReferenceBundleLoader {
    private static final Logger logger = LoggerFactory.getLogger(ReferenceBundleLoader.class);
    private final CompartmentManager compartmentManager;
    private final DataStore datastore;
    private final ConsentValidator consentValidator;
    private final int pageCount;

    public ReferenceBundleLoader(CompartmentManager compartmentManager,
                                 DataStore datastore, ConsentValidator consentValidator, int pageCount) {
        this.compartmentManager = compartmentManager;
        this.datastore = datastore;
        this.consentValidator = consentValidator;
        this.pageCount = pageCount;
    }

    public Mono<List<Resource>> fetchUnknownResources_new(Map<String, List<String>> refsPerGroup,
                                                Map<String, AnnotatedAttributeGroup> groupMap) {
        //Map<String, Set<String>> unknownRefsPerGroup = findUnloadedReferences_new(refsPerGroup, patientBundle, coreBundle);
        var chunkedRefsPerGroup = chunkRefs(refsPerGroup);
        var bundles = chunkedRefsPerGroup.stream().map(c -> createBatchBundle(c, groupMap));



        // TODO put everything after "executeBatch" into caller?
        //var x=  Flux.fromStream(bundles).flatMap(datastore::executeBundle).map(l -> Flux.fromIterable(l));

        return Flux.fromStream(bundles)
                .flatMap(datastore::executeBundle)
                .flatMap(Flux::fromIterable)
                .collectList()
                .map(l -> {
                    System.out.println("Received resources: " + l.size());
                    return l;
                });

    }

    // TODO put into ReferenceResolver?
    public void checkMissingReferences(List<String> expectedRefs, List<String> receivedRefs, PatientResourceBundle patientBundle, ResourceBundle coreBundle) {
        //var notReceived = expectedRefs.stream().flatMap(Collection::stream).toList().stream()
        //        .filter(ref -> !receivedRefs.contains(ref)).toList();
        var notReceived = expectedRefs.stream().filter(ref -> !receivedRefs.contains(ref)).toList();

        if (!notReceived.isEmpty()) {
            logger.warn("Some references were not loaded: {}", notReceived); // TODO does this pretty-print the list?
        }
        notReceived.forEach(missingRef -> {
            if (compartmentManager.isInCompartment(missingRef) && patientBundle != null) {
                patientBundle.put(missingRef);
            } else {
                coreBundle.put(missingRef);
            }
        });
    }

    // refsPerGroup = groupID -> List<refs>
    private static Bundle createBatchBundle(Map<String, Set<String>> refsPerGroup, Map<String, AnnotatedAttributeGroup> groupMap) {
        // Build the batch bundle
        Bundle batchBundle = new Bundle();
        batchBundle.setType(Bundle.BundleType.BATCH);
        batchBundle.getMeta().setLastUpdated(new Date());


        refsPerGroup.forEach((groupID, refs) -> {
            //String joinedIds = ids.stream().sorted().collect(Collectors.joining(","));
            //type + "?_id=" + joinedIds + "&_count=" + ids.size())
            var ag = groupMap.get(groupID);
            if ("Patient".equals(ag.resourceType())) {
                System.out.println("-------------------------------- is patient ref allowed?----------------------");
            }
            // TODO manually try calling FHIR Server with bundle?
            // TODO pass mappingTree
            // TODO split refs to get IDs
            var queryPerFilter = ag.queries(null, ag.resourceType()).stream().map(query ->
                    Query.of(query.type(), query.params()
                            .appendParams(QueryParams.of("_id", QueryParams.multiStringValue(refs.stream().toList())))
                            .appendParams(QueryParams.of("_count", QueryParams.stringValue(String.valueOf(refs.size()))))));

            queryPerFilter.forEach(query -> {
                Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();
                entry.setRequest(new Bundle.BundleEntryRequestComponent()
                        .setMethod(Bundle.HTTPVerb.GET)
                        .setUrl(query.toString()));
                batchBundle.addEntry(entry);
            });


        });

        return batchBundle;
    }


    public Mono<Void> fetchUnknownResources(
            Map<ResourceGroup, List<ReferenceWrapper>> extractedReferences,
            @Nullable PatientResourceBundle patientBundle,
            ResourceBundle coreBundle, boolean applyConsent, Map<String, AnnotatedAttributeGroup> groupMap) {

        // TODO extractedReferences are grouped by ResourceGroup, i.e. per Resource and not per group!!
        //          --> requesting each Codntion/id seperately works as "expected" currently
        //      PROBLEM: why is there apperently always just one unkown reference?
        Set<String> unknownReferences = findUnloadedReferences(extractedReferences, patientBundle, coreBundle);
        //Map<ResourceGroup, Set<String>> unknownReferences_new = findUnloadedReferences_new(extractedReferences, patientBundle, coreBundle);
        List<Map<String, Set<String>>> groupedReferencesBySearchString = groupReferencesByTypeInChunks(unknownReferences);
        //List<Map<String, Set<String>>> groupedReferencesBySearchString_new = groupReferencesByTypeInChunks(unknownReferences_new);
        unknownReferences.forEach(s -> System.out.print(s + ", "));
        System.out.println("");
        System.out.println("map size: " + extractedReferences.size());
        if(!groupedReferencesBySearchString.isEmpty()) {
            groupedReferencesBySearchString.getFirst().forEach((key, value) -> {
                System.out.println("resource type: " + key);
                value.forEach(s -> System.out.println("id: " + s));
            });
        }


        if (groupedReferencesBySearchString.isEmpty()) {
            return Mono.empty();
        }

        return
                Flux.fromIterable(groupedReferencesBySearchString)
                        .concatMap(datastore::executeSearchBatch)
                        .map(resources -> cacheSearchResults(patientBundle, coreBundle, applyConsent, resources))
                        .doOnNext(loadedReferences -> {
                            Set<String> notLoaded = new HashSet<>(unknownReferences);
                            loadedReferences.forEach(notLoaded::remove);
                            if (!notLoaded.isEmpty()) {
                                logger.warn("Some references were not loaded: {}", notLoaded);
                            }
                            notLoaded.forEach(unloaded -> {
                                if (compartmentManager.isInCompartment(unloaded) && patientBundle != null) {
                                    patientBundle.put(unloaded);
                                } else {
                                    coreBundle.put(unloaded);
                                }
                            });
                        }).then();
    }

    /**
     * Assigns search results to their adequate cache.
     *
     * @param patientBundle optional cache containing patient resources
     * @param coreBundle    cache containing core resources
     * @param applyConsent  flag if consent is applied on patientBundle
     * @param resources     search results
     * @return loadedReferences
     */
    // TODO move to referenceResolver?
    public List<String> cacheSearchResults(@Nullable PatientResourceBundle patientBundle, ResourceBundle coreBundle, boolean applyConsent, List<Resource> resources) {
        List<String> loadedReferences = new ArrayList<>();
        resources.forEach(resource -> {
            String relativeUrl = ResourceUtils.getRelativeURL(resource);
            boolean isPatientResource = compartmentManager.isInCompartment(relativeUrl);

            if (isPatientResource && patientBundle == null) {
                logger.warn("CoreBundle loaded reference {} belonging to a Patient ", relativeUrl);
                coreBundle.put(relativeUrl);
            }
            if (isPatientResource) {
                try {
                    assert patientBundle != null;
                    consentValidator.checkPatientIdAndConsent(patientBundle, applyConsent, resource);
                    patientBundle.bundle().put(resource);
                    loadedReferences.add(relativeUrl);
                } catch (ConsentViolatedException | PatientIdNotFoundException | ReferenceToPatientException e) {
                    patientBundle.put(relativeUrl);
                }
            } else {
                coreBundle.put(resource);
                loadedReferences.add(relativeUrl);
            }
        });
        return loadedReferences;
    }


    // TODO move to referenceResolver?
    public void cacheSearchResults(@Nullable PatientResourceBundle patientBundle, ResourceBundle coreBundle, boolean applyConsent, Resource resource) {
        String relativeUrl = ResourceUtils.getRelativeURL(resource);
        boolean isPatientResource = compartmentManager.isInCompartment(relativeUrl);

        if (isPatientResource && patientBundle == null) {
            logger.warn("CoreBundle loaded reference {} belonging to a Patient ", relativeUrl);
            coreBundle.put(relativeUrl);
        }
        if (isPatientResource) {
            try {
                assert patientBundle != null;
                consentValidator.checkPatientIdAndConsent(patientBundle, applyConsent, resource);
                patientBundle.bundle().put(resource);
                //return true;
            } catch (ConsentViolatedException | PatientIdNotFoundException | ReferenceToPatientException e) {
                patientBundle.put(relativeUrl);
            }
        } else {
            coreBundle.put(resource);
            //return true;
        }

       // return false;
    }


    public Set<String> findUnloadedReferences(
            Map<ResourceGroup, List<ReferenceWrapper>> referencesWrappers,
            @Nullable PatientResourceBundle patientBundle,
            ResourceBundle coreBundle) {

        return referencesWrappers.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .flatMap(wrapper -> wrapper.references().stream()
                                .filter(Objects::nonNull)
                                .filter(reference -> {
                                    boolean isPatientResource = compartmentManager.isInCompartment(reference);
                                    if (isPatientResource && patientBundle == null) {
                                        coreBundle.put(reference);
                                        logger.warn("Patient resource loaded by reference outside of Patient Context");
                                        return false;
                                    }

                                    // Get the Optional<Resource> or null if not seen
                                    Optional<Resource> resourceOpt = isPatientResource
                                            ? patientBundle.bundle().get(reference)
                                            : coreBundle.get(reference);
                                    if(resourceOpt != null) {
                                        System.out.println("reference " + reference + " already known");
                                    }else {
                                        System.out.println("reference " + reference + " not known yet");
                                    }
                                    return resourceOpt == null;
                                })))
                .collect(Collectors.toSet());
    }

    public Map<String, Set<String>> findUnloadedReferences_new(
            Map<String, List<ReferenceWrapper>> referencesWrappers,
                                              @Nullable PatientResourceBundle patientBundle,
                                              ResourceBundle coreBundle) {
        return referencesWrappers.entrySet().stream().map(e -> {
            var unloadedReferences = e.getValue().stream().flatMap(wrapper -> wrapper.references().stream()
            .filter(Objects::nonNull)
                    .filter(reference -> {
                        boolean isPatientResource = compartmentManager.isInCompartment(reference);
                        if (isPatientResource && patientBundle == null) {
                            coreBundle.put(reference);
                            logger.warn("Patient resource loaded by reference outside of Patient Context");
                            return false;
                        }

                        // Get the Optional<Resource> or null if not seen
                        Optional<Resource> resourceOpt = isPatientResource
                                ? patientBundle.bundle().get(reference)
                                : coreBundle.get(reference);

                        return resourceOpt == null;
                    })).collect(Collectors.toSet());
            return Map.entry(e.getKey(), unloadedReferences);
        }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    // creates list of maps where each map contains n values
    public List<Map<String, Set<String>>> chunkRefs(Map<String, List<String>> refsPerGroup) {
        List<Map<String, Set<String>>> chunks = new ArrayList<>();
        Map<String, Set<String>> currentChunk = new HashMap<>();
        int currentChunkSize = 0;

        for(Map.Entry<String, List<String>> entry : refsPerGroup.entrySet()) {
            var groupID = entry.getKey();
            for (String ref : entry.getValue()) {
                var refId = ref.split("/")[1]; // TODO error handling?
                currentChunk.computeIfAbsent(groupID, k -> new HashSet<>()).add(refId);
                currentChunkSize++;

                if(currentChunkSize == pageCount) {
                    chunks.add(currentChunk);
                    currentChunk = new HashMap<>();
                    currentChunkSize = 0;
                }
            }


        }
        if (!currentChunk.isEmpty()) {
            chunks.add(currentChunk);
        }

        return chunks;
    }

    /**
     * Groups references into chunks limited by pagecount size of the fhir server webclient.
     *
     * @param references reference strings to be chunked
     * @return list of chunks containing the References grouped by Type.
     */
    public List<Map<String, Set<String>>> groupReferencesByTypeInChunks(Set<String> references) {
        List<String> absoluteRefs = new ArrayList<>();
        List<String> malformedRefs = new ArrayList<>();

        List<Map<String, Set<String>>> chunks = new ArrayList<>();
        Map<String, Set<String>> currentChunk = new LinkedHashMap<>();


        int currentCount = 0;

        for (String ref : references.stream().sorted().toList()) {
            if (ref.startsWith("http")) {
                absoluteRefs.add(ref);
                continue;
            }
            String[] parts = ref.split("/");
            if (parts.length != 2) {
                malformedRefs.add(ref);
                continue;
            }

            String resourceType = parts[0];
            String id = parts[1];

            currentChunk.computeIfAbsent(resourceType, k -> new LinkedHashSet<>()).add(id);
            currentCount++;

            if (currentCount == pageCount) {
                chunks.add(currentChunk);
                currentChunk = new LinkedHashMap<>();
                currentCount = 1;
            }
        }

        if (!currentChunk.isEmpty()) {
            chunks.add(currentChunk);
        }

        if (!absoluteRefs.isEmpty()) {
            logger.warn("Ignoring absolute references (not supported): {}", absoluteRefs);
        }

        if (!malformedRefs.isEmpty()) {
            logger.warn("Ignoring malformed references: {}", malformedRefs);
        }

        return chunks;
    }


}
