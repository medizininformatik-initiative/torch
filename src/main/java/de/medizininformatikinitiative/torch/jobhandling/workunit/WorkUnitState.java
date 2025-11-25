package de.medizininformatikinitiative.torch.jobhandling.workunit;

import java.time.Instant;
import java.util.Optional;

public record WorkUnitState(
        WorkUnitStatus status,
        Instant startedAt,
        Optional<Instant> finishedAt,
        int retry) {

    private final static int MAX_RETRIES = 5;

    public static WorkUnitState initNow() {
        return new WorkUnitState(WorkUnitStatus.INIT, Instant.now(), Optional.empty(), 0);
    }

    public static WorkUnitState startNow() {
        return new WorkUnitState(WorkUnitStatus.IN_PROGRESS, Instant.now(), Optional.empty(), 0);
    }

    public WorkUnitState finishNow(WorkUnitStatus status) {
        return new WorkUnitState(status, startedAt, Optional.of(Instant.now()), 0);
    }

    public WorkUnitState markFailed() {
        return new WorkUnitState(WorkUnitStatus.FAILED, startedAt, finishedAt, retry);
    }

    public WorkUnitState markTempFailed() {
        return new WorkUnitState(WorkUnitStatus.TEMP_FAILED, startedAt, finishedAt, retry);
    }

    public WorkUnitState incrementRetry() {
        if (retry + 1 >= MAX_RETRIES) {
            return markFailed();
        } else return new WorkUnitState(status, startedAt, finishedAt, retry + 1);
    }

    public WorkUnitState rerollFromTempFailed() {
        if (status != WorkUnitStatus.TEMP_FAILED) {
            return this;
        }

        WorkUnitState afterRetry = incrementRetry();
        if (afterRetry.status() == WorkUnitStatus.FAILED) {
            return afterRetry;
        }

        return new WorkUnitState(
                WorkUnitStatus.INIT,
                Instant.now(),
                Optional.empty(),
                afterRetry.retry()
        );
    }


    public WorkUnitState skip() {
        return new WorkUnitState(WorkUnitStatus.SKIPPED, startedAt, Optional.ofNullable(Instant.now()), 0);
    }

    public WorkUnitState onFailure(boolean retryable) {
        if (!retryable) {
            return markFailed();
        }
        return markTempFailed();
    }
}
