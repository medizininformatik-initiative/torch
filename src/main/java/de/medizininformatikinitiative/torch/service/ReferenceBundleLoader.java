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
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Date;
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
            Map<ResourceGroup, List<ReferenceWrapper>> referencesWrappers,
            @Nullable PatientResourceBundle patientBundle,
            ResourceBundle coreBundle, boolean applyConsent) {

        Set<String> unknownReferences = findUnloadedReferences(referencesWrappers, patientBundle, coreBundle);
        Map<String, Set<String>> groupedReferencesByType = groupReferencesByType(unknownReferences);

        if (groupedReferencesByType.isEmpty()) {
            return Mono.empty();
        }
        Bundle batchBundle = createBatchBundleForReferences(groupedReferencesByType);

        return datastore.fetchResourcesByReferences(batchBundle)
                .flatMap(resource -> {
                    String relativeUrl = ResourceUtils.getRelativeURL(resource);
                    boolean isPatientResource = compartmentManager.isInCompartment(relativeUrl);

                    if (isPatientResource && patientBundle == null) {
                        logger.warn("CoreBundle loaded reference {} belonging to a Patient ", relativeUrl);
                        return Mono.empty();
                    }
                    if (isPatientResource) {
                        try {
                            checkBundleAndConsent(patientBundle, applyConsent, resource);
                            patientBundle.bundle().put(resource);
                            return Mono.just(relativeUrl);
                        } catch (ConsentViolatedException e) {
                            patientBundle.put(relativeUrl);
                            return Mono.empty(); // Skip resource on consent violation
                        } catch (PatientIdNotFoundException | ReferenceToPatientException e) {
                            return Mono.error(e);
                        }
                    } else {
                        coreBundle.put(resource);
                        return Mono.just(relativeUrl);
                    }
                })
                .collectList()
                .doOnNext(loadedReferences -> {
                    Set<String> notLoaded = new HashSet<>(unknownReferences);
                    loadedReferences.forEach(notLoaded::remove);
                    if (!notLoaded.isEmpty()) {
                        logger.warn("Some references were not loaded: {}", notLoaded);
                    }
                    notLoaded.parallelStream().forEach(unloaded -> {
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

    public Bundle createBatchBundleForReferences(Map<String, Set<String>> groupedReferencesByType) {
        // Build the batch bundle
        Bundle batchBundle = new Bundle();
        batchBundle.setType(Bundle.BundleType.BATCH);
        batchBundle.getMeta().setLastUpdated(new Date());


        groupedReferencesByType.forEach((type, ids) -> {
            String joinedIds = String.join(",", ids);
            Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();
            entry.setRequest(new Bundle.BundleEntryRequestComponent()
                    .setMethod(Bundle.HTTPVerb.GET)
                    .setUrl(type + "?_id=" + joinedIds));
            batchBundle.addEntry(entry);
        });

        return batchBundle;

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
     * For a resource it checks if the resource is part of the bundle it claims to be.
     *
     * <p> When a resource is loaded it is checked if the resource is a patient or core Resource
     * (core Resources should not link to patient resources) and checks in case of patient resources
     * if it fits the consent and is assigned to the correct patient.
     *
     * @param patientBundle bundle to which the loaded resource should belong
     * @param applyConsent  flag if batch has a consent check
     * @param resource      resource to check
     * @return true if fitting otherwise it throws errors
     */
    public boolean checkBundleAndConsent(PatientResourceBundle patientBundle, boolean applyConsent, Resource resource) throws PatientIdNotFoundException, ConsentViolatedException, ReferenceToPatientException {
        try {
            String resourcePatientId = ResourceUtils.patientId((DomainResource) resource);
            if (!resourcePatientId.equals(patientBundle.patientId())) {
                throw new ReferenceToPatientException("Patient loaded reference belonging to another patient");
            }

            if (applyConsent && !consentValidator.checkConsent((DomainResource) resource, patientBundle)) {
                throw new ConsentViolatedException("Consent Violated in Patient Resource");
            }

            return true;

        } catch (PatientIdNotFoundException e) {
            throw e;
        }
    }


    public Map<String, Set<String>> groupReferencesByType(Set<String> references) {
        List<String> absoluteRefs = new ArrayList<>();
        List<String> malformedRefs = new ArrayList<>();

        Map<String, Set<String>> groupedReferencesByType = references.stream()
                .filter(ref -> {
                    if (ref.startsWith("http")) {
                        absoluteRefs.add(ref);
                        return false;
                    } else if (!ref.contains("/") || ref.split("/").length != 2) {
                        malformedRefs.add(ref);
                        return false;
                    }
                    return true;
                })
                .map(ref -> ref.split("/"))
                .collect(Collectors.groupingBy(
                        parts -> parts[0],
                        Collectors.mapping(parts -> parts[1], Collectors.toSet())
                ));
        if (!absoluteRefs.isEmpty()) {
            logger.warn("Ignoring absolute references (not supported): {}", absoluteRefs);
        }

        if (!malformedRefs.isEmpty()) {
            logger.warn("Ignoring malformed references: {}", malformedRefs);
        }
        return groupedReferencesByType;
    }
}
