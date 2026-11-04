package de.medizininformatikinitiative.torch.diagnostics.exclusions;

/**
 * Stages at which a patient might be excluded from further processing.
 */
public enum PatientExclusionStage {
    CONSENT_FETCH,
    /**
     * None of the resources fulfilling an inclusion criterion group lies inside the patient's consent window.
     */
    INCLUSION_CONSENT,
    DIRECT_LOAD,
    CASCADING_DELETE
}
