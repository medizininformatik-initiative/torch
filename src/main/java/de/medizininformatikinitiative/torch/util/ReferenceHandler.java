package de.medizininformatikinitiative.torch.util;

import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.exceptions.ReferenceToPatientException;
import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.management.ConsentHandler;
import de.medizininformatikinitiative.torch.model.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.ReferenceWrapper;
import de.medizininformatikinitiative.torch.model.ResourceBundle;
import de.medizininformatikinitiative.torch.model.ResourceGroupWrapper;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.service.DataStore;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class ReferenceHandler {

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

    /**
     * Handles a ReferenceWrapper by resolving its references and updating the patient bundle.
     *
     * @param referenceWrapper The reference wrapper to handle.
     * @param patientBundle    The patient bundle being updated.
     * @param coreBundle       to be updated and queried, that contains a centrally shared concurrent HashMap
     * @param applyConsent     if consent has to be applied (only relevant if patientBundle given)
     * @param groupMap
     * @return A Mono emitting a list of ResourceGroupWrappers corresponding to the resolved references.
     */
    public Mono<List<ResourceGroupWrapper>> handleReference(ReferenceWrapper referenceWrapper, Optional<PatientResourceBundle> patientBundle, ResourceBundle coreBundle, Boolean applyConsent, Map<String, AnnotatedAttributeGroup> groupMap) {
        return Flux.fromIterable(referenceWrapper.references())
                .flatMap(reference -> {
                    Mono<ResourceGroupWrapper> referenceResource;
                    if (patientBundle.isPresent() && patientBundle.get().contains(reference)) {
                        referenceResource = patientBundle.get().get(reference);
                    } else if (coreBundle.contains(reference)) {
                        referenceResource = coreBundle.get(reference);
                    } else {
                        referenceResource = getResourceGroupWrapperMono(patientBundle, applyConsent, reference);
                    }
                    return referenceResource.flatMap(resourceWrapper -> {
                        Set<String> groups = referenceWrapper.refAttribute().linkedGroups().stream()
                                .map(groupMap::get)
                                .filter(group -> profileMustHaveChecker.fulfilled(resourceWrapper.resource(), group))
                                .map(AnnotatedAttributeGroup::id)
                                .collect(Collectors.toSet());
                        if (referenceWrapper.refAttribute().mustHave() && groups.isEmpty()) {
                            return Mono.error(new MustHaveViolatedException("MustHave condition violated for " + reference));
                        }
                        return Mono.just(resourceWrapper.addGroups(groups));
                    }).onErrorResume(MustHaveViolatedException.class, e -> Mono.empty());
                })
                .collectList().flatMap(resourceList -> {
                    if (referenceWrapper.refAttribute().mustHave() && resourceList.isEmpty()) {
                        return Mono.error(new MustHaveViolatedException("MustHave condition violated: No valid references were resolved."));
                    }
                    return Mono.just(resourceList);
                });
    }

    public Mono<ResourceGroupWrapper> getResourceGroupWrapperMono(Optional<PatientResourceBundle> patientBundle, Boolean applyConsent, String reference) {
        return Mono.justOrEmpty(dataStore.fetchDomainResource(reference))
                .flatMap(resourceMono -> resourceMono)
                .switchIfEmpty(Mono.error(new RuntimeException("Failed to fetch resource: received empty response")))
                .flatMap(resource -> {
                    if (compartmentManager.isInCompartment(resource)) {
                        if (patientBundle.isPresent()) {

                            try {

                                if (ResourceUtils.patientId(resource).equals(patientBundle.get().patientId())) {
                                    if (applyConsent) {
                                        if (consentHandler.checkConsent(resource, patientBundle.get())) {
                                            return Mono.just(new ResourceGroupWrapper(resource, Set.of()));
                                        } else {
                                            return Mono.error(new ConsentViolatedException("Consent Violated in Patient Resource"));
                                        }
                                    } else {
                                        return Mono.just(new ResourceGroupWrapper(resource, Set.of()));
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

                    return Mono.just(new ResourceGroupWrapper(resource, Set.of()));
                });
    }

}
