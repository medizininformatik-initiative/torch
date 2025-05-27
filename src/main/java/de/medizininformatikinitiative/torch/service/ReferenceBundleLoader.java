package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.consent.ConsentValidator;
import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.exceptions.DataStoreException;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.exceptions.ReferenceToPatientException;
import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ReferenceWrapper;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import de.medizininformatikinitiative.torch.util.ResourceUtils;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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


    public Mono<Void> fetchUnknownResources(
            Map<ResourceGroup, List<ReferenceWrapper>> extractedReferences,
            @Nullable PatientResourceBundle patientBundle,
            ResourceBundle coreBundle, boolean applyConsent) {

        Set<String> unknownReferences = findUnloadedReferences(extractedReferences, patientBundle, coreBundle);
        List<Map<String, Set<String>>> groupedReferencesBySearchString = groupReferencesByTypeInChunks(unknownReferences);

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
                                    patientBundle.bundle().put(unloaded);
                                } else {
                                    coreBundle.put(unloaded);
                                }
                            });
                        }).onErrorResume(DataStoreException.class, e -> {
                            logger.error("Failed to fetch resources, marking all as invalid: {}", e.getMessage());
                            unknownReferences.forEach(ref -> {
                                if (compartmentManager.isInCompartment(ref) && patientBundle != null) {
                                    patientBundle.bundle().put(ref);
                                } else {
                                    coreBundle.put(ref);
                                }
                            });
                            return Mono.empty();
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
    private List<String> cacheSearchResults(@Nullable PatientResourceBundle patientBundle, ResourceBundle coreBundle, boolean applyConsent, List<Resource> resources) {
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

                                    return resourceOpt == null;
                                })))
                .collect(Collectors.toSet());
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
