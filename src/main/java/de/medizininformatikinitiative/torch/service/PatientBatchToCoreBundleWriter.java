package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.model.management.ResourceAttribute;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;

import java.util.Map;
import java.util.Set;

public class PatientBatchToCoreBundleWriter {

    private final CompartmentManager compartmentManager;

    public PatientBatchToCoreBundleWriter(CompartmentManager compartmentManager) {
        this.compartmentManager = compartmentManager;
    }

    /**
     * Updates the coreBundle with resource groups from patientResourceBundle,
     * adding only those that are outside the compartment.
     */
    public void updateCoreBundleWithPatientBundle(ResourceBundle patientResourceBundle, ResourceBundle coreBundle) {
        Set<Map.Entry<ResourceGroup, Boolean>> groups = patientResourceBundle.resourceGroupValidity().entrySet();

        for (Map.Entry<ResourceGroup, Boolean> entry : groups) {
            ResourceGroup group = entry.getKey();
            boolean isValid = entry.getValue();

            // Only consider valid resource groups
            if (!isValid) {
                continue;
            }

            // Check if the resource group is outside the compartment
            if (!compartmentManager.isInCompartment(group)) {
                migrateResourceGroup(group, patientResourceBundle, coreBundle);
            }
        }
    }

    /**
     * Migrates a resource group along with its parent-child relationships.
     */
    private void migrateResourceGroup(ResourceGroup group, ResourceBundle patientResourceBundle, ResourceBundle coreBundle) {
        // Copy group validity
        coreBundle.addResourceGroupValidity(group, true);

        // Copy parent-child relations
        Set<ResourceAttribute> childAttributes = patientResourceBundle.childResourceGroupToResourceAttributesMap().getOrDefault(group, Set.of());
        for (ResourceAttribute attribute : childAttributes) {
            Set<ResourceGroup> childGroups = patientResourceBundle.resourceAttributeToChildResourceGroup().getOrDefault(attribute, Set.of());

            for (ResourceGroup child : childGroups) {
                coreBundle.addAttributeToChild(attribute, child);
            }
        }

        // Copy child-parent relations
        Set<ResourceAttribute> parentAttributes = patientResourceBundle.parentResourceGroupToResourceAttributesMap().getOrDefault(group, Set.of());
        for (ResourceAttribute attribute : parentAttributes) {
            Set<ResourceGroup> parentGroups = patientResourceBundle.resourceAttributeToParentResourceGroup().getOrDefault(attribute, Set.of());

            for (ResourceGroup parent : parentGroups) {
                coreBundle.addAttributeToParent(attribute, parent);
            }
        }
    }
}