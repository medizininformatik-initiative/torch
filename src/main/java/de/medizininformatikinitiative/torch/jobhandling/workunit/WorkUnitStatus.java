package de.medizininformatikinitiative.torch.jobhandling.workunit;

public enum WorkUnitStatus {
    INIT,
    IN_PROGRESS,
    SKIPPED,
    FINISHED,
    FAILED,
    TEMP_FAILED;

    /**
     * "Done" from an orchestration perspective
     */
    public boolean isDone() {
        return this == FINISHED || this == SKIPPED || this == FAILED;
    }

    public boolean shouldRerollToInit() {
        return this == IN_PROGRESS || this == TEMP_FAILED;
    }

    public boolean isTerminalFailure() {
        return this == FAILED;
    }
}
