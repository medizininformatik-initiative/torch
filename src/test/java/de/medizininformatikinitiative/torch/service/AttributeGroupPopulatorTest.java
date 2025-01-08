package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.model.crtdl.Attribute;
import de.medizininformatikinitiative.torch.model.crtdl.AttributeGroup;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttributeGroupPopulatorTest {
    private final IntegrationTestSetup itSetup = new IntegrationTestSetup();


    @Test
    void generate() {
        AttributeGroupPopulator generator = new AttributeGroupPopulator(itSetup.structureDefinitionHandler());
        AttributeGroup group = new AttributeGroup(
                "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab"
                , List.of(), List.of());


        assertThat(generator.populate(group).attributes()).hasSize(10);
        assertThat(generator.populate(group).attributes()).containsExactly(
                new Attribute("Observation.id", true),
                new Attribute("Observation.meta.profile", true),
                new Attribute("Observation.subject.reference", true),
                new Attribute("Observation.implicitRules", false),
                new Attribute("Observation.modifierExtension", false),
                new Attribute("Observation.identifier:analyseBefundCode.use", false),
                new Attribute("Observation.status", true),
                new Attribute("Observation.value[x]:valueQuantity.comparator", false),
                new Attribute("Observation.referenceRange.modifierExtension", false),
                new Attribute("Observation.component.modifierExtension", false)
        );
    }

    @Test
    void generateInvalidProfile() {
        AttributeGroupPopulator generator = new AttributeGroupPopulator(itSetup.structureDefinitionHandler());
        AttributeGroup group = new AttributeGroup("invalid", List.of(), List.of());

        assertThatThrownBy(() -> {
            generator.populate(group);
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown Profile: invalid");
    }
}