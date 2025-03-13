package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.management.ResourceAttribute;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;

import java.util.*;

public class CascadingDelete {

    void handleBundle(ResourceBundle resourceBundle, Map<String, AnnotatedAttributeGroup> groupMap) {
        Set<ResourceGroup> invalidResourceGroups = resourceBundle.getInvalid().keySet();
        // Use a local queue for processing
        Queue<ResourceGroup> processingQueue = new LinkedList<>(invalidResourceGroups);
        // Process the queue
        while (!processingQueue.isEmpty()) {
            ResourceGroup invalidResourceGroup = processingQueue.poll();


            processingQueue.addAll(handleChildren(resourceBundle, groupMap, invalidResourceGroup));

            Set<ResourceAttribute> parents = resourceBundle.childToAttributeMap().get(invalidResourceGroup);


        }


    }


    /**
     * given a list of attributes it invalidates the connection from children down.
     *
     * @param resourceBundle coreResourcebundle to be handled
     * @param groupMap       known attributeGroups
     * @param parentRG       parentRG that triggered the deletion process.
     * @return children to be deleted
     */
    Set<ResourceGroup> handleChildren(ResourceBundle resourceBundle, Map<String, AnnotatedAttributeGroup> groupMap, ResourceGroup parentRG) {
        Set<ResourceGroup> resourceGroups = new LinkedHashSet<>();
        Set<ResourceAttribute> resourceAttributes = resourceBundle.parentToAttributesMap().get(parentRG);
        for (ResourceAttribute resourceAttribute : resourceAttributes) {
            if (resourceBundle.removeParentRGFromAttribute(parentRG, resourceAttribute)) {
                Set<ResourceGroup> childrenResourceGroups = resourceBundle.resourceAttributeToChildResourceGroup().get(resourceAttribute);

                for (ResourceGroup group : childrenResourceGroups) {
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

        return resourceGroups;
    }


}
