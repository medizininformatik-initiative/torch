package de.medizininformatikinitiative.torch.diagnostics;

/**
 * Composite key that uniquely identifies a single exclusion criterion in diagnostics.
 *
 * @param kind         the high-level reason for exclusion
 * @param name         human-readable label for the criterion (may be {@code null})
 * @param groupRef     reference to the resource group this criterion applies to (may be {@code null})
 * @param attributeRef reference to the specific attribute within the group (may be {@code null})
 */
public record CriterionKey(
        ExclusionKind kind,
        String name,
        String groupRef,
        String attributeRef
) {
}
