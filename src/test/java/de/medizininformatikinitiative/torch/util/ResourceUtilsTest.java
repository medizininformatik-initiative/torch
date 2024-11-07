package de.medizininformatikinitiative.torch.util;

import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResourceUtilsTest {


    @Nested
    class getPatientId {


    }

    @Test
    void testGetPatientId_PatientResource() throws PatientIdNotFoundException {
        Patient patient = new Patient();
        patient.setId("123");

        String patientId = ResourceUtils.patientId(patient);

        assertThat(patientId).isEqualTo("123");
    }

    @Test
    void testGetPatientId_ConsentWithPatientReference() throws PatientIdNotFoundException {
        Consent consent = new Consent();
        consent.setPatient(new Reference("Patient/123"));

        String patientId = ResourceUtils.patientId(consent);
        assertEquals("123", patientId);
    }

    @Test
    void testGetPatientId_ResourceWithSubjectReference() throws Exception {
        // Mock a custom resource with hasSubject and getSubject methods
        Consent consent = new Consent();
        consent.setPatient(new Reference("Patient/123"));

        String patientId = ResourceUtils.patientId(consent);
        assertEquals("123", patientId);
    }

    @Test
    void testGetPatientId_ThrowsExceptionWhenNoPatientId() {
        DomainResource resource = new Consent(); // No patient reference
        assertThrows(PatientIdNotFoundException.class, () -> ResourceUtils.patientId(resource));
    }

    @Test
    void testGetPatientReference_ValidReference() throws PatientIdNotFoundException {
        String reference = "Patient/123";

        String result = ResourceUtils.getPatientReference(reference);

        assertThat(result).isEqualTo("123");
    }

    @Test
    void testGetPatientReference_InvalidReference() {
        String invalidReference = "NotPatient/123";
        assertThrows(PatientIdNotFoundException.class, () -> ResourceUtils.getPatientReference(invalidReference));
    }

    @Test
    void testGetPatientIdFromBundle_WithPatient() throws PatientIdNotFoundException {
        Bundle bundle = new Bundle();
        Patient patient = new Patient();
        patient.setId("123");
        bundle.addEntry().setResource(patient);

        String patientId = ResourceUtils.getPatientIdFromBundle(bundle);
        assertEquals("123", patientId);
    }

    @Test
    void testGetPatientIdFromBundle_NoDomainResource() {
        Bundle bundle = new Bundle(); // Empty bundle
        assertThrows(PatientIdNotFoundException.class, () -> ResourceUtils.getPatientIdFromBundle(bundle));
    }

    @Test
    void testGetPatientIdFromBundle_NullOrEmptyBundle() {
        assertThrows(PatientIdNotFoundException.class, () -> ResourceUtils.getPatientIdFromBundle(null));

        Bundle bundle = new Bundle();
        assertThrows(PatientIdNotFoundException.class, () -> ResourceUtils.getPatientIdFromBundle(bundle));
    }
}
