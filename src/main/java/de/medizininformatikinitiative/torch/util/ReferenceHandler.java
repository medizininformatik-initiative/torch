package de.medizininformatikinitiative.torch.util;

import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ReferenceWrapper;
import de.medizininformatikinitiative.torch.model.management.ResourceAttribute;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class ReferenceHandler {
    private static final Logger logger = LoggerFactory.getLogger(ReferenceHandler.class);
    private final ProfileMustHaveChecker profileMustHaveChecker;


    public ReferenceHandler(ProfileMustHaveChecker profileMustHaveChecker) {
        this.profileMustHaveChecker = profileMustHaveChecker;

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
     * @param references    References extracted from a single resource for a single resourceGroup to be handled
     * @param patientBundle ResourceBundle containing patient information (Optional for core bundle)
     * @param coreBundle    coreResourceBundle containing the core Resources
     * @param groupMap      cache containing all known attributeGroups
     * @return newly added ResourceGroups to be processed
     */
    public Flux<ResourceGroup> handleReferences(List<ReferenceWrapper> references,
                                                @Nullable PatientResourceBundle patientBundle,
                                                ResourceBundle coreBundle,
                                                Map<String, AnnotatedAttributeGroup> groupMap,
                                                Set<ResourceGroup> knownGroups) throws MustHaveViolatedException {
        ResourceBundle processingBundle = (patientBundle != null) ? patientBundle.bundle() : coreBundle;
        ResourceGroup parentGroup = new ResourceGroup(references.getFirst().resourceId(), references.getFirst().groupId());

        List<ReferenceWrapper> unprocessedReferences = filterUnprocessedReferences(references, processingBundle);
        return Flux.fromIterable(unprocessedReferences)
                .concatMap(ref -> handleReference(ref, patientBundle, coreBundle, groupMap).doOnNext(
                        resourceGroupList -> {
                            ResourceAttribute referenceAttribute = ref.toResourceAttributeGroup();
                            resourceGroupList.forEach(resourceGroup -> processingBundle.addAttributeToChild(referenceAttribute, resourceGroup));
                        }
                ))
                .collectList()
                .flatMapMany(results -> Flux.fromIterable(results.stream()
                        .flatMap(List::stream)
                        .toList()))
                .filter(group -> !knownGroups.contains(group))
                .onErrorResume(MustHaveViolatedException.class, e -> {
                    processingBundle.addResourceGroupValidity(parentGroup, false);
                    logger.warn("MustHaveViolatedException occurred. Stopping resource processing: {}", e.getMessage());
                    return Flux.empty();
                });
    }

    /**
     * Handles a ReferenceWrapper by resolving its references and updating the processing bundle.
     *
     * @param referenceWrapper The reference wrapper to handle.
     * @param patientBundle    The patient bundle being updated, if present.
     * @param coreBundle       to be updated and queried, that contains a centrally shared concurrent HashMap.
     * @param groupMap         Map of attribute groups for validation.
     * @return A Flux emitting a list of ResourceGroups corresponding to the resolved references.
     */
    public Flux<List<ResourceGroup>> handleReference(ReferenceWrapper referenceWrapper,
                                                     @Nullable PatientResourceBundle patientBundle,
                                                     ResourceBundle coreBundle,
                                                     Map<String, AnnotatedAttributeGroup> groupMap) {

        ResourceBundle processingBundle = patientBundle != null ? patientBundle.bundle() : coreBundle;

        List<ResourceGroup> allValidGroups = referenceWrapper.references()
                .stream()
                .map(reference -> {
                    if (patientBundle != null && patientBundle.contains(reference)) {
                        return patientBundle.get(reference);
                    } else {
                        return coreBundle.get(reference);
                    }
                })
                .filter(Objects::nonNull)
                .filter(Optional::isPresent)
                .flatMap(resource -> collectValidGroups(referenceWrapper, groupMap, resource.get(), processingBundle).stream())
                .toList();

        // Now run your must-have validation and wrap in Flux
        return checkReferenceViolatesMustHave(referenceWrapper, allValidGroups, processingBundle);
    }


    /**
     * Collects all valid resourceGroups for the currently processed ResourceBundle.
     * <p> For a given reference and resource checks if already a valid group in processingBundle.
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
     * Iterates over the references to find unprocessed reference wrappers.
     *
     * <p>Checks for reference resource and attribute, if that combination has already been checked.
     *
     * @param references       referencesWrappers to be handled
     * @param processingBundle bundle on which the referenceWrappers are processed
     * @return filters unprocessed ReferenceWrappers
     * @throws MustHaveViolatedException when attribute group in resource is invalid.
     */
    private List<ReferenceWrapper> filterUnprocessedReferences(List<ReferenceWrapper> references,
                                                               ResourceBundle processingBundle) throws MustHaveViolatedException {
        List<ReferenceWrapper> uncheckedReferences = new ArrayList<>();

        for (ReferenceWrapper reference : references) {
            ResourceAttribute resourceAttribute = reference.toResourceAttributeGroup();
            ResourceGroup parentGroup = reference.toResourceGroup();

            Boolean isValid = processingBundle.resourceAttributeValidity().get(resourceAttribute);
            processingBundle.addAttributeToParent(resourceAttribute, parentGroup);
            if (Boolean.TRUE.equals(isValid)) {
                // Already valid, skip
            } else {
                if (Boolean.FALSE.equals(isValid)) {
                    if (reference.refAttribute().mustHave()) {
                        processingBundle.addResourceGroupValidity(parentGroup, false);
                        throw new MustHaveViolatedException(
                                "Must-have attribute violated for reference: " + reference + " in group: " + parentGroup);
                    }

                } else {
                    uncheckedReferences.add(reference);
                }
            }
        }
        return uncheckedReferences;
    }


}
