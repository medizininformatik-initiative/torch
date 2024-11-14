package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Element;
import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static de.medizininformatikinitiative.torch.util.DiscriminatorResolver.resolveDiscriminator;

/**
 * Class for resolving and checking Slicing
 */
public class Slicing {

    private static final Logger logger = LoggerFactory.getLogger(Slicing.class);
    private final FhirContext ctx;


    /**
     * Constructor for Slicing
     */
    public Slicing(FhirContext ctx) {

        this.ctx = ctx;
    }

    /**
     * Checks if the given element is a sliced element and returns the sliced element.
     *
     * @param base                Hapi Base (Element) which should be checked
     * @param elementID           Element ID of the above element.
     * @param structureDefinition Struturedefinition of the Ressource to which the element belongs
     * @return Returns null if no slicing is found and an elementdefinition for the slice otherwise
     */
    public ElementDefinition checkSlicing(Base base, String elementID, StructureDefinition structureDefinition) {

        StructureDefinition.StructureDefinitionSnapshotComponent snapshot = structureDefinition.getSnapshot();
        String fhirPath = "StructureDefinition.snapshot.element.where(path = '" + elementID + "')";

        ElementDefinition slicedElement = snapshot.getElementByPath(elementID);
        if (elementID.contains(":")) {
            slicedElement = snapshot.getElementById(elementID);
        }

        AtomicReference<ElementDefinition> returnElement = new AtomicReference<>(slicedElement);

        if (slicedElement == null) {
            logger.warn("slicedElement null {} {}", elementID, structureDefinition.getUrl());
            return null;
        }
        if (!slicedElement.hasSlicing()) {
            logger.warn("Element has no slicing {} {}", elementID, structureDefinition.getUrl());
            return null;
        }


        List<ElementDefinition.ElementDefinitionSlicingDiscriminatorComponent> slicingDiscriminator = slicedElement.getSlicing().getDiscriminator();

        List<ElementDefinition> ElementDefinition = ctx.newFhirPath().evaluate(structureDefinition, fhirPath, ElementDefinition.class);
        ElementDefinition.forEach(element -> {
            logger.trace("Slice to be handled {}", element.getIdElement());
            boolean foundSlice = true;
            if (element.hasSliceName()) {
                //iterate over every discriminator and test if base holds for it
                for (ElementDefinition.ElementDefinitionSlicingDiscriminatorComponent discriminator : slicingDiscriminator) {
                    if (!resolveDiscriminator(base, element, discriminator, snapshot)) {
                        logger.trace("Check failed {}", element.getIdElement());
                        foundSlice = false;
                        break; // Stop iterating if condition check fails
                    }
                }
                if (foundSlice) {
                    //logger.error("Check passed {}", element.getIdElement());
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
        logger.info("Generating Slicing Conditions for ElementID: {}", elementID);
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
                    logger.trace("Slicing Discriminator {} {}", discriminator.getType(), path);
                    // Generate FHIR Path condition based on the discriminator type and path
                    switch (discriminator.getType()) {
                        case VALUE, PATTERN:
                            logger.trace("Pattern discriminator found");
                            conditions.addAll(collectConditionsfromPattern(elementID, snapshot, path));
                            break;
                        case EXISTS:
                            conditions.add(path + ".exists()");
                            break;
                        case TYPE:
                            logger.trace("Type discriminator found");
                            conditions.add(path + ".ofType({type})");
                            break;
                        case PROFILE:
                            conditions.add(path + ".conformsTo({profile})");
                            break;
                        default:
                            throw new UnsupportedOperationException("Unsupported discriminator type: " + discriminator.getType());
                    }
                }
            }

            // TODO : Future handling for ordered and rules if needed
            // TODO (slicing.hasOrdered())
            // TODO (slicing.hasRules()) {

        }

        return conditions;
    }


    List<String> collectConditionsfromPattern(String elementId, StructureDefinition.StructureDefinitionSnapshotComponent snapshot, String path) {
        List<String> conditions = new ArrayList<>();
        if (!path.equals("$this")) {
            elementId += "." + path;
        }
        logger.debug("Getting Conditions {}", elementId);
        ElementDefinition elementDefinition = snapshot.getElementById(elementId);
        if (elementDefinition == null) {

            logger.debug("Unsupported Element potentially contains Profile reference {}", elementId);
            return conditions;
        }
        if (elementDefinition.hasFixedOrPattern()) {

            Element pattern = elementDefinition.getFixedOrPattern();
            logger.debug("Got Pattern ");
            conditions.addAll(traverseValueRec(path, pattern));
        } else {
            logger.warn("No Pattern found {} in its Pattern/Value slicing", elementId);

        }

        return conditions;
    }

    List<String> traverseValueRec(String basePath, Element pattern) {

        List<String> conditions = new ArrayList<>();
        if (pattern.isPrimitive()) {
            conditions.add(basePath + "='" + pattern.primitiveValue() + "'");
        } else {
            pattern.children().forEach(
                    child -> {
                        if (child.hasValues()) {
                            child.getValues().forEach(
                                    value -> conditions.addAll(traverseValueRec(basePath + "." + child.getName(), (Element) value))
                            );

                        }

                    }


            );

        }


        return conditions;


    }


}
