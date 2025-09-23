package de.medizininformatikinitiative.torch.model.management;

import de.medizininformatikinitiative.torch.model.consent.NonContinuousPeriod;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PatientResourceBundleTest {

    @Test
    void constructorWithCachelessResourceBundle() {
        CachelessResourceBundle cacheless = new CachelessResourceBundle(new ResourceBundle());
        PatientResourceBundle bundle2 = new PatientResourceBundle("p2", cacheless);

        assertThat(bundle2.patientId()).isEqualTo("p2");
        assertThat(bundle2.bundle()).isNotNull();
    }

    @Test
    void constructorWithConsentPeriods() {
        NonContinuousPeriod period = NonContinuousPeriod.of();
        PatientResourceBundle bundle2 = new PatientResourceBundle("p2", period);

        assertThat(bundle2.patientId()).isEqualTo("p2");
        assertThat(bundle2.consentPeriods()).isEqualTo(period);
        assertThat(bundle2.bundle()).isNotNull();
    }

    @Test
    void putAndGetResource() {
        PatientResourceBundle bundle = new PatientResourceBundle("patient1");
        Patient patient = new Patient();
        patient.setId("pat1");

        bundle.put(patient);

        Optional<?> retrieved = bundle.get("Patient/pat1");
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get()).isEqualTo(patient);
    }

    @Test
    void removeResource() {
        Patient patient = new Patient();
        patient.setId("pat1");
        PatientResourceBundle bundle = new PatientResourceBundle("patient1");
        bundle.put(patient);

        assertThat(bundle.contains("Patient/pat1")).isTrue();

        bundle.remove("Patient/pat1");
        assertThat(bundle.contains("Patient/pat1")).isFalse();
    }

    @Test
    void putResourceWithGroupAndBoolean() {
        PatientResourceBundle bundle = new PatientResourceBundle("patient1");
        Observation obs = new Observation();
        obs.setId("obs1");


        boolean result = bundle.put(obs, "group1", true);
        assertThat(result).isTrue(); // should be true if added successfully
        assertThat(bundle.contains("Observation/obs1")).isTrue();
    }

    @Test
    void putResourceReferenceCreatesEmptyOptional() {
        PatientResourceBundle bundle = new PatientResourceBundle("patient1");
        String ref = "unknownResource";
        bundle.put(ref);

        Optional<?> retrieved = bundle.get(ref);
        assertThat(retrieved).isEmpty();
    }

    @Test
    void isEmptyReflectsUnderlyingBundle() {
        PatientResourceBundle bundle = new PatientResourceBundle("patient1");
        assertThat(bundle.isEmpty()).isTrue();

        Patient patient = new Patient();
        patient.setId("p1");
        bundle.put(patient);

        assertThat(bundle.isEmpty()).isFalse();
    }

    @Test
    void containsReturnsTrueForExistingResource() {
        PatientResourceBundle bundle = new PatientResourceBundle("patient1");
        Patient patient = new Patient();
        patient.setId("p1");
        bundle.put(patient);

        assertThat(bundle.contains("Patient/p1")).isTrue();
    }

}
