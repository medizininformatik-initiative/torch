package de.medizininformatikinitiative.util;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import de.medizininformatikinitiative.CDSStructureDefinitionHandler;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.StructureDefinition;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class Slicing {

    private static final FhirContext ctx = FhirContext.forR4();
    private static final IParser parser = ctx.newJsonParser().setPrettyPrint(true);

    CDSStructureDefinitionHandler handler;

    public Slicing(CDSStructureDefinitionHandler handler) {
        this.handler = handler;
    }

    public void checkSlicing(Base base,String elementID,StructureDefinition structureDefinition){
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot = structureDefinition.getSnapshot();
        String fhirPath= FHIRPATHbuilder.build("StructureDefinition.snapshot.element","path = '"+elementID+"'");

        List<ElementDefinition> ElementDefinition = ctx.newFhirPath().evaluate(structureDefinition, fhirPath, ElementDefinition.class);
        ElementDefinition.forEach(element -> {


            if (element.hasPath() &&  element.hasSliceName()) {
                if(Objects.equals(element.getPath(), elementID)){
                    System.out.println("Slicing of Element: "+elementID+" found in " + element.getPath()+" "+element.getIdElement().getValue());
                }

            }
        });

    }

}
