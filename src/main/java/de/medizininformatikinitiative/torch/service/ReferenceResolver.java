package de.medizininformatikinitiative.torch.service;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.management.ConsentHandler;
import de.medizininformatikinitiative.torch.model.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.ReferenceWrapper;
import de.medizininformatikinitiative.torch.model.ResourceBundle;
import de.medizininformatikinitiative.torch.model.ResourceGroupWrapper;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.util.ProfileMustHaveChecker;
import de.medizininformatikinitiative.torch.util.ReferenceExtractor;
import de.medizininformatikinitiative.torch.util.ReferenceHandler;
import de.medizininformatikinitiative.torch.util.ResourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Service class responsible for resolving references within a PatientResourceBundle and the CoreBundle.
 */
public class ReferenceResolver {
    private static final Logger logger = LoggerFactory.getLogger(ReferenceResolver.class);

    private final ReferenceExtractor referenceExtractor;
    private final CompartmentManager compartmentManager;
    private final ReferenceHandler referenceHandler;

    /**
     * Constructs a ReferenceResolver with the necessary dependencies.
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
     * Resolves references within the coreBundle.
     */
    public Mono<ResourceBundle> resolveCoreBundle(ResourceBundle coreBundle, Map<String, AnnotatedAttributeGroup> groupMap) {
        return Flux.fromIterable(coreBundle.values())
                .expand(wrapper -> processCoreResourceWrapper(wrapper, coreBundle, groupMap))
                .then(Mono.just(coreBundle));
    }

    /**
     * Processes a ResourceGroupWrapper in the coreBundle.
     */
    private Flux<ResourceGroupWrapper> processCoreResourceWrapper(ResourceGroupWrapper wrapper, ResourceBundle coreBundle, Map<String, AnnotatedAttributeGroup> groupMap) {
        return extractReferences(wrapper, groupMap)
                .flatMapMany(Flux::fromIterable)
                .flatMap(ref -> referenceHandler.handleReference(ref, Optional.empty(), coreBundle, false, groupMap)
                        .onErrorResume(MustHaveViolatedException.class, e -> {
                            logger.warn("MustHaveViolatedException for reference {} in core resource {}: {}. Removing affected groups.",
                                    ref.refAttribute(),
                                    ResourceUtils.getRelativeURL(wrapper.resource()),
                                    e.getMessage());
                            // Remove only the group associated with the failing reference
                            wrapper.removeGroups(Set.of(ref.GroupId()));
                            coreBundle.put(wrapper);
                            return Mono.empty(); // Continue processing other references
                        }))
                .flatMap(resourceWrappers -> Flux.fromIterable(resourceWrappers)
                        .filter(resourceWrapper -> !compartmentManager.isInCompartment(resourceWrapper.resource()))
                        .filter(coreBundle::put)
                );
    }

    /**
     * Processes a batch of PatientResourceBundles sequentially.
     */
    Mono<PatientBatchWithConsent> processSinglePatientBatch(
            PatientBatchWithConsent batch, ResourceBundle coreBundle, Map<String, AnnotatedAttributeGroup> groupMap) {
        return Flux.fromIterable(batch.bundles().entrySet())
                .concatMap(entry -> resolvePatient(entry.getValue(), coreBundle, batch.applyConsent(), groupMap)
                        .map(updatedBundle -> Map.entry(entry.getKey(), updatedBundle)))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .map(updatedBundles -> new PatientBatchWithConsent(updatedBundles, batch.applyConsent()));
    }

    /**
     * Resolves all references within the given PatientResourceBundle.
     */
    public Mono<PatientResourceBundle> resolvePatient(
            PatientResourceBundle patientBundle,
            ResourceBundle coreBundle,
            Boolean applyConsent,
            Map<String, AnnotatedAttributeGroup> groupMap) {

        return Flux.fromIterable(patientBundle.values())
                .expand(wrapper -> processPatientResourceWrapper(wrapper, patientBundle, coreBundle, applyConsent, groupMap))
                .then(Mono.just(patientBundle));
    }

    /**
     * Processes a ResourceGroupWrapper and updates the PatientResourceBundle accordingly.
     */
    private Flux<ResourceGroupWrapper> processPatientResourceWrapper(ResourceGroupWrapper wrapper, PatientResourceBundle patientBundle, ResourceBundle coreBundle, Boolean applyConsent, Map<String, AnnotatedAttributeGroup> groupMap) {
        return extractReferences(wrapper, groupMap)
                .flatMapMany(Flux::fromIterable)
                .flatMap(ref -> referenceHandler.handleReference(ref, Optional.of(patientBundle), coreBundle, applyConsent, groupMap)
                        .onErrorResume(MustHaveViolatedException.class, e -> {
                            logger.debug("MustHaveViolatedException for reference {} in parent resource {}: {}. Removing affected groups.",
                                    ref.refAttribute(),
                                    ResourceUtils.getRelativeURL(wrapper.resource()),
                                    e.getMessage());

                            // Remove only the group associated with the failing reference
                            wrapper.removeGroups(Set.of(ref.GroupId()));
                            patientBundle.put(wrapper);

                            return Mono.empty(); // Continue processing other references
                        }))
                .flatMap(resourceWrappers -> Flux.fromIterable(resourceWrappers)
                        .flatMap(resourceWrapper -> {
                            logger.trace("Handling {}", ResourceUtils.getRelativeURL(resourceWrapper.resource()));
                            if (compartmentManager.isInCompartment(resourceWrapper.resource())) {
                                boolean updated = patientBundle.put(resourceWrapper);
                                return updated ? Flux.just(resourceWrapper) : Flux.empty();
                            } else {
                                coreBundle.put(resourceWrapper);
                                return Flux.empty();
                            }
                        }));
    }


    /**
     * Extracts references from a ResourceGroupWrapper.
     */
    private Mono<List<ReferenceWrapper>> extractReferences(ResourceGroupWrapper wrapper, Map<String, AnnotatedAttributeGroup> groupMap) {
        try {

            return Mono.just(referenceExtractor.extract(wrapper, groupMap));
        } catch (MustHaveViolatedException e) {
            return Mono.error(e);
        }
    }

}
