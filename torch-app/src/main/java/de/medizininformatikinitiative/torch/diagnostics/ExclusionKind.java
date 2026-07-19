package de.medizininformatikinitiative.torch.diagnostics;

/**
 * High-level reason why a patient or resource was excluded during extraction.
 */
public enum ExclusionKind {
    MUST_HAVE,
    CONSENT,
    REFERENCE_NOT_FOUND,
    REFERENCE_OUTSIDE_BATCH,
    REFERENCE_INVALID
}
