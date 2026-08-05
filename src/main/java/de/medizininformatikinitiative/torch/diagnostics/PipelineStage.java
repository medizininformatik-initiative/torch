package de.medizininformatikinitiative.torch.diagnostics;

public enum PipelineStage {
    COHORT_QUERY,
    CONSENT_FETCH,
    DIRECT_LOAD,
    REFERENCE_RESOLVE,
    CASCADING_DELETE,
    COPY_REDACT
}
