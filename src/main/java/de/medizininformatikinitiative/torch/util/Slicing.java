package de.medizininformatikinitiative.torch.util;

import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.Element;
import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.UriType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static de.medizininformatikinitiative.torch.util.DiscriminatorResolver.resolveDiscriminator;

/**
 * Class for resolving and checking Slicing
 */
public class Slicing {

    private static final Logger logger = LoggerFactory.getLogger(Slicing.class);

    /**
     * Checks if the given element is a sliced element and returns the sliced element otherwise null.
     *
     * @param base       HAPI Base (Element) which should be checked
     * @param elementId  Element ID of the above element.
     * @param definition definition of the Resource to which the element belongs
     * @return Returns null if no slicing is found and an ElementDefinition for the slice otherwise
     */
    static ElementDefinition checkSlicing(Base base, String elementId, CompiledStructureDefinition definition) {
        ElementDefinition slicedElement = definition.elementDefinitionById(elementId);

        if (slicedElement == null) {
            return null;
        }

        if (!slicedElement.hasSlicing()) {
            return null;
        }

        AtomicReference<ElementDefinition> returnElement = new AtomicReference<>(null);
        List<ElementDefinition.ElementDefinitionSlicingDiscriminatorComponent> slicingDiscriminator = slicedElement.getSlicing().getDiscriminator();

        Stream<ElementDefinition> elementDefinitions = definition.elementDefinitionByPath(slicedElement.getPath());
        elementDefinitions.filter(ElementDefinition::hasSliceName).forEach(element -> {

            boolean foundSlice = true;

            for (ElementDefinition.ElementDefinitionSlicingDiscriminatorComponent discriminator : slicingDiscriminator) {

                if ("url".equals(discriminator.getPath()) && "VALUE".equals(discriminator.getType().toString())) {

                    if ("Extension".equals(element.getType().getFirst().getWorkingCode())) {
                        UriType baseTypeUrl = (UriType) base.getNamedProperty("url").getValues().getFirst();
                        List<CanonicalType> profiles = element.getType().stream()
                                .flatMap(type -> type.getProfile().stream())
                                .toList();
                        // Check if any profile matches the base type URL
                        boolean anyMatchBaseUrl = profiles.stream()
                                .anyMatch(profile -> profile.getValue().equals(baseTypeUrl.getValue()));
                        if (anyMatchBaseUrl) {
                            continue;
                        } else {
                            foundSlice = false;
                            break;
                        }
                    }

                }
                if (!resolveDiscriminator(base, element, discriminator, definition.structureDefinition().getSnapshot())) {
                    foundSlice = false;
                    break; // Stop iterating if condition check fails
                }
            }
            if (foundSlice) {
                returnElement.set(element);
            }


        });

        return returnElement.get();
    }

    /**
     * Generates FHIR Path conditions based on the element ID and the snapshot of the StructureDefinition.
     *
     * @param elementID  ElementID that needs to be resolved
     * @param definition compiled Structure Definition for improved performance
     * @return List of FHIR Path conditions as strings
     */
    public static List<String> generateConditionsForFHIRPath(String elementID, CompiledStructureDefinition definition) {
        List<String> conditions = new ArrayList<>();
        // Find the sliced element using the element ID
        ElementDefinition slicedElement = definition.elementDefinitionById(elementID);
        if (slicedElement == null) {
            throw new IllegalArgumentException("Element with ID " + elementID + " not found in snapshot.");
        }

        // Find the parent element using the path of the sliced element
        ElementDefinition parentElement = definition.elementDefinitionById(slicedElement.getPath());
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
                            conditions.addAll(Slicing.collectConditionsfromPattern(elementID, definition, path));
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


    static List<String> collectConditionsfromPattern(String elementId, CompiledStructureDefinition definition, String path) {
        List<String> conditions = new ArrayList<>();
        if (!path.equals("$this")) {
            elementId += "." + path;
        }
        logger.trace("Getting Conditions {}", elementId);
        ElementDefinition elementDefinition = definition.elementDefinitionById(elementId);
        if (elementDefinition == null) {

            logger.debug("Unsupported Element potentially contains Profile reference {}", elementId);
            return conditions;
        }
        if (elementDefinition.hasFixedOrPattern()) {

            Element pattern = elementDefinition.getFixedOrPattern();
            conditions.addAll(Slicing.traverseValueRec(path, pattern));
        } else {
            logger.warn("No Pattern found {} in its Pattern/Value slicing", elementId);

        }

        return conditions;
    }

    static List<String> traverseValueRec(String basePath, Element pattern) {

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
