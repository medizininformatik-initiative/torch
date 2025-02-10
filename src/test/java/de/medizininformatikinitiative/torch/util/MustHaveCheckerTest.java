package de.medizininformatikinitiative.torch.util;

import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MustHaveCheckerTest {

    private static final IntegrationTestSetup INTEGRATION_TEST_SETUP = new IntegrationTestSetup();

    @Test
    void groupNoMustHave() {
        Observation src = new Observation();
        src.setSubject(new Reference("Patient/123"));
        AnnotatedAttribute effective = new AnnotatedAttribute("Observation.effective", "Observation.effective", "Observation.effective", false);
        AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Test", "Test", List.of(effective), List.of());
        MustHaveChecker checker = new MustHaveChecker(INTEGRATION_TEST_SETUP.fhirContext());

        assertThat(checker.fulfilled(src, group)).isTrue();
    }

    @Test
    void groupMustHave() {
        Observation src = new Observation();
        src.setSubject(new Reference("Patient/123"));
        src.setId("1243");
        AnnotatedAttribute effective = new AnnotatedAttribute("Observation.id", "Observation.id", "Observation.id", true);
        AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Test", "Test", List.of(effective), List.of());
        MustHaveChecker checker = new MustHaveChecker(INTEGRATION_TEST_SETUP.fhirContext());

        assertThat(checker.fulfilled(src, group)).isTrue();
    }

    @Test
    void groupMustHaveFail() {
        Observation src = new Observation();
        src.setSubject(new Reference("Patient/123"));
        AnnotatedAttribute effective = new AnnotatedAttribute("Observation.id", "Observation.id", "Observation.id", true);
        AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Test", "Test", List.of(effective), List.of());
        MustHaveChecker checker = new MustHaveChecker(INTEGRATION_TEST_SETUP.fhirContext());

        assertThat(checker.fulfilled(src, group)).isFalse();
    }
}