package de.medizininformatikinitiative.torch.model.extraction;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Holds extraction metadata for a single FHIR resource.
 * <p>
 * A {@code ResourceExtractionInfo} describes:
 * <ul>
 *   <li>which resource groups validated this resource</li>
 *   <li>which attributes reference which other resource IDs</li>
 * </ul>
 * Instances are immutable and safe to merge.
 */
public record ResourceExtractionInfo(
        Set<String> groups,                              // groupIds valid for this resource
        Map<String, Set<String>> attributeToReferences   // elementId -> Set<resourceId>
) {

    /**
     * Creates an immutable extraction info instance.
     * <p>
     * Null inputs are normalized to empty collections. All collections are defensively copied
     * and made unmodifiable.
     *
     * @param groups                valid group IDs for this resource.
     * @param attributeToReferences mapping from attribute reference to referenced resource IDs.
     */
    @JsonCreator
    public ResourceExtractionInfo(
            @JsonProperty("groups") Set<String> groups,
            @JsonProperty("attributeToReferences") Map<String, Set<String>> attributeToReferences
    ) {
        // Jackson may pass null → handle it
        this.groups = groups == null ? Set.of() : Set.copyOf(groups);
        this.attributeToReferences =
                attributeToReferences == null
                        ? Map.of()
                        : attributeToReferences.entrySet().stream()
                        .collect(Collectors.toUnmodifiableMap(
                                Map.Entry::getKey,
                                e -> Set.copyOf(e.getValue())
                        ));
    }

    /**
     * Builds an extraction info map for all valid resources in a bundle.
     * <p>
     * The returned map is keyed by resource ID.
     *
     * @param bundle the resource bundle to extract metadata from.
     * @return resourceId → extraction info.
     */
    public static Map<String, ResourceExtractionInfo> toExtractionInfoMap(ResourceBundle bundle) {

        Set<String> resourceIds =
                bundle.resourceGroupValidity().entrySet().stream()
                        .filter(Map.Entry::getValue)
                        .map(Map.Entry::getKey)
                        .map(ResourceGroup::resourceId)
                        .collect(Collectors.toSet());

        return resourceIds.stream()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> ResourceExtractionInfo.of(bundle, id)
                ));
    }

    public static ResourceExtractionInfo of(ResourceBundle bundle, String resourceId) {
        return new ResourceExtractionInfo(
                collectValidGroups(bundle, resourceId),
                computeAttributeReferenceMap(bundle, resourceId)
        );
    }

    /**
     * Computes the attribute-to-referenced-resources map for a resource.
     * <p>
     * Only valid attributes and valid referenced resource groups are considered.
     * Attributes without valid references are omitted.
     *
     * @param bundle     the resource bundle.
     * @param resourceId the resource id.
     * @return attributeRef → referenced resource IDs.
     */
    public static Map<String, Set<String>> computeAttributeReferenceMap(
            ResourceBundle bundle,
            String resourceId
    ) {
        Map<String, Set<String>> attrToRefs = new HashMap<>();

        bundle.resourceAttributeValidity().entrySet().stream()
                .filter(Map.Entry::getValue) // only valid attributes
                .map(Map.Entry::getKey)      // ResourceAttribute
                .filter(attr -> attr.resourceId().equals(resourceId))
                .forEach(attr -> {

                    String attributeRef = attr.annotatedAttribute().attributeRef();

                    // All child groups for this attribute
                    Set<ResourceGroup> childGroups =
                            bundle.resourceAttributeToChildResourceGroup()
                                    .getOrDefault(attr, Set.of());

                    // Filter only valid referenced groups and extract their resourceIds
                    Set<String> validReferencedResourceIds =
                            childGroups.stream()
                                    .filter(g -> Boolean.TRUE.equals(bundle.isValidResourceGroup(g)))
                                    .map(ResourceGroup::resourceId)
                                    .collect(Collectors.toSet());

                    // Only add non-empty entries
                    if (!validReferencedResourceIds.isEmpty()) {
                        attrToRefs
                                .computeIfAbsent(attributeRef, k -> new HashSet<>())
                                .addAll(validReferencedResourceIds);
                    }
                });

        return attrToRefs;
    }

    /**
     * Collects all valid group IDs associated with a resource.
     *
     * @param bundle     the resource bundle.
     * @param resourceId the resource id.
     * @return set of valid group IDs.
     */
    private static Set<String> collectValidGroups(ResourceBundle bundle, String resourceId) {
        return bundle.resourceGroupValidity().entrySet().stream()
                .filter(Map.Entry::getValue) // only valid groups
                .map(Map.Entry::getKey)      // ResourceGroup
                .filter(g -> g.resourceId().equals(resourceId))
                .map(ResourceGroup::groupId)
                .collect(Collectors.toSet());
    }

    /**
     * Merges this extraction info with another instance.
     * <p>
     * Groups are merged by union. Attribute references are deep-merged
     * (union of referenced resource IDs per attribute).
     *
     * @param other the other extraction info (may be null).
     * @return a new merged extraction info instance.
     */
    public ResourceExtractionInfo merge(ResourceExtractionInfo other) {
        if (other == null) {
            return this;
        }

        // --- merge groups (union) ---
        Set<String> mergedGroups = new HashSet<>(this.groups());
        mergedGroups.addAll(other.groups());

        // --- merge attributeToReferences (deep merge) ---
        Map<String, Set<String>> mergedAttr = new HashMap<>();

        // 1. copy current attributes
        this.attributeToReferences().forEach((attr, refs) ->
                mergedAttr.put(attr, new HashSet<>(refs))
        );

        // 2. merge in other's attributes
        other.attributeToReferences().forEach((attr, refs) ->
                mergedAttr
                        .computeIfAbsent(attr, k -> new HashSet<>())
                        .addAll(refs)
        );

        // --- return new merged instance ---
        return new ResourceExtractionInfo(
                mergedGroups,
                mergedAttr
        );
    }
}
