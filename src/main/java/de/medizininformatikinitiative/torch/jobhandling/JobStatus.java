package de.medizininformatikinitiative.torch.jobhandling;

public enum JobStatus {

    PENDING("Pending"),
    PAUSED("Paused"),
    TEMP_FAILED("Temporarily failed"),
    RUNNING_GET_COHORT("Fetching cohort"),
    RUNNING_PROCESS_BATCH("Processing batches"),
    RUNNING_PROCESS_CORE("Processing core data"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    CANCELLED("Cancelled"),
    DELETED("Deleted");

    private final String display;

    JobStatus(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }

    public boolean isFinal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED || this == DELETED;
    }
}
