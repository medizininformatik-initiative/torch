package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.exceptions.CoreReferenceToPatientException;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.management.ConsentHandler;
import de.medizininformatikinitiative.torch.model.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.ReferenceWrapper;
import de.medizininformatikinitiative.torch.model.ResourceBundle;
import de.medizininformatikinitiative.torch.model.ResourceGroupWrapper;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.util.ProfileMustHaveChecker;
import de.medizininformatikinitiative.torch.util.ReferenceExtractor;
import org.hl7.fhir.r4.model.DomainResource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service class responsible for resolving references within a PatientResourceBundle.
 */
public class ReferenceResolver {

    private final ReferenceExtractor referenceExtractor;
    private final DataStore dataStore;
    private final ProfileMustHaveChecker profileMustHaveChecker;
    private final CompartmentManager compartmentManager;
    private final ConsentHandler consentHandler;
    private final Map<String, AnnotatedAttributeGroup> attributeGroupMap;


    /**
     * Constructs a ReferenceResolver with the necessary dependencies.
     *
     * @param referenceExtractor     Utility to extract references from resources.
     * @param dataStore              Data store to fetch resources by reference.
     * @param profileMustHaveChecker Checker to validate profile constraints.
     * @param compartmentManager     Manager to determine resource compartmentalization.
     * @param consentHandler         Handler to manage consents.
     * @param attributeGroupMap      Map of attribute group identifiers to their definitions.
     * @param coreBundle             Core resource bundle shared across patients.
     */
    public ReferenceResolver(ReferenceExtractor referenceExtractor,
                             DataStore dataStore,
                             ProfileMustHaveChecker profileMustHaveChecker,
                             CompartmentManager compartmentManager,
                             ConsentHandler consentHandler,
                             Map<String, AnnotatedAttributeGroup> attributeGroupMap,
                             ResourceBundle coreBundle) {
        this.referenceExtractor = referenceExtractor;
        this.dataStore = dataStore;
        this.profileMustHaveChecker = profileMustHaveChecker;
        this.compartmentManager = compartmentManager;
        this.consentHandler = consentHandler;
        this.attributeGroupMap = attributeGroupMap;

    }

