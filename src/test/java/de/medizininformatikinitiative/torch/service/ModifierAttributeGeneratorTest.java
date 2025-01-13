package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.model.crtdl.Attribute;
import de.medizininformatikinitiative.torch.model.crtdl.AttributeGroup;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModifierAttributeGeneratorTest {

    private final IntegrationTestSetup itSetup = new IntegrationTestSetup();
    private final ModifierAttributeGenerator generator = new ModifierAttributeGenerator(itSetup.structureDefinitionHandler());

    @Test
    void generateBaseElements() {

        AttributeGroup group = new AttributeGroup(
                "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab"
                , List.of(), List.of());

        //TODO: Modifier enscheiden ob hinzufügen oder nicht
        assertThat(generator.generate(group).attributes()).containsExactly(
                new Attribute("Observation.implicitRules", false),
                new Attribute("Observation.modifierExtension", false),
                new Attribute("Observation.status", true)
        );
    }

    @Test
    void generate() {
        AttributeGroup group = new AttributeGroup(
                "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab"
                , List.of(new Attribute("Observation.referenceRange.id", false)), List.of());


        assertThat(generator.generate(group).attributes()).containsExactly(
                new Attribute("Observation.implicitRules", false),
                new Attribute("Observation.modifierExtension", false),
                new Attribute("Observation.status", true),
                new Attribute("Observation.referenceRange.modifierExtension", false)
        );
    }


    @Test
    void generateInvalidProfile() {
        AttributeGroup group = new AttributeGroup("invalid", List.of(), List.of());

        assertThatThrownBy(() -> {
            generator.generate(group);
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown Profile: invalid");
    }
}