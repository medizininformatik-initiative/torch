package de.medizininformatikinitiative.torch.util;

import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

public class DiscriminatorResolver {

    private static final Logger logger = LoggerFactory.getLogger(DiscriminatorResolver.class);

    /**
     * Resolves the discriminator for a given slice
     *
     * @param base          Element to be sliced
     * @param slice         ElementDefinition of the slice
     * @param discriminator Discriminator to be resolved
     * @param elementID     path to the element
     * @param snapshot      Snapshot of the StructureDefinition
     * @return
     */
    public static Boolean resolveDiscriminator(Base base, ElementDefinition slice, ElementDefinition.ElementDefinitionSlicingDiscriminatorComponent discriminator, StructureDefinition.StructureDefinitionSnapshotComponent snapshot) {
        //System.out.println("Discriminator "+discriminator);
        return switch (discriminator.getType().toCode()) {
            case "exists" -> false;
            case "pattern", "value" ->
                    resolvePattern(base, slice, discriminator, snapshot); //pattern is deprecated and functionally equal to value
            case "profile" -> false;  //
            case "type" -> resolveType(base, slice, discriminator, snapshot);
            default -> false;
        };


    }




    /**
     * Resolves the Path for a given slice
     *
     * @param slice ElementDefinition of the slice
     * @return String path that has to be wandered
     */
    private static ElementDefinition resolveSlicePath(ElementDefinition slice, ElementDefinition.ElementDefinitionSlicingDiscriminatorComponent discriminator, StructureDefinition.StructureDefinitionSnapshotComponent snapshot) {
        String path = discriminator.getPath();
        if (Objects.equals(path, "$this")) {
            return slice;
        }
        return snapshot.getElementByPath(slice.getId() + "." + slice.getPath());
    }

    /**
     * Resolves the Pattern for a given slice.
     *
     * @param base          The base element to be sliced.
     * @param slice         The ElementDefinition of the slice.
     * @param discriminator The discriminator that defines how to slice the base element.
     * @param snapshot      The snapshot of the StructureDefinition.
     * @return True if the pattern is resolved successfully; false otherwise.
     */
    private static Boolean resolvePattern(Base base, ElementDefinition slice,
                                          ElementDefinition.ElementDefinitionSlicingDiscriminatorComponent discriminator,
                                          StructureDefinition.StructureDefinitionSnapshotComponent snapshot) {
        // Resolve the element containing the fixed or pattern information.
        //logger.debug("Element to be resolved {} {} ", slice.getId(), discriminator.getPath());
        ElementDefinition elementContainingInfo = resolveSlicePath(slice, discriminator, snapshot);

        // Resolve the base element along the path specified by the discriminator.
        Base resolvedBase = resolveElementPath(base, discriminator);

        // If the resolved base element is null, the pattern cannot be resolved.
        if (resolvedBase == null) {
            return false;
        }
        // If the resolved base element is null, the pattern cannot be resolved.
        if (elementContainingInfo == null) {
            return false;
        }

        // Check if the element containing the info has a fixed value or pattern.
        if (elementContainingInfo.hasFixedOrPattern()) {

            // Get the fixed value or pattern from the element definition.
            Base fixedOrPatternValue = elementContainingInfo.getFixedOrPattern();
            return compareBaseToFixedOrPattern(resolvedBase, fixedOrPatternValue);

        }

        // Return false if no fixed or pattern value is found.
        return false;
    }

    // Custom comparison method
    private static boolean compareBaseToFixedOrPattern(Base resolvedBase, Base fixedOrPatternValue) {
        // Implement the logic to compare resolvedBase to fixedOrPatternValue
        // You may need to recursively compare the elements that are defined in fixedOrPatternValue
        // Example: if fixedOrPatternValue is a complex type, iterate over its children
        if (!Objects.equals(resolvedBase.fhirType(), fixedOrPatternValue.fhirType())) {
            logger.warn("Incompatible Data types when comparing ");
            return false;
        }

        if (fixedOrPatternValue.isPrimitive()) {
            logger.debug("Handling Primitive Types  {}", fixedOrPatternValue.getIdBase());
            return resolvedBase.equalsDeep(fixedOrPatternValue);
        } else {
            logger.debug("Handling Complex Types  {}", fixedOrPatternValue.fhirType());
            // Iterate over the children of fixedOrPatternValue and compare them to the corresponding children in resolvedBase
            for (Property child : fixedOrPatternValue.children()) {
                if (child.hasValues()) {

                    String childName = child.getName();
                    Property resolvedChild = resolvedBase.getChildByName(childName);

                    logger.debug("Handling Child {} {}", childName, resolvedChild);

                    // If the resolved base doesn't have this child, return false
                    if (resolvedChild == null) {
                        return false;
                    } else {
                        return compareBaseToFixedOrPattern(resolvedChild.getValues().get(0), child.getValues().get(0));
                    }
                }

            }
        }
        return true;

    }


    /**
     * Resolves the element based on the given path from a discriminator
     *
     * @param base          The base element from which the path starts
     * @param discriminator The discriminator that contains the path
     * @return The resolved element if the path is valid, null otherwise
     */
    private static Base resolveElementPath(Base base, ElementDefinition.ElementDefinitionSlicingDiscriminatorComponent discriminator) {
        // Extract the path from the discriminator

        String path = discriminator.getPath();

        if (path.equalsIgnoreCase("$this")) {
            return base;
        }
        // Split the path by the dot to handle subpaths
        String[] parts = path.split("\\.");

        // Start with the base element
        Base currentElement = base;

        // Iterate through each part of the path
        for (String part : parts) {
            if (currentElement == null) {
                return null;
            }

            // Resolve the next element based on the current part of the path
            List<Base> nextElements = currentElement.listChildrenByName(part);

            // If there are no elements matching this part of the path, return null
            if (nextElements == null || nextElements.isEmpty()) {
                return null;
            }

            // Move to the next element in the path
            currentElement = nextElements.get(0);
        }

        // Return the resolved element
        return currentElement;
    }


    /**
     * Resolves the Type for a given slice
     *
     * @param base     Element to be sliced
     * @param slice    ElementDefinition of the slice
     * @param snapshot Snapshot of the StructureDefinition
     * @return
     */
    private static Boolean resolveType(Base base, ElementDefinition slice, ElementDefinition.ElementDefinitionSlicingDiscriminatorComponent
            discriminator, StructureDefinition.StructureDefinitionSnapshotComponent snapshot) {
        ElementDefinition elementContainingInfo = resolveSlicePath(slice, discriminator, snapshot);
        return base.fhirType().equalsIgnoreCase(elementContainingInfo.getType().get(0).getWorkingCode());
    }


}
