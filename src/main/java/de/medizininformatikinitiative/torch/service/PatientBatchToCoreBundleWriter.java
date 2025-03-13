package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;

public class PatientBatchToCoreBundleWriter {

    private final CompartmentManager compartmentManager;

    PatientBatchToCoreBundleWriter(CompartmentManager compartmentManager) {
        this.compartmentManager = compartmentManager;
    }


    public void updateCoreBundleWithPatientBundle(ResourceBundle patientResourceBundle, ResourceBundle coreBundle) {


    }


}
