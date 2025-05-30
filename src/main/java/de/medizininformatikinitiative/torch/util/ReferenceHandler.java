package de.medizininformatikinitiative.torch.util;

import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ReferenceWrapper;
import de.medizininformatikinitiative.torch.model.management.ResourceAttribute;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class ReferenceHandler {
    private static final Logger logger = LoggerFactory.getLogger(ReferenceHandler.class);
    private final ResourceGroupValidator resourceGroupValidator;


    public ReferenceHandler(ResourceGroupValidator resourceGroupValidator) {
        this.resourceGroupValidator = resourceGroupValidator;

    }

    private static List<ResourceGroup> checkReferenceViolatesMustHave(ReferenceWrapper referenceWrapper, List<ResourceGroup> list, ResourceBundle processingBundle) throws MustHaveViolatedException {
        ResourceAttribute referenceAttribute = referenceWrapper.toResourceAttributeGroup();
        if (referenceWrapper.refAttribute().mustHave() && list.isEmpty()) {
            processingBundle.setResourceAttributeInValid(referenceAttribute);
            throw new MustHaveViolatedException(
                    "MustHave condition violated: No valid references were resolved for " + referenceWrapper.references()
            );
        }
        if (referenceWrapper.references().isEmpty()) {
            return List.of();
        }
        processingBundle.setResourceAttributeValid(referenceAttribute);
        return list;
    }

    /**
     * @param references    References extracted from a single resource for a single resourceGroup to be handled
     * @param patientBundle ResourceBundle containing patient information (Optional for core bundle)
     * @param coreBundle    coreResourceBundle containing the core Resources
     * @param groupMap      cache containing all known attributeGroups
     * @return newly added ResourceGroups to be processed
     */
    public List<ResourceGroup> handleReferences(List<ReferenceWrapper> references,
                                                @Nullable PatientResourceBundle patientBundle,
                                                ResourceBundle coreBundle,
                                                Map<String, AnnotatedAttributeGroup> groupMap) {
        ResourceBundle processingBundle = (patientBundle != null) ? patientBundle.bundle() : coreBundle;
        ResourceGroup parentGroup = new ResourceGroup(references.getFirst().resourceId(), references.getFirst().groupId());

        try {
            List<ReferenceWrapper> unprocessedReferences = filterUnprocessedReferences(references, processingBundle);
            Set<ResourceGroup> knownGroups = processingBundle.getKnownResourceGroups();
            return unprocessedReferences.stream()
                    // map each reference to a list of ResourceGroups
                    .map(ref -> {
                        List<ResourceGroup> resourceGroupList;
                        try {
                            resourceGroupList = handleReference(ref, patientBundle, coreBundle, groupMap);
                        } catch (MustHaveViolatedException e) {
                            processingBundle.addResourceGroupValidity(parentGroup, false);
                            return Collections.<ResourceGroup>emptyList();
                        }
                        ResourceAttribute referenceAttribute = ref.toResourceAttributeGroup();
                        // side effect: add attribute to each resource group
                        resourceGroupList.forEach(rg -> processingBundle.addAttributeToChild(referenceAttribute, rg));
                        return resourceGroupList;
                    })
                    .flatMap(List::stream)
                    .filter(group -> !knownGroups.contains(group))
                    .toList();
        } catch (MustHaveViolatedException e) {
            processingBundle.addResourceGroupValidity(parentGroup, false);
            logger.warn("MustHaveViolatedException occurred. Stopping resource processing: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Handles a ReferenceWrapper by resolving its references and updating the processing bundle.
     *
     * <p>
     * Checks if referenceAttribute
     *
     * @param referenceWrapper The reference wrapper to handle.
     * @param patientBundle    The patient bundle being updated, if present.
     * @param coreBundle       to be updated and queried, that contains a centrally shared concurrent HashMap.
     * @param groupMap         Map of attribute groups for validation.
     * @return A Flux emitting a list of ResourceGroups corresponding to the resolved references.
     */
    public List<ResourceGroup> handleReference(ReferenceWrapper referenceWrapper,
                                               @Nullable PatientResourceBundle patientBundle,
                                               ResourceBundle coreBundle,
                                               Map<String, AnnotatedAttributeGroup> groupMap) throws MustHaveViolatedException {

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
                .flatMap(resource -> resourceGroupValidator.collectValidGroups(referenceWrapper, groupMap, resource.get(), processingBundle).stream())
                .toList();

        // Now run your must-have validation and wrap in Flux
        return checkReferenceViolatesMustHave(referenceWrapper, allValidGroups, processingBundle);
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
            if (isValid == null) {
                uncheckedReferences.add(reference);
            } else {
                if (!isValid && reference.refAttribute().mustHave()) {
                    processingBundle.addResourceGroupValidity(parentGroup, false);
                    throw new MustHaveViolatedException(
                            "Must-have attribute violated for reference: " + reference + " in group: " + parentGroup);
                }

            }
        }
        return uncheckedReferences;
    }


}
