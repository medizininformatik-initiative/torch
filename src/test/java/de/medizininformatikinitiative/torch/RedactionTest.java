package de.medizininformatikinitiative.torch;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.model.management.ExtractionRedactionWrapper;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import org.hl7.fhir.r4.model.*;
import org.junit.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static de.medizininformatikinitiative.torch.util.FhirUtil.createAbsentReasonExtension;
import static org.assertj.core.api.Assertions.assertThat;

public class RedactionTest {

    public static final String INPUT_CONDITION_DIR = "src/test/resources/InputResources/Condition/";
    public static final String EXPECTED_OUTPUT_DIR = "src/test/resources/RedactTest/expectedOutput/";
    public static final String OBSERVATION_LAB = "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab";
    public static final String DIAGNOSIS = "https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose";
    public static final String MEDICATION = "https://www.medizininformatik-initiative.de/fhir/core/modul-medikation/StructureDefinition/Medication";
    public static final String PATIENT = "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/PatientPseudonymisiert";
    public static final String VITALSTATUS = "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/Vitalstatus";

    private final IntegrationTestSetup integrationTestSetup = new IntegrationTestSetup();

    private final FhirContext fhirContext = FhirContext.forR4();

    @ParameterizedTest
    @ValueSource(strings = {"Observation_lab_Missing_Elements_Unknown_Slices.json"})
    public void testObservationLab(String resource) throws IOException {
        DomainResource src = integrationTestSetup.readResource("src/test/resources/InputResources/Observation/" + resource);
        DomainResource expected = integrationTestSetup.readResource(EXPECTED_OUTPUT_DIR + resource);

        ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(src, Set.of(OBSERVATION_LAB), Map.of("Observation.subject", Set.of("Patient/VHF-MIXED-TEST-CASE-0001-a"), "Observation.encounter", Set.of("Encounter/VHF-MIXED-TEST-CASE-0001-a-E-1")), Set.of());
        DomainResource tgt = (DomainResource) integrationTestSetup.redaction().redact(wrapper);

        assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Observation-mii-exa-test-data-patient-1-vitalstatus-1.json"
    })
    public void testValueSetBindingPassingThroughAsDiscriminator(String resource) throws IOException {
        DomainResource src = integrationTestSetup.readResource("src/test/resources/InputResources/Observation/" + resource);
        DomainResource expected = integrationTestSetup.readResource(EXPECTED_OUTPUT_DIR + resource);

        ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(src, Set.of(VITALSTATUS), Map.of("Observation.subject", Set.of("Patient/VHF-MIXED-TEST-CASE-0001-a"), "Observation.encounter", Set.of("Encounter/VHF-MIXED-TEST-CASE-0001-a-E-1")), Set.of());
        DomainResource tgt = (DomainResource) integrationTestSetup.redaction().redact(wrapper);

        assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Observation-mii-exa-test-data-patient-1-vitalstatus-1-identifier.json"
    })
    public void testReferenceComplexType(String resource) throws IOException {
        DomainResource src = integrationTestSetup.readResource("src/test/resources/InputResources/Observation/" + resource);
        DomainResource expected = integrationTestSetup.readResource(EXPECTED_OUTPUT_DIR + resource);

        ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(src, Set.of(VITALSTATUS), Map.of("Observation.subject", Set.of("Patient/VHF-MIXED-TEST-CASE-0001-a"), "Observation.encounter", Set.of("Encounter/VHF-MIXED-TEST-CASE-0001-a-E-1")), Set.of());
        DomainResource tgt = (DomainResource) integrationTestSetup.redaction().redact(wrapper);

        assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expected));
    }

    @Test
    public void unknownSlice() throws IOException {
        DomainResource expected = integrationTestSetup.readResource(EXPECTED_OUTPUT_DIR + "unknownSlice.json");
        org.hl7.fhir.r4.model.Condition src = new org.hl7.fhir.r4.model.Condition();
        Meta meta = new Meta();
        meta.setProfile(List.of(new CanonicalType(DIAGNOSIS)));
        src.setMeta(meta);
        Coding code = new Coding("Test", "Test", "Test");
        CodeableConcept concept = new CodeableConcept();
        concept.setCoding(List.of(code));
        src.setCode(concept);

        ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(src, Set.of(DIAGNOSIS), Map.of(), Set.of());
        DomainResource tgt = (DomainResource) integrationTestSetup.redaction().redact(wrapper);

        assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expected));
    }

    @Nested
    class Condition {


        @ParameterizedTest
        @ValueSource(strings = {"Condition-mii-exa-diagnose-condition-minimal.json", "Condition-mii-exa-diagnose-mehrfachkodierung-primaercode.json", "Condition-mii-exa-diagnose-mehrfachkodierung-primaercode.json", "Condition-mii-exa-diagnose-multiple-kodierungen.json", "Condition-mii-exa-test-data-patient-1-diagnose-1.json", "Condition-mii-exa-test-data-patient-1-diagnose-2.json", "Condition-mii-exa-test-data-patient-3-diagnose-1.json", "Condition-mii-exa-test-data-patient-4-diagnose-1.json"})
        public void diagnosisAllValid(String resource) throws IOException {
            DomainResource src = integrationTestSetup.readResource(INPUT_CONDITION_DIR + resource);
            DomainResource expected = integrationTestSetup.readResource(INPUT_CONDITION_DIR + resource);

            ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(src, Set.of(DIAGNOSIS), Map.of("Condition.subject", Set.of("Patient/12345", "Patient/mii-exa-test-data-patient-1", "Patient/mii-exa-test-data-patient-3"), "Condition.encounter", Set.of("Encounter/12345")), Set.of());
            DomainResource tgt = (DomainResource) integrationTestSetup.redaction().redact(wrapper);

            assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expected));
        }

        @ParameterizedTest
        @ValueSource(strings = {"Condition-mii-exa-diagnose-condition-minimal.json", "Condition-mii-exa-diagnose-mehrfachkodierung-primaercode.json"})
        public void invalidReferences(String resource) throws IOException {
            DomainResource src = integrationTestSetup.readResource(INPUT_CONDITION_DIR + resource);
            DomainResource expected = integrationTestSetup.readResource(EXPECTED_OUTPUT_DIR + resource);

            ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(src, Set.of(DIAGNOSIS), Map.of("Condition.subject", Set.of(), "Condition.encounter", Set.of("Encounter/12345")), Set.of());
            DomainResource tgt = (DomainResource) integrationTestSetup.redaction().redact(wrapper);

            assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expected));
        }


        @ParameterizedTest
        @ValueSource(strings = {"DiagnosisWithInvalidSliceCode.json"})
        public void diagnosisInvalidElements(String resource) throws IOException {
            DomainResource src = integrationTestSetup.readResource(INPUT_CONDITION_DIR + resource);
            DomainResource expected = integrationTestSetup.readResource(EXPECTED_OUTPUT_DIR + resource);

            ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(src, Set.of(DIAGNOSIS), Map.of("Condition.subject", Set.of("Patient/12345"), "Condition.encounter", Set.of("Encounter/12345")), Set.of());
            DomainResource tgt = (DomainResource) integrationTestSetup.redaction().redact(wrapper);

            assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expected));
        }


        @ParameterizedTest
        @ValueSource(strings = {"Diagnosis1.json", "Diagnosis2.json"})
        public void diagnosisMissingElements(String resource) throws IOException {
            DomainResource src = integrationTestSetup.readResource(INPUT_CONDITION_DIR + resource);
            DomainResource expected = integrationTestSetup.readResource(EXPECTED_OUTPUT_DIR + resource);

            ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(src, Set.of(DIAGNOSIS), Map.of("Condition.subject", Set.of("Patient/12345", "Patient/123"), "Condition.encounter", Set.of("Encounter/12345")), Set.of());
            DomainResource tgt = (DomainResource) integrationTestSetup.redaction().redact(wrapper);

            assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expected));
        }

        @ParameterizedTest
        @ValueSource(strings = {"DiagnosisUnknownComplexExtension.json"})
        public void removeUnknownComplexExtension(String resource) throws IOException {
            DomainResource src = integrationTestSetup.readResource(INPUT_CONDITION_DIR + resource);
            DomainResource expected = integrationTestSetup.readResource(EXPECTED_OUTPUT_DIR + resource);

            ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(src, Set.of(DIAGNOSIS), Map.of("Condition.subject", Set.of("Patient/12345", "Patient/123"), "Condition.encounter", Set.of("Encounter/12345")), Set.of());
            DomainResource tgt = (DomainResource) integrationTestSetup.redaction().redact(wrapper);

            assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expected));
        }

        /* TODO comment in and fix removing of unknown primitive extensions
        @ParameterizedTest

        @ValueSource(strings = {"DiagnosisUnknownPrimitiveExtension.json"})
        public void removeUnknownPrimitiveExtension(String resource) throws IOException {
            DomainResource src = integrationTestSetup.readResource(INPUT_CONDITION_DIR + resource);
            DomainResource expected = integrationTestSetup.readResource(EXPECTED_OUTPUT_DIR + resource);

            ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(src, Set.of(DIAGNOSIS), Map.of("Condition.subject", Set.of("Patient/12345", "Patient/123"), "Condition.encounter", Set.of("Encounter/12345")), Set.of());
            DomainResource tgt = (DomainResource) integrationTestSetup.redaction().redact(wrapper);

            assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expected));
        }

         */


    }


    @Test
    public void backboneElementHandling() {
        Meta meta = new Meta();
        meta.addProfile(MEDICATION);
        Medication medication = new Medication();
        medication.setId("medication-id");
        medication.setMeta(meta);

        Medication expectedMedication = new Medication();
        expectedMedication.setId("medication-id");
        expectedMedication.setMeta(meta);
        Medication.MedicationIngredientComponent ingredient = new Medication.MedicationIngredientComponent();
        ingredient.addExtension(createAbsentReasonExtension("masked"));
        expectedMedication.setIngredient(List.of(ingredient));


        ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(medication, Set.of(MEDICATION), Map.of(), Set.of());
        DomainResource tgt = (DomainResource) integrationTestSetup.redaction().redact(wrapper);

        assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expectedMedication));
    }

    @Test
    public void patient() {
        Meta meta = new Meta();
        meta.addProfile("anyprofile");
        Patient patient = new Patient();
        patient.setId("patient-id");
        patient.setMeta(meta);

        Patient expectedPatient = new Patient();
        expectedPatient.setId("patient-id");
        Meta metaModified = new Meta();
        metaModified.addProfile(PATIENT);
        expectedPatient.setMeta(metaModified);
        Identifier identifier = new Identifier();
        identifier.addExtension(createAbsentReasonExtension("masked"));
        expectedPatient.setIdentifier(List.of(identifier));

        ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(patient, Set.of(PATIENT), Map.of(), Set.of());
        DomainResource tgt = (DomainResource) integrationTestSetup.redaction().redact(wrapper);

        assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expectedPatient));
    }


}