    /**
     * Resolves references within the coreBundle using an empty PatientResourceBundle.
     *
     * @param applyConsent Flag indicating whether to apply consent.
     * @return A Mono that completes when processing is done.
     */
    public Mono<Void> resolveCoreBundle(ResourceBundle coreBundle) {
        return Flux.<ResourceGroupWrapper>create(sink -> {
                    coreBundle.values().forEach(wrapper -> processCoreResourceWrapper(wrapper, coreBundle, sink));
                    sink.complete();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    /**
     * Processes a ResourceGroupWrapper in the coreBundle.
     *
     * @param wrapper The resource group wrapper to process.
     * @param sink    FluxSink to emit new ResourceGroupWrappers for processing.
     */
    private void processCoreResourceWrapper(ResourceGroupWrapper wrapper, ResourceBundle coreBundle, FluxSink<ResourceGroupWrapper> sink) {
        extractReferences(wrapper)
                .flatMapMany(Flux::fromIterable)
                .flatMap(ref -> handleReference(ref, Optional.empty(), coreBundle, false)) // Use empty patientBundle
                .doOnNext(resourceWrappers -> resourceWrappers.forEach(resourceWrapper -> {
                    if (coreBundle.put(resourceWrapper)) {
                        sink.next(resourceWrapper);
                    }
                }))
                .subscribe();
    }


    /**
     * Resolves all references within the given PatientResourceBundle.
     *
     * @param patientBundle The patient resource bundle to resolve.
     * @param applyConsent  Flag indicating whether to apply consent.
     * @return A Mono emitting the updated PatientResourceBundle upon completion.
     */
    public Mono<PatientResourceBundle> resolvePatient(PatientResourceBundle patientBundle, ResourceBundle coreBundle, Boolean applyConsent) {
        return Flux.<ResourceGroupWrapper>create(sink -> {
                    patientBundle.values().forEach(wrapper -> processPatientResourceWrapper(wrapper, patientBundle, coreBundle, applyConsent, sink));
                    sink.complete();
                })
                .subscribeOn(Schedulers.boundedElastic()) // Ensure processing occurs on a bounded elastic scheduler
                .then(Mono.just(patientBundle));
    }


    /**
     * Processes a ResourceGroupWrapper and updates the PatientResourceBundle accordingly.
     * New ResourceGroupWrappers are emitted into the provided FluxSink for dynamic processing.
     *
     * @param wrapper       The resource group wrapper to process.
     * @param patientBundle The patient resource bundle being updated.
     * @param applyConsent  Flag indicating whether to apply consent.
     * @param sink          The FluxSink to emit new ResourceGroupWrappers.
     */
    private void processPatientResourceWrapper(ResourceGroupWrapper wrapper, PatientResourceBundle patientBundle, ResourceBundle coreBundle, Boolean applyConsent, FluxSink<ResourceGroupWrapper> sink) {
        extractReferences(wrapper)
                .flatMapMany(Flux::fromIterable)
                .flatMap(ref -> handleReference(ref, Optional.of(patientBundle), coreBundle, applyConsent))
                .doOnNext(resourceWrappers -> resourceWrappers.forEach(resourceWrapper -> {
                    boolean updated = false;
                    if (compartmentManager.isInCompartment(resourceWrapper.resource())) {
                        updated = patientBundle.put(resourceWrapper);
                    } else {
                        updated = coreBundle.put(resourceWrapper);
                    }
                    // Emit only if the wrapper is new or has been updated
                    if (updated) {
                        sink.next(resourceWrapper);
                    }
                }))
                .subscribe();
    }


    /**
     * Extracts references from a ResourceGroupWrapper.
     *
     * @param wrapper The resource group wrapper from which to extract references.
     * @return A Mono emitting a list of ReferenceWrappers.
     */
    private Mono<List<ReferenceWrapper>> extractReferences(ResourceGroupWrapper wrapper) {
        try {
            List<ReferenceWrapper> references = referenceExtractor.extract(wrapper);
            return Mono.just(references);
        } catch (MustHaveViolatedException e) {
            return Mono.error(e);
        }
    }

    /**
     * Handles a ReferenceWrapper by resolving its references and updating the patient bundle.
     *
     * @param referenceWrapper The reference wrapper to handle.
     * @param patientBundle    The patient bundle being updated.
     * @param applyConsent
     * @return A Mono emitting a list of ResourceGroupWrappers corresponding to the resolved references.
     */
    public Mono<List<ResourceGroupWrapper>> handleReference(ReferenceWrapper referenceWrapper, Optional<PatientResourceBundle> patientBundle, ResourceBundle coreBundle, Boolean applyConsent) {
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
                        Set<AnnotatedAttributeGroup> groups = referenceWrapper.refAttribute().linkedGroups().stream()
                                .map(attributeGroupMap::get)
                                .filter(group -> profileMustHaveChecker.fulfilled((DomainResource) resourceWrapper.resource(), group))
                                .collect(Collectors.toSet());

                        if (referenceWrapper.refAttribute().mustHave() && groups.isEmpty()) {
                            return Mono.error(new MustHaveViolatedException("MustHave condition violated for " + reference));
                        }

                        resourceWrapper.addGroups(groups);
                        return Mono.just(resourceWrapper);
                    }).onErrorResume(MustHaveViolatedException.class, e -> Mono.empty());
                })
                .collectList();
    }

    public Mono<ResourceGroupWrapper> getResourceGroupWrapperMono(Optional<PatientResourceBundle> patientBundle, Boolean applyConsent, String reference) {
        Mono<ResourceGroupWrapper> referenceResource;
        referenceResource = dataStore.fetchResourceByReference(reference)
                .flatMap(resource ->
                {
                    if (compartmentManager.isInCompartment(resource)) {
                        if (patientBundle.isPresent()) {
                            if (applyConsent) {
                                if (consentHandler.checkConsent((DomainResource) resource, patientBundle.get())) {
                                    return Mono.just(resource);
                                } else {
                                    return Mono.empty();
                                }
                            } else {
                                return Mono.just(resource);
                            }
                        } else {
                            //Case in compartment but not from patientBundle
                            return Mono.error(new CoreReferenceToPatientException("Patient Resource referenced in Core Bundle"));
                        }
                    }
                    return Mono.just(resource);
                })
                .map(resource -> new ResourceGroupWrapper(resource, Set.of()));
        return referenceResource;
    }
}
