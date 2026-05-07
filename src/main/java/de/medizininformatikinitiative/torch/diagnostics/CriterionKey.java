package de.medizininformatikinitiative.torch.diagnostics;

/**
 * Composite key that uniquely identifies a single exclusion criterion in diagnostics.
 *
 * @param kind         the high-level reason for exclusion
 * @param id           CRTDL element ID of the criterion (e.g. {@code Observation.code}); may be {@code null}
 * @param name         human-readable label for the criterion (may be {@code null})
 * @param groupRef     reference to the resource group this criterion applies to (may be {@code null})
 * @param attributeRef FHIRPath expression of the attribute within the group (may be {@code null})
 */
public record CriterionKey(
        ExclusionKind kind,
        String id,
        String name,
        String groupRef,
        String attributeRef
) {
}
