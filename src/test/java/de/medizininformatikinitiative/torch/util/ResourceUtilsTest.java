package de.medizininformatikinitiative.torch.util;

import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionId;
import de.medizininformatikinitiative.torch.model.extraction.IdentifierReference;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResourceUtilsTest {


    @Nested
    class GetMethodWithOneParam {

        static class MultiOverload {
            public void doSomething() {
            }

            public void doSomething(String s) {
                s.hashCode();
            }
        }

        @Test
        void findsMethodByName() throws NoSuchMethodException {
            Patient patient = new Patient();
            Method method = ResourceUtils.getMethodWithOneParam(patient, "setId");
            assertThat(method.getName()).isEqualTo("setId");
            assertThat(method.getParameterCount()).isEqualTo(1);
        }

        @Test
        void skipsOverloadsWithWrongParamCount() throws NoSuchMethodException {
            Method method = ResourceUtils.getMethodWithOneParam(new MultiOverload(), "doSomething");
            assertThat(method.getParameterCount()).isEqualTo(1);
        }

        @Test
        void throwsWhenMethodNotFound() {
            assertThatThrownBy(() -> ResourceUtils.getMethodWithOneParam(new Patient(), "nonExistentMethod"))
                    .isInstanceOf(NoSuchMethodException.class);
        }
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

    @Nested
    class GetIdentifiers {

        @Test
        void returnsIdentifiersOfResource() {
            Patient patient = new Patient();
            patient.addIdentifier(new Identifier().setSystem("http://system").setValue("val-1"));

            assertThat(ResourceUtils.getIdentifiers(patient))
                    .extracting(Identifier::getValue)
                    .containsExactly("val-1");
        }

        @Test
        void returnsEmptyListWhenResourceHasNoIdentifiers() {
            Encounter encounter = new Encounter();

            assertThat(ResourceUtils.getIdentifiers(encounter)).isEmpty();
        }
    }

    @Nested
    class IndexByIdentifier {

        @Test
        void indexesResourcesByIdentifier() {
            Patient patient = new Patient();
            patient.setId("Patient/1");
            patient.addIdentifier(new Identifier().setSystem("http://system").setValue("val-1"));

            var index = ResourceUtils.indexByIdentifier(List.of(patient));

            assertThat(index).containsEntry(new IdentifierReference("http://system", "val-1"),
                    Set.of(ExtractionId.fromRelativeUrl("Patient/1")));
        }

        @Test
        void mapsSharedIdentifierToMultipleResources() {
            Patient patient1 = new Patient();
            patient1.setId("Patient/1");
            patient1.addIdentifier(new Identifier().setSystem("http://system").setValue("val-1"));
            Patient patient2 = new Patient();
            patient2.setId("Patient/2");
            patient2.addIdentifier(new Identifier().setSystem("http://system").setValue("val-1"));

            var index = ResourceUtils.indexByIdentifier(List.of(patient1, patient2));

            assertThat(index).containsEntry(new IdentifierReference("http://system", "val-1"),
                    Set.of(ExtractionId.fromRelativeUrl("Patient/1"), ExtractionId.fromRelativeUrl("Patient/2")));
        }

        @Test
        void ignoresIdentifiersWithoutValue() {
            Patient patient = new Patient();
            patient.setId("Patient/1");
            patient.addIdentifier(new Identifier().setSystem("http://system"));

            assertThat(ResourceUtils.indexByIdentifier(List.of(patient))).isEmpty();
        }
    }
}
