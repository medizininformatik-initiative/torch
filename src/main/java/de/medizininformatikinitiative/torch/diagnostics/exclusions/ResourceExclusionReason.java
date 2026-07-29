package de.medizininformatikinitiative.torch.diagnostics.exclusions;

/**
 * Reasons for which a resource might be excluded from further processing.
 */
public enum ResourceExclusionReason {
    MUST_HAVE,
    CONSENT,
    REFERENCE_NOT_FOUND,
    RESOURCE_OUTSIDE_BATCH,
    REFERENCE_INVALID
}
