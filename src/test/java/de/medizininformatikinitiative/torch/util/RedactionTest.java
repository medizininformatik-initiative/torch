package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.medizininformatikinitiative.torch.diagnostics.redaction.RedactionCause;
import de.medizininformatikinitiative.torch.diagnostics.redaction.RedactionIssues;
import de.medizininformatikinitiative.torch.diagnostics.redaction.RedactionViolation;
import de.medizininformatikinitiative.torch.diagnostics.redaction.RedactionViolationReason;
import de.medizininformatikinitiative.torch.exceptions.RedactionException;
import de.medizininformatikinitiative.torch.management.StructureDefinitionHandler;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionId;
import de.medizininformatikinitiative.torch.model.management.CopyTreeNode;
import de.medizininformatikinitiative.torch.model.management.ExtractionRedactionWrapper;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Duration;
import org.hl7.fhir.r4.model.Element;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.EvidenceVariable;
import org.hl7.fhir.r4.model.Expression;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Narrative;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.Questionnaire;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.SampledData;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.UsageContext;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static de.medizininformatikinitiative.torch.util.FhirUtil.createAbsentReasonExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

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
    public static final String ENCOUNTER = "https://www.medizininformatik-initiative.de/fhir/core/modul-fall/StructureDefinition/KontaktGesundheitseinrichtung";
    private static final String DATA_ABSENT_REASON_URL = "http://hl7.org/fhir/StructureDefinition/data-absent-reason";
    public static final String BLOOD_PRESSURE = "https://gematik.de/fhir/isik/StructureDefinition/ISiKBlutdruckSystemischArteriell";

    private final IntegrationTestSetup integrationTestSetup;

    private final FhirContext fhirContext = FhirContext.forR4();

    public RedactionTest() throws IOException {
        integrationTestSetup = new IntegrationTestSetup();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Observation_lab_Missing_Elements_Unknown_Slices.json"})
    void testObservationLab(String resource) throws IOException, RedactionException {
        DomainResource src = integrationTestSetup.readResource(INPUT_OBSERVATION_DIR + resource);
        DomainResource expected = integrationTestSetup.readResource(EXPECTED_OUTPUT_DIR + resource);

        ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(src, Set.of(OBSERVATION_LAB), Map.of("Observation.subject", ExtractionId.of("Patient/VHF-MIXED-TEST-CASE-0001-a"), "Observation.encounter", ExtractionId.of("Encounter/VHF-MIXED-TEST-CASE-0001-a-E-1")), new CopyTreeNode("dummy"));
        DomainResource tgt = integrationTestSetup.redaction().redact(wrapper);

        assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expected));
    }

    /**
     * Reproduces #1168: a component's {@code code.coding} array matches a slice's pattern only if the
     * matching coding is somewhere in the array, not just at the first position.
     */
    @Test
    void bloodPressureComponentMatchesSliceRegardlessOfCodingOrder() throws RedactionException {
        Observation observation = new Observation();
        observation.setId("bp1");
        Meta meta = new Meta();
        meta.addProfile(BLOOD_PRESSURE);
        observation.setMeta(meta);

        Observation.ObservationComponentComponent systolic = observation.addComponent();
        CodeableConcept systolicCode = new CodeableConcept();
        systolicCode.addCoding(new Coding("http://snomed.info/sct", "271649006", "Systolic blood pressure (observable entity)"));
        systolicCode.addCoding(new Coding("http://loinc.org", "8480-6", "Systolic blood pressure"));
        systolicCode.addCoding(new Coding("urn:iso:std:iso:11073:10101", "150017", "Systolic blood pressure"));
        systolic.setCode(systolicCode);
        systolic.setValue(new Quantity().setValue(128).setUnit("millimeter Mercury column").setSystem("http://unitsofmeasure.org").setCode("mm[Hg]"));

        Observation.ObservationComponentComponent diastolic = observation.addComponent();
        CodeableConcept diastolicCode = new CodeableConcept();
        diastolicCode.addCoding(new Coding("http://snomed.info/sct", "271650006", "Diastolic blood pressure (observable entity)"));
        diastolicCode.addCoding(new Coding("http://loinc.org", "8462-4", "Diastolic blood pressure"));
        diastolicCode.addCoding(new Coding("urn:iso:std:iso:11073:10101", "150018", "Diastolic blood pressure"));
        diastolic.setCode(diastolicCode);
        diastolic.setValue(new Quantity().setValue(76).setUnit("millimeter Mercury column").setSystem("http://unitsofmeasure.org").setCode("mm[Hg]"));

        ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(observation, Set.of(BLOOD_PRESSURE), Map.of(), new CopyTreeNode("dummy"));
        Observation tgt = (Observation) integrationTestSetup.redaction().redact(wrapper);

        assertThat(tgt.getComponent()).hasSize(2);
        assertThat(tgt.getComponent().get(0).getCode().getCoding())
                .extracting(Coding::getSystem, Coding::getCode)
                .contains(tuple("http://snomed.info/sct", "271649006"));
        assertThat(tgt.getComponent().get(0).getValueQuantity().getValue()).isEqualByComparingTo("128");
        assertThat(tgt.getComponent().get(0).getValueQuantity().getCode()).isEqualTo("mm[Hg]");
        assertThat(tgt.getComponent().get(1).getCode().getCoding())
                .extracting(Coding::getSystem, Coding::getCode)
                .contains(tuple("http://snomed.info/sct", "271650006"));
        assertThat(tgt.getComponent().get(1).getValueQuantity().getValue()).isEqualByComparingTo("76");
        assertThat(tgt.getComponent().get(1).getValueQuantity().getCode()).isEqualTo("mm[Hg]");
    }

    /**
     * A required named slice (here {@code DiastolicBP}) entirely absent from the source data gets a masked
     * stub appended, without discarding already-processed sibling components (e.g. a present {@code SystolicBP}).
     * The stub's own required children are filled rather than left absent, since a bare {@code data-absent-reason}
     * on the stub alone does not satisfy their base FHIR cardinality; {@code code} takes the slice's pattern.
     */
    @Test
    void missingRequiredSliceAppendsStubWithoutDiscardingExistingComponents() throws RedactionException {
        Observation observation = new Observation();
        observation.setId("bp2");
        Meta meta = new Meta();
        meta.addProfile(BLOOD_PRESSURE);
        observation.setMeta(meta);

        Observation.ObservationComponentComponent systolic = observation.addComponent();
        CodeableConcept systolicCode = new CodeableConcept();
        systolicCode.addCoding(new Coding("http://snomed.info/sct", "271649006", "Systolic blood pressure (observable entity)"));
        systolicCode.addCoding(new Coding("http://loinc.org", "8480-6", "Systolic blood pressure"));
        systolic.setCode(systolicCode);
        systolic.setValue(new Quantity().setValue(128).setUnit("millimeter Mercury column").setSystem("http://unitsofmeasure.org").setCode("mm[Hg]"));
        // DiastolicBP (also required by the profile) is intentionally left absent from the source data.

        ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(observation, Set.of(BLOOD_PRESSURE), Map.of(), new CopyTreeNode("dummy"));
        Observation tgt = (Observation) integrationTestSetup.redaction().redact(wrapper);

        assertThat(tgt.getComponent()).hasSize(2);
        assertThat(tgt.getComponent().get(0).getCode().getCoding())
                .extracting(Coding::getSystem, Coding::getCode)
                .contains(tuple("http://snomed.info/sct", "271649006"));
        assertThat(tgt.getComponent().get(0).getValueQuantity().getValue()).isEqualByComparingTo("128");

        Observation.ObservationComponentComponent stub = tgt.getComponent().get(1);
        assertThat(stub.getExtensionsByUrl("http://hl7.org/fhir/StructureDefinition/data-absent-reason")).isNotEmpty();
        // the slice's patternCodeableConcept instead of a mask, so the stub still matches its own slice
        assertThat(stub.getCode().equalsDeep(new CodeableConcept(new Coding().setSystem("http://loinc.org").setCode("8462-4")))).isTrue();
    }

    @Test
    void testValueSetBindingPassingThroughAsDiscriminator() throws IOException, RedactionException {
        DomainResource src = integrationTestSetup.readResource(INPUT_OBSERVATION_DIR + "Observation-mii-exa-test-data-patient-1-vitalstatus-1.json");
        DomainResource expected = integrationTestSetup.readResource(EXPECTED_OUTPUT_DIR + "Observation-mii-exa-test-data-patient-1-vitalstatus-1.json");

        ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(src, Set.of(VITALSTATUS), Map.of("Observation.subject", ExtractionId.of("Patient/VHF-MIXED-TEST-CASE-0001-a"), "Observation.encounter", ExtractionId.of("Encounter/VHF-MIXED-TEST-CASE-0001-a-E-1")), new CopyTreeNode("dummy"));
        DomainResource tgt = integrationTestSetup.redaction().redact(wrapper);

        assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expected));
    }

    @Test
    void referenceComplexType() throws IOException, RedactionException {
        DomainResource src = integrationTestSetup.readResource(INPUT_OBSERVATION_DIR + "Observation-mii-exa-test-data-patient-1-vitalstatus-1-identifier.json");
        DomainResource expected = integrationTestSetup.readResource(EXPECTED_OUTPUT_DIR + "Observation-mii-exa-test-data-patient-1-vitalstatus-1-identifier.json");

        ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(src, Set.of(VITALSTATUS), Map.of("Observation.subject", ExtractionId.of("Patient/VHF-MIXED-TEST-CASE-0001-a"), "Observation.encounter", ExtractionId.of("Encounter/VHF-MIXED-TEST-CASE-0001-a-E-1")), new CopyTreeNode("dummy"));
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
    void fallbackForUndefinedElement() throws IOException, RedactionException {
        DomainResource src = integrationTestSetup.readResource(INPUT_CONDITION_DIR + "DiagnosisWithUndefinedElement.json");
        DomainResource expected = integrationTestSetup.readResource(EXPECTED_OUTPUT_DIR + "DiagnosisWithUndefinedElement.json");

        ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(src, Set.of(DIAGNOSIS), Map.of("Condition.subject", ExtractionId.of("Patient/12345", "Patient/123"), "Condition.encounter", ExtractionId.of("Encounter/12345")), new CopyTreeNode("dummy"));
        DomainResource tgt = integrationTestSetup.redaction().redact(wrapper);

        assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expected));
    }

    @Test
    void unknownSlice() throws IOException, RedactionException {
        DomainResource expected = integrationTestSetup.readResource(EXPECTED_OUTPUT_DIR + "unknownSlice.json");
        org.hl7.fhir.r4.model.Condition src = new org.hl7.fhir.r4.model.Condition();
        Meta meta = new Meta();
        meta.setProfile(List.of(new CanonicalType(DIAGNOSIS)));
        src.setMeta(meta);
        Coding code = new Coding("Test", "Test", "Test");
        CodeableConcept concept = new CodeableConcept();
        concept.setCoding(List.of(code));
        src.setCode(concept);

        ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(src, Set.of(DIAGNOSIS), Map.of(), new CopyTreeNode("dummy"));
        DomainResource tgt = integrationTestSetup.redaction().redact(wrapper);

        assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DiagnosisUnknownPrimitiveExtension.json", "DiagnosisWithExtensionAtCode.json", "DiagnosisUnknownComplexExtension.json", "DiagnosisWithExtensionAtExtensionsValue.json"})
    void removeUnknownPrimitiveAndComplexExtension(String resource) throws IOException, RedactionException {
        DomainResource src = integrationTestSetup.readResource(INPUT_CONDITION_DIR + resource);
        DomainResource expected = integrationTestSetup.readResource(EXPECTED_OUTPUT_DIR + resource);

        ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(src, Set.of(DIAGNOSIS), Map.of("Condition.subject", ExtractionId.of("Patient/12345", "Patient/123"), "Condition.encounter", ExtractionId.of("Encounter/12345")), new CopyTreeNode("dummy"));
        DomainResource tgt = integrationTestSetup.redaction().redact(wrapper);

        assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expected));
    }


    @Test
    void backboneElementHandling() throws RedactionException {
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
        CodeableConcept item = new CodeableConcept();
        item.addExtension(createAbsentReasonExtension("masked"));
        ingredient.setItem(item);
        expectedMedication.setIngredient(List.of(ingredient));


        ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(medication, Set.of(MEDICATION), Map.of(), new CopyTreeNode("Medication"));
        DomainResource tgt = integrationTestSetup.redaction().redact(wrapper);

        assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expectedMedication));
    }

    @Test
    void patient() throws RedactionException {
        Meta meta = new Meta();
        meta.addProfile("anyprofile");
        Patient patient = new Patient();
        patient.setId("patient-id");
        patient.setMeta(meta);
        Identifier inputIdentifier = new Identifier();
        inputIdentifier.setSystem("http://example.org/pid");
        inputIdentifier.setValue("12345");
        patient.setIdentifier(List.of(inputIdentifier.copy()));

        Patient expectedPatient = new Patient();
        expectedPatient.setId("patient-id");
        Meta metaModified = new Meta();
        metaModified.addProfile(PATIENT);
        expectedPatient.setMeta(metaModified);
        expectedPatient.setIdentifier(List.of(inputIdentifier.copy()));

        ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(patient, Set.of(PATIENT), Map.of(), new CopyTreeNode("dummy"));
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
    void testMultiProfilesWithCustomStructureDefinition() throws IOException, RedactionException {
        DomainResource src = integrationTestSetup.readResource(INPUT_OBSERVATION_DIR + "MultiProfileObservationWithPatternSlicing.json");
        DomainResource expected = integrationTestSetup.readResource(EXPECTED_OUTPUT_DIR + "MultiProfileObservationWithPatternSlicing.json");
        StructureDefinitionHandler definitionHandler = new StructureDefinitionHandler(new File("src/test/resources/StructureDefinitions/"), new ResourceReader(integrationTestSetup.fhirContext()));
        definitionHandler.processDirectory();
        Redaction redaction = new Redaction(definitionHandler);
        ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(src, Set.of("http://example.org/fhir/StructureDefinition/observation-with-pattern-slicing", "http://example.org/fhir/StructureDefinition/observation-with-pattern-slicing-modified", "http://example.org/fhir/StructureDefinition/observation-without-slicing"), Map.of(), new CopyTreeNode("dummy"));
        DomainResource tgt = redaction.redact(wrapper);

        assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expected));

    }

    /**
     * Tests a required named slice ({@code min=1}) defined via {@code contentReference} instead of {@code type}
     * (a valid FHIR construct, e.g. for recursive structures). With no matching instance in the data, the
     * missing-slice masking has no type to build a stub from and must skip that slice instead of failing.
     *
     * @throws IOException when test file not found
     */
    @Test
    void missingRequiredSliceWithoutType() throws IOException, RedactionException {
        Observation src = new Observation();
        Meta meta = new Meta();
        meta.addProfile("http://example.org/fhir/StructureDefinition/observation-with-typeless-required-slice");
        src.setMeta(meta);
        src.setStatus(Observation.ObservationStatus.FINAL);
        src.setCode(new CodeableConcept(new Coding("http://loinc.org", "85353-1", "Vital signs panel")));
        Observation.ObservationComponentComponent heartRate = new Observation.ObservationComponentComponent();
        heartRate.setCode(new CodeableConcept(new Coding("http://loinc.org", "8867-4", "Heart rate")));
        src.setComponent(List.of(heartRate));

        StructureDefinitionHandler definitionHandler = new StructureDefinitionHandler(new File("src/test/resources/StructureDefinitions/"), new ResourceReader(integrationTestSetup.fhirContext()));
        definitionHandler.processDirectory();
        Redaction redaction = new Redaction(definitionHandler);
        ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(src.copy(), Set.of("http://example.org/fhir/StructureDefinition/observation-with-typeless-required-slice"), Map.of(), new CopyTreeNode("dummy"));
        DomainResource tgt = redaction.redact(wrapper);

        assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(src));
    }

    @Test
    void missingRequiredDurationGetsDataAbsentReasonPlaceholder() throws IOException, RedactionException {
        String profile = "http://example.org/fhir/StructureDefinition/encounter-with-required-length";
        Encounter src = new Encounter();
        src.setMeta(new Meta().addProfile(profile));
        src.setStatus(Encounter.EncounterStatus.FINISHED);
        src.setClass_(new Coding("http://terminology.hl7.org/CodeSystem/v3-ActCode", "IMP", "inpatient encounter"));

        DomainResource tgt = customProfileRedaction().redact(new ExtractionRedactionWrapper(src.copy(), Set.of(profile), Map.of(), new CopyTreeNode("dummy")));

        Encounter encounter = (Encounter) tgt;
        assertThat(encounter.getLength()).isInstanceOf(Duration.class);
        assertThat(encounter.getLength().hasValue()).isFalse();
        assertThat(encounter.getLength().getExtension()).singleElement()
                .satisfies(extension -> assertThat(extension.equalsDeep(createAbsentReasonExtension("masked"))).isTrue());
    }

    /**
     * A placeholder of a type with required children (here {@code UsageContext.code} and {@code UsageContext.value[x]},
     * both 1..1 in the base specification) gets those children masked too, since a bare data-absent-reason on the
     * placeholder alone would be invalid (#1378).
     */
    @Test
    void missingRequiredTypeWithRequiredChildrenGetsMaskedChildren() throws IOException, RedactionException {
        String profile = "http://example.org/fhir/StructureDefinition/questionnaire-with-required-use-context";
        Questionnaire src = new Questionnaire();
        src.setMeta(new Meta().addProfile(profile));
        src.setStatus(Enumerations.PublicationStatus.ACTIVE);

        Questionnaire tgt = (Questionnaire) customProfileRedaction().redact(new ExtractionRedactionWrapper(src.copy(), Set.of(profile), Map.of(), new CopyTreeNode("dummy")));

        UsageContext useContext = tgt.getUseContextFirstRep();
        assertThat(useContext.getExtension()).singleElement()
                .satisfies(extension -> assertThat(extension.equalsDeep(createAbsentReasonExtension("masked"))).isTrue());
        assertThat(useContext.getCode().getExtension()).singleElement()
                .satisfies(extension -> assertThat(extension.equalsDeep(createAbsentReasonExtension("masked"))).isTrue());
        assertThat(useContext.getValueCodeableConcept().getExtension()).singleElement()
                .satisfies(extension -> assertThat(extension.equalsDeep(createAbsentReasonExtension("masked"))).isTrue());
    }

    /**
     * A required child of a masked placeholder that carries a profile {@code pattern[x]} gets that pattern instead
     * of a data-absent-reason (#1378).
     */
    @Test
    void missingRequiredTypeWithPatternedRequiredChildGetsPatternValue() throws IOException, RedactionException {
        String profile = "http://example.org/fhir/StructureDefinition/questionnaire-with-required-use-context-pattern";
        Questionnaire src = new Questionnaire();
        src.setMeta(new Meta().addProfile(profile));
        src.setStatus(Enumerations.PublicationStatus.ACTIVE);

        Questionnaire tgt = (Questionnaire) customProfileRedaction().redact(new ExtractionRedactionWrapper(src.copy(), Set.of(profile), Map.of(), new CopyTreeNode("dummy")));

        UsageContext useContext = tgt.getUseContextFirstRep();
        assertThat(useContext.getCode().equalsDeep(new Coding().setSystem("http://terminology.hl7.org/CodeSystem/usage-context-type").setCode("focus"))).isTrue();
        assertThat(useContext.getValueCodeableConcept().getExtension()).singleElement()
                .satisfies(extension -> assertThat(extension.equalsDeep(createAbsentReasonExtension("masked"))).isTrue());
    }

    /**
     * The base-required children of a required Signature placeholder ({@code type}, {@code when} and the Reference
     * {@code who}) are masked too, even though the profile snapshot does not list them and their type comes from
     * HAPI's {@code Reference(...)} type code (#1378).
     */
    @Test
    void missingRequiredSignatureGetsMaskedBaseRequiredChildren() throws IOException, RedactionException {
        String profile = "http://example.org/fhir/StructureDefinition/provenance-with-required-signature";
        Provenance src = new Provenance();
        src.setMeta(new Meta().addProfile(profile));

        Provenance tgt = (Provenance) customProfileRedaction().redact(new ExtractionRedactionWrapper(src.copy(), Set.of(profile), Map.of(), new CopyTreeNode("dummy")));

        assertThat(tgt.getSignature()).singleElement().satisfies(signature -> {
            assertThat(signature.hasExtension(DATA_ABSENT_REASON_URL)).isTrue();
            assertThat(signature.getType()).singleElement().satisfies(type -> assertThat(type.hasExtension(DATA_ABSENT_REASON_URL)).isTrue());
            assertThat(signature.getWhenElement().hasExtension(DATA_ABSENT_REASON_URL)).isTrue();
            assertThat(signature.getWho().hasExtension(DATA_ABSENT_REASON_URL)).isTrue();
        });
    }

    /**
     * An absent child the base specification requires ({@code Observation.status}, 1..1) is masked even if the
     * profile snapshot does not list it.
     */
    @Test
    void absentBaseRequiredChildNotInProfileIsMasked() throws IOException, RedactionException {
        String profile = "http://example.org/fhir/StructureDefinition/observation-without-slicing";
        Observation src = new Observation();
        src.setMeta(new Meta().addProfile(profile));
        src.setCode(new CodeableConcept(new Coding("http://loinc.org", "8867-4", "Heart rate")));

        Observation tgt = (Observation) customProfileRedaction().redact(new ExtractionRedactionWrapper(src.copy(), Set.of(profile), Map.of(), new CopyTreeNode("dummy")));

        assertThat(tgt.getStatusElement().hasExtension(DATA_ABSENT_REASON_URL)).isTrue();
        assertThat(tgt.getCode().equalsDeep(src.getCode())).isTrue();
    }

    /**
     * A Reference child only the profile requires ({@code Identifier.assigner}) is masked on the Identifier
     * placeholder, also when the snapshot gives it no type.
     */
    @Test
    void missingRequiredIdentifierGetsMaskedProfileRequiredReference() throws IOException, RedactionException {
        String profile = "http://example.org/fhir/StructureDefinition/questionnaire-with-required-identifier-assigner";
        Questionnaire src = new Questionnaire();
        src.setMeta(new Meta().addProfile(profile));
        src.setStatus(Enumerations.PublicationStatus.ACTIVE);

        Questionnaire tgt = (Questionnaire) customProfileRedaction().redact(new ExtractionRedactionWrapper(src.copy(), Set.of(profile), Map.of(), new CopyTreeNode("dummy")));

        assertThat(tgt.getIdentifier()).singleElement().satisfies(identifier -> {
            assertThat(identifier.getExtension()).singleElement()
                    .satisfies(extension -> assertThat(extension.equalsDeep(createAbsentReasonExtension("masked"))).isTrue());
            assertThat(identifier.getAssigner().hasExtension(DATA_ABSENT_REASON_URL)).isTrue();
        });
    }

    /**
     * {@code Identifier.use} is an {@code Enumeration} in HAPI; its placeholder must keep the data-absent-reason.
     */
    @Test
    void missingRequiredEnumerationChildKeepsDataAbsentReason() throws IOException, RedactionException {
        String profile = "http://example.org/fhir/StructureDefinition/questionnaire-with-required-identifier-use";
        Questionnaire src = new Questionnaire();
        src.setMeta(new Meta().addProfile(profile));
        src.setStatus(Enumerations.PublicationStatus.ACTIVE);

        Questionnaire tgt = (Questionnaire) customProfileRedaction().redact(new ExtractionRedactionWrapper(src.copy(), Set.of(profile), Map.of(), new CopyTreeNode("dummy")));

        assertThat(tgt.getIdentifier()).singleElement()
                .satisfies(identifier -> assertThat(identifier.getUseElement().hasExtension(DATA_ABSENT_REASON_URL)).isTrue());
    }

    /**
     * A required UsageContext placeholder whose base-required {@code code} cannot be built (the profile types it
     * with an unknown type) is left out rather than attached incomplete, and a warning is logged.
     */
    @Test
    void missingRequiredTypeWithUnbuildableBaseRequiredChildIsNotAttached() throws IOException, RedactionException {
        String profile = "http://example.org/fhir/StructureDefinition/questionnaire-with-required-use-context-unknown-code-type";
        Questionnaire src = new Questionnaire();
        src.setMeta(new Meta().addProfile(profile));
        src.setStatus(Enumerations.PublicationStatus.ACTIVE);
        Redaction redaction = customProfileRedaction();

        Logger redactionLogger = (Logger) LoggerFactory.getLogger(Redaction.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        redactionLogger.addAppender(appender);
        Questionnaire tgt;
        try {
            tgt = (Questionnaire) redaction.redact(new ExtractionRedactionWrapper(src.copy(), Set.of(profile), Map.of(), new CopyTreeNode("dummy")));
        } finally {
            redactionLogger.detachAppender(appender);
        }

        assertThat(tgt.getUseContext()).isEmpty();
        assertThat(appender.list)
                .filteredOn(event -> event.getLevel() == Level.WARN)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message).contains("Unmaskable").contains("useContext").contains("UsageContext"));
    }

    /**
     * A child only the profile requires ({@code Identifier.system}) that cannot be built does not keep the
     * Identifier placeholder from being attached, since the placeholder still satisfies the base specification.
     */
    @Test
    void missingRequiredTypeWithUnbuildableProfileRequiredChildIsAttached() throws IOException, RedactionException {
        String profile = "http://example.org/fhir/StructureDefinition/questionnaire-with-required-identifier-unknown-system-type";
        Questionnaire src = new Questionnaire();
        src.setMeta(new Meta().addProfile(profile));
        src.setStatus(Enumerations.PublicationStatus.ACTIVE);

        Questionnaire tgt = (Questionnaire) customProfileRedaction().redact(new ExtractionRedactionWrapper(src.copy(), Set.of(profile), Map.of(), new CopyTreeNode("dummy")));

        assertThat(tgt.getIdentifier()).singleElement().satisfies(identifier -> {
            assertThat(identifier.hasExtension(DATA_ABSENT_REASON_URL)).isTrue();
            assertThat(identifier.hasSystem()).isFalse();
        });
    }

    /**
     * A BackboneElement stub is attached before its children are filled, so a base-required child that cannot be
     * built ({@code item.linkId}, typed with an unknown type) leaves the stub in place and logs a warning.
     */
    @Test
    void missingRequiredBackboneWithUnbuildableBaseRequiredChildIsKeptWithWarning() throws IOException, RedactionException {
        String profile = "http://example.org/fhir/StructureDefinition/questionnaire-with-required-item-unknown-link-id-type";
        Questionnaire src = new Questionnaire();
        src.setMeta(new Meta().addProfile(profile));
        src.setStatus(Enumerations.PublicationStatus.ACTIVE);
        Redaction redaction = customProfileRedaction();

        RedactionResult result = redactCapturingWarnings(redaction, new ExtractionRedactionWrapper(src.copy(), Set.of(profile), Map.of(), new CopyTreeNode("dummy")));

        assertThat(((Questionnaire) result.resource()).getItem()).singleElement().satisfies(item -> {
            assertThat(item.hasExtension(DATA_ABSENT_REASON_URL)).isTrue();
            assertThat(item.hasLinkId()).isFalse();
        });
        assertThat(result.warnings()).anySatisfy(message -> assertThat(message).contains("Unmaskable").contains("item").contains("BackboneElement"));
    }

    /**
     * A required element wiped for matching no slice stays attached even if one of its base-required children
     * ({@code component.code}, typed with an unknown type) cannot be built; a warning names the element ID.
     */
    @Test
    void elementMatchingNoSliceWithUnbuildableBaseRequiredChildIsKeptWithWarning() throws IOException, RedactionException {
        String profile = "http://example.org/fhir/StructureDefinition/observation-with-required-component-unknown-code-type";
        Observation src = new Observation();
        src.setMeta(new Meta().addProfile(profile));
        src.setStatus(Observation.ObservationStatus.FINAL);
        src.addComponent().setCode(new CodeableConcept(new Coding("http://loinc.org", "8480-6", "Systolic blood pressure")));
        Redaction redaction = customProfileRedaction();

        RedactionResult result = redactCapturingWarnings(redaction, new ExtractionRedactionWrapper(src.copy(), Set.of(profile), Map.of(), new CopyTreeNode("dummy")));

        assertThat(((Observation) result.resource()).getComponent()).singleElement().satisfies(component -> {
            assertThat(component.hasExtension(DATA_ABSENT_REASON_URL)).isTrue();
            assertThat(component.hasCode()).isFalse();
        });
        assertThat(result.warnings()).anySatisfy(message -> assertThat(message).contains("Unmaskable").contains("Observation.component"));
    }

    /**
     * A profile that types a choice element as {@code BackboneElement} leaves no setter to attach a stub with;
     * the element stays absent instead of failing the redaction.
     */
    @Test
    void requiredBackboneWithoutSetterIsLeftAbsent() throws IOException, RedactionException {
        String profile = "http://example.org/fhir/StructureDefinition/observation-with-backbone-typed-component-value";
        Observation src = new Observation();
        src.setMeta(new Meta().addProfile(profile));
        src.setStatus(Observation.ObservationStatus.FINAL);
        src.addComponent().setCode(new CodeableConcept(new Coding("http://loinc.org", "8480-6", "Systolic blood pressure")));

        Observation tgt = (Observation) customProfileRedaction().redact(new ExtractionRedactionWrapper(src.copy(), Set.of(profile), Map.of(), new CopyTreeNode("dummy")));

        assertThat(tgt.getComponent()).singleElement().satisfies(component -> assertThat(component.hasValue()).isFalse());
    }

    private record RedactionResult(DomainResource resource, List<String> warnings) {
    }

    private static RedactionResult redactCapturingWarnings(Redaction redaction, ExtractionRedactionWrapper wrapper) throws RedactionException {
        Logger redactionLogger = (Logger) LoggerFactory.getLogger(Redaction.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        redactionLogger.addAppender(appender);
        DomainResource resource;
        try {
            resource = redaction.redact(wrapper);
        } finally {
            redactionLogger.detachAppender(appender);
        }
        return new RedactionResult(resource, appender.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList());
    }

    private Redaction customProfileRedaction() throws IOException {
        StructureDefinitionHandler definitionHandler = new StructureDefinitionHandler(new File("src/test/resources/StructureDefinitions/"), new ResourceReader(integrationTestSetup.fhirContext()));
        definitionHandler.processDirectory();
        return new Redaction(definitionHandler);
    }

    /**
     * Reproduces #1230: a required extension slot with no matching instance must be left absent, not masked
     * with an anonymous {@code Extension} stub, since that has no url and serializes as {@code "url": null}.
     */
    @Test
    void requiredExtensionSlotIsLeftAbsentRatherThanMaskedWithoutUrl() throws IOException, RedactionException {
        Condition src = new Condition();
        Meta meta = new Meta();
        meta.addProfile("http://example.org/fhir/StructureDefinition/condition-with-required-extension-slot");
        src.setMeta(meta);
        src.addExtension(new Extension("http://example.org/fhir/StructureDefinition/optional-ref"));

        StructureDefinitionHandler definitionHandler = new StructureDefinitionHandler(new File("src/test/resources/StructureDefinitions/"), new ResourceReader(integrationTestSetup.fhirContext()));
        definitionHandler.processDirectory();
        Redaction redaction = new Redaction(definitionHandler);
        ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(src.copy(), Set.of("http://example.org/fhir/StructureDefinition/condition-with-required-extension-slot"), Map.of(), new CopyTreeNode("dummy"));
        DomainResource tgt = redaction.redact(wrapper);

        assertThat(tgt.hasExtension()).isFalse();
        assertThat(tgt.hasModifierExtension()).isFalse();
    }

    /**
     * Required elements that redaction leaves absent or invalid are recorded once per occurrence, at the outermost
     * element that ends up absent or invalid in the output.
     */
    @Nested
    class ReportsUnmaskableRequiredElements {

        private RedactionIssues redact(DomainResource src, String profile) throws IOException, RedactionException {
            src.setMeta(new Meta().addProfile(profile));
            RedactionIssues issues = new RedactionIssues();
            customProfileRedaction().redact(new ExtractionRedactionWrapper(src, Set.of(profile), Map.of(), new CopyTreeNode("dummy")), issues);
            return issues;
        }

        private static Questionnaire questionnaire() {
            Questionnaire questionnaire = new Questionnaire();
            questionnaire.setId("q1");
            questionnaire.setStatus(Enumerations.PublicationStatus.ACTIVE);
            return questionnaire;
        }

        private static Observation observationWithComponent(String code) {
            Observation observation = new Observation();
            observation.setId("o1");
            observation.setStatus(Observation.ObservationStatus.FINAL);
            observation.addComponent().setCode(new CodeableConcept(new Coding("http://loinc.org", code, null)));
            return observation;
        }

        private static RedactionViolation violation(String resourceType, String elementId, String elementType,
                                                    RedactionViolationReason reason, String example) {
            return new RedactionViolation(resourceType, elementId, elementType, reason, 1, List.of(example));
        }

        private static RedactionViolation unmaskableChild(String resourceType, String elementId, String elementType,
                                                          String failedChildId, String example) {
            return new RedactionViolation(resourceType, elementId, elementType, RedactionViolationReason.UNMASKABLE_REQUIRED_CHILD,
                    new RedactionCause(failedChildId, "NoSuchType", RedactionViolationReason.UNRESOLVABLE_TYPE), 1, List.of(example));
        }

        @Test
        void successfulMaskRecordsNothing() throws Exception {
            RedactionIssues issues = redact(questionnaire(), "http://example.org/fhir/StructureDefinition/questionnaire-with-required-use-context");

            assertThat(issues.violations()).isEmpty();
        }

        @Test
        void droppedPlaceholderIsRecordedOnceAtTheDroppedElement() throws Exception {
            RedactionIssues issues = redact(questionnaire(), "http://example.org/fhir/StructureDefinition/questionnaire-with-required-use-context-unknown-code-type");

            assertThat(issues.violations()).containsExactly(unmaskableChild("Questionnaire", "Questionnaire.useContext", "UsageContext",
                    "Questionnaire.useContext.code", "Questionnaire/q1"));
        }

        /**
         * {@code useContext.value[x]} is typed as UsageContext, whose own base-required {@code value[x]} cannot be built:
         * the issue names that innermost element, not the direct child.
         */
        @Test
        void droppedPlaceholderNamesInnermostFailedChild() throws Exception {
            RedactionIssues issues = redact(questionnaire(), "http://example.org/fhir/StructureDefinition/questionnaire-with-nested-unbuildable-use-context");

            assertThat(issues.violations()).containsExactly(unmaskableChild("Questionnaire", "Questionnaire.useContext", "UsageContext",
                    "Questionnaire.useContext.value[x].value[x]", "Questionnaire/q1"));
        }

        @Test
        void backboneStubMissingBaseRequiredChildIsRecorded() throws Exception {
            RedactionIssues issues = redact(questionnaire(), "http://example.org/fhir/StructureDefinition/questionnaire-with-required-item-unknown-link-id-type");

            assertThat(issues.violations()).containsExactly(unmaskableChild("Questionnaire", "Questionnaire.item", "BackboneElement",
                    "Questionnaire.item.linkId", "Questionnaire/q1"));
        }

        @Test
        void elementMatchingNoSliceMissingBaseRequiredChildIsRecorded() throws Exception {
            RedactionIssues issues = redact(observationWithComponent("8480-6"), "http://example.org/fhir/StructureDefinition/observation-with-required-component-unknown-code-type");

            assertThat(issues.violations()).containsExactly(unmaskableChild("Observation", "Observation.component", "BackboneElement",
                    "Observation.component.code", "Observation/o1"));
        }

        @Test
        void complexElementMatchingNoSliceMissingBaseRequiredChildIsRecorded() throws Exception {
            Questionnaire src = questionnaire();
            src.addUseContext(new UsageContext(new Coding("http://terminology.hl7.org/CodeSystem/usage-context-type", "venue", null),
                    new CodeableConcept(new Coding("http://example.org", "x", null))));

            RedactionIssues issues = redact(src, "http://example.org/fhir/StructureDefinition/questionnaire-with-sliced-use-context-unknown-value-type");

            assertThat(issues.violations()).containsExactly(unmaskableChild("Questionnaire", "Questionnaire.useContext", "UsageContext",
                    "Questionnaire.useContext.value[x]", "Questionnaire/q1"));
        }

        @Test
        void backboneWithoutSetterIsRecorded() throws Exception {
            RedactionIssues issues = redact(observationWithComponent("8480-6"), "http://example.org/fhir/StructureDefinition/observation-with-backbone-typed-component-value");

            assertThat(issues.violations()).containsExactly(violation("Observation", "Observation.component.value[x]", "BackboneElement",
                    RedactionViolationReason.NO_SETTER, "Observation/o1"));
        }

        @Test
        void unresolvableTypeOfAbsentRequiredElementIsRecorded() throws Exception {
            RedactionIssues issues = redact(questionnaire(), "http://example.org/fhir/StructureDefinition/questionnaire-with-required-name-unknown-type");

            assertThat(issues.violations()).containsExactly(violation("Questionnaire", "Questionnaire.name", "NoSuchType",
                    RedactionViolationReason.UNRESOLVABLE_TYPE, "Questionnaire/q1"));
        }

        @Test
        void unbuildableMissingRequiredSliceIsRecorded() throws Exception {
            RedactionIssues issues = redact(observationWithComponent("8867-4"), "http://example.org/fhir/StructureDefinition/observation-with-unbuildable-required-slice");

            assertThat(issues.violations()).containsExactly(violation("Observation", "Observation.component:systolic", "NoSuchType",
                    RedactionViolationReason.UNRESOLVABLE_TYPE, "Observation/o1"));
        }

        /**
         * The Identifier placeholder is attached, but lacks {@code system}, which only the profile requires.
         */
        @Test
        void profileRequiredChildMissingFromAttachedStubIsRecorded() throws Exception {
            RedactionIssues issues = redact(questionnaire(), "http://example.org/fhir/StructureDefinition/questionnaire-with-required-identifier-unknown-system-type");

            assertThat(issues.violations()).containsExactly(violation("Questionnaire", "Questionnaire.identifier.system", "NoSuchType",
                    RedactionViolationReason.UNRESOLVABLE_TYPE, "Questionnaire/q1"));
        }

        @Test
        void sameFailureOnSeveralResourcesIsAggregated() throws Exception {
            String profile = "http://example.org/fhir/StructureDefinition/questionnaire-with-required-name-unknown-type";
            Redaction redaction = customProfileRedaction();
            RedactionIssues issues = new RedactionIssues();
            for (int i = 0; i < 5; i++) {
                Questionnaire src = questionnaire();
                src.setId("q" + i);
                src.setMeta(new Meta().addProfile(profile));
                redaction.redact(new ExtractionRedactionWrapper(src, Set.of(profile), Map.of(), new CopyTreeNode("dummy")), issues);
            }

            assertThat(issues.violations()).singleElement().satisfies(violation -> {
                assertThat(violation.count()).isEqualTo(5);
                assertThat(violation.examples()).containsExactly("Questionnaire/q0", "Questionnaire/q1", "Questionnaire/q2");
            });
        }
    }

    @Nested
    class ConditionTest {

        @ParameterizedTest
        @ValueSource(strings = {"Condition-mii-exa-diagnose-condition-minimal.json", "Condition-mii-exa-diagnose-mehrfachkodierung-primaercode.json", "Condition-mii-exa-diagnose-mehrfachkodierung-primaercode.json", "Condition-mii-exa-diagnose-multiple-kodierungen.json", "Condition-mii-exa-test-data-patient-1-diagnose-1.json", "Condition-mii-exa-test-data-patient-1-diagnose-2.json", "Condition-mii-exa-test-data-patient-3-diagnose-1.json", "Condition-mii-exa-test-data-patient-4-diagnose-1.json"})
        void diagnosisAllValid(String resource) throws IOException, RedactionException {
            DomainResource src = integrationTestSetup.readResource(INPUT_CONDITION_DIR + resource);
            DomainResource expected = integrationTestSetup.readResource(INPUT_CONDITION_DIR + resource);

            ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(src, Set.of(DIAGNOSIS), Map.of("Condition.subject", ExtractionId.of("Patient/12345", "Patient/mii-exa-test-data-patient-1", "Patient/mii-exa-test-data-patient-3"), "Condition.encounter", ExtractionId.of("Encounter/12345")), new CopyTreeNode("dummy"));
            DomainResource tgt = integrationTestSetup.redaction().redact(wrapper);

            assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expected));
        }

        @ParameterizedTest
        @ValueSource(strings = {"Condition-mii-exa-diagnose-condition-minimal.json", "Condition-mii-exa-diagnose-mehrfachkodierung-primaercode.json"})
        void invalidReferences(String resource) throws IOException, RedactionException {
            DomainResource src = integrationTestSetup.readResource(INPUT_CONDITION_DIR + resource);
            DomainResource expected = integrationTestSetup.readResource(EXPECTED_OUTPUT_DIR + resource);

            ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(src, Set.of(DIAGNOSIS), Map.of("Condition.subject", Set.of(), "Condition.encounter", ExtractionId.of("Encounter/12345")), new CopyTreeNode("dummy"));
            DomainResource tgt = integrationTestSetup.redaction().redact(wrapper);

            assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expected));
        }

        @ParameterizedTest
        @ValueSource(strings = {"DiagnosisWithInvalidSliceCode.json"})
        void diagnosisInvalidElements(String resource) throws IOException, RedactionException {
            DomainResource src = integrationTestSetup.readResource(INPUT_CONDITION_DIR + resource);
            DomainResource expected = integrationTestSetup.readResource(EXPECTED_OUTPUT_DIR + resource);

            ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(src, Set.of(DIAGNOSIS), Map.of("Condition.subject", ExtractionId.of("Patient/12345"), "Condition.encounter", ExtractionId.of("Encounter/12345")), new CopyTreeNode("dummy"));
            DomainResource tgt = integrationTestSetup.redaction().redact(wrapper);

            assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expected));
        }

        @ParameterizedTest
        @ValueSource(strings = {"Diagnosis1.json", "Diagnosis2.json"})
        void diagnosisMissingElements(String resource) throws IOException, RedactionException {
            DomainResource src = integrationTestSetup.readResource(INPUT_CONDITION_DIR + resource);
            DomainResource expected = integrationTestSetup.readResource(EXPECTED_OUTPUT_DIR + resource);

            ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(src, Set.of(DIAGNOSIS), Map.of("Condition.subject", ExtractionId.of("Patient/12345", "Patient/123"), "Condition.encounter", ExtractionId.of("Encounter/12345")), new CopyTreeNode("dummy"));
            DomainResource tgt = integrationTestSetup.redaction().redact(wrapper);

            assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expected));
        }


        /**
         * Tests for multi profile with CDS Todesursache and Diagnosis Profiles
         *
         * @param resource Todesursache1.json does only contain the who icd10 (who) which is not currently allowed in diagnosis profile
         *                 Todesursache2.json does contain 2 legal codings icd10 and sct from two different profiles
         *                 Todesursache3.json does not contain the required icd 10 (who)
         *                 Todesursache4.json only contains one of the two required category.coding slices (snomed, missing loinc)
         * @throws IOException when test file not found
         */
        @ParameterizedTest
        @ValueSource(strings = {"Todesursache1.json", "Todesursache2.json", "Todesursache3.json", "Todesursache4.json"})
        void testMultipleProfiles(String resource) throws IOException, RedactionException {
            DomainResource src = integrationTestSetup.readResource(INPUT_CONDITION_DIR + resource);
            DomainResource expected = integrationTestSetup.readResource(EXPECTED_OUTPUT_DIR + resource);

            ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(src.copy(), Set.of(DIAGNOSIS, TODESURSACHE), Map.of("Condition.subject", ExtractionId.of("Patient/12345", "Patient/123"), "Condition.encounter", ExtractionId.of("Encounter/12345")), new CopyTreeNode("dummy"));
            DomainResource tgt = integrationTestSetup.redaction().redact(wrapper);

            assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expected));
        }

        @ParameterizedTest
        @ValueSource(strings = {"Diagnosis1.json"})
        void failsWhenRequiredProfileMissing(String resource) throws IOException {
            DomainResource src = integrationTestSetup.readResource(INPUT_CONDITION_DIR + resource);

            assertThatThrownBy(() -> ExtractionRedactionWrapper.of(src.copy(), Set.of(DIAGNOSIS, TODESURSACHE), Map.of("Condition.subject", ExtractionId.of("Patient/12345", "Patient/123"), "Condition.encounter", ExtractionId.of("Encounter/12345")), new CopyTreeNode("dummy"))).isInstanceOf(RedactionException.class);
        }

        @Test
        void failsWhenMetaMissing() {
            Condition condition = new Condition();

            assertThatThrownBy(() -> ExtractionRedactionWrapper.of(condition.copy(), Set.of(DIAGNOSIS, TODESURSACHE), Map.of("Condition.subject", ExtractionId.of("Patient/12345", "Patient/123"), "Condition.encounter", ExtractionId.of("Encounter/12345")), new CopyTreeNode("dummy"))).isInstanceOf(RedactionException.class);
        }

        @Test
        void failsWhenProvidedInvalidProfiles() throws RedactionException {
            Condition condition = new Condition();
            Meta meta = new Meta();
            meta.setProfile(List.of(new CanonicalType("InvalidProfile")));
            condition.setMeta(meta);
            ExtractionRedactionWrapper wrapper = ExtractionRedactionWrapper.of(condition.copy(), Set.of("InvalidProfile"), Map.of("Condition.subject", ExtractionId.of("Patient/12345", "Patient/123"), "Condition.encounter", ExtractionId.of("Encounter/12345")), new CopyTreeNode("dummy"));
            var redaction = integrationTestSetup.redaction();

            assertThatThrownBy(() -> redaction.redact(wrapper)).isInstanceOf(RedactionException.class);
        }
    }

    @Nested
    class EncounterTest {
        @Test
        void supportNestedReferences() throws RedactionException {
            Encounter src = new Encounter();
            Meta meta = new Meta();
            meta.setProfile(List.of(new CanonicalType(ENCOUNTER)));
            src.setMeta(meta);
            src.setStatus(Encounter.EncounterStatus.ARRIVED);
            src.setClass_(new Coding("Test", "Test", "Test"));
            src.setId("Encounter/12345");
            src.setSubject(new Reference("Patient/12345"));
            src.setDiagnosis(List.of(new Encounter.DiagnosisComponent().setCondition(new Reference("Condition/12345")).setUse(new CodeableConcept(new Coding("Test", "Test", "")))));

            ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(src.copy(), Set.of(ENCOUNTER), Map.of("Encounter.subject", ExtractionId.of("Patient/12345", "Patient/123"), "Encounter.diagnosis", ExtractionId.of("Condition/12345")), new CopyTreeNode("dummy"));
            var redaction = integrationTestSetup.redaction();
            Encounter tgt = (Encounter) redaction.redact(wrapper);


            assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(src));
        }

        @Test
        void handlesReferenceGracefully() throws RedactionException {
            Encounter src = new Encounter();
            Meta meta = new Meta();
            meta.setProfile(List.of(new CanonicalType(ENCOUNTER)));
            src.setMeta(meta);
            src.setStatus(Encounter.EncounterStatus.ARRIVED);
            src.setClass_(new Coding("Test", "Test", "Test"));
            src.setId("Encounter/12345");
            src.setSubject(new Reference("/"));
            src.setDiagnosis(List.of(new Encounter.DiagnosisComponent().setCondition(new Reference("Condition/12345")).setUse(new CodeableConcept(new Coding("Test", "Test", "")))));

            ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(src.copy(), Set.of(ENCOUNTER), Map.of("Encounter.subject", ExtractionId.of("Patient/12345", "Patient/123"), "Encounter.diagnosis", ExtractionId.of("Condition/12345")), new CopyTreeNode("dummy"));
            var redaction = integrationTestSetup.redaction();
            Encounter tgt = (Encounter) redaction.redact(wrapper);

            Encounter expected = src.copy();
            StringType referenceElement = new StringType();
            referenceElement.addExtension(
                    FhirUtil.createAbsentReasonExtension("masked")  // must be lowercase per spec
            );
            Reference dar = new Reference();
            dar.setReferenceElement(referenceElement);
            expected.setSubject(dar);
            assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expected));
        }

        @Test
        void handlesAlreadyMaskedReferenceGracefully() throws RedactionException {
            Encounter src = new Encounter();
            Meta meta = new Meta();
            meta.setProfile(List.of(new CanonicalType(ENCOUNTER)));
            src.setMeta(meta);
            src.setStatus(Encounter.EncounterStatus.ARRIVED);
            src.setClass_(new Coding("Test", "Test", "Test"));
            src.setId("Encounter/12345");
            StringType referenceElement = new StringType();
            referenceElement.addExtension(
                    FhirUtil.createAbsentReasonExtension("masked")  // must be lowercase per spec
            );
            Reference dar = new Reference();
            dar.setReferenceElement(referenceElement);
            src.setSubject(dar);
            src.setDiagnosis(List.of(new Encounter.DiagnosisComponent().setCondition(new Reference("Condition/12345")).setUse(new CodeableConcept(new Coding("Test", "Test", "")))));

            ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(src.copy(), Set.of(ENCOUNTER), Map.of("Encounter.subject", ExtractionId.of("Patient/12345", "Patient/123"), "Encounter.diagnosis", ExtractionId.of("Condition/12345")), new CopyTreeNode("dummy"));
            var redaction = integrationTestSetup.redaction();

            ch.qos.logback.classic.Logger logbackLogger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(Redaction.class);
            ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender = new ch.qos.logback.core.read.ListAppender<>();
            appender.start();
            logbackLogger.addAppender(appender);
            Encounter tgt;
            try {
                tgt = (Encounter) redaction.redact(wrapper);
            } finally {
                logbackLogger.detachAppender(appender);
            }

            assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(src));
            assertThat(appender.list)
                    .extracting(event -> event.getFormattedMessage())
                    .anyMatch(message -> message.contains("REDACTION_03") && message.contains("Encounter") && message.contains("12345"));
        }

        /**
         * modifierExtension, like extension, is handled by the URL-aware extension pipeline rather than
         * slice-matching, so it must pass through untouched instead of being checked for missing required slices.
         */
        @Test
        void modifierExtensionSkipsSliceCheck() throws RedactionException {
            Encounter src = new Encounter();
            Meta meta = new Meta();
            meta.setProfile(List.of(new CanonicalType(ENCOUNTER)));
            src.setMeta(meta);
            src.setStatus(Encounter.EncounterStatus.ARRIVED);
            src.setClass_(new Coding("Test", "Test", "Test"));
            src.setId("Encounter/12345");
            src.setSubject(new Reference("Patient/12345"));
            src.setDiagnosis(List.of(new Encounter.DiagnosisComponent().setCondition(new Reference("Condition/12345")).setUse(new CodeableConcept(new Coding("Test", "Test", "")))));
            src.addModifierExtension(new Extension("http://example.org/fhir/StructureDefinition/some-modifier-extension", new StringType("value")));

            ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(src.copy(), Set.of(ENCOUNTER), Map.of("Encounter.subject", ExtractionId.of("Patient/12345", "Patient/123"), "Encounter.diagnosis", ExtractionId.of("Condition/12345")), new CopyTreeNode("dummy"));
            var redaction = integrationTestSetup.redaction();
            Encounter tgt = (Encounter) redaction.redact(wrapper);

            assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(src));
        }

        @Test
        void ignoresExistingDAR() throws RedactionException {
            Encounter src = new Encounter();
            Meta meta = new Meta();
            meta.setProfile(List.of(new CanonicalType(ENCOUNTER)));
            src.setMeta(meta);
            src.setStatus(Encounter.EncounterStatus.ARRIVED);
            Coding absent = new Coding();
            absent.setExtension(List.of(createAbsentReasonExtension("unknown")));
            src.setClass_(absent);
            src.setId("Encounter/12345");
            src.setSubject(new Reference("Patient/12345"));
            src.setDiagnosis(List.of(new Encounter.DiagnosisComponent().setCondition(new Reference("Condition/12345")).setUse(new CodeableConcept(new Coding("Test", "Test", "")))));

            ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(src.copy(), Set.of(ENCOUNTER), Map.of("Encounter.subject", ExtractionId.of("Patient/12345", "Patient/123"), "Encounter.diagnosis", ExtractionId.of("Condition/12345")), new CopyTreeNode("dummy"));
            var redaction = integrationTestSetup.redaction();
            Encounter tgt = (Encounter) redaction.redact(wrapper);
            System.out.println(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(src));
            System.out.println("Actual");

            System.out.println(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt));

            assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(src));
        }

        @Test
        void ignoresNonCanonicalDAR() throws RedactionException {
            Encounter src = new Encounter();
            Meta meta = new Meta();
            meta.setProfile(List.of(new CanonicalType(ENCOUNTER)));
            src.setMeta(meta);
            src.setStatus(Encounter.EncounterStatus.ARRIVED);
            Coding absent = new Coding();
            absent.setExtension(List.of(new Extension().setUrl("http://terminology.hl7.org/CodeSystem/data-absent-reason").setValue(new CodeType(("unknown")))));
            src.setClass_(absent);
            src.setId("Encounter/12345");
            src.setSubject(new Reference("Patient/12345"));
            src.setDiagnosis(List.of(new Encounter.DiagnosisComponent().setCondition(new Reference("Condition/12345")).setUse(new CodeableConcept(new Coding("Test", "Test", "")))));

            ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(src.copy(), Set.of(ENCOUNTER), Map.of("Encounter.subject", ExtractionId.of("Patient/12345", "Patient/123"), "Encounter.diagnosis", ExtractionId.of("Condition/12345")), new CopyTreeNode("dummy"));
            var redaction = integrationTestSetup.redaction();
            Encounter tgt = (Encounter) redaction.redact(wrapper);
            System.out.println(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(src));
            System.out.println("Actual");

            System.out.println(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt));

            assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(src));
        }

    }

    /**
     * A masked stub must still satisfy the cardinality of its type's required children (#1378), otherwise
     * strict parsers such as the .NET FHIR SDK reject the whole resource.
     */
    @Nested
    class MaskedStubRequiredChildrenTest {

        private static final String EKG = "https://gematik.de/fhir/isik/StructureDefinition/ISiKEKG";
        private static final String DYNAMISCHE_KOMPLIANCE = "https://www.medizininformatik-initiative.de/fhir/ext/modul-icu/StructureDefinition/dynamische-kompliance";
        private static final String EIN_AUSSCHLUSS_KRITERIUM = "https://www.medizininformatik-initiative.de/fhir/modul-studie/StructureDefinition/mii-pr-studie-ein-auschluss-kriterium";
        private static final String MEDICATION_STATEMENT = "https://www.medizininformatik-initiative.de/fhir/core/modul-medikation/StructureDefinition/MedicationStatement";
        private static final String PATHO_COMPOSITION = "https://www.medizininformatik-initiative.de/fhir/ext/modul-patho/StructureDefinition/mii-pr-patho-composition";

        private <T extends DomainResource> T redact(T resource, String profile) throws RedactionException {
            resource.setMeta(new Meta().addProfile(profile));
            @SuppressWarnings("unchecked")
            T redacted = (T) integrationTestSetup.redaction().redact(new ExtractionRedactionWrapper(resource, Set.of(profile), Map.of(), new CopyTreeNode("dummy")));
            return redacted;
        }

        private static boolean masked(Element element) {
            return element.hasExtension(DATA_ABSENT_REASON_URL);
        }

        /**
         * The profile tightens {@code value}, {@code system} and {@code code} of {@code boundsDuration} to 1..1,
         * while the base Duration type has them 0..1.
         */
        @Test
        void boundsDurationGetsProfileRequiredChildrenMasked() throws RedactionException {
            MedicationStatement medicationStatement = new MedicationStatement();
            medicationStatement.setId("bounds");
            Duration bounds = new Duration();
            bounds.setUnit("d");
            medicationStatement.addDosage().getTiming().getRepeat().setBounds(bounds);

            MedicationStatement tgt = redact(medicationStatement, MEDICATION_STATEMENT);

            Duration redacted = tgt.getDosageFirstRep().getTiming().getRepeat().getBoundsDuration();
            assertThat(masked(redacted.getValueElement())).isTrue();
            assertThat(masked(redacted.getSystemElement())).isTrue();
            assertThat(masked(redacted.getCodeElement())).isTrue();
        }

        @Test
        void absentRequiredComplexElementGetsMaskedRequiredChildren() throws RedactionException {
            Observation observation = new Observation();
            observation.setId("ekg");
            observation.setStatus(Observation.ObservationStatus.FINAL);
            observation.setCode(new CodeableConcept(new Coding("http://loinc.org", "11524-6", "EKG study")));
            observation.addComponent().setCode(new CodeableConcept(new Coding("urn:oid:2.16.840.1.113883.6.24", "131329", "MDC_ECG_ELEC_POTL_I")));

            Observation tgt = redact(observation, EKG);

            SampledData stub = tgt.getComponentFirstRep().getValueSampledData();
            assertThat(masked(stub)).isTrue();
            // origin, period and dimensions are required by the SampledData type itself, data only by the profile
            assertThat(masked(stub.getOrigin())).isTrue();
            assertThat(masked(stub.getPeriodElement())).isTrue();
            assertThat(masked(stub.getDimensionsElement())).isTrue();
            assertThat(masked(stub.getDataElement())).isTrue();
            assertThat(stub.hasFactor()).isFalse();
        }

        @Test
        void requiredChildWithFixedValueGetsThatValueInsteadOfMask() throws RedactionException {
            Composition composition = new Composition();
            composition.setId("patho");

            Composition tgt = redact(composition, PATHO_COMPOSITION);

            assertThat(masked(tgt.getText())).isTrue();
            assertThat(tgt.getText().getStatus()).isEqualTo(Narrative.NarrativeStatus.EXTENSIONS);
            assertThat(masked(tgt.getText().getStatusElement())).isFalse();
            assertThat(tgt.getText().hasDiv()).isTrue();
        }

        @Test
        void complexValueMatchingNoSliceGetsMaskedRequiredChildren() throws RedactionException {
            EvidenceVariable evidenceVariable = new EvidenceVariable();
            evidenceVariable.setId("criterion");
            evidenceVariable.addCharacteristic().setDefinition(new Expression().setDescription("test").setName("test-id")
                    .setLanguage("de").setExpression("test").setReference("http://example.org/fhir/test"));

            EvidenceVariable tgt = redact(evidenceVariable, EIN_AUSSCHLUSS_KRITERIUM);

            Expression stub = tgt.getCharacteristicFirstRep().getDefinitionExpression();
            assertThat(masked(stub)).isTrue();
            assertThat(masked(stub.getLanguageElement())).isTrue();
            assertThat(stub.hasDescription()).isFalse();
            assertThat(stub.hasExpression()).isFalse();
        }

        @Test
        void sampledDataMatchingNoSliceGetsMaskedRequiredChildren() throws RedactionException {
            Observation observation = new Observation();
            observation.setId("compliance");
            observation.setStatus(Observation.ObservationStatus.FINAL);
            observation.setValue(new SampledData().setOrigin(new Quantity().setValue(0)).setPeriod(10).setDimensions(1).setData("1 2 3"));

            Observation tgt = redact(observation, DYNAMISCHE_KOMPLIANCE);

            SampledData stub = tgt.getValueSampledData();
            assertThat(masked(stub)).isTrue();
            assertThat(masked(stub.getOrigin())).isTrue();
            assertThat(masked(stub.getPeriodElement())).isTrue();
            assertThat(masked(stub.getDimensionsElement())).isTrue();
            assertThat(stub.hasData()).isFalse();
        }

        @Test
        void backboneElementMatchingNoSliceGetsMaskedRequiredChildren() throws RedactionException {
            Observation observation = new Observation();
            observation.setId("bp3");
            observation.addComponent()
                    .setCode(new CodeableConcept(new Coding("http://loinc.org", "8867-4", "Heart rate")))
                    .setValue(new Quantity().setValue(60));

            Observation tgt = redact(observation, BLOOD_PRESSURE);

            assertThat(tgt.getComponent()).hasSize(1);
            Observation.ObservationComponentComponent stub = tgt.getComponentFirstRep();
            assertThat(masked(stub)).isTrue();
            assertThat(masked(stub.getCode())).isTrue();
            assertThat(stub.hasValue()).isFalse();
        }

        /**
         * An absent required element is masked even if the profile gives it a {@code pattern[x]}: TORCH must not
         * assert a code the source never had. Only the children of a masked stub take profile constants.
         */
        @Test
        void absentRequiredElementWithPatternIsMaskedNotFilled() throws RedactionException {
            Observation observation = new Observation();
            observation.setId("bp4");
            observation.setStatus(Observation.ObservationStatus.FINAL);

            Observation tgt = redact(observation, BLOOD_PRESSURE);

            CodeableConcept code = tgt.getCode();
            assertThat(masked(code)).isTrue();
            assertThat(code.getCoding()).singleElement().satisfies(coding -> {
                assertThat(masked(coding)).isTrue();
                assertThat(coding.hasCode()).isFalse();
            });
        }
    }
}
