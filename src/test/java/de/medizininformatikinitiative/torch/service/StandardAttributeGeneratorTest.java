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

        var standardAddedGroup = generate(attributeGroup, "Patient", compartmentManager);

        assertThat(standardAddedGroup.hasMustHave()).isTrue();
        assertThat(standardAddedGroup.attributes()).containsExactly(
                new AnnotatedAttribute("Patient.id", "Patient.id", "Patient.id", true),
                new AnnotatedAttribute("Patient.meta.profile", "Patient.meta.profile", "Patient.meta.profile", true)
        );
    }

    @Test
    void consent() {
        var attributeGroup = new AttributeGroup("test", "groupRef", List.of(new Attribute("Consent.identifier", false)), List.of());

        var standardAddedGroup = generate(attributeGroup, "Consent", compartmentManager);

        assertThat(standardAddedGroup.hasMustHave()).isTrue();
        assertThat(standardAddedGroup.attributes()).containsExactly(

                new AnnotatedAttribute("Consent.id", "Consent.id", "Consent.id", true),
                new AnnotatedAttribute("Consent.meta.profile", "Consent.meta.profile", "Consent.meta.profile", true)
        );
    }

    @Test
    void observation() {
        var attributeGroup = new AttributeGroup("test", "groupRef", List.of(new Attribute("Observation.identifier", false)), List.of());

        var standardAddedGroup = generate(attributeGroup, "Observation", compartmentManager);

        assertThat(standardAddedGroup.hasMustHave()).isTrue();
        assertThat(standardAddedGroup.attributes()).containsExactly(
                new AnnotatedAttribute("Observation.id", "Observation.id", "Observation.id", true),
                new AnnotatedAttribute("Observation.meta.profile", "Observation.meta.profile", "Observation.meta.profile", true)
        );
    }

    @Test
    void coreCase() {
        var attributeGroup = new AttributeGroup("test", "groupRef", List.of(), List.of());

        var standardAddedGroup = generate(attributeGroup, "Medication", compartmentManager);

        assertThat(standardAddedGroup.hasMustHave()).isTrue();
        assertThat(standardAddedGroup.attributes()).containsExactly(
                new AnnotatedAttribute("Medication.id", "Medication.id", "Medication.id", true),
                new AnnotatedAttribute("Medication.meta.profile", "Medication.meta.profile", "Medication.meta.profile", true)
        );

    }


    @Test
    void defaultCase() {
        var attributeGroup = new AttributeGroup("test", "groupRef", List.of(new Attribute("Condition.code", false)), List.of());

        var standardAddedGroup = generate(attributeGroup, "Condition", compartmentManager);

        assertThat(standardAddedGroup.hasMustHave()).isTrue();
        assertThat(standardAddedGroup.attributes()).contains(
                new AnnotatedAttribute("Condition.id", "Condition.id", "Condition.id", true),
                new AnnotatedAttribute("Condition.meta.profile", "Condition.meta.profile", "Condition.meta.profile", true)
        );

    }

}