package de.medizininformatikinitiative.torch.util;

import de.medizininformatikinitiative.torch.consent.ConsentValidator;
import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.exceptions.ReferenceToPatientException;
import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ReferenceWrapper;
import de.medizininformatikinitiative.torch.model.management.ResourceAttribute;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import de.medizininformatikinitiative.torch.service.DataStore;
import org.hl7.fhir.r4.model.DomainResource;
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

public class ReferenceHandler {
    private static final Logger logger = LoggerFactory.getLogger(ReferenceHandler.class);

    private final DataStore dataStore;
    private final ProfileMustHaveChecker profileMustHaveChecker;
    private final CompartmentManager compartmentManager;
    private final ConsentValidator consentValidator;

    public ReferenceHandler(DataStore dataStore,
                            ProfileMustHaveChecker profileMustHaveChecker,
                            CompartmentManager compartmentManager,
                            ConsentValidator consentValidator) {
        this.dataStore = dataStore;
        this.profileMustHaveChecker = profileMustHaveChecker;
        this.compartmentManager = compartmentManager;
        this.consentValidator = consentValidator;
    }

    private static Flux<List<ResourceGroup>> checkReferenceViolatesMustHave(ReferenceWrapper referenceWrapper, List<ResourceGroup> list, ResourceBundle processingBundle) {
        ResourceAttribute referenceAttribute = referenceWrapper.toResourceAttributeGroup();
        if (referenceWrapper.refAttribute().mustHave() && list.isEmpty()) {
            processingBundle.setResourceAttributeInValid(referenceAttribute);
            return Flux.error(new MustHaveViolatedException(
                    "MustHave condition violated: No valid references were resolved for " + referenceWrapper.references()
            ));
        }
        processingBundle.setResourceAttributeValid(referenceAttribute);
        return Flux.just(list);
    }

