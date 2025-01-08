package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.management.StructureDefinitionHandler;
import de.medizininformatikinitiative.torch.model.crtdl.AttributeGroup;
import org.hl7.fhir.r4.model.StructureDefinition;

public class AttributeGroupPopulator {

    private final StructureDefinitionHandler profileHandler;
    private final ModifierAttributeGenerator modifierAttributeGenerator;


    AttributeGroupPopulator(StructureDefinitionHandler structureDefinitionHandler) {
        this.profileHandler = structureDefinitionHandler;
        this.modifierAttributeGenerator = new ModifierAttributeGenerator(structureDefinitionHandler);
    }

    /**
     * @param group AttributeGroup to be populated with additional attributes
     * @return attribute group with added modifiers and standard attributes
     */
    AttributeGroup populate(AttributeGroup group) {
        StructureDefinition definition = profileHandler.getDefinition(group.groupReference());
        if (definition != null) {
            return modifierAttributeGenerator.generate(StandardAttributeGenerator.generate(group, definition.getType()));
        } else {
            throw new IllegalArgumentException("Unknown Profile: " + group.groupReference());
        }
    }
}
