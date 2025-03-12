package de.medizininformatikinitiative.torch.util;

import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.exceptions.ReferenceToPatientException;
import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.management.ConsentHandler;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ReferenceWrapper;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import de.medizininformatikinitiative.torch.service.DataStore;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ReferenceHandler {
    private static final Logger logger = LoggerFactory.getLogger(ReferenceHandler.class);

    private final DataStore dataStore;
    private final ProfileMustHaveChecker profileMustHaveChecker;
    private final CompartmentManager compartmentManager;
    private final ConsentHandler consentHandler;

    public ReferenceHandler(DataStore dataStore,
                            ProfileMustHaveChecker profileMustHaveChecker,
                            CompartmentManager compartmentManager,
                            ConsentHandler consentHandler) {
        this.dataStore = dataStore;
        this.profileMustHaveChecker = profileMustHaveChecker;
        this.compartmentManager = compartmentManager;
        this.consentHandler = consentHandler;
    }

    public Flux<ResourceGroup> handleReferences(List<ReferenceWrapper> references,
                                                @Nullable PatientResourceBundle patientBundle,
                                                ResourceBundle coreBundle,
                                                Boolean applyConsent,
                                                Map<String, AnnotatedAttributeGroup> groupMap) {
        ResourceBundle processingBundle = patientBundle == null ? patientBundle.bundle() : coreBundle;

        Set<ResourceGroup> knownGroups = processingBundle.getKnownResourceGroups();
        System.out.println("Known Groups: " + knownGroups);

        return Flux.fromIterable(references)
                .concatMap(ref -> handleReference(ref, patientBundle, coreBundle, applyConsent, groupMap)) // Sequential execution
                .collectList()
                .flatMapMany(results -> Flux.fromIterable(results.stream()
                        .flatMap(List::stream)
                        .toList()))
                .filter(group -> !knownGroups.contains(group))
                .onErrorResume(MustHaveViolatedException.class, e -> {
                    logger.warn("MustHaveViolatedException occurred. Stopping resource processing: {}", e.getMessage());
                    return Flux.error(e); // Propagate the error to the caller
                });
    }


    /**
     * Handles a ReferenceWrapper by resolving its references and updating the patient bundle.
     *
     * @param referenceWrapper The reference wrapper to handle.
     * @param patientBundle    The patient bundle being updated.
     * @param coreBundle       to be updated and queried, that contains a centrally shared concurrent HashMap.
     * @param applyConsent     If consent has to be applied (only relevant if patientBundle is present).
     * @param groupMap         Map of attribute groups for validation.
     * @return A Mono emitting a list of ResourceGroupWrappers corresponding to the resolved references.
     */
    /**
     * Handles a ReferenceWrapper by resolving its references and updating the patient bundle.
     *
     * @param referenceWrapper The reference wrapper to handle.
     * @param patientBundle    The patient bundle being updated.
     * @param coreBundle       to be updated and queried, that contains a centrally shared concurrent HashMap.
     * @param applyConsent     If consent has to be applied (only relevant if patientBundle is present).
     * @param groupMap         Map of attribute groups for validation.
     * @return A Flux emitting a list of ResourceGroups corresponding to the resolved references.
     */
    public Flux<List<ResourceGroup>> handleReference(ReferenceWrapper referenceWrapper,
                                                     @Nullable PatientResourceBundle patientBundle,
                                                     ResourceBundle coreBundle,
                                                     Boolean applyConsent,
                                                     Map<String, AnnotatedAttributeGroup> groupMap) {

        ResourceBundle processingBundle = patientBundle != null ? patientBundle.bundle() : coreBundle;

        return Flux.fromIterable(referenceWrapper.references())
                .concatMap(reference -> {
                    Mono<Resource> referenceResource;

                    // Try to get the resource from available bundles or fetch it
                    if (patientBundle != null && patientBundle.contains(reference)) {
                        referenceResource = patientBundle.get(reference);
                    } else if (coreBundle.contains(reference)) {
                        referenceResource = coreBundle.get(reference);
                    } else {
                        logger.debug("Reference {} not found in patientBundle or coreBundle, attempting fetch.", reference);
                        referenceResource = getResourceMono(patientBundle, applyConsent, reference)
                                .doOnSuccess(resource -> {
                                    if (compartmentManager.isInCompartment(resource)) {
                                        if (patientBundle != null) {
                                            patientBundle.put(resource);
                                        }
                                    } else {
                                        coreBundle.put(resource);
                                    }
                                });
                    }

                    return referenceResource.flatMap(resource -> {
                        String resourceUrl = ResourceUtils.getRelativeURL(resource);

                        List<ResourceGroup> newValidGroups = referenceWrapper.refAttribute().linkedGroups().stream()
                                .map(groupMap::get)
                                .map(group -> {
                                    ResourceGroup resourceGroup = new ResourceGroup(resourceUrl, group.id());

                                    // Check if the resource group is new
                                    Boolean isValid = processingBundle.isValidResourceGroup(resourceGroup);

                                    if (isValid == null) { // Unknown group, check validity
                                        Boolean fulfilled = profileMustHaveChecker.fulfilled(resource, group);
                                        isValid = Boolean.TRUE.equals(fulfilled); // Ensure `null` defaults to `false`
                                        processingBundle.addResourceGroupValidity(resourceGroup, isValid);
                                    }

                                    return isValid ? resourceGroup : null;
                                })
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList());

                        logger.debug("New valid groups found: {}", newValidGroups);


                        if (referenceWrapper.refAttribute().mustHave() && newValidGroups.isEmpty()) {
                            return Mono.error(new MustHaveViolatedException(
                                    "MustHave condition violated for reference " + reference + " (" + resourceUrl + ") - No matching groups found."
                            ));
                        }

                        return Mono.just(newValidGroups);
                    });
                })
                .collectList()
                .map(lists -> lists.stream()
                        .flatMap(List::stream)
                        .collect(Collectors.toList()))
                .flatMapMany(list -> {

                    logger.info("✅ Final ResourceGroup List: {}", list);

                    if (referenceWrapper.refAttribute().mustHave() && list.isEmpty()) {
                        return Flux.error(new MustHaveViolatedException(
                                "MustHave condition violated: No valid references were resolved for " + referenceWrapper.references()
                        ));
                    }
                    return Flux.just(list);
                });
    }


    public Mono<Resource> getResourceMono(@Nullable PatientResourceBundle patientBundle, Boolean applyConsent, String reference) {
        return dataStore.fetchDomainResource(reference)
                .switchIfEmpty(Mono.error(new RuntimeException("Failed to fetch resource: received empty response")))
                .flatMap(resource -> {
                    if (compartmentManager.isInCompartment(resource)) {
                        if (patientBundle != null) {

                            try {

                                if (ResourceUtils.patientId(resource).equals(patientBundle.patientId())) {
                                    if (applyConsent) {
                                        if (consentHandler.checkConsent(resource, patientBundle)) {
                                            return Mono.just(resource);
                                        } else {
                                            return Mono.error(new ConsentViolatedException("Consent Violated in Patient Resource"));
                                        }
                                    } else {
                                        return Mono.just(resource);
                                    }
                                } else {
                                    return Mono.error(new ReferenceToPatientException("Patient loaded Reference belonging to other Patient"));
                                }
                            } catch (PatientIdNotFoundException e) {
                                return Mono.error(e);
                            }
                        } else {
                            return Mono.error(new ReferenceToPatientException("Patient Resource referenced in Core Bundle"));
                        }
                    }

                    return Mono.just(resource);
                });
    }

}
