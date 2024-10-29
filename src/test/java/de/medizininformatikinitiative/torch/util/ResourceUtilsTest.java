package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ResourceUtilsTest {



    @Test
    void testGetPatientId_PatientResource() throws PatientIdNotFoundException {
        Patient patient = new Patient();
        patient.setId("123");

        String patientId = ResourceUtils.getPatientId(patient);
        assertEquals("123", patientId);
    }

    @Test
    void testGetPatientId_ConsentWithPatientReference() throws PatientIdNotFoundException {
        Consent consent = new Consent();
        consent.setPatient(new Reference("Patient/123"));

        String patientId = ResourceUtils.getPatientId(consent);
        assertEquals("123", patientId);
    }

    @Test
    void testGetPatientId_ResourceWithSubjectReference() throws Exception {
        // Mock a custom resource with hasSubject and getSubject methods
        Consent consent = new Consent();
        consent.setPatient(new Reference("Patient/123"));

        String patientId = ResourceUtils.getPatientId(consent);
        assertEquals("123", patientId);
    }

    @Test
    void testGetPatientId_ThrowsExceptionWhenNoPatientId() {
        DomainResource resource = new Consent(); // No patient reference
        assertThrows(PatientIdNotFoundException.class, () -> ResourceUtils.getPatientId(resource));
    }

    @Test
    void testGetPatientReference_ValidReference() throws PatientIdNotFoundException {
        String reference = "Patient/123";
        String patientId = ResourceUtils.getPatientReference(reference);
        assertEquals("123", patientId);
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
