package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.model.crtdl.Attribute;
import de.medizininformatikinitiative.torch.model.crtdl.AttributeGroup;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static de.medizininformatikinitiative.torch.service.StandardAttributeGenerator.generate;
import static org.assertj.core.api.Assertions.assertThat;

class StandardAttributeGeneratorTest {


    CompartmentManager compartmentManager = new CompartmentManager("compartmentdefinition-patient.json");

    StandardAttributeGeneratorTest() throws IOException {
    }

    @Test
    void patient() {
        var attributeGroup = new AttributeGroup("test", "groupRef", List.of(new Attribute("Patient.name", false)), List.of());

        var standardAddedGroup = generate(attributeGroup, "Patient", compartmentManager, "patientGroup");

        assertThat(standardAddedGroup.hasMustHave()).isFalse();
        assertThat(standardAddedGroup.attributes()).containsExactly(
                new AnnotatedAttribute("Patient.id", "Patient.id", "Patient.id", false),
                new AnnotatedAttribute("Patient.meta.profile", "Patient.meta.profile", "Patient.meta.profile", false)
        );
    }

    @Test
    void consent() {
        var attributeGroup = new AttributeGroup("test", "groupRef", List.of(new Attribute("Consent.identifier", false)), List.of());

        var standardAddedGroup = generate(attributeGroup, "Consent", compartmentManager, "patientGroup");

        assertThat(standardAddedGroup.hasMustHave()).isFalse();
        assertThat(standardAddedGroup.attributes()).containsExactly(

                new AnnotatedAttribute("Consent.id", "Consent.id", "Consent.id", false),
                new AnnotatedAttribute("Consent.meta.profile", "Consent.meta.profile", "Consent.meta.profile", false),
                new AnnotatedAttribute("Consent.patient", "Consent.patient", "Consent.patient", false, List.of("patientGroup"))
        );
    }

    @Test
    void observation() {
        var attributeGroup = new AttributeGroup("test", "groupRef", List.of(new Attribute("Observation.identifier", false)), List.of());

        var standardAddedGroup = generate(attributeGroup, "Observation", compartmentManager, "group1");

        assertThat(standardAddedGroup.hasMustHave()).isFalse();
        assertThat(standardAddedGroup.attributes()).containsExactly(
                new AnnotatedAttribute("Observation.id", "Observation.id", "Observation.id", false),
                new AnnotatedAttribute("Observation.meta.profile", "Observation.meta.profile", "Observation.meta.profile", false),
                new AnnotatedAttribute("Observation.subject", "Observation.subject", "Observation.subject", false, List.of("group1"))
        );
    }

    @Test
    void coreCase() {
        var attributeGroup = new AttributeGroup("test", "groupRef", List.of(), List.of());

        var standardAddedGroup = generate(attributeGroup, "Medication", compartmentManager, "patientGroup");

        assertThat(standardAddedGroup.hasMustHave()).isFalse();
        assertThat(standardAddedGroup.attributes()).containsExactly(
                new AnnotatedAttribute("Medication.id", "Medication.id", "Medication.id", false),
                new AnnotatedAttribute("Medication.meta.profile", "Medication.meta.profile", "Medication.meta.profile", false)
        );

    }


    @Test
    void defaultCase() {
        var attributeGroup = new AttributeGroup("test", "groupRef", List.of(new Attribute("Condition.code", false)), List.of());

        var standardAddedGroup = generate(attributeGroup, "Condition", compartmentManager, "patientGroup");

        assertThat(standardAddedGroup.hasMustHave()).isFalse();
        assertThat(standardAddedGroup.attributes()).contains(
                new AnnotatedAttribute("Condition.id", "Condition.id", "Condition.id", false),
                new AnnotatedAttribute("Condition.meta.profile", "Condition.meta.profile", "Condition.meta.profile", false)
        );

    }

}