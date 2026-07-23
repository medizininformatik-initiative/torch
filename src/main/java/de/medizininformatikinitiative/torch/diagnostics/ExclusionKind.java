package de.medizininformatikinitiative.torch.diagnostics;

/**
 * High-level reason why a patient or resource was excluded during extraction.
 *
 * <p>The enum values are written to {@code exclusions.csv}. Keep names stable unless
 * the diagnostics file format is intentionally changed.</p>
 */
public enum ExclusionKind {
    /**
     * A required resource for a requested group was missing.
     */
    MUST_HAVE_RESOURCE,
    /**
     * A required attribute on an otherwise present resource was missing.
     */
    MUST_HAVE_FIELD,
    /**
     * The patient did not satisfy the required consent provisions.
     */
    CONSENT,
    /**
     * A referenced resource could not be found on the FHIR server.
     */
    REFERENCE_NOT_FOUND,
    /**
     * A referenced resource belongs to a patient outside the current extraction batch.
     */
    REFERENCE_OUTSIDE_BATCH,

    /**
     * A reference value could not be parsed or resolved as a valid FHIR reference.
     */
    REFERENCE_INVALID,
    /**
     * A patient was removed after cascading delete because the remaining bundle no
     * longer satisfied must-have requirements.
     *
     * <p>This is a patient-level event. It deliberately does not identify a concrete
     * resource or attribute, because resources affected by cascading delete may already
     * have been loaded, counted, or removed in earlier stages.</p>
     */
    MUST_HAVE_CASCADE
}
