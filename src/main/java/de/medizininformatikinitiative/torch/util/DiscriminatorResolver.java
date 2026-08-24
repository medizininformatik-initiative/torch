package de.medizininformatikinitiative.torch.util;

import de.medizininformatikinitiative.torch.model.extraction.ExtractionId;
import de.medizininformatikinitiative.torch.model.management.ReferenceResolutionContext;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.Property;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.hl7.fhir.r4.model.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves Slicing Discriminators. Essential for handling slicing
 */
public class DiscriminatorResolver {

    private static final Logger logger = LoggerFactory.getLogger(DiscriminatorResolver.class);

    /**
     * Resolves the discriminator for a given slice
     *
     * @param base              Element to be sliced
     * @param slice             ElementDefinition of the slice
     * @param discriminator     Discriminator to be resolved
     * @param snapshot          Snapshot of the StructureDefinition
     * @param resolutionContext lookups needed to evaluate a {@code resolve()} step in the discriminator path
     * @return true if Discriminator could be resolved, false otherwise
     */
    public static Boolean resolveDiscriminator(Base base, ElementDefinition slice, ElementDefinition.ElementDefinitionSlicingDiscriminatorComponent discriminator, StructureDefinition.StructureDefinitionSnapshotComponent snapshot, ReferenceResolutionContext resolutionContext) {
        return switch (discriminator.getType().toCode()) {
            case "pattern", "value" ->
                    resolvePattern(base, slice, discriminator, snapshot, resolutionContext); //pattern is deprecated and functionally equal to value
            case "type" -> resolveType(base, slice, discriminator, snapshot, resolutionContext);
            case "profile" -> resolveProfile(base, slice, discriminator, resolutionContext);
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
                                          StructureDefinition.StructureDefinitionSnapshotComponent snapshot,
                                          ReferenceResolutionContext resolutionContext) {

        ElementDefinition elementContainingInfo = resolveSlicePath(slice, discriminator, snapshot);

        if (elementContainingInfo == null) {
            logger.trace("Could not resolve slice path for {}", discriminator.getPath());
            return false;
        }

        Base resolvedBase = resolveElementPath(base, discriminator, resolutionContext);

        if (resolvedBase == null) {
            logger.trace("Could not resolve base {}", base);
            return false;
        }

        if (elementContainingInfo.hasFixedOrPattern()) {

            Type fixedOrPatternValue = elementContainingInfo.getFixedOrPattern();
            return compareBaseToFixedOrPattern(resolvedBase, fixedOrPatternValue);

        }

        if (elementContainingInfo.hasBinding()) {

            ElementDefinition.ElementDefinitionBindingComponent binding = elementContainingInfo.getBinding();
            logger.warn("Valueset binding to {} passed through without check ", binding.getValueSet());
            return true;
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

                if (resolvedChild == null || !resolvedChild.hasValues()) {
                    return false;
                }
                // A repeating resolved property (e.g. CodeableConcept.coding) matches a fixed value if any of its
                // values satisfies it, not only its first - the matching entry need not be first in the source data.
                Base fixedChildValue = fixedChild.getValues().getFirst();
                boolean anyMatch = resolvedChild.getValues().stream()
                        .anyMatch(resolvedChildValue -> compareBaseToFixedOrPattern(resolvedChildValue, fixedChildValue));
                if (!anyMatch) {
                    return false;
                }
            }

            return true;
        }
    }


    /**
     * Resolves the element based on the given path from a discriminator. Path segments are handled relative to
     * {@code base}: {@code $this} is a no-op, {@code resolve()} follows a {@link Reference} via
     * {@code resolutionContext} (a reference that does not resolve, e.g. because the target is outside the batch,
     * yields {@code null} rather than throwing), and any other segment descends into a named child.
     *
     * @param base              The base element from which the path starts
     * @param discriminator     The discriminator that contains the path
     * @param resolutionContext lookup used to follow a {@code resolve()} step in the path
     * @return The resolved element if the path is valid, null otherwise
     */
    private static Base resolveElementPath(Base base, ElementDefinition.ElementDefinitionSlicingDiscriminatorComponent discriminator, ReferenceResolutionContext resolutionContext) throws FHIRException {
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
                if (part.equalsIgnoreCase("$this")) {
                    continue;
                }
                if ("resolve()".equals(part)) {
                    currentElement = resolveReference(currentElement, resolutionContext);
                    continue;
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
            logger.trace("In Slicing Base  {} contains no valid children", currentElement.getIdBase());
            return null;
        }

        // Return the resolved element
        return currentElement;
    }

