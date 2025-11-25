package de.medizininformatikinitiative.torch.jobhandling;

public enum JobStatus {
    PENDING,
    RUNNING_CREATE_BATCHES,
    RUNNING_PROCESS_BATCH,
    RUNNING_PROCESS_CORE,
    PAUSED,
    FAILED,
    COMPLETED,
    CANCELLED;

    public boolean isFinal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
