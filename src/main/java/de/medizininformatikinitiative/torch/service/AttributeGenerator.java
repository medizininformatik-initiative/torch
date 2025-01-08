package de.medizininformatikinitiative.torch.service;


import de.medizininformatikinitiative.torch.management.StructureDefinitionHandler;
import de.medizininformatikinitiative.torch.model.crtdl.Attribute;
import org.hl7.fhir.r4.model.StructureDefinition;

import java.util.ArrayList;
import java.util.List;

public class AttributeGenerator {


    private StructureDefinitionHandler structureDefinitionHandler;

    AttributeGenerator(StructureDefinitionHandler structureDefinitionHandler) {
        this.structureDefinitionHandler = structureDefinitionHandler;
    }

    public List<Attribute> genModifierAttribute(String profileUrl) {
        List<Attribute> attributeList = new ArrayList<>();
        StructureDefinition.StructureDefinitionSnapshotComponent definition = structureDefinitionHandler.getSnapshot(profileUrl);
        definition.getElement().forEach(element -> {
            if (element.hasIsModifier()) {
                if (element.getIsModifier()) {
                    attributeList.add(new Attribute(element.getId(), false));
                }
            }
        });
        return attributeList;

    }

    ;


    //Gets Attribute Group
    //Loads Profile from Structure Definition Handler
    //get Standardattributes based on ResourceType
    // Extracts modifiers
    // Creates attribute list
    //constructs new attribute group with updated list
}