    /**
     * @param references    References extracted from a single resource to be handled
     * @param patientBundle ResourceBundle containing patient information (Optional for core bundle)
     * @param coreBundle    coreResourceBundle containing the core Resources
     * @param applyConsent  if consent is applicable for patient resources
     * @param groupMap      cache containing all known attributeGroups
     * @return newly added ResourceGroups to be processed
     */
    public Flux<ResourceGroup> handleReferences(List<ReferenceWrapper> references,
                                                @Nullable PatientResourceBundle patientBundle,
                                                ResourceBundle coreBundle,
                                                boolean applyConsent,
                                                Map<String, AnnotatedAttributeGroup> groupMap) {
        ResourceBundle processingBundle = (patientBundle != null) ? patientBundle.bundle() : coreBundle;

        Set<ResourceGroup> knownGroups = processingBundle.getKnownResourceGroups();

        return Flux.fromIterable(references)
                .concatMap(ref -> handleReference(ref, patientBundle, coreBundle, applyConsent, groupMap).doOnNext(
                        resourceGroupList -> {
                            ResourceAttribute referenceAttribute = new ResourceAttribute(ref.parentId(), ref.refAttribute());
                            resourceGroupList.forEach(resourceGroup -> processingBundle.addAttributeToChild(referenceAttribute, resourceGroup));
                        }
                ))
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
     * @return A Flux emitting a list of ResourceGroups corresponding to the resolved references.
     */
    public Flux<List<ResourceGroup>> handleReference(ReferenceWrapper referenceWrapper,
                                                     @Nullable PatientResourceBundle patientBundle,
                                                     ResourceBundle coreBundle,
                                                     boolean applyConsent,
                                                     Map<String, AnnotatedAttributeGroup> groupMap) {

        ResourceBundle processingBundle = patientBundle != null ? patientBundle.bundle() : coreBundle;

        return Flux.fromIterable(referenceWrapper.references())
                .concatMap(reference -> {
                    Mono<Resource> referenceResource;

                    // Try to get the resource from available bundles or fetch it
                    if (patientBundle != null && patientBundle.contains(reference)) {
                        referenceResource = Mono.justOrEmpty(patientBundle.get(reference));
                    } else if (coreBundle.contains(reference)) {
                        referenceResource = Mono.justOrEmpty(coreBundle.get(reference));
                    } else {
                        logger.debug("Reference {} not found in patientBundle or coreBundle, attempting fetch.", reference);
                        referenceResource = getResourceMono(patientBundle, applyConsent, reference)
                                .doOnSuccess(resource -> {
                                    if (resource != null) {


                                        if (compartmentManager.isInCompartment(resource)) {
                                            if (patientBundle != null) {
                                                patientBundle.put(resource);
                                            }
                                        } else {
                                            coreBundle.put(resource);
                                        }
                                    }
                                })
                                .onErrorResume(e -> {
                                    logger.error("Error fetching resource for reference {}: {}", reference, e.getMessage());
                                    // Return an empty list on error
                                    referenceWrapper.refAttribute().linkedGroups()
                                            .forEach(groupId -> {
                                                ResourceGroup resourceGroup = new ResourceGroup(reference, groupId);
                                                // Check if the resource group is new
                                                processingBundle.addResourceGroupValidity(resourceGroup, false);
                                            });
                                    return Mono.empty();
                                });

                    }

                    return referenceResource.flatMap(resource -> {
                        List<ResourceGroup> validGroups = collectValidGroups(referenceWrapper, groupMap, resource, processingBundle);
                        logger.trace("Valid groups found: {}", validGroups);
                        return Mono.just(validGroups);
                    });
                })
                .collectList()
                .map(lists -> lists.stream()
                        .flatMap(List::stream)
                        .toList()
                )
                .flatMapMany(list -> checkReferenceViolatesMustHave(referenceWrapper, list, processingBundle));
    }

    /**
     * Collects all valid resourceGroups for the currently processed ResourceBundle.
     * <p> For a given reference and resource checks if a valid group present in processingBundle.
     * If resourceGroups not assigned yet, executes filter, musthave (Without References) and profile checks.
     *
     * @param groupMap         known attribute groups
     * @param resource         Resource to be checked
     * @param processingBundle bundle that is currently processed
     * @return ResourceGroup if previously unknown and assignable to the group.
     */
    private List<ResourceGroup> collectValidGroups(ReferenceWrapper referenceWrapper, Map<String, AnnotatedAttributeGroup> groupMap, Resource resource, ResourceBundle processingBundle) {
        return referenceWrapper.refAttribute().linkedGroups().stream()
                .map(groupId -> {
                    ResourceGroup resourceGroup = new ResourceGroup(ResourceUtils.getRelativeURL(resource), groupId);
                    Boolean isValid = processingBundle.isValidResourceGroup(resourceGroup);
                    if (isValid == null) {
                        AnnotatedAttributeGroup group = groupMap.get(groupId);
                        boolean fulfilled = profileMustHaveChecker.fulfilled(resource, group);
                        if (group.compiledFilter() != null) {
                            fulfilled = fulfilled && group.compiledFilter().test(resource);
                        }
                        logger.trace("Group {} for Reference: {}", groupId, fulfilled);
                        isValid = fulfilled;
                        processingBundle.addResourceGroupValidity(resourceGroup, isValid);
                    }
                    return isValid ? resourceGroup : null;
                })
                .filter(Objects::nonNull)
                .toList();
    }


    /**
     * For a unknown reference it gets the resource.
     * If consent is applied to patientResources, it uses the patientBundle to check consent.
     *
     * @param patientBundle used for consent
     * @param applyConsent  should consent be used.
     * @param reference     reference string to be fetched from server. Only relative URLs are handled.
     * @return loaded resource checked for consent and correct bundle
     */
    public Mono<Resource> getResourceMono(@Nullable PatientResourceBundle patientBundle, boolean applyConsent, String reference) {
        logger.debug("Getting resource for {}", reference);
        return dataStore.fetchResourceByReference(reference)
                .flatMap(resource -> {
                    if (!compartmentManager.isInCompartment(resource)) {
                        return Mono.just(resource);
                    }
                    return checkBundleAndConsent(patientBundle, applyConsent, resource);
                });

    }

    /**
     * For a resource it checks if the resource is part of the bundle it claims to be
     *
     * <p> When a resource is loaded it is checked if the resource is a patient or core Resource
     * (core Resources should not link to patient resources) and checks in case of patient resources
     * if it fits the consent and is assigned to the correct patient.
     *
     * @param patientBundle bundle to which the loaded resource should belong
     * @param applyConsent  flag if batch has a consent check
     * @param resource      resource to check
     * @return Mono emitting the resource if valid, or an error otherwise
     */
    private Mono<Resource> checkBundleAndConsent(PatientResourceBundle patientBundle, boolean applyConsent, Resource resource) {
        if (patientBundle == null) {
            return Mono.error(new ReferenceToPatientException("Patient Resource referenced in Core Bundle"));
        }

        try {
            String resourcePatientId = ResourceUtils.patientId((DomainResource) resource);
            if (!resourcePatientId.equals(patientBundle.patientId())) {
                return Mono.error(new ReferenceToPatientException("Patient loaded reference belonging to another patient"));
            }

            if (applyConsent && !consentValidator.checkConsent((DomainResource) resource, patientBundle)) {
                return Mono.error(new ConsentViolatedException("Consent Violated in Patient Resource"));
            }

            return Mono.just(resource);

        } catch (PatientIdNotFoundException e) {
            return Mono.error(e);
        }
    }


}
