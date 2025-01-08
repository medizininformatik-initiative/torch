package de.medizininformatikinitiative.torch.service;


import de.medizininformatikinitiative.torch.management.StructureDefinitionHandler;
import de.medizininformatikinitiative.torch.model.crtdl.Attribute;
import de.medizininformatikinitiative.torch.model.crtdl.AttributeGroup;
import org.hl7.fhir.r4.model.StructureDefinition;

import java.util.ArrayList;
import java.util.List;

public class ModifierAttributeGenerator {
    private final StructureDefinitionHandler structureDefinitionHandler;

    ModifierAttributeGenerator(StructureDefinitionHandler structureDefinitionHandler) {
        this.structureDefinitionHandler = structureDefinitionHandler;
    }

    /**
     * Adds modifier attributes to a attribute group according to the profile in the group reference.
     * Throws illegal ArgumentException when unknown profile
     *
     * @param group group to which modifiers should be added
     * @return group with added modifiers
     */
    public AttributeGroup generate(AttributeGroup group) {
        List<Attribute> attributeList = new ArrayList<>();
        StructureDefinition.StructureDefinitionSnapshotComponent definition = structureDefinitionHandler.getSnapshot(group.groupReference());
        definition.getElement().forEach(element -> {
            if (element.hasIsModifier()) {
                if (element.getIsModifier()) {
                    //Must have false, since modifiers don't always exist.
                    attributeList.add(new Attribute(element.getId(), false));
                }
            }
        });
        return group.addAttributes(attributeList);
    }
}
