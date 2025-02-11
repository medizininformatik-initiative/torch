package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.model.crtdl.Attribute;
import de.medizininformatikinitiative.torch.model.crtdl.AttributeGroup;
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
                new Attribute("Patient.name", false),
                new Attribute("Patient.id", true),
                new Attribute("Patient.meta.profile", true)
        );
    }

    @Test
    void consent() {
        var attributeGroup = new AttributeGroup("test", "groupRef", List.of(new Attribute("Consent.identifier", false)), List.of());

        var standardAddedGroup = generate(attributeGroup, "Consent", compartmentManager);

        assertThat(standardAddedGroup.hasMustHave()).isTrue();
        assertThat(standardAddedGroup.attributes()).containsExactly(
                new Attribute("Consent.identifier", false),
                new Attribute("Consent.id", true),
                new Attribute("Consent.meta.profile", true),
                new Attribute("Consent.patient.reference", true)
        );
    }

    @Test
    void observation() {
        var attributeGroup = new AttributeGroup("test", "groupRef", List.of(new Attribute("Observation.identifier", false)), List.of());

        var standardAddedGroup = generate(attributeGroup, "Observation", compartmentManager);

        assertThat(standardAddedGroup.hasMustHave()).isTrue();
        assertThat(standardAddedGroup.attributes()).containsExactly(
                new Attribute("Observation.identifier", false),
                new Attribute("Observation.id", true),
                new Attribute("Observation.meta.profile", true),
                new Attribute("Observation.subject", true)
        );
    }

    @Test
    void coreCase() {
        var attributeGroup = new AttributeGroup("test", "groupRef", List.of(), List.of());

        var standardAddedGroup = generate(attributeGroup, "Medication", compartmentManager);

        assertThat(standardAddedGroup.hasMustHave()).isTrue();
        assertThat(standardAddedGroup.attributes()).containsExactly(
                new Attribute("Medication.id", true),
                new Attribute("Medication.meta.profile", true)
        );

    }


    @Test
    void defaultCase() {
        var attributeGroup = new AttributeGroup("test", "groupRef", List.of(new Attribute("Condition.code", false)), List.of());

        var standardAddedGroup = generate(attributeGroup, "Condition", compartmentManager);

        assertThat(standardAddedGroup.hasMustHave()).isTrue();
        assertThat(standardAddedGroup.attributes()).containsExactly(
                new Attribute("Condition.code", false),
                new Attribute("Condition.id", true),
                new Attribute("Condition.meta.profile", true),
                new Attribute("Condition.subject", true)
        );

    }

}