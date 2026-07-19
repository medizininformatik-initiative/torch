package de.medizininformatikinitiative.torch.consent;

import org.hl7.fhir.r4.model.Consent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PatientResourceTest {

    @Test
    void pairsPatientIdWithResource() {
        Consent consent = new Consent();
        consent.setId("Consent/123");

        PatientResource<Consent> patientResource = new PatientResource<>("patient1", consent);

        assertThat(patientResource.patientId()).isEqualTo("patient1");
        assertThat(patientResource.resource()).isSameAs(consent);
    }
}
