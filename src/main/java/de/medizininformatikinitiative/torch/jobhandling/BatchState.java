package de.medizininformatikinitiative.torch.jobhandling;

import de.medizininformatikinitiative.torch.jobhandling.workunit.WorkUnitState;
import de.medizininformatikinitiative.torch.jobhandling.workunit.WorkUnitStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Manages the state of the batch.
 * On creation a matching batch ndjson is created with all ids called "[batchid]-patients.ndjson".
 * A finished batch state requires the persistence of the result ndjson named "[batchid]-result.ndjson"
 * and a core resource group relation  "[batchid]-core.ndjson" ndjson.
 *
 * @param batchId unique ID of the batch
 * @param state
 */
public record BatchState(UUID batchId,
                         WorkUnitState state) {

    public static BatchState init() {
        return new BatchState(UUID.randomUUID(), WorkUnitState.initNow());
    }

    public WorkUnitStatus status() {
        return state.status();
    }

    public Instant startedAt() {
        return state.startedAt();
    }

    public int retry() {
        return state.retry();
    }

    public BatchState startNow() {
        return new BatchState(batchId, WorkUnitState.startNow());
    }

    public BatchState rerollToInit() {
        return new BatchState(batchId, WorkUnitState.initNow());
    }

    public BatchState finishNow(WorkUnitStatus terminal) {
        return new BatchState(batchId, state.finishNow(terminal));
    }

    public BatchState skip() {
        return new BatchState(batchId, state.skip());
    }

    public BatchState onFailure(boolean retryable) {
        return new BatchState(batchId, state.onFailure(retryable));
    }

    public BatchState rerollFromTempFailed() {
        return new BatchState(batchId, state.rerollFromTempFailed());
    }
}
