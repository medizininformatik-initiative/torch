package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.management.StructureDefinitionHandler;
import de.medizininformatikinitiative.torch.model.management.CopyTreeNode;
import de.medizininformatikinitiative.torch.model.management.ExtractionRedactionWrapper;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import org.hl7.fhir.r4.model.*;
import org.junit.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static de.medizininformatikinitiative.torch.util.FhirUtil.createAbsentReasonExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RedactionTest {

    public static final String INPUT_CONDITION_DIR = "src/test/resources/InputResources/Condition/";
    public static final String INPUT_OBSERVATION_DIR = "src/test/resources/InputResources/Observation/";
    public static final String EXPECTED_OUTPUT_DIR = "src/test/resources/RedactTest/expectedOutput/";
    public static final String OBSERVATION_LAB = "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab";
    public static final String TODESURSACHE = "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/Todesursache";
    public static final String DIAGNOSIS = "https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose";
    public static final String MEDICATION = "https://www.medizininformatik-initiative.de/fhir/core/modul-medikation/StructureDefinition/Medication";
    public static final String PATIENT = "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/PatientPseudonymisiert";
    public static final String VITALSTATUS = "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/Vitalstatus";

    private final IntegrationTestSetup integrationTestSetup;

    private final FhirContext fhirContext = FhirContext.forR4();

    public RedactionTest() throws IOException {
        integrationTestSetup = new IntegrationTestSetup();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Observation_lab_Missing_Elements_Unknown_Slices.json"})
    void testObservationLab(String resource) throws IOException {
        DomainResource src = integrationTestSetup.readResource(INPUT_OBSERVATION_DIR + resource);
        DomainResource expected = integrationTestSetup.readResource(EXPECTED_OUTPUT_DIR + resource);

        ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(src, Set.of(OBSERVATION_LAB), Map.of("Observation.subject", Set.of("Patient/VHF-MIXED-TEST-CASE-0001-a"), "Observation.encounter", Set.of("Encounter/VHF-MIXED-TEST-CASE-0001-a-E-1")), new CopyTreeNode("Observation"));
        DomainResource tgt = integrationTestSetup.redaction().redact(wrapper);

        assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expected));
    }

    @Test
    public void testValueSetBindingPassingThroughAsDiscriminator() throws IOException {
        DomainResource src = integrationTestSetup.readResource(INPUT_OBSERVATION_DIR + "Observation-mii-exa-test-data-patient-1-vitalstatus-1.json");
        DomainResource expected = integrationTestSetup.readResource(EXPECTED_OUTPUT_DIR + "Observation-mii-exa-test-data-patient-1-vitalstatus-1.json");

        ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(src, Set.of(VITALSTATUS), Map.of("Observation.subject", Set.of("Patient/VHF-MIXED-TEST-CASE-0001-a"), "Observation.encounter", Set.of("Encounter/VHF-MIXED-TEST-CASE-0001-a-E-1")), new CopyTreeNode("Encounter"));
        DomainResource tgt = integrationTestSetup.redaction().redact(wrapper);

        assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expected));
    }

    @Test
    public void referenceComplexType() throws IOException {
        DomainResource src = integrationTestSetup.readResource(INPUT_OBSERVATION_DIR + "Observation-mii-exa-test-data-patient-1-vitalstatus-1-identifier.json");
        DomainResource expected = integrationTestSetup.readResource(EXPECTED_OUTPUT_DIR + "Observation-mii-exa-test-data-patient-1-vitalstatus-1-identifier.json");

        ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(src, Set.of(VITALSTATUS), Map.of("Observation.subject", Set.of("Patient/VHF-MIXED-TEST-CASE-0001-a"), "Observation.encounter", Set.of("Encounter/VHF-MIXED-TEST-CASE-0001-a-E-1")), new CopyTreeNode("Encounter"));
        DomainResource tgt = integrationTestSetup.redaction().redact(wrapper);

        assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expected));
    }

    /**
     * Test with "DiagnosisWithUndefinedElement.json" for the fields
     * Condition.clinicalstatus.coding  and Condition.note
     * not covered by Structuredefintion:
     * "https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose"
     *
     * @throws IOException e.g. when file not found
     */
    @Test
    public void fallbackForUndefinedElement() throws IOException {
        DomainResource src = integrationTestSetup.readResource(INPUT_CONDITION_DIR + "DiagnosisWithUndefinedElement.json");
        DomainResource expected = integrationTestSetup.readResource(EXPECTED_OUTPUT_DIR + "DiagnosisWithUndefinedElement.json");

        ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(src, Set.of(DIAGNOSIS), Map.of("Condition.subject", Set.of("Patient/12345", "Patient/123"), "Condition.encounter", Set.of("Encounter/12345")), new CopyTreeNode("Encounter"));
        DomainResource tgt = integrationTestSetup.redaction().redact(wrapper);

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

        ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(src, Set.of(DIAGNOSIS), Map.of(), new CopyTreeNode("Condition"));
        DomainResource tgt = integrationTestSetup.redaction().redact(wrapper);

        assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DiagnosisUnknownPrimitiveExtension.json", "DiagnosisWithExtensionAtCode.json", "DiagnosisUnknownComplexExtension.json", "DiagnosisWithExtensionAtExtensionsValue.json"})
    void removeUnknownPrimitiveAndComplexExtension(String resource) throws IOException {
        DomainResource src = integrationTestSetup.readResource(INPUT_CONDITION_DIR + resource);
        DomainResource expected = integrationTestSetup.readResource(EXPECTED_OUTPUT_DIR + resource);

        ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(src, Set.of(DIAGNOSIS), Map.of("Condition.subject", Set.of("Patient/12345", "Patient/123"), "Condition.encounter", Set.of("Encounter/12345")), new CopyTreeNode("Encounter"));
        DomainResource tgt = integrationTestSetup.redaction().redact(wrapper);

        assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expected));
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


        ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(medication, Set.of(MEDICATION), Map.of(), new CopyTreeNode("Medication"));
        DomainResource tgt = integrationTestSetup.redaction().redact(wrapper);

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

        ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(patient, Set.of(PATIENT), Map.of(), new CopyTreeNode("Patient"));
        DomainResource tgt = integrationTestSetup.redaction().redact(wrapper);

        assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expectedPatient));
    }

    /**
     * Tests for multi profile with Custom Profiles allowing in 2 profiles
     * a slicing on Observation.component with coding pattern with different required fields (value and interpretation)
     * with data absent reasons being set and
     * a profile without any slicing that enforces that Observation.component.dataAbsentReason is required.
     *
     * @throws IOException when test file not found
     */
    @Test
    public void testMultiProfilesWithCustomStructureDefinition() throws IOException {
        DomainResource src = integrationTestSetup.readResource(INPUT_OBSERVATION_DIR + "MultiProfileObservationWithPatternSlicing.json");
        DomainResource expected = integrationTestSetup.readResource(EXPECTED_OUTPUT_DIR + "MultiProfileObservationWithPatternSlicing.json");
        StructureDefinitionHandler definitionHandler = new StructureDefinitionHandler(new File("src/test/resources/StructureDefinitions/"), new ResourceReader(integrationTestSetup.fhirContext()));
        definitionHandler.processDirectory();
        Redaction redaction = new Redaction(definitionHandler);
        ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(src, Set.of("http://example.org/fhir/StructureDefinition/observation-with-pattern-slicing", "http://example.org/fhir/StructureDefinition/observation-with-pattern-slicing-modified", "http://example.org/fhir/StructureDefinition/observation-without-slicing"), Map.of(), new CopyTreeNode("Observation"));
        DomainResource tgt = redaction.redact(wrapper);

        assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expected));

    }

    @Nested
    class Condition {

        @ParameterizedTest
        @ValueSource(strings = {"Condition-mii-exa-diagnose-condition-minimal.json", "Condition-mii-exa-diagnose-mehrfachkodierung-primaercode.json", "Condition-mii-exa-diagnose-mehrfachkodierung-primaercode.json", "Condition-mii-exa-diagnose-multiple-kodierungen.json", "Condition-mii-exa-test-data-patient-1-diagnose-1.json", "Condition-mii-exa-test-data-patient-1-diagnose-2.json", "Condition-mii-exa-test-data-patient-3-diagnose-1.json", "Condition-mii-exa-test-data-patient-4-diagnose-1.json"})
        void diagnosisAllValid(String resource) throws IOException {
            DomainResource src = integrationTestSetup.readResource(INPUT_CONDITION_DIR + resource);
            DomainResource expected = integrationTestSetup.readResource(INPUT_CONDITION_DIR + resource);

            ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(src, Set.of(DIAGNOSIS), Map.of("Condition.subject", Set.of("Patient/12345", "Patient/mii-exa-test-data-patient-1", "Patient/mii-exa-test-data-patient-3"), "Condition.encounter", Set.of("Encounter/12345")), new CopyTreeNode("Condition"));
            DomainResource tgt = integrationTestSetup.redaction().redact(wrapper);

            assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expected));
        }

        @ParameterizedTest
        @ValueSource(strings = {"Condition-mii-exa-diagnose-condition-minimal.json", "Condition-mii-exa-diagnose-mehrfachkodierung-primaercode.json"})
        void invalidReferences(String resource) throws IOException {
            DomainResource src = integrationTestSetup.readResource(INPUT_CONDITION_DIR + resource);
            DomainResource expected = integrationTestSetup.readResource(EXPECTED_OUTPUT_DIR + resource);

            ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(src, Set.of(DIAGNOSIS), Map.of("Condition.subject", Set.of(), "Condition.encounter", Set.of("Encounter/12345")), new CopyTreeNode("Condition"));
            DomainResource tgt = integrationTestSetup.redaction().redact(wrapper);

            assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expected));
        }

        @ParameterizedTest
        @ValueSource(strings = {"DiagnosisWithInvalidSliceCode.json"})
        void diagnosisInvalidElements(String resource) throws IOException {
            DomainResource src = integrationTestSetup.readResource(INPUT_CONDITION_DIR + resource);
            DomainResource expected = integrationTestSetup.readResource(EXPECTED_OUTPUT_DIR + resource);

            ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(src, Set.of(DIAGNOSIS), Map.of("Condition.subject", Set.of("Patient/12345"), "Condition.encounter", Set.of("Encounter/12345")), new CopyTreeNode("Condition"));
            DomainResource tgt = integrationTestSetup.redaction().redact(wrapper);

            assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expected));
        }

        @ParameterizedTest
        @ValueSource(strings = {"Diagnosis1.json", "Diagnosis2.json"})
        void diagnosisMissingElements(String resource) throws IOException {
            DomainResource src = integrationTestSetup.readResource(INPUT_CONDITION_DIR + resource);
            DomainResource expected = integrationTestSetup.readResource(EXPECTED_OUTPUT_DIR + resource);

            ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(src, Set.of(DIAGNOSIS), Map.of("Condition.subject", Set.of("Patient/12345", "Patient/123"), "Condition.encounter", Set.of("Encounter/12345")), new CopyTreeNode("Condition"));
            DomainResource tgt = integrationTestSetup.redaction().redact(wrapper);

            assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expected));
        }


        /**
         * Tests for multi profile with CDS Todesursache and Diagnosis Profiles
         *
         * @param resource Todesursache1.json does only contain the who icd10 (who) which is not currently allowed in diagnosis profile
         *                 Todesursache2.json does contain 2 legal codings icd10 and sct from two different profiles
         *                 Todesursache3.json does not contain the required icd 10 (who)
         * @throws IOException when test file not found
         */
        @ParameterizedTest
        @ValueSource(strings = {"Todesursache1.json", "Todesursache2.json", "Todesursache3.json"})
        void testMultipleProfiles(String resource) throws IOException {
            DomainResource src = integrationTestSetup.readResource(INPUT_CONDITION_DIR + resource);
            DomainResource expected = integrationTestSetup.readResource(EXPECTED_OUTPUT_DIR + resource);

            ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(src, Set.of(DIAGNOSIS, TODESURSACHE), Map.of("Condition.subject", Set.of("Patient/12345", "Patient/123"), "Condition.encounter", Set.of("Encounter/12345")), new CopyTreeNode("Condition"));
            DomainResource tgt = integrationTestSetup.redaction().redact(wrapper);

            assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expected));
        }

        @ParameterizedTest
        @ValueSource(strings = {"Diagnosis1.json"})
        void failsWhenRequiredProfileMissing(String resource) throws IOException {
            DomainResource src = integrationTestSetup.readResource(INPUT_CONDITION_DIR + resource);
            ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(src, Set.of(DIAGNOSIS, TODESURSACHE), Map.of("Condition.subject", Set.of("Patient/12345", "Patient/123"), "Condition.encounter", Set.of("Encounter/12345")), new CopyTreeNode("Condition"));
            var redaction = integrationTestSetup.redaction();

            assertThatThrownBy(() -> redaction.redact(wrapper)).isInstanceOf(RuntimeException.class);
        }
    }
}
