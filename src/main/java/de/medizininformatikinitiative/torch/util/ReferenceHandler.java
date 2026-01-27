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
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Component
public class ReferenceHandler {
    private static final Logger logger = LoggerFactory.getLogger(ReferenceHandler.class);
    private final ProfileMustHaveChecker profileMustHaveChecker;


    public ReferenceHandler(ProfileMustHaveChecker profileMustHaveChecker) {
        this.profileMustHaveChecker = profileMustHaveChecker;

    }

    private static Flux<List<ResourceGroup>> checkReferenceViolatesMustHave(ReferenceWrapper referenceWrapper, List<ResourceGroup> validRGs, ResourceBundle processingBundle) {
        ResourceAttribute referenceAttribute = referenceWrapper.toResourceAttributeGroup();
        if (referenceWrapper.refAttribute().mustHave() && validRGs.isEmpty()) {
            processingBundle.setResourceAttributeInValid(referenceAttribute);
            return Flux.error(new MustHaveViolatedException(
                    "MustHave condition violated: No valid references were resolved for " + referenceWrapper.references()
            ));
        }
        processingBundle.setResourceAttributeValid(referenceAttribute);
        return Flux.just(validRGs);
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
                                                Set<ResourceGroup> knownGroups) {
        ResourceBundle processingBundleForParent = (patientBundle != null) ? patientBundle.bundle() : coreBundle;
        ResourceGroup parentGroup = new ResourceGroup(references.getFirst().resourceId(), references.getFirst().groupId());

        List<ReferenceWrapper> unprocessedReferences;
        try {
            unprocessedReferences = filterUnprocessedReferences(references, processingBundleForParent);
        } catch (MustHaveViolatedException e) {
            return Flux.error(e);
        }
        return Flux.fromIterable(unprocessedReferences)
                .concatMap(ref -> handleReferenceAttribute(ref, patientBundle, coreBundle, groupMap).doOnNext(
                        resourceGroupList -> {
                            ResourceAttribute referenceAttribute = ref.toResourceAttributeGroup();
                            resourceGroupList.forEach(resourceGroup -> processingBundleForParent.addAttributeToChild(referenceAttribute, resourceGroup));
                        }
                ))
                .collectList()
                .flatMapMany(results -> Flux.fromIterable(results.stream()
                        .flatMap(List::stream)
                        .toList()))
                .filter(group -> !knownGroups.contains(group))
                .onErrorResume(MustHaveViolatedException.class, e -> {
                    processingBundleForParent.addResourceGroupValidity(parentGroup, false);
                    logger.warn("MustHaveViolatedException occurred. Stopping resource processing: {}", e.getMessage());
                    return Flux.empty();
                });
    }

    /**
     * Handles a ReferenceWrapper (i.e. references  from one attribute) by resolving its references and updating the processing bundle.
     *
     * @param referenceWrapper The reference wrapper to handle.
     * @param patientBundle    The patient bundle being updated, if present.
     * @param coreBundle       to be updated and queried, that contains a centrally shared concurrent HashMap.
     * @param groupMap         Map of attribute groups for validation.
     * @return A Flux emitting a list of ResourceGroups corresponding to the resolved references.
     */
    public Flux<List<ResourceGroup>> handleReferenceAttribute(ReferenceWrapper referenceWrapper,
                                                              @Nullable PatientResourceBundle patientBundle,
                                                              ResourceBundle coreBundle,
                                                              Map<String, AnnotatedAttributeGroup> groupMap) {

        ResourceBundle processingBundleForParent = patientBundle != null ? patientBundle.bundle() : coreBundle;

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
                .flatMap(resource -> collectValidGroups(referenceWrapper, groupMap, resource.get(), processingBundleForParent).stream())
                .toList();

        // Now run your must-have validation and wrap in Flux
        return checkReferenceViolatesMustHave(referenceWrapper, allValidGroups, processingBundleForParent);
    }


    /**
     * Collects all valid resourceGroups for the currently processed ResourceBundle.
     * <p> For a given reference and resource checks if already a valid group in processingBundle.
     * If resourceGroups not assigned yet, executes filter, musthave (Without References) and profile checks.
     *
     * @param groupMap                  known attribute groups
     * @param resource                  Resource to be checked
     * @param processingBundleForParent bundle that is currently processed
     * @return ResourceGroup if previously unknown and assignable to the group.
     */
    private List<ResourceGroup> collectValidGroups(ReferenceWrapper referenceWrapper, Map<String, AnnotatedAttributeGroup> groupMap, Resource resource, ResourceBundle processingBundleForParent) {
        return referenceWrapper.refAttribute().linkedGroups().stream()
                .map(linkedGroupID -> {
                    ResourceGroup resourceGroup = new ResourceGroup(ResourceUtils.getRelativeURL(resource), linkedGroupID);
                    Boolean isValid = processingBundleForParent.isValidResourceGroup(resourceGroup);
                    if (isValid == null) {
                        AnnotatedAttributeGroup group = groupMap.get(linkedGroupID);
                        boolean fulfilled = profileMustHaveChecker.fulfilled(resource, group);
                        isValid = fulfilled;
                        processingBundleForParent.addResourceGroupValidity(resourceGroup, isValid);
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
     * @param references                referencesWrappers to be handled
     * @param processingBundleForParent bundle on which the referenceWrappers are processed
     * @return filters unprocessed ReferenceWrappers
     * @throws MustHaveViolatedException when attribute group in resource is invalid.
     */
    private List<ReferenceWrapper> filterUnprocessedReferences(List<ReferenceWrapper> references,
                                                               ResourceBundle processingBundleForParent) throws MustHaveViolatedException {
        List<ReferenceWrapper> uncheckedReferences = new ArrayList<>();

        for (ReferenceWrapper reference : references) {
            ResourceAttribute parentAttribute = reference.toResourceAttributeGroup();
            ResourceGroup parentGroup = reference.toResourceGroup();

            Boolean isValid = processingBundleForParent.resourceAttributeValidity().get(parentAttribute);
            processingBundleForParent.addAttributeToParent(parentAttribute, parentGroup);
            if (Boolean.TRUE.equals(isValid)) {
                // Already valid, skip
            } else {
                if (Boolean.FALSE.equals(isValid)) {
                    if (reference.refAttribute().mustHave()) {
                        processingBundleForParent.addResourceGroupValidity(parentGroup, false);
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
