package de.medizininformatikinitiative.torch.util;

import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import org.hl7.fhir.r4.model.*;
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
    void groupNoMustHave() {
        AnnotatedAttribute effective = new AnnotatedAttribute("Observation.effective", "Observation.effective", false);
        AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Test", "Observation", "Test", List.of(effective), List.of(), null);
        ProfileMustHaveChecker checker = new ProfileMustHaveChecker(integrationTestSetup.fhirContext());

        assertThat(checker.fulfilled(validObservation, group)).isTrue();
    }

    @Test
    void groupMustHave() {
        AnnotatedAttribute effective = new AnnotatedAttribute("Observation.id", "Observation.id", true);
        AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Test", "Observation", "Test", List.of(effective), List.of(), null);
        ProfileMustHaveChecker checker = new ProfileMustHaveChecker(integrationTestSetup.fhirContext());

        assertThat(checker.fulfilled(validObservation, group)).isTrue();
    }

    @Test
    void groupMustHaveFail() {
        Observation observation = new Observation();
        observation.setSubject(new Reference("Patient/123"));
        AnnotatedAttribute effective = new AnnotatedAttribute("Observation.id", "Observation.id", true);
        AnnotatedAttribute effective2 = new AnnotatedAttribute("Observation.subject", "Observation.subject", true);
        AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Test", "Observation", "Test", List.of(effective, effective2), List.of(), null);
        ProfileMustHaveChecker checker = new ProfileMustHaveChecker(integrationTestSetup.fhirContext());

        assertThat(checker.fulfilled(observation, group)).isFalse();
    }

    @Test
    void groupProfileFail() {
        Observation observation = new Observation();
        AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Test", "Observation", "Test", List.of(), List.of(), null);
        ProfileMustHaveChecker checker = new ProfileMustHaveChecker(integrationTestSetup.fhirContext());

        assertThat(checker.fulfilled(observation, group)).isFalse();
    }

    @Test
    void groupProfileIgnoredForPatient() {
        Patient patient = new Patient();
        AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Test", "Observation", "Test", List.of(), List.of(), null);
        ProfileMustHaveChecker checker = new ProfileMustHaveChecker(integrationTestSetup.fhirContext());

        assertThat(checker.fulfilled(patient, group)).isTrue();
    }


    @Test
    void shouldHandleNullProfileInMeta() {
        Observation observation = new Observation();
        observation.setMeta(null); // No meta data
        AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Test", "Observation", "Test", List.of(), List.of(), null);
        ProfileMustHaveChecker checker = new ProfileMustHaveChecker(integrationTestSetup.fhirContext());

        assertThat(checker.fulfilled(observation, group)).isFalse();
    }

    @Test
    void shouldHandleNullProfilesList() {
        Observation observation = new Observation();
        observation.setMeta(new Meta()); // Meta exists but has no profiles
        AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Test", "Observation", "Test", List.of(), List.of(), null);
        ProfileMustHaveChecker checker = new ProfileMustHaveChecker(integrationTestSetup.fhirContext());

        assertThat(checker.fulfilled(observation, group)).isFalse();
    }


    @Test
    void shouldHandleEmptyAnnotatedAttributes() {
        AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Test", "Observation", "Test", List.of(), List.of(), null); // Empty attributes
        ProfileMustHaveChecker checker = new ProfileMustHaveChecker(integrationTestSetup.fhirContext());

        assertThat(checker.fulfilled(validObservation, group)).isTrue();
    }

    @Test
    void shouldHandleNullResource() {
        AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Test", "Observation", "Test", List.of(), List.of(), null);
        ProfileMustHaveChecker checker = new ProfileMustHaveChecker(integrationTestSetup.fhirContext());

        assertThat(checker.fulfilled(null, group)).isFalse(); // Null resource should return false
    }

}
