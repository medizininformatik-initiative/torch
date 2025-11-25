package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.consent.ConsentValidator;
import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
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

    public ReferenceBundleLoader(CompartmentManager compartmentManager,
                                 DataStore datastore, ConsentValidator consentValidator) {
        this.compartmentManager = compartmentManager;
        this.datastore = datastore;
        this.consentValidator = consentValidator;
    }


    public Mono<Void> fetchUnknownResources(
            Map<ResourceGroup, List<ReferenceWrapper>> extractedReferences,
            @Nullable PatientResourceBundle patientBundle,
            ResourceBundle coreBundle, boolean applyConsent) {

        Set<String> unknownReferences = findUnloadedReferences(extractedReferences, patientBundle, coreBundle);
        List<Map<String, Set<String>>> groupedReferencesBySearchString = datastore.groupReferencesByTypeInChunks(unknownReferences);

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


}
