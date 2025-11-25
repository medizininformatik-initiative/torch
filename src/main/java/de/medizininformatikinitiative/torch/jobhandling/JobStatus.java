package de.medizininformatikinitiative.torch.jobhandling;

public enum JobStatus {
    PENDING,
    RUNNING_GET_COHORT,
    RUNNING_PROCESS_BATCH,
    RUNNING_PROCESS_CORE,
    PAUSED,
    FAILED,
    COMPLETED,
    CANCELLED,
    TEMP_FAILED;

    public boolean isFinal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
