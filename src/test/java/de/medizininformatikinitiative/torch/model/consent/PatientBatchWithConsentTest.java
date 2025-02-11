package de.medizininformatikinitiative.torch.model.consent;

import de.medizininformatikinitiative.torch.management.PatientResourceBundle;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

class PatientBatchWithConsentTest {


    @Test
    public void keepTest() {
        PatientResourceBundle bundle1 = new PatientResourceBundle("1", Provisions.of());
        PatientResourceBundle bundle2 = new PatientResourceBundle("2", Provisions.of());


        PatientBatchWithConsent batch = new PatientBatchWithConsent(true, new HashMap<>());

    }

}