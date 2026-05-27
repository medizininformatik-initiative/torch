package de.medizininformatikinitiative.torch.diagnostics;

/**
 * Composite key that uniquely identifies a single exclusion criterion in diagnostics.
 *
 * @param kind         the high-level reason for exclusion
 * @param id           CRTDL element ID of the criterion (e.g. {@code Observation.code}); may be {@code null}
 * @param groupRef     reference to the resource group this criterion applies to (may be {@code null})
 * @param attributeRef FHIRPath expression of the attribute within the group (may be {@code null})
 */
public record CriterionKey(
        ExclusionKind kind,
        String id,
        String groupRef,
        String attributeRef
) {

    /**
     * Returns a human-readable label for this criterion, derived from its fields.
     * For reference exclusions the label is derived from the kind; for others it is the {@code id}.
     */
    public String displayName() {
        if (id != null) return id;
        return switch (kind) {
            case REFERENCE_NOT_FOUND -> "Reference target not found";
            case REFERENCE_OUTSIDE_BATCH -> "Reference outside patient batch";
            case REFERENCE_INVALID -> "Invalid reference format";
            default -> null;
        };
    }
}
