package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.model.management.ResourceAttribute;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PatientBatchToCoreBundleWriter {

    private final CompartmentManager compartmentManager;

    PatientBatchToCoreBundleWriter(CompartmentManager compartmentManager) {
        this.compartmentManager = compartmentManager;
    }


    public void updateCoreBundleWithPatientBundle(ResourceBundle patientResourceBundle, ResourceBundle coreBundle) {
        Set<Map.Entry<ResourceGroup, Boolean>> groups = patientResourceBundle.resourceGroupValidity().entrySet();
        Set<ResourceAttribute> attributes = new HashSet<ResourceAttribute>();

        for (Map.Entry<ResourceGroup, Boolean> entry : groups) {
            ResourceGroup group = entry.getKey();
            boolean isValid = entry.getValue(); // Assuming this Boolean represents some kind of validity

            // Check if the resource group is in the compartment
            if (!compartmentManager.isInCompartment(group)) {
                coreBundle.resourceGroupValidity().put(group, isValid);

            }
        }

    }


}
