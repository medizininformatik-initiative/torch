package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttributeGeneratorTest {

    private final IntegrationTestSetup itSetup = new IntegrationTestSetup();

    @Test
    void genModifierAttribute() {
        AttributeGenerator generator = new AttributeGenerator(itSetup.structureDefinitionHandler());

        assertThat(generator.genModifierAttribute("https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab")).hasSize(7);
    }

    @Test
    void genModifierAttributeInvalidProfile() {
        AttributeGenerator generator = new AttributeGenerator(itSetup.structureDefinitionHandler());
        assertThatThrownBy(() -> {
            generator.genModifierAttribute("invalid");
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown Profile: invalid");
    }


}