package de.medizininformatikinitiative.torch.service;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.exceptions.ResourceTypeMissmatchException;
import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.management.ConsentHandler;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroupWrapper;
import de.medizininformatikinitiative.torch.util.ProfileMustHaveChecker;
import de.medizininformatikinitiative.torch.util.ReferenceExtractor;
import de.medizininformatikinitiative.torch.util.ReferenceHandler;
import de.medizininformatikinitiative.torch.util.ResourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Map;
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
                .expand(wrapper -> processResourceWrapper(wrapper, null, coreBundle, false, groupMap))
                .then(Mono.just(coreBundle));
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
                //Input alle direkt geladenen PAtientenresourcen und expanden auf returnwert
                .expand(wrapper -> processResourceWrapper(wrapper, patientBundle, coreBundle, applyConsent, groupMap))
                .then(Mono.just(patientBundle));
    }

    /**
     * Processes a ResourceGroupWrapper and updates the PatientResourceBundle accordingly.
     */
    private Flux<ResourceGroupWrapper> processResourceWrapper(ResourceGroupWrapper wrapper, @Nullable PatientResourceBundle patientBundle, ResourceBundle coreBundle, Boolean applyConsent, Map<String, AnnotatedAttributeGroup> groupMap) {

        Set<String> deleteGroups = new HashSet<>();

        if (compartmentManager.isInCompartment(wrapper.resource()) && patientBundle == null) {
            return Flux.error(new ResourceTypeMissmatchException("Handling a Patient Resource Bundle without a Patient Resource Bundle"));
        }


        return Flux.fromIterable(wrapper.groupSet())
                .flatMap(group -> {
                    return Mono.fromCallable(() -> referenceExtractor.extract(wrapper, groupMap, group))
                            .flatMapMany(references -> referenceHandler.handleReferences(references, patientBundle, coreBundle, applyConsent, groupMap))
                            .onErrorResume(MustHaveViolatedException.class, e -> {
                                deleteGroups.add(group); // Mark this group for deletion
                                return Flux.empty(); // Skip processing for this group
                            });
                })
                .collectList()
                .doOnSuccess(results -> {
                    ResourceGroupWrapper updatedWrapper = wrapper.removeGroups(deleteGroups);
                    if (patientBundle != null) {
                        patientBundle.put(updatedWrapper);
                    } else {
                        coreBundle.put(updatedWrapper);
                    }

                })// Collect all emitted elements into List<List<ResourceGroupWrapper>>
                .flatMapMany(results -> Flux.fromIterable(results.stream()
                        .toList()))
                .flatMap(resourceGroupWrapper -> {
                    if (patientBundle != null && compartmentManager.isInCompartment(resourceGroupWrapper.resource())) {
                        boolean updated = patientBundle.put(resourceGroupWrapper);
                        return updated ? Mono.just(resourceGroupWrapper) : Mono.empty();
                    } else {
                        if (compartmentManager.isInCompartment(resourceGroupWrapper.resource())) {
                            logger.warn("Resource {} has been ignored since it is a Patient Resource that has been found within a core bundle processing", ResourceUtils.getRelativeURL(resourceGroupWrapper.resource()));
                            return Mono.empty();
                        }
                        coreBundle.put(resourceGroupWrapper);
                        return Mono.just(resourceGroupWrapper);
                    }
                });
    }


}
