package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.model.crtdl.Attribute;
import de.medizininformatikinitiative.torch.model.crtdl.AttributeGroup;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import org.junit.jupiter.api.Test;

import static de.medizininformatikinitiative.torch.testUtil.FhirTestHelper.emptyAttributeGroup;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttributeGroupPopulatorTest {
    private final IntegrationTestSetup itSetup = new IntegrationTestSetup();
    private final AttributeGroupPopulator generator = new AttributeGroupPopulator(itSetup.structureDefinitionHandler());

    @Test
    void generate() {

        AttributeGroup group = emptyAttributeGroup(
                "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab");

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
        AttributeGroup group = emptyAttributeGroup(
                "unkown");
        assertThatThrownBy(() -> {
            generator.populate(group);
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown Profile: unkown");
    }
}