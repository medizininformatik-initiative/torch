package de.medizininformatikinitiative.torch.model.extraction;

import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record ResourceExtractionInfo(
        Set<String> groups,                              // groupIds valid for this resource
        Map<String, Set<String>> attributeToReferences   // elementId -> Set<resourceId>
) {

    public ResourceExtractionInfo {
        groups = Set.copyOf(groups);
        attributeToReferences = Map.copyOf(attributeToReferences);
    }

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

    private static Set<String> collectValidGroups(ResourceBundle bundle, String resourceId) {
        return bundle.resourceGroupValidity().entrySet().stream()
                .filter(Map.Entry::getValue) // only valid groups
                .map(Map.Entry::getKey)      // ResourceGroup
                .filter(g -> g.resourceId().equals(resourceId))
                .map(ResourceGroup::groupId)
                .collect(Collectors.toSet());
    }

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
