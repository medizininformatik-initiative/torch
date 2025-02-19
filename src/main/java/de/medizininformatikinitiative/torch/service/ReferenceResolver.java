package de.medizininformatikinitiative.torch.service;

import ca.uhn.fhir.context.FhirContext;
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
import de.medizininformatikinitiative.torch.util.ReferenceHandler;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service class responsible for resolving references within a PatientResourceBundle and the CoreBundle.
 */
public class ReferenceResolver {
    private final ReferenceExtractor referenceExtractor;
    private final CompartmentManager compartmentManager;
    private final ReferenceHandler referenceHandler;

    /**
     * Constructs a ReferenceResolver with the necessary dependencies.
     *
     * @param ctx                    Utility to extract references from resources.
     * @param dataStore              Data store to fetch resources by reference.
     * @param profileMustHaveChecker Checker to validate profile constraints.
     * @param compartmentManager     Manager to determine resource compartmentalization.
     * @param consentHandler         Handler to manage consents.
     */
    public ReferenceResolver(FhirContext ctx,
                             DataStore dataStore,
                             ProfileMustHaveChecker profileMustHaveChecker,
                             CompartmentManager compartmentManager,
                             ConsentHandler consentHandler) {
        this.referenceExtractor = new ReferenceExtractor(ctx);
        this.compartmentManager = compartmentManager;
        this.referenceHandler = new ReferenceHandler(dataStore, profileMustHaveChecker, compartmentManager, consentHandler);
    }


    /**
     * Resolves references within the coreBundle using an empty PatientResourceBundle.
     *
     * @param coreBundle to be handled
     * @param groupMap
     * @return A Mono that completes when processing is done.
     */
    public Mono<ResourceBundle> resolveCoreBundle(ResourceBundle coreBundle, Map<String, AnnotatedAttributeGroup> groupMap) {
        return Flux.<ResourceGroupWrapper>create(sink -> {
                    coreBundle.values().forEach(wrapper -> processCoreResourceWrapper(wrapper, coreBundle, sink, groupMap));
                    sink.complete();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then(Mono.just(coreBundle));
    }

    /**
     * Processes a ResourceGroupWrapper in the coreBundle.
     *
     * @param wrapper    The resource group wrapper to process.
     * @param coreBundle coreBundle to be processed
     * @param sink       FluxSink to emit new ResourceGroupWrappers for processing.
     * @param groupMap   Map of all valid attributeGroups
     */
    private void processCoreResourceWrapper(ResourceGroupWrapper wrapper, ResourceBundle coreBundle, FluxSink<ResourceGroupWrapper> sink, Map<String, AnnotatedAttributeGroup> groupMap) {
        extractReferences(wrapper, groupMap)
                .flatMapMany(Flux::fromIterable)
                .flatMap(ref -> referenceHandler.handleReference(ref, Optional.empty(), coreBundle, false, groupMap))
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
     * @param coreBundle    to be updated and queried, that contains a centrally shared concurrent HashMap
     * @param applyConsent  Flag indicating whether to apply consent.
     * @param groupMap
     * @return A Mono emitting the updated PatientResourceBundle upon completion.
     */
    public Mono<PatientResourceBundle> resolvePatient(PatientResourceBundle patientBundle, ResourceBundle coreBundle, Boolean applyConsent, Map<String, AnnotatedAttributeGroup> groupMap) {
        return Flux.<ResourceGroupWrapper>create(sink -> {
                    patientBundle.values().forEach(wrapper -> processPatientResourceWrapper(wrapper, patientBundle, coreBundle, applyConsent, groupMap, sink));
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
     * @param coreBundle    to be updated and queried, that contains a centrally shared concurrent HashMap
     * @param applyConsent  Flag indicating whether to apply consent.
     * @param groupMap
     * @param sink          The FluxSink to emit new ResourceGroupWrappers.
     */
    private void processPatientResourceWrapper(ResourceGroupWrapper wrapper, PatientResourceBundle patientBundle, ResourceBundle coreBundle, Boolean applyConsent, Map<String, AnnotatedAttributeGroup> groupMap, FluxSink<ResourceGroupWrapper> sink) {
        extractReferences(wrapper, groupMap)
                .flatMapMany(Flux::fromIterable)
                .flatMap(ref -> referenceHandler.handleReference(ref, Optional.of(patientBundle), coreBundle, applyConsent, groupMap))
                .doOnNext(resourceWrappers -> resourceWrappers.forEach(resourceWrapper -> {
                    if (compartmentManager.isInCompartment(resourceWrapper.resource())) {
                        boolean updated = patientBundle.put(resourceWrapper);
                        if (updated) {
                            sink.next(resourceWrapper);
                        }
                    } else {
                        coreBundle.put(resourceWrapper);
                    }


                }))
                .subscribe();
    }


    /**
     * Extracts references from a ResourceGroupWrapper.
     *
     * @param wrapper  The resource group wrapper from which to extract references.
     * @param groupMap
     * @return A Mono emitting a list of ReferenceWrappers.
     */
    private Mono<List<ReferenceWrapper>> extractReferences(ResourceGroupWrapper wrapper, Map<String, AnnotatedAttributeGroup> groupMap) {
        try {
            List<ReferenceWrapper> references = referenceExtractor.extract(wrapper, groupMap);
            return Mono.just(references);
        } catch (MustHaveViolatedException e) {
            return Mono.error(e);
        }
    }


}
