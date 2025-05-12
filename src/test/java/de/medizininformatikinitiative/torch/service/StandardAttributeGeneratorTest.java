package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.model.crtdl.Attribute;
import de.medizininformatikinitiative.torch.model.crtdl.AttributeGroup;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StandardAttributeGeneratorTest {

    private StandardAttributeGenerator standardAttributeGenerator;

    @BeforeEach
    void setUp() throws IOException {
        var compartmentManager = new CompartmentManager("compartmentdefinition-patient.json");
        var integrationTestSetup = new IntegrationTestSetup();
        standardAttributeGenerator = new StandardAttributeGenerator(compartmentManager, integrationTestSetup.structureDefinitionHandler());
    }

    @Test
    void patient() {
        var attributeGroup = new AttributeGroup("test", "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/PatientPseudonymisiert", List.of(new Attribute("Patient.name", false)), List.of());

        var standardAddedGroup = standardAttributeGenerator.generate(attributeGroup, "patientGroup");

        assertThat(standardAddedGroup.hasMustHave()).isFalse();
        assertThat(standardAddedGroup.attributes()).containsExactly(
                new AnnotatedAttribute("Patient.id", "Patient.id", "Patient.id", false),
                new AnnotatedAttribute("Patient.meta.profile", "Patient.meta.profile", "Patient.meta.profile", false)
        );
    }

    @Test
    void consent() {
        var attributeGroup = new AttributeGroup("test", "https://www.medizininformatik-initiative.de/fhir/modul-consent/StructureDefinition/mii-pr-consent-einwilligung", List.of(new Attribute("Consent.identifier", false)), List.of());

        var standardAddedGroup = standardAttributeGenerator.generate(attributeGroup, "patientGroup");

        assertThat(standardAddedGroup.hasMustHave()).isFalse();
        assertThat(standardAddedGroup.attributes()).containsExactly(
                new AnnotatedAttribute("Consent.id", "Consent.id", "Consent.id", false),
                new AnnotatedAttribute("Consent.meta.profile", "Consent.meta.profile", "Consent.meta.profile", false),
                new AnnotatedAttribute("Consent.patient", "Consent.patient", "Consent.patient", false, List.of("patientGroup"))
        );
    }

    @Test
    void observation() {
        var attributeGroup = new AttributeGroup("test", "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab", List.of(new Attribute("Observation.identifier", false)), List.of());

        var standardAddedGroup = standardAttributeGenerator.generate(attributeGroup, "group1");

        assertThat(standardAddedGroup.hasMustHave()).isFalse();
        assertThat(standardAddedGroup.attributes()).containsExactly(
                new AnnotatedAttribute("Observation.id", "Observation.id", "Observation.id", false),
                new AnnotatedAttribute("Observation.meta.profile", "Observation.meta.profile", "Observation.meta.profile", false),
                new AnnotatedAttribute("Observation.subject", "Observation.subject", "Observation.subject", false, List.of("group1"))
        );
    }

    @Test
    void coreCase() {
        var attributeGroup = new AttributeGroup("test", "https://www.medizininformatik-initiative.de/fhir/core/modul-medikation/StructureDefinition/Medication", List.of(), List.of());

        var standardAddedGroup = standardAttributeGenerator.generate(attributeGroup, "patientGroup");

        assertThat(standardAddedGroup.hasMustHave()).isFalse();
        assertThat(standardAddedGroup.attributes()).containsExactly(
                new AnnotatedAttribute("Medication.id", "Medication.id", "Medication.id", false),
                new AnnotatedAttribute("Medication.meta.profile", "Medication.meta.profile", "Medication.meta.profile", false)
        );

    }

    @Test
    void defaultCase() {
        var attributeGroup = new AttributeGroup("test", "https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose", List.of(new Attribute("Condition.code", false)), List.of());

        var standardAddedGroup = standardAttributeGenerator.generate(attributeGroup, "patientGroup");

        assertThat(standardAddedGroup.hasMustHave()).isFalse();
        assertThat(standardAddedGroup.attributes()).containsExactly(
                new AnnotatedAttribute("Condition.id", "Condition.id", "Condition.id", false),
                new AnnotatedAttribute("Condition.meta.profile", "Condition.meta.profile", "Condition.meta.profile", false),
                new AnnotatedAttribute("Condition.subject", "Condition.subject", "Condition.subject", false, List.of("patientGroup"))
        );
    }
}
