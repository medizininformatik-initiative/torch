package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;


import de.medizininformatikinitiative.torch.CdsStructureDefinitionHandler;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
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
        String fhirPath = "StructureDefinition.snapshot.element.where(path = '" + elementID + "')";
        ElementDefinition slicedElement = snapshot.getElementByPath(elementID);
        AtomicReference<ElementDefinition> returnElement = new AtomicReference<>(slicedElement);

        if (slicedElement == null || !slicedElement.hasSlicing()) {
            logger.warn("Element not sliced {}",elementID);
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
                        logger.debug("Check failed {}", element.getIdElement());
                        foundSlice = false;
                        break; // Stop iterating if condition check fails
                    }
                }
                if (foundSlice) {
                    logger.debug("Check passed {}", element.getIdElement());
                    returnElement.set(element);
                }

            }
        });

        return returnElement.get();
    }


    /**
     * Generates FHIR Path conditions based on the element ID and the snapshot of the StructureDefinition.
     *
     * @param elementID ElementID that needs to be resolved
     * @param snapshot  StructureDefinitionSnapshotComponent containing the structure definition
     * @return List of FHIR Path conditions as strings
     */
    public List<String> generateConditionsForFHIRPath(String elementID, StructureDefinition.StructureDefinitionSnapshotComponent snapshot) {
        List<String> conditions = new ArrayList<>();

        // Find the sliced element using the element ID
        ElementDefinition slicedElement = snapshot.getElementById(elementID);
        if (slicedElement == null) {
            throw new IllegalArgumentException("Element with ID " + elementID + " not found in snapshot.");
        }

        // Find the parent element using the path of the sliced element
        ElementDefinition parentElement = snapshot.getElementById(slicedElement.getPath());
        if (parentElement != null && parentElement.hasSlicing()) {
            ElementDefinition.ElementDefinitionSlicingComponent slicing = parentElement.getSlicing();

            if (slicing.hasDiscriminator()) {
                // Iterate over discriminators to generate conditions
                for (ElementDefinition.ElementDefinitionSlicingDiscriminatorComponent discriminator : slicing.getDiscriminator()) {

                    String path = discriminator.getPath();
                    logger.info("Slicing Discriminator {} {}",discriminator.getType(),path);
                    // Generate FHIR Path condition based on the discriminator type and path
                    switch (discriminator.getType()) {
                        case VALUE, PATTERN:
                            logger.info("Pattern discriminator found");
                            conditions.addAll(collectConditionsfromPattern(elementID, snapshot, path));
                            break;
                        case EXISTS:
                            conditions.add(slicedElement.getPath() + "." + path + ".exists()");
                            break;
                        case TYPE:
                            logger.info("Type discriminator found");
                            conditions.add(slicedElement.getPath() + "." + path + ".ofType({type})");
                            break;
                        case PROFILE:
                            conditions.add(slicedElement.getPath() + "." + path + ".conformsTo({profile})");
                            break;
                        default:
                            throw new UnsupportedOperationException("Unsupported discriminator type: " + discriminator.getType());
                    }
                }
            }

            // Future handling for ordered and rules if needed
            /*
            if (slicing.hasOrdered()) {
                // Add conditions related to ordered slicing
            }
            if (slicing.hasRules()) {
                // Add conditions related to slicing rules
            }
            */
        }

        return conditions;
    }


    private List<String> collectConditionsfromPattern(String elementId, StructureDefinition.StructureDefinitionSnapshotComponent snapshot, String path) {
        List<String> conditions=new ArrayList<>();
        if(path!="$this"){
            elementId+="."+path;
        }
        logger.info("Getting Conditions {}",elementId);
        ElementDefinition elementDefinition= snapshot.getElementById(elementId);
        if(elementDefinition==null){
            logger.warn("Unsupported Element potentially contains Profile reference {}",elementId);
            return conditions;
        }
        if(elementDefinition.hasFixedOrPattern()){
            //While deprecated the term pattern describes it better unlike value.
            Element pattern = elementDefinition.getFixedOrPattern();
            logger.debug("Got Pattern ");
            conditions.addAll(traverseValueRec(elementDefinition.getPath(),pattern));
        }else{
            logger.warn("No Pattern found {} in its Pattern/Value slicing",elementId);

        }

        return conditions;
    }

    private List<String> traverseValueRec(String basePath, Element pattern){

        List<String> conditions=new ArrayList<>();
        if(pattern.isPrimitive()){
            conditions.add(basePath+"='"+pattern.primitiveValue()+"'");
        }else{
        pattern.children().forEach(
                child ->{
                    if(child.hasValues()){
                        child.getValues().forEach(
                                value->{
                                    conditions.addAll(traverseValueRec(basePath+"."+child.getName(), (Element) value));
                                }
                        );

                    }

                }


        );

        }



        return conditions;


    }


}
