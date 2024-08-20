package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;


import de.medizininformatikinitiative.torch.CdsStructureDefinitionHandler;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.StructureDefinition;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static de.medizininformatikinitiative.torch.util.DiscriminatorResolver.resolveDiscriminator;

public class Slicing {

    private FhirContext ctx;
    private IParser parser;

    CdsStructureDefinitionHandler handler;

    /**
     * Constructor for Slicing
     *
     * @param handler CDSStructureDefinitionHandler
     */
    public Slicing(CdsStructureDefinitionHandler handler) {
        this.handler = handler;
        this.ctx = handler.ctx;
        parser = ctx.newJsonParser().setPrettyPrint(true);
    }

    /**
     * Checks if the given element is a sliced element and returns the sliced element.
     * @param base
     * @param elementID
     * @param structureDefinition
     * @return
     */
    public ElementDefinition checkSlicing(Base base, String elementID, StructureDefinition structureDefinition) {
        AtomicReference<ElementDefinition> returnElement = new AtomicReference<>(new ElementDefinition());
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot = structureDefinition.getSnapshot();
        String fhirPath = FhirPathBuilder.build("StructureDefinition.snapshot.element", "path = '" + elementID + "'");
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

    /**
     *
     * @param elementID ElementID that needs to be resolved
     * @param structureDefinition
     * @return
     */
    public String generateFhirPath(String elementID, StructureDefinition structureDefinition){
        //In ElementIDs you can find slicing by resolving the : operator.
        //Case 1: Type discriminator marked by [x]
        //Case 2: All other operators found in discriminator information.
        if(elementID.contains(":")) {
            StructureDefinition.StructureDefinitionSnapshotComponent snapshot = structureDefinition.getSnapshot();


        }
        else{
        if(elementID.contains("[x]")){
            return elementID.replace("[x]","");
        }

        }


        return elementID;

    }


    /**
     * Checks the condition for the given sliced element
     * @param base
     * @param sliced
     * @param element
     * @param structureDefinition
     * @return
     */
    public boolean checkCondition(Base base, ElementDefinition sliced, ElementDefinition element,StructureDefinition structureDefinition) {
        ElementDefinition.ElementDefinitionSlicingComponent slicingComponent = sliced.getSlicing();
        AtomicReference<Boolean> result= new AtomicReference<>(false);
        if (slicingComponent.hasDiscriminator()) {
            List<ElementDefinition.ElementDefinitionSlicingDiscriminatorComponent> slicingDiscriminator = sliced.getSlicing().getDiscriminator();
            slicingDiscriminator.forEach(discriminator -> {

                String type = discriminator.getType().toCode();
                StringType path = discriminator.getPathElement();

                StructureDefinition.StructureDefinitionSnapshotComponent snapshot = structureDefinition.getSnapshot();
                result.set(resolveDiscriminator(base, element, type, path.getValue(), snapshot));
            });
        }
        return result.get();
    }


}
