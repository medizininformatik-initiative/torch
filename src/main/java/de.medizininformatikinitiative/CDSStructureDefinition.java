package de.medizininformatikinitiative;

import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.StructureDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class CDSStructureDefinition {

    StructureDefinition structureDefinition;

    private HashMap<String, ElementDefinition> elementMap = new HashMap<>();

    public CDSStructureDefinition(StructureDefinition structureDefinition) {
        this.structureDefinition = structureDefinition;
        for (ElementDefinition elementDefinition : structureDefinition.getSnapshot().getElement()) {
            elementMap.put(elementDefinition.getId(), elementDefinition);
        }
    }


    public StructureDefinition getStructureDefinition() {
        return structureDefinition;
    }

    public HashMap<String, ElementDefinition> getElementMap() {
        return elementMap;
    }
}
