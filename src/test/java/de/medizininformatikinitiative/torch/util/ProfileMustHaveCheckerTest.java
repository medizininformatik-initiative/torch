package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.IFhirPath;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Property;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Test", "Observation", "Test", List.of(effective), List.of());
        ProfileMustHaveChecker checker = new ProfileMustHaveChecker(integrationTestSetup.fhirContext());

        assertThat(checker.fulfilled(validObservation, group)).isTrue();
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
    void mustHave_withDataAbsentReason_shouldReturnFalse() {
        Observation observation = new Observation();
        Meta meta = new Meta();
        meta.setProfile(List.of(new CanonicalType("Test")));
        observation.setMeta(meta);
        observation.setId("123");

        // Set effective with a DAR extension
        DateTimeType effective = new DateTimeType();
        effective.addExtension("http://hl7.org/fhir/StructureDefinition/data-absent-reason", new CodeType("unknown"));
        observation.setEffective(effective);

        AnnotatedAttribute effectiveAttr = new AnnotatedAttribute("Observation.effective", "Observation.effective", true);
        AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Test", "Observation", "Test", List.of(effectiveAttr), List.of());
        ProfileMustHaveChecker checker = new ProfileMustHaveChecker(integrationTestSetup.fhirContext());

        assertThat(checker.fulfilled(observation, group)).isFalse();
    }

    @Test
    void mustHave_withDataAbsentReasonTerminologyUrl_shouldReturnFalse() {
        Observation observation = new Observation();
        Meta meta = new Meta();
        meta.setProfile(List.of(new CanonicalType("Test")));
        observation.setMeta(meta);
        observation.setId("123");

        DateTimeType effective = new DateTimeType();
        effective.addExtension("http://terminology.hl7.org/CodeSystem/data-absent-reason", new CodeType("unknown"));
        observation.setEffective(effective);

        AnnotatedAttribute effectiveAttr = new AnnotatedAttribute("Observation.effective", "Observation.effective", true);
        AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Test", "Observation", "Test", List.of(effectiveAttr), List.of());
        ProfileMustHaveChecker checker = new ProfileMustHaveChecker(integrationTestSetup.fhirContext());

        assertThat(checker.fulfilled(observation, group)).isFalse();
    }

    @Test
    void mustHave_withUnrelatedExtension_shouldReturnTrue() {
        Observation observation = new Observation();
        Meta meta = new Meta();
        meta.setProfile(List.of(new CanonicalType("Test")));
        observation.setMeta(meta);
        observation.setId("123");

        DateTimeType effective = new DateTimeType("2024-01-01");
        effective.addExtension("http://some.other/extension", new CodeType("value"));
        observation.setEffective(effective);

        AnnotatedAttribute effectiveAttr = new AnnotatedAttribute("Observation.effective", "Observation.effective", true);
        AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Test", "Observation", "Test", List.of(effectiveAttr), List.of());
        ProfileMustHaveChecker checker = new ProfileMustHaveChecker(integrationTestSetup.fhirContext());

        assertThat(checker.fulfilled(observation, group)).isTrue();
    }

    @Test
    void mustHave_backboneElement_withDataAbsentReason_shouldReturnFalse() {
        Observation observation = new Observation();
        Meta meta = new Meta();
        meta.setProfile(List.of(new CanonicalType("Test")));
        observation.setMeta(meta);
        observation.setId("123");

        Observation.ObservationComponentComponent component = new Observation.ObservationComponentComponent();
        component.addExtension("http://hl7.org/fhir/StructureDefinition/data-absent-reason", new CodeType("unknown"));
        observation.addComponent(component);

        AnnotatedAttribute componentAttr = new AnnotatedAttribute("Observation.component", "Observation.component", true);
        AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Test", "Observation", "Test", List.of(componentAttr), List.of());
        ProfileMustHaveChecker checker = new ProfileMustHaveChecker(integrationTestSetup.fhirContext());

        assertThat(checker.fulfilled(observation, group)).isFalse();
    }

    @Test
    void shouldHandleNullGroup() {
        ProfileMustHaveChecker checker = new ProfileMustHaveChecker(integrationTestSetup.fhirContext());

        assertThat(checker.fulfilled(validObservation, (AnnotatedAttributeGroup) null)).isFalse();
    }

    @Test
    void mustHave_baseNotElement_shouldReturnTrue() {
        IFhirPath mockFhirPath = mock(IFhirPath.class);
        FhirContext mockFhirContext = mock(FhirContext.class);
        when(mockFhirContext.newFhirPath()).thenReturn(mockFhirPath);
        Base rawBase = new Base() {
            @Override
            public String fhirType() {
                return "Base";
            }

            @Override
            public Base copy() {
                return this;
            }

            @Override
            public boolean equalsDeep(Base other) {
                return this == other;
            }

            @Override
            public boolean equalsShallow(Base other) {
                return this == other;
            }

            @Override
            public String getIdBase() {
                return "";
            }

            @Override
            public void setIdBase(String value) {

            }

            @Override
            public boolean isEmpty() {
                return false;
            }

            @Override
            protected void listChildren(List<Property> children) {
            }

        };

        when(mockFhirPath.evaluate(any(), anyString(), eq(Base.class))).thenReturn(List.of(rawBase));

        ProfileMustHaveChecker checker = new ProfileMustHaveChecker(mockFhirContext);
        AnnotatedAttribute attr = new AnnotatedAttribute("Observation.effective", "Observation.effective", true);

        assertThat(checker.fulfilled(validObservation, attr)).isTrue();
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

}
