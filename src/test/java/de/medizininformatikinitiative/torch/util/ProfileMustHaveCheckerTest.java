package de.medizininformatikinitiative.torch.util;

import de.medizininformatikinitiative.torch.diagnostics.MustHaveEvaluation;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileMustHaveCheckerTest {

    private static final Observation validObservation = new Observation();
    private static IntegrationTestSetup integrationTestSetup;

    @BeforeAll
    static void setup() throws IOException {
        integrationTestSetup = new IntegrationTestSetup();
        Meta meta = new Meta();
        meta.setProfile(List.of(new CanonicalType("Invalid"), new CanonicalType("Test|123")));
        validObservation.setMeta(meta);
        validObservation.setSubject(new Reference("Patient/123"));
        validObservation.setId("1243");
    }

    @Test
    void shouldReturnNotApplicable_whenResourceIsNotDomainResource() {
        Parameters parameters = new Parameters();
        AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Test", "Observation", "Test", List.of(), List.of());
        ProfileMustHaveChecker checker = new ProfileMustHaveChecker(integrationTestSetup.fhirContext());

        MustHaveEvaluation eval = checker.evaluateFirst(parameters, group);

        assertThat(eval.applicable()).isFalse();
    }

    @Test
    void groupNoMustHave() {
        AnnotatedAttribute effective = new AnnotatedAttribute("Observation.effective", "Observation.effective", false);
        AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Test", "Observation", "Test", List.of(effective), List.of());
        ProfileMustHaveChecker checker = new ProfileMustHaveChecker(integrationTestSetup.fhirContext());

        assertThat(checker.fulfilled(validObservation, group)).isTrue();
    }

    @Test
    void groupNullFails() {

        ProfileMustHaveChecker checker = new ProfileMustHaveChecker(integrationTestSetup.fhirContext());

        assertThat(checker.fulfilled((Resource) validObservation, null)).isFalse();
    }

    @Test
    void groupMustHave() {
        AnnotatedAttribute effective = new AnnotatedAttribute("Observation.id", "Observation.id", true);
        AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Test", "Observation", "Test", List.of(effective), List.of());
        ProfileMustHaveChecker checker = new ProfileMustHaveChecker(integrationTestSetup.fhirContext());

        assertThat(checker.fulfilled(validObservation, group)).isTrue();
    }

    @Test
    void groupMustHaveFail() {
        Observation observation = new Observation();
        observation.setSubject(new Reference("Patient/123"));
        AnnotatedAttribute effective = new AnnotatedAttribute("Observation.id", "Observation.id", true);
        AnnotatedAttribute effective2 = new AnnotatedAttribute("Observation.subject", "Observation.subject", true);
        AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Test", "Observation", "Test", List.of(effective, effective2), List.of());
        ProfileMustHaveChecker checker = new ProfileMustHaveChecker(integrationTestSetup.fhirContext());

        assertThat(checker.fulfilled(observation, group)).isFalse();
    }

    @Test
    void groupProfileFail() {
        Observation observation = new Observation();
        AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Test", "Observation", "Test", List.of(), List.of());
        ProfileMustHaveChecker checker = new ProfileMustHaveChecker(integrationTestSetup.fhirContext());

        assertThat(checker.fulfilled(observation, group)).isFalse();
    }

    @Test
    void groupProfileIgnoredForPatient() {
        Patient patient = new Patient();
        AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Test", "Observation", "Test", List.of(), List.of());
        ProfileMustHaveChecker checker = new ProfileMustHaveChecker(integrationTestSetup.fhirContext());

        assertThat(checker.fulfilled(patient, group)).isTrue();
    }


    @Test
    void shouldHandleNullProfileInMeta() {
        Observation observation = new Observation();
        observation.setMeta(null); // No meta data
        AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Test", "Observation", "Test", List.of(), List.of());
        ProfileMustHaveChecker checker = new ProfileMustHaveChecker(integrationTestSetup.fhirContext());

        assertThat(checker.fulfilled(observation, group)).isFalse();
    }

    @Test
    void shouldHandleNullProfilesList() {
        Observation observation = new Observation();
        observation.setMeta(new Meta()); // Meta exists but has no profiles
        AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Test", "Observation", "Test", List.of(), List.of());
        ProfileMustHaveChecker checker = new ProfileMustHaveChecker(integrationTestSetup.fhirContext());

        assertThat(checker.fulfilled(observation, group)).isFalse();
    }


    @Test
    void shouldHandleEmptyAnnotatedAttributes() {
        AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Test", "Observation", "Test", List.of(), List.of()); // Empty attributes
        ProfileMustHaveChecker checker = new ProfileMustHaveChecker(integrationTestSetup.fhirContext());

        assertThat(checker.fulfilled(validObservation, group)).isTrue();
    }

    @Test
    void shouldHandleNullResource() {
        AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Test", "Observation", "Test", List.of(), List.of());
        ProfileMustHaveChecker checker = new ProfileMustHaveChecker(integrationTestSetup.fhirContext());

        assertThat(checker.fulfilled(null, group)).isFalse(); // Null resource should return false
    }

    @Test
    void shouldSkipNonMustHaveAttributes_andStillReturnOk() {
        // in-scope resource
        Observation observation = new Observation();
        observation.setMeta(validObservation.getMeta());
        observation.setId("123"); // makes Observation.id present
        observation.setSubject(new Reference("Patient/123"));

        // first attribute is NOT must-have -> should hit `continue`
        AnnotatedAttribute notMustHave = new AnnotatedAttribute("Observation.status", "Observation.status", false);

        // second attribute IS must-have and is fulfilled -> loop continues and returns ok
        AnnotatedAttribute mustHaveId = new AnnotatedAttribute("Observation.id", "Observation.id", true);

        AnnotatedAttributeGroup group = new AnnotatedAttributeGroup(
                "Test", "Observation", "Test",
                List.of(notMustHave, mustHaveId),
                List.of()
        );

        ProfileMustHaveChecker checker = new ProfileMustHaveChecker(integrationTestSetup.fhirContext());

        MustHaveEvaluation eval = checker.evaluateFirst(observation, group);

        assertThat(eval.applicable()).isTrue();
        assertThat(eval.fulfilled()).isTrue();
        assertThat(eval.firstViolated()).isEmpty();
    }

    @Test
    void shouldSkipNonMustHaveAttributes_andReturnFirstMustHaveViolation() {
        // in-scope resource: has meta profile "Test|123"
        Observation observation = new Observation();
        observation.setMeta(validObservation.getMeta());
        observation.setSubject(new Reference("Patient/123"));
        // deliberately DO NOT set id -> Observation.id must-have will fail

        AnnotatedAttribute notMustHave = new AnnotatedAttribute("Observation.status", "Observation.status", false);

        AnnotatedAttribute mustHaveId = new AnnotatedAttribute("Observation.id", "Observation.id", true);
        AnnotatedAttribute mustHaveSubject = new AnnotatedAttribute("Observation.subject", "Observation.subject", true);

        AnnotatedAttributeGroup group = new AnnotatedAttributeGroup(
                "Test", "Observation", "Test",
                List.of(notMustHave, mustHaveId, mustHaveSubject),
                List.of()
        );

        ProfileMustHaveChecker checker = new ProfileMustHaveChecker(integrationTestSetup.fhirContext());

        MustHaveEvaluation eval = checker.evaluateFirst(observation, group);

        assertThat(eval.applicable()).isTrue();
        assertThat(eval.fulfilled()).isFalse();
        assertThat(eval.firstViolated()).contains(mustHaveId); // mustHaveId is first must-have and fails
    }

}
