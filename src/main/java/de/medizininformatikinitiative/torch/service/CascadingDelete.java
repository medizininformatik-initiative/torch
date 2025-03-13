package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.management.ResourceAttribute;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;

import java.util.*;

public class CascadingDelete {

    private final AnnotatedAttribute genericAttribute = new AnnotatedAttribute("direct", "direct", "direct", false);

    void handleBundle(ResourceBundle resourceBundle, Map<String, AnnotatedAttributeGroup> groupMap) {
        Set<ResourceGroup> invalidResourceGroups = resourceBundle.getInvalid().keySet();
        // Use a local queue for processing
        Queue<ResourceGroup> processingQueue = new LinkedList<>(invalidResourceGroups);
        // Process the queue
        while (!processingQueue.isEmpty()) {
            ResourceGroup invalidResourceGroup = processingQueue.poll();
            Set<ResourceAttribute> childrenAttributes = resourceBundle.parentToAttributesMap().get(invalidResourceGroup);
            if (!childrenAttributes.isEmpty()) {
                processingQueue.addAll(handleChildren(childrenAttributes, resourceBundle));
            }
            Set<ResourceAttribute> parents = resourceBundle.childToAttributeMap().get(invalidResourceGroup);


        }


    }


    /**
     * given a list of attributes it invalidates the connection from children down.
     *
     * @param resourceAttributes the resourceAttributes to be invalidated
     * @param resourceBundle     coreResourcebundle to be handled
     * @return children to be deleted
     */
    Set<ResourceGroup> handleChildren(Set<ResourceAttribute> resourceAttributes,
                                      ResourceBundle resourceBundle) {
        Set<ResourceGroup> resourceGroups = new LinkedHashSet<>();

        for (ResourceAttribute resourceAttribute : resourceAttributes) {
            Set<ResourceGroup> childrenResourceGroups = resourceBundle.resourceAttributeToChildResourceGroup().get(resourceAttribute);

            for (ResourceGroup group : childrenResourceGroups) {

                if (resourceBundle.removeChildAttributeFromParentRG(group, resourceAttribute)) {
                    resourceGroups.add(group);
                }
            }
            resourceBundle.resourceAttributeToChildResourceGroup().remove(resourceAttribute);
        }

        return resourceGroups;
    }


}