    /**
     * Follows a {@code resolve()} path step: {@code currentElement} must be a {@link Reference} to a resource
     * reachable through {@code resolutionContext}. Returns {@code null} for anything else (not a reference, an
     * unparsable/absolute/contained reference, or a reference not present in the batch), the same "path did not
     * resolve" outcome as any other unmatched path segment.
     */
    private static Base resolveReference(Base currentElement, ReferenceResolutionContext resolutionContext) {
        if (!(currentElement instanceof Reference reference) || !reference.hasReference()) {
            return null;
        }
        try {
            ExtractionId id = ExtractionId.fromRelativeUrl(reference.getReference());
            return resolutionContext.referenceResolver().apply(id).orElse(null);
        } catch (IllegalArgumentException e) {
            logger.trace("Could not parse reference '{}' for resolve()", reference.getReference());
            return null;
        }
    }


    /**
     * Resolves the Type for a given slice
     *
     * @param base              Element to be sliced
     * @param slice             ElementDefinition of the slice
     * @param snapshot          Snapshot of the StructureDefinition
     * @param resolutionContext lookups needed to evaluate a {@code resolve()} step in the discriminator path
     * @return true if type can be resolved and false if not
     */
    private static Boolean resolveType(Base base, ElementDefinition slice, ElementDefinition.ElementDefinitionSlicingDiscriminatorComponent discriminator, StructureDefinition.StructureDefinitionSnapshotComponent snapshot, ReferenceResolutionContext resolutionContext) {

        // A reference-resolving path (e.g. "$this.resolve()") has no matching element in this snapshot to walk to
        // via resolveSlicePath - the type constraint instead lives on the slice's own Reference.targetProfile.
        if (isReferenceResolvingPath(discriminator.getPath())) {
            return resolveReferenceResolvingType(base, slice, discriminator, resolutionContext);
        }

        ElementDefinition elementContainingInfo = resolveSlicePath(slice, discriminator, snapshot);

        if (elementContainingInfo == null || elementContainingInfo.getType().isEmpty()) {
            return false; // No type information means the type cannot be resolved, so return false
        }

        Base resolvedBase = resolveElementPath(base, discriminator, resolutionContext);

        if (resolvedBase == null) {
            return false;
        }

        // Proceed with the type comparison
        return elementContainingInfo.getType().stream().anyMatch(x -> resolvedBase.fhirType().equalsIgnoreCase(x.getCode()));
    }

    private static boolean isReferenceResolvingPath(String path) {
        return path.endsWith("resolve()");
    }

    /**
     * Resolves a {@code type} discriminator whose path resolves an external reference. The type constrained by each
     * of the slice's {@code Reference.targetProfile} entries is looked up via {@code resolutionContext} (falling
     * back to the canonical URL's trailing segment, which is the resource type for base HL7 StructureDefinition
     * URLs such as {@code http://hl7.org/fhir/StructureDefinition/ImagingStudy}), and compared against the FHIR
     * type of the resolved instance.
     */
    private static Boolean resolveReferenceResolvingType(Base base, ElementDefinition slice, ElementDefinition.ElementDefinitionSlicingDiscriminatorComponent discriminator, ReferenceResolutionContext resolutionContext) {
        Base resolvedBase = resolveElementPath(base, discriminator, resolutionContext);

        if (resolvedBase == null) {
            return false;
        }

        return slice.getType().stream()
                .flatMap(type -> type.getTargetProfile().stream())
                .map(CanonicalType::getValue)
                .anyMatch(targetProfile -> resolvedBase.fhirType().equalsIgnoreCase(targetResourceType(targetProfile, resolutionContext)));
    }

    private static String targetResourceType(String targetProfileUrl, ReferenceResolutionContext resolutionContext) {
        return resolutionContext.profileResolver().apply(targetProfileUrl)
                .map(CompiledStructureDefinition::type)
                .orElseGet(() -> targetProfileUrl.substring(targetProfileUrl.lastIndexOf('/') + 1));
    }

    /**
     * Resolves a {@code profile} discriminator: the path is expected to resolve an external reference (e.g.
     * {@code $this.resolve()} or {@code resolve()}), and the resolved instance must declare conformance, via its
     * own {@code meta.profile}, to one of the slice's {@code Reference.targetProfile} entries. Version suffixes
     * ({@code url|version}) are ignored on both sides.
     */
    private static Boolean resolveProfile(Base base, ElementDefinition slice, ElementDefinition.ElementDefinitionSlicingDiscriminatorComponent discriminator, ReferenceResolutionContext resolutionContext) {
        Base resolvedBase = resolveElementPath(base, discriminator, resolutionContext);

        if (!(resolvedBase instanceof Resource resolvedResource)) {
            return false;
        }

        Set<String> declaredProfiles = resolvedResource.getMeta().getProfile().stream()
                .map(CanonicalType::getValue)
                .map(ResourceUtils::stripVersion)
                .collect(Collectors.toSet());

        return slice.getType().stream()
                .flatMap(type -> type.getTargetProfile().stream())
                .map(CanonicalType::getValue)
                .map(ResourceUtils::stripVersion)
                .anyMatch(declaredProfiles::contains);
    }

}
