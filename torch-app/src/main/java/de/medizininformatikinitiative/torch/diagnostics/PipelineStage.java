package de.medizininformatikinitiative.torch.diagnostics;

public enum PipelineStage {
    CONSENT_FETCH,
    DIRECT_LOAD,
    REFERENCE_RESOLVE,
    CASCADING_DELETE,
    COPY_REDACT
}
