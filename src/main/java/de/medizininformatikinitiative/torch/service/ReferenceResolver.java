package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.exceptions.ResourceTypeMissmatchException;
import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceAttribute;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import de.medizininformatikinitiative.torch.util.ReferenceExtractor;
import de.medizininformatikinitiative.torch.util.ReferenceHandler;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

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
     *
     * @param compartmentManager for deciding if Resources are in the
     * @param referenceHandler   for handling extracted references
     * @param referenceExtractor for extracting references from cache or loading them from server
     */
    public ReferenceResolver(CompartmentManager compartmentManager,
                             ReferenceHandler referenceHandler, ReferenceExtractor referenceExtractor) {
        this.referenceExtractor = referenceExtractor;
        this.compartmentManager = compartmentManager;
        this.referenceHandler = referenceHandler;
    }

    /**
     * Extracts all known valid ResourceGroups from direct loading and then resolves references
     * until no new ResourceGroups could be found.
     *
     * @param coreBundle bundle containing core resources and ResourceGroups to be processed
     * @param groupMap   map of known attribute groups
     * @return newly added resourceGroups to be fed back into reference handling pipeline.
     */
    public Mono<ResourceBundle> resolveCoreBundle(ResourceBundle coreBundle, Map<String, AnnotatedAttributeGroup> groupMap) {
        return Flux.fromIterable(coreBundle.resourceGroupValidity().keySet())
                .expand(resourceGroup -> processResourceGroup(resourceGroup, null, coreBundle, false, groupMap).onErrorResume(e -> {

                    logger.warn("Error processing resource group {} in Core Bundle: {}", resourceGroup, e.getMessage());
                    return Flux.empty(); // Skip this resource group on error
                }))
                .then(Mono.just(coreBundle));
    }

    /**
     * Extracts all known valid ResourceGroups from direct loading and then resolves references
     * until no new ResourceGroups could be found.
     *
     * @param batch        patientBatch containing Patientbundles with patient resources and ResourceGroups to be processed
     * @param coreBundle   bundle containing core resources
     * @param applyConsent if consent is to be applied to patient resources
     * @param groupMap     map of known attribute groups
     * @return newly added resourceGroups to be fed back into reference handling pipeline.
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
     * Extracts all known valid ResourceGroups from direct loading and then resolves references
     * until no new ResourceGroups could be found.
     *
     * @param patientBundle bundle containing patient resources
     * @param coreBundle    bundle containing core resources
     * @param applyConsent  if consent is to be applied to patient resources
     * @param groupMap      map of known attribute groups
     * @return newly added resourceGroups to be fed back into reference handling pipeline.
     */
    public Mono<PatientResourceBundle> resolvePatient(
            PatientResourceBundle patientBundle,
            ResourceBundle coreBundle,
            Boolean applyConsent,
            Map<String, AnnotatedAttributeGroup> groupMap) {

        return Flux.fromIterable(patientBundle.bundle().resourceGroupValidity().keySet())
                //Input alle direkt geladenen resourceGroup und expanden auf returnwert
                .expand(resourceGroup -> processResourceGroup(resourceGroup, patientBundle, coreBundle, applyConsent, groupMap).onErrorResume(e -> {

                    logger.warn("Error processing resource group {} in PatientBundle: {}", resourceGroup, e.getMessage());
                    return Flux.empty(); // Skip this resource group on error
                }))
                .then(Mono.just(patientBundle));
    }

    /**
     * For a given ResourceGroup it extracts the references if a reference attribute is in the attribute group.
     * For every found attribute it updates the parent to attribute relation and then tries to handle the reference.
     *
     * @param parentResourceGroup Resource to be handled
     * @param patientBundle       bundle containing patient resources
     * @param coreBundle          bundle containing core resources
     * @param applyConsent        if consent is to applied to patient resources
     * @param groupMap            map of known attribute groups
     * @return newly added resourceGroups to be fed back into reference handling pipeline.
     */
    private Flux<ResourceGroup> processResourceGroup(ResourceGroup parentResourceGroup, @Nullable PatientResourceBundle patientBundle, ResourceBundle coreBundle, Boolean applyConsent, Map<String, AnnotatedAttributeGroup> groupMap) {

        Mono<Resource> resourceMono = null;
        boolean patientResource = compartmentManager.isInCompartment(parentResourceGroup);
        if (patientResource && patientBundle == null) {

            return Flux.error(new ResourceTypeMissmatchException("Handling a Patient Resource Bundle without a Patient Resource Bundle"));
        }
        ResourceBundle processingBundle = patientBundle != null ? patientBundle.bundle() : coreBundle;
        if (patientResource) {
            resourceMono = processingBundle.get(parentResourceGroup.resourceId());
        } else {
            resourceMono = coreBundle.get(parentResourceGroup.resourceId());
        }


        return resourceMono
                .flatMapMany(resource -> Mono.fromCallable(() -> referenceExtractor.extract(resource, groupMap, parentResourceGroup.groupId()))
                        .flatMapMany(references -> {
                            AtomicBoolean hasMustHaveViolation = new AtomicBoolean(false);
                            boolean shouldProcess = references.stream().anyMatch(reference -> {
                                ResourceAttribute resourceAttribute = new ResourceAttribute(parentResourceGroup.resourceId(), reference.refAttribute());
                                Boolean isValid = processingBundle.resourceAttributeValidity().get(resourceAttribute);
                                if (Boolean.TRUE.equals(isValid)) {
                                    return false; // Skip processing if already validated as true
                                } else if (Boolean.FALSE.equals(isValid)) {
                                    if (reference.refAttribute().mustHave()) {
                                        hasMustHaveViolation.set(true); // Flag the exception to be handled later
                                        return false;
                                    }
                                    return false; // Skip if explicitly false and not must-have
                                }

                                // If null, add attribute and mark for processing
                                processingBundle.addAttributeToParent(resourceAttribute, parentResourceGroup);
                                return true;
                            });
                            if (hasMustHaveViolation.get()) {
                                return Flux.error(new MustHaveViolatedException("Must-have attribute violated in group: " + parentResourceGroup));
                            }

                            //  If no references need processing, return empty
                            if (!shouldProcess) {
                                return Flux.empty();
                            }

                            // Handle all references together for this resourceGroup
                            return referenceHandler.handleReferences(references, patientBundle, coreBundle, applyConsent, groupMap);

                        })
                        .onErrorResume(MustHaveViolatedException.class, e -> {

                            processingBundle.addResourceGroupValidity(parentResourceGroup, false);

                            return Flux.empty(); // Skip processing for this group
                        })).onErrorResume(Exception.class, e -> {
                    // Catch any other unexpected exceptions
                    return Flux.error(new RuntimeException("Unexpected error occurred while processing resource group", e));
                });
    }


}
