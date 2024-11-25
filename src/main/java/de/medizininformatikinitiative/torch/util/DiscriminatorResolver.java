package de.medizininformatikinitiative.torch.util;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Resolves Slicing Discriminators. Essential for handling slicing
 */
public class DiscriminatorResolver {

    private static final Logger logger = LoggerFactory.getLogger(DiscriminatorResolver.class);

    /**
     * Resolves the discriminator for a given slice
     *
     * @param base          Element to be sliced
     * @param slice         ElementDefinition of the slice
     * @param discriminator Discriminator to be resolved
     * @param snapshot      Snapshot of the StructureDefinition
     * @return true if Discriminator could be resolved, false otherwise
     */
    public static Boolean resolveDiscriminator(Base base, ElementDefinition slice, ElementDefinition.ElementDefinitionSlicingDiscriminatorComponent discriminator, StructureDefinition.StructureDefinitionSnapshotComponent snapshot) {
        return switch (discriminator.getType().toCode()) {
            case "pattern", "value" ->
                    resolvePattern(base, slice, discriminator, snapshot); //pattern is deprecated and functionally equal to value
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
        return snapshot.getElementById(slice.getId() + "." + path);
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
        ElementDefinition elementContainingInfo = resolveSlicePath(slice, discriminator, snapshot);

        if (elementContainingInfo == null) {
            return false;
        }
        Base resolvedBase = resolveElementPath(base, discriminator);

        if (resolvedBase == null) {
            return false;
        }

        if (elementContainingInfo.hasFixedOrPattern()) {

            Type fixedOrPatternValue = elementContainingInfo.getFixedOrPattern();
            return compareBaseToFixedOrPattern(resolvedBase, fixedOrPatternValue);

        }

        // Return false if no fixed or pattern value is found.
        return false;
    }

    private static boolean compareBaseToFixedOrPattern(Base resolvedBase, Base fixedOrPatternValue) {
        if (resolvedBase == null || fixedOrPatternValue == null) {
            logger.trace("One or both inputs are null: resolvedBase={}, fixedOrPatternValue={}", resolvedBase, fixedOrPatternValue);
            return false;
        }
        if (!Objects.equals(resolvedBase.fhirType(), fixedOrPatternValue.fhirType())) {
            logger.trace("Incompatible Data types when comparing {} {}", resolvedBase.fhirType(), fixedOrPatternValue.fhirType());
            return false;
        }
        if (fixedOrPatternValue.isPrimitive()) {
            return resolvedBase.equalsDeep(fixedOrPatternValue);
        } else {
            List<Property> fixedChildren = fixedOrPatternValue.children().stream()
                    .filter(Property::hasValues)
                    .toList();
            List<Property> resolvedChildren = resolvedBase.children().stream()
                    .filter(Property::hasValues)
                    .toList();
            if (fixedChildren.size() > resolvedChildren.size()) {
                logger.trace("Mismatch in number of children: fixedOrPatternValue has {} children, resolvedBase has {} children",
                        fixedChildren.size(), resolvedChildren.size());
                return false;
            }

            for (Property fixedChild : fixedChildren) {
                String childName = fixedChild.getName();
                Property resolvedChild = resolvedBase.getChildByName(childName);

                logger.trace("Handling Child {} {}", childName, resolvedChild);
                if (resolvedChild == null || !resolvedChild.hasValues()) {
                    logger.trace("Missing or isEmpty child '{}' in resolvedBase", childName);
                    return false;
                }
                Base resolvedChildValue = resolvedChild.getValues().getFirst();
                Base fixedChildValue = fixedChild.getValues().getFirst();
                boolean childComparison = compareBaseToFixedOrPattern(resolvedChildValue, fixedChildValue);
                if (!childComparison) {
                    logger.trace("Mismatch found in child '{}'", childName);
                    return false;
                }
            }

            return true;
        }
    }


    /**
     * Resolves the element based on the given path from a discriminator
     *
     * @param base          The base element from which the path starts
     * @param discriminator The discriminator that contains the path
     * @return The resolved element if the path is valid, null otherwise
     */
    private static Base resolveElementPath(Base base, ElementDefinition.ElementDefinitionSlicingDiscriminatorComponent discriminator) throws FHIRException {
        // Extract the path from the discriminator

        String path = discriminator.getPath();

        if (path.equalsIgnoreCase("$this")) {
            return base;
        }
        // Split the path by the dot to handle subpaths
        String[] parts = path.split("\\.");

        // Start with the base element
        Base currentElement = base;
        try {
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
                currentElement = nextElements.getFirst();
            }
        } catch (FHIRException e) {
            logger.error("In Slicing Base  {} contains no valid children", currentElement.getIdBase());
            return null;
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
     * @return true if type can be resolved and false if not
     */
    private static Boolean resolveType(Base base, ElementDefinition slice, ElementDefinition.ElementDefinitionSlicingDiscriminatorComponent discriminator, StructureDefinition.StructureDefinitionSnapshotComponent snapshot) {
        ElementDefinition elementContainingInfo = resolveSlicePath(slice, discriminator, snapshot);

        // Check if the element contains any type information
        if (elementContainingInfo.getType().isEmpty()) {
            return false; // No type information means the type cannot be resolved, so return false
        }

        // Proceed with the type comparison
        return elementContainingInfo.getType().stream().anyMatch(x -> base.fhirType().equalsIgnoreCase(x.getCode()));
    }


}
