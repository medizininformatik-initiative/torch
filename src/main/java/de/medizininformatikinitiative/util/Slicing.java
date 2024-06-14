package de.medizininformatikinitiative.util;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
;

import de.medizininformatikinitiative.CDSStructureDefinitionHandler;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.StructureDefinition;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static de.medizininformatikinitiative.util.DiscriminatorResolver.resolveDiscriminator;

public class Slicing {

    private static final FhirContext ctx = FhirContext.forR4();
    private static final IParser parser = ctx.newJsonParser().setPrettyPrint(true);

    CDSStructureDefinitionHandler handler;

    public Slicing(CDSStructureDefinitionHandler handler) {
        this.handler = handler;
    }

    public ElementDefinition checkSlicing(Base base, String elementID, StructureDefinition structureDefinition) {
        AtomicReference<ElementDefinition> returnElement = new AtomicReference<>(new ElementDefinition());
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot = structureDefinition.getSnapshot();
        String fhirPath = FHIRPATHbuilder.build("StructureDefinition.snapshot.element", "path = '" + elementID + "'");
        ElementDefinition slicedElement = snapshot.getElementByPath(elementID);


        List<ElementDefinition> ElementDefinition = ctx.newFhirPath().evaluate(structureDefinition, fhirPath, ElementDefinition.class);
        ElementDefinition.forEach(element -> {
            if (element.hasSliceName()) {
                if(checkCondition(base, slicedElement, element,structureDefinition)){
                    returnElement.set(element);
                }

            }
        });

    return returnElement.get();
    }

    public boolean checkCondition(Base base, ElementDefinition sliced, ElementDefinition element,StructureDefinition structureDefinition) {
        ElementDefinition.ElementDefinitionSlicingComponent slicingComponent = sliced.getSlicing();
        AtomicReference<Boolean> result= new AtomicReference<>(false);
        if (slicingComponent.hasDiscriminator()) {
            List<ElementDefinition.ElementDefinitionSlicingDiscriminatorComponent> slicingDiscriminator = sliced.getSlicing().getDiscriminator();
            slicingDiscriminator.forEach(discriminator -> {
                //System.out.println(" Path " + discriminator.getPathElement().getValue());
                String type = discriminator.getType().toCode();
                StringType path = discriminator.getPathElement();
                //System.out.println(" Type "+type+" Path "+path);
                StructureDefinition.StructureDefinitionSnapshotComponent snapshot = structureDefinition.getSnapshot();
                result.set(resolveDiscriminator(base, element, type, path.getValue(), snapshot));
            });
        }
        return result.get();
    }


}
