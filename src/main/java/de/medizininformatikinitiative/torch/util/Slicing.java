package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;


import de.medizininformatikinitiative.torch.CdsStructureDefinitionHandler;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static de.medizininformatikinitiative.torch.util.DiscriminatorResolver.resolveDiscriminator;

public class Slicing {

    private static final Logger logger = LoggerFactory.getLogger(Slicing.class);
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
     *
     * @param base
     * @param elementID
     * @param structureDefinition
     * @return
     */
    public ElementDefinition checkSlicing(Base base, String elementID, StructureDefinition structureDefinition) {

        StructureDefinition.StructureDefinitionSnapshotComponent snapshot = structureDefinition.getSnapshot();
        String fhirPath = FhirPathBuilder.build("StructureDefinition.snapshot.element", "path = '" + elementID + "'");
        ElementDefinition slicedElement = snapshot.getElementByPath(elementID);
        AtomicReference<ElementDefinition> returnElement = new AtomicReference<>(slicedElement);

        if (slicedElement == null || !slicedElement.hasSlicing()) {
            logger.info("Element not sliced");
            return null; // Return null if the sliced element is not found or has no slicing
        }

        List<ElementDefinition.ElementDefinitionSlicingDiscriminatorComponent> slicingDiscriminator = slicedElement.getSlicing().getDiscriminator();

        List<ElementDefinition> ElementDefinition = ctx.newFhirPath().evaluate(structureDefinition, fhirPath, ElementDefinition.class);
        ElementDefinition.forEach(element -> {
            boolean foundSlice = true;
            if (element.hasSliceName()) {
                //iterate over every discriminator and test if base holds for it
                for (ElementDefinition.ElementDefinitionSlicingDiscriminatorComponent discriminator : slicingDiscriminator) {
                    if (!resolveDiscriminator(base, element, discriminator, snapshot)) {
                        logger.info("Check failed {}", element.getIdElement());
                        foundSlice = false;
                        break; // Stop iterating if condition check fails
                    }
                }
                if (foundSlice) {
                    logger.info("Check passed {}", element.getIdElement());
                    returnElement.set(element);
                }

            }
        });

        return returnElement.get();
    }

    /**
     * @param elementID           ElementID that needs to be resolved
     * @param structureDefinition
     * @return
     */
    public String generateFhirPath(String elementID, StructureDefinition structureDefinition) {
        //In ElementIDs you can find slicing by resolving the : operator.
        //Case 1: Type discriminator marked by [x]
        //Case 2: All other operators found in discriminator information.
        if (elementID.contains(":")) {
            StructureDefinition.StructureDefinitionSnapshotComponent snapshot = structureDefinition.getSnapshot();
            //TODO Find Slicing Parent
            String slicingParent = elementID.split(":")[0];
            //TODO Get conditions by resolving the discriminators and wandering to the subelements defining them
            // Find the element that matches the slicingParent in the snapshot
            ElementDefinition parentElement = null;
            for (ElementDefinition element : snapshot.getElement()) {
                if (element.getPath().equals(slicingParent)) {
                    parentElement = element;
                    break;
                }
            }
            if (parentElement != null && parentElement.hasSlicing()) {
                ElementDefinition.ElementDefinitionSlicingComponent slicing = parentElement.getSlicing();
                if (slicing.hasDiscriminator()) {
                    //Find all  conditions and add to FHIR Path

                }
                if (slicing.hasOrdered()) {

                    //Find all  conditions and add to FHIR Path
                }
                if (slicing.hasRules()) {

                    //Find all  conditions and add to FHIR Path
                }
            }


        } else {
            //Case elementid is not sliced specifically but applies to all type sliced elements e.g. Observation.effective[x].value
            if (elementID.contains("[x]")) {
                return elementID.replace("[x]", "");
            }

        }


        return elementID;

    }


}
