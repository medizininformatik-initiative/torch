package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.management.ResourceAttribute;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import de.medizininformatikinitiative.torch.model.management.cachingResourceBundle;

import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public class CascadingDelete {

    PatientBatchWithConsent handlePatientBatch(PatientBatchWithConsent patientBatch, Map<String, AnnotatedAttributeGroup> groupMap) {
        patientBatch.bundles().values().forEach(bundle -> handleBundle(bundle.bundle(), groupMap));
        return patientBatch;
    }

    void handleBundle(cachingResourceBundle cachingResourceBundle, Map<String, AnnotatedAttributeGroup> groupMap) {
        Set<ResourceGroup> invalidResourceGroups = cachingResourceBundle.getInvalid().keySet();
        Queue<ResourceGroup> processingQueue = new LinkedList<>(invalidResourceGroups);
        while (!processingQueue.isEmpty()) {
            ResourceGroup invalidResourceGroup = processingQueue.poll();
            processingQueue.addAll(handleChildren(cachingResourceBundle, groupMap, invalidResourceGroup));
            processingQueue.addAll(handleParents(cachingResourceBundle, invalidResourceGroup));
        }

    }

    /**
     * given a parent ResourceGroup it invalidates the connection from children down.
     *
     * @param cachingResourceBundle coreResourcebundle to be handled
     * @param groupMap              known attributeGroups
     * @param parentRG              parentRG that triggered the deletion process.
     * @return children to be deleted
     */
    Set<ResourceGroup> handleChildren(cachingResourceBundle cachingResourceBundle, Map<String, AnnotatedAttributeGroup> groupMap, ResourceGroup parentRG) {
        Set<ResourceGroup> resourceGroups = new LinkedHashSet<>();
        Set<ResourceAttribute> resourceAttributes = cachingResourceBundle.parentResourceGroupToResourceAttributesMap().get(parentRG);

        if (resourceAttributes == null) {
            return Set.of();
        }

        for (ResourceAttribute resourceAttribute : resourceAttributes) {
            if (cachingResourceBundle.resourceAttributeValid(resourceAttribute) && cachingResourceBundle.removeParentRGFromAttribute(parentRG, resourceAttribute)) {
                cachingResourceBundle.removeAttributefromParentRG(parentRG, resourceAttribute);
                cachingResourceBundle.setResourceAttributeInValid(resourceAttribute);
                Set<ResourceGroup> childrenResourceGroups = cachingResourceBundle.resourceAttributeToChildResourceGroup().get(resourceAttribute);

                for (ResourceGroup group : childrenResourceGroups) {
                    cachingResourceBundle.removeChildRGFromAttribute(group, resourceAttribute);
                    if (cachingResourceBundle.removeParentAttributeFromChildRG(group, resourceAttribute)) {

                        AnnotatedAttributeGroup attributeGroup = groupMap.get(group.groupId());
                        if (attributeGroup.includeReferenceOnly()) {
                            resourceGroups.add(group);
                            cachingResourceBundle.addResourceGroupValidity(group, false);
                        }
                    }
                }
                cachingResourceBundle.resourceAttributeToChildResourceGroup().remove(resourceAttribute);
            }

        }

        return resourceGroups;
    }

    /**
     * @param cachingResourceBundle resourceBundle to be handled
     * @param childRG               rg whose parents have to be handled
     * @return removes the connection of a invalid ResourceGroup to its parents propagates up if must have attribute.
     */
    Set<ResourceGroup> handleParents(cachingResourceBundle cachingResourceBundle, ResourceGroup childRG) {
        Set<ResourceGroup> resourceGroups = new LinkedHashSet<>();
        Set<ResourceAttribute> resourceAttributes = cachingResourceBundle.childResourceGroupToResourceAttributesMap().getOrDefault(childRG, Set.of());

        for (ResourceAttribute resourceAttribute : resourceAttributes) {
            boolean wasLastChild = cachingResourceBundle.removeParentAttributeFromChildRG(childRG, resourceAttribute);
            cachingResourceBundle.removeChildRGFromAttribute(childRG, resourceAttribute);

            if (wasLastChild) {
                cachingResourceBundle.setResourceAttributeInValid(resourceAttribute);

                // If it's a must-have attribute, escalate to all parent groups of the attribute
                if (resourceAttribute.annotatedAttribute().mustHave()) {
                    Set<ResourceGroup> parentGroups = cachingResourceBundle.resourceAttributeToParentResourceGroup()
                            .getOrDefault(resourceAttribute, Set.of());

                    // Add all parent groups to be invalidated
                    resourceGroups.addAll(parentGroups);

                    // Ensure invalidation is applied recursively
                    for (ResourceGroup parentGroup : parentGroups) {
                        cachingResourceBundle.removeAttributefromParentRG(parentGroup, resourceAttribute);
                        cachingResourceBundle.addResourceGroupValidity(parentGroup, false);
                    }
                }
            }
        }

        return resourceGroups;
    }
}
