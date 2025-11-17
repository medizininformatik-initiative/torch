package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.management.ResourceAttribute;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

@Component
public class CascadingDelete {

    PatientBatchWithConsent handlePatientBatch(PatientBatchWithConsent patientBatch, Map<String, AnnotatedAttributeGroup> groupMap) {
        patientBatch.bundles().values().parallelStream().forEach(bundle -> handleBundle(bundle.bundle(), groupMap));
        return patientBatch;
    }

    void handleBundle(ResourceBundle resourceBundle, Map<String, AnnotatedAttributeGroup> groupMap) {
        Set<ResourceGroup> invalidResourceGroups = resourceBundle.getInvalid().keySet();
        Queue<ResourceGroup> processingQueue = new LinkedList<>(invalidResourceGroups);
        while (!processingQueue.isEmpty()) {
            ResourceGroup invalidResourceGroup = processingQueue.poll();
            processingQueue.addAll(handleChildren(resourceBundle, groupMap, invalidResourceGroup));
            processingQueue.addAll(handleParents(resourceBundle, invalidResourceGroup));
        }

    }

    /**
     * given a parent ResourceGroup it invalidates the connection from children down.
     *
     * @param resourceBundle coreResourcebundle to be handled
     * @param groupMap       known attributeGroups
     * @param parentRG       parentRG that triggered the deletion process.
     * @return children to be deleted
     */
    Set<ResourceGroup> handleChildren(ResourceBundle resourceBundle, Map<String, AnnotatedAttributeGroup> groupMap, ResourceGroup parentRG) {
        Set<ResourceGroup> resourceGroups = new LinkedHashSet<>();
        Set<ResourceAttribute> resourceAttributes = resourceBundle.parentResourceGroupToResourceAttributesMap().get(parentRG);

        if (resourceAttributes == null) {
            return Set.of();
        }

        for (ResourceAttribute resourceAttribute : resourceAttributes) {
            if (resourceBundle.resourceAttributeValid(resourceAttribute)) {
                if (resourceBundle.removeParentRGFromAttribute(parentRG, resourceAttribute)) {
                    resourceBundle.removeAttributefromParentRG(parentRG, resourceAttribute);
                    resourceBundle.setResourceAttributeInValid(resourceAttribute);
                    Set<ResourceGroup> childrenResourceGroups = resourceBundle.resourceAttributeToChildResourceGroup().get(resourceAttribute);

                    for (ResourceGroup group : childrenResourceGroups) {
                        resourceBundle.removeChildRGFromAttribute(group, resourceAttribute);
                        if (resourceBundle.removeParentAttributeFromChildRG(group, resourceAttribute)) {

                            AnnotatedAttributeGroup attributeGroup = groupMap.get(group.groupId());
                            if (attributeGroup.includeReferenceOnly()) {
                                resourceGroups.add(group);
                                resourceBundle.addResourceGroupValidity(group, false);
                            }
                        }
                    }
                    resourceBundle.resourceAttributeToChildResourceGroup().remove(resourceAttribute);
                }
            }
        }

        return resourceGroups;
    }

    /**
     * @param resourceBundle resourceBundle to be handled
     * @param childRG        rg whose parents have to be handled
     * @return removes the connection of a invalid ResourceGroup to its parents propagates up if must have attribute.
     */
    Set<ResourceGroup> handleParents(ResourceBundle resourceBundle, ResourceGroup childRG) {
        Set<ResourceGroup> resourceGroups = new LinkedHashSet<>();
        Set<ResourceAttribute> resourceAttributes = resourceBundle.childResourceGroupToResourceAttributesMap().getOrDefault(childRG, Set.of());

        for (ResourceAttribute resourceAttribute : resourceAttributes) {
            boolean wasLastChild = resourceBundle.removeParentAttributeFromChildRG(childRG, resourceAttribute);
            resourceBundle.removeChildRGFromAttribute(childRG, resourceAttribute);

            if (wasLastChild) {
                resourceBundle.setResourceAttributeInValid(resourceAttribute);

                // If it's a must-have attribute, escalate to all parent groups of the attribute
                if (resourceAttribute.annotatedAttribute().mustHave()) {
                    Set<ResourceGroup> parentGroups = resourceBundle.resourceAttributeToParentResourceGroup()
                            .getOrDefault(resourceAttribute, Set.of());

                    // Add all parent groups to be invalidated
                    resourceGroups.addAll(parentGroups);

                    // Ensure invalidation is applied recursively
                    for (ResourceGroup parentGroup : parentGroups) {
                        resourceBundle.removeAttributefromParentRG(parentGroup, resourceAttribute);
                        resourceBundle.addResourceGroupValidity(parentGroup, false);
                    }
                }
            }
        }

        return resourceGroups;
    }
}
