package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ReferenceWrapper;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import de.medizininformatikinitiative.torch.util.ReferenceExtractor;
import de.medizininformatikinitiative.torch.util.ReferenceHandler;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service class responsible for resolving references within a PatientResourceBundle and the CoreBundle.
 */
public class ReferenceResolver {
    private static final Logger logger = LoggerFactory.getLogger(ReferenceResolver.class);

    private final ReferenceExtractor referenceExtractor;
    private final CompartmentManager compartmentManager;
    private final ReferenceHandler referenceHandler;
    private final ReferenceBundleLoader bundleLoader;

    /**
     * Constructs a ReferenceResolver with the necessary dependencies.
     *
     * @param compartmentManager for deciding if Resources are in the
     * @param referenceHandler   for handling extracted references
     * @param referenceExtractor for extracting references from cache or loading them from server
     * @param bundleLoader
     */
    public ReferenceResolver(CompartmentManager compartmentManager,
                             ReferenceHandler referenceHandler, ReferenceExtractor referenceExtractor, ReferenceBundleLoader bundleLoader) {
        this.referenceExtractor = referenceExtractor;
        this.compartmentManager = compartmentManager;
        this.referenceHandler = referenceHandler;
        this.bundleLoader = bundleLoader;
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
        return Mono.just(coreBundle.getValidResourceGroups())
                .expand(currentGroupSet -> processResourceGroups(currentGroupSet, null, coreBundle, false, groupMap).onErrorResume(e -> {
                    return Mono.empty(); // Skip this resource group on error
                }))
                .then(Mono.just(coreBundle));
    }

    /**
     * Extracts all known valid ResourceGroups from direct loading and then resolves references
     * until no new ResourceGroups could be found.
     *
     * @param batch      patientBatch containing Patientbundles with patient resources and ResourceGroups to be processed
     * @param coreBundle bundle containing core resources
     * @param groupMap   map of known attribute groups
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
            boolean applyConsent,
            Map<String, AnnotatedAttributeGroup> groupMap) {

        return Mono.just(patientBundle.bundle().getValidResourceGroups())
                .expand(currentGroupSet ->
                        processResourceGroups(currentGroupSet, patientBundle, coreBundle, applyConsent, groupMap)
                                .onErrorResume(e -> {
                                    logger.warn("Error processing resource group set {} in PatientBundle: {}", currentGroupSet, e.getMessage());
                                    return Mono.empty(); // Skip this group on error
                                })
                )
                .then(Mono.just(patientBundle));
    }

    /**
     * For a given List of ResourceGroup it extracts the references if a reference attribute is in the attribute group.
     * For every found attribute it updates the parent to attribute relation and then tries to handle the reference.
     *
     * @param validResourceGroups valid not yet processed ResourceGroup to be handled
     * @param patientBundle       bundle containing patient resources
     * @param coreBundle          bundle containing core resources
     * @param applyConsent        if consent is to applied to patient resources
     * @param groupMap            map of known attribute groups
     * @return newly added resourceGroups to be fed back into reference handling pipeline.
     */
    public Mono<Set<ResourceGroup>> processResourceGroups(
            Set<ResourceGroup> validResourceGroups,
            @Nullable PatientResourceBundle patientBundle,
            ResourceBundle coreBundle,
            boolean applyConsent,
            Map<String, AnnotatedAttributeGroup> groupMap) {
        Map<ResourceGroup, List<ReferenceWrapper>> references =
                loadReferencesFromResources(validResourceGroups, patientBundle, coreBundle, groupMap);

        return bundleLoader.fetchUnknownResources(references, patientBundle, coreBundle, applyConsent)
                .thenMany(
                        Flux.fromIterable(references.entrySet())
                                .parallel()
                                .runOn(Schedulers.parallel())
                                .flatMap(entry ->
                                        {
                                            try {
                                                return referenceHandler.handleReferences(
                                                        entry.getValue(),
                                                        patientBundle,
                                                        coreBundle,
                                                        groupMap
                                                );
                                            } catch (MustHaveViolatedException e) {
                                                return Flux.empty();
                                            }
                                        }
                                )
                )
                .collect(Collectors.toSet())
                .flatMap(set -> set.isEmpty() ? Mono.empty() : Mono.just(set));
    }


    public Map<ResourceGroup, List<ReferenceWrapper>> loadReferencesFromResources(
            Set<ResourceGroup> parentResourceGroups,
            @Nullable PatientResourceBundle patientBundle,
            ResourceBundle coreBundle,
            Map<String, AnnotatedAttributeGroup> groupMap) {

        ResourceBundle processingBundle = (patientBundle == null) ? coreBundle : patientBundle.bundle();

        return parentResourceGroups.parallelStream()
                .map(resourceGroup -> {
                    boolean isPatientResource = compartmentManager.isInCompartment(resourceGroup);

                    if (isPatientResource && patientBundle == null) {
                        logger.warn("Skipping resourceGroup {}: Patient resource requires a PatientResourceBundle", resourceGroup);
                        processingBundle.addResourceGroupValidity(resourceGroup, false);
                        return Map.entry(resourceGroup, Collections.<ReferenceWrapper>emptyList());
                    }

                    Optional<Resource> resource = isPatientResource
                            ? patientBundle.bundle().get(resourceGroup.resourceId())
                            : coreBundle.get(resourceGroup.resourceId());

                    if (resource.isPresent()) {
                        try {
                            List<ReferenceWrapper> extracted = referenceExtractor.extract(resource.get(), groupMap, resourceGroup.groupId());
                            return Map.entry(resourceGroup, extracted);
                        } catch (MustHaveViolatedException e) {
                            synchronized (processingBundle) {
                                processingBundle.addResourceGroupValidity(resourceGroup, false);
                            }
                            return Map.entry(resourceGroup, Collections.<ReferenceWrapper>emptyList());
                        }
                    } else {
                        synchronized (processingBundle) {
                            logger.warn("Empty resource marked as valid for group {}", resourceGroup);
                            processingBundle.addResourceGroupValidity(resourceGroup, false);
                        }
                        return Map.entry(resourceGroup, Collections.<ReferenceWrapper>emptyList());
                    }
                })
                .filter(entry -> !entry.getValue().isEmpty())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (list1, list2) -> {
                            List<ReferenceWrapper> merged = new ArrayList<>(list1);
                            merged.addAll(list2);
                            return merged;
                        }
                ));
    }


}
