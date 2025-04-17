package de.medizininformatikinitiative.torch.util;

import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileMustHaveCheckerTest {

    private static final IntegrationTestSetup INTEGRATION_TEST_SETUP = new IntegrationTestSetup();
    private static final Observation src = new Observation();

    @BeforeAll
    static void setup() {
        Meta meta = new Meta();
        meta.setProfile(List.of(new CanonicalType("Invalid"), new CanonicalType("Test")));
        src.setMeta(meta);
        src.setSubject(new Reference("Patient/123"));
        src.setId("1243");
    }

    @Test
    void groupNoMustHave() {
        AnnotatedAttribute effective = new AnnotatedAttribute("Observation.effective", "Observation.effective", "Observation.effective", false);
        AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Test", "Test", List.of(effective), List.of(), null);
        ProfileMustHaveChecker checker = new ProfileMustHaveChecker(INTEGRATION_TEST_SETUP.fhirContext());

        assertThat(checker.fulfilled(src, group)).isTrue();
    }

    @Test
    void groupMustHave() {
        AnnotatedAttribute effective = new AnnotatedAttribute("Observation.id", "Observation.id", "Observation.id", true);
        AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Test", "Test", List.of(effective), List.of(), null);
        ProfileMustHaveChecker checker = new ProfileMustHaveChecker(INTEGRATION_TEST_SETUP.fhirContext());

        assertThat(checker.fulfilled(src, group)).isTrue();
    }

    @Test
    void groupMustHaveFail() {
        Observation src = new Observation();
        src.setSubject(new Reference("Patient/123"));
        AnnotatedAttribute effective = new AnnotatedAttribute("Observation.id", "Observation.id", "Observation.id", true);
        AnnotatedAttribute effective2 = new AnnotatedAttribute("Observation.subject", "Observation.subject", "Observation.subject", true);
        AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Test", "Test", List.of(effective, effective2), List.of(), null);
        ProfileMustHaveChecker checker = new ProfileMustHaveChecker(INTEGRATION_TEST_SETUP.fhirContext());

        assertThat(checker.fulfilled(src, group)).isFalse();
    }

    @Test
    void groupProfileFail() {
        Observation src = new Observation();
        AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Test", "Test", List.of(), List.of(), null);
        ProfileMustHaveChecker checker = new ProfileMustHaveChecker(INTEGRATION_TEST_SETUP.fhirContext());

        assertThat(checker.fulfilled(src, group)).isFalse();
    }

    @Test
    void groupProfileIgnoredForPatient() {
        Patient src = new Patient();
        AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Test", "Test", List.of(), List.of(), null);
        ProfileMustHaveChecker checker = new ProfileMustHaveChecker(INTEGRATION_TEST_SETUP.fhirContext());

        assertThat(checker.fulfilled(src, group)).isTrue();
    }


    @Test
    void shouldHandleNullProfileInMeta() {
        Observation src = new Observation();
        src.setMeta(null); // No meta data
        AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Test", "Test", List.of(), List.of(), null);
        ProfileMustHaveChecker checker = new ProfileMustHaveChecker(INTEGRATION_TEST_SETUP.fhirContext());

        assertThat(checker.fulfilled(src, group)).isFalse();
    }

    @Test
    void shouldHandleNullProfilesList() {
        Observation src = new Observation();
        src.setMeta(new Meta()); // Meta exists but has no profiles
        AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Test", "Test", List.of(), List.of(), null);
        ProfileMustHaveChecker checker = new ProfileMustHaveChecker(INTEGRATION_TEST_SETUP.fhirContext());

        assertThat(checker.fulfilled(src, group)).isFalse();
    }


    @Test
    void shouldHandleEmptyAnnotatedAttributes() {
        AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Test", "Test", List.of(), List.of(), null); // Empty attributes
        ProfileMustHaveChecker checker = new ProfileMustHaveChecker(INTEGRATION_TEST_SETUP.fhirContext());

        assertThat(checker.fulfilled(src, group)).isTrue();
    }

    @Test
    void shouldHandleNullResource() {
        AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Test", "Test", List.of(), List.of(), null);
        ProfileMustHaveChecker checker = new ProfileMustHaveChecker(INTEGRATION_TEST_SETUP.fhirContext());

        assertThat(checker.fulfilled(null, group)).isFalse(); // Null resource should return false
    }

}
