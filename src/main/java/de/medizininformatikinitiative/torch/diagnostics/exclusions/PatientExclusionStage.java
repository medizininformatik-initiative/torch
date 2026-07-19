package de.medizininformatikinitiative.torch.diagnostics.exclusions;

/**
 * Stages at which a patient might be excluded from further processing.
 */
public enum PatientExclusionStage {
    CONSENT,
    DIRECT_LOAD,
    CASCADING_DELETE
}
