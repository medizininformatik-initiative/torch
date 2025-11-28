package de.medizininformatikinitiative.torch.jobhandling;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

/**
 * Manages the state of the batch.
 * On creation a matching batch ndjson is created with all ids called "[batchid]-patients.ndjson".
 * A finished batch state requires the persistence of the result ndjson named "[batchid]-result.ndjson"
 * and a core resource group relation  "[batchid]-core.ndjson" ndjson.
 *
 * @param batchId    unique ID of the batch
 * @param status     status of the batch processing
 * @param startedAt  time of starting
 * @param finishedAt time of finishing
 * @param retry
 */
public record BatchState(UUID batchId,
                         WorkUnitStatus status,
                         Optional<Instant> startedAt,
                         Optional<Instant> finishedAt, int retry) {

    public BatchState(UUID batchId,
                      WorkUnitStatus status,
                      Optional<Instant> startedAt,
                      Optional<Instant> finishedAt) {
        this(batchId, status, startedAt, finishedAt, 0);
    }

    public BatchState {
        requireNonNull(batchId);
        requireNonNull(status);

        startedAt = Optional.ofNullable(startedAt).orElse(Optional.empty());
        finishedAt = Optional.ofNullable(finishedAt).orElse(Optional.empty());
    }

    public BatchState updateStatus(WorkUnitStatus newStatus) {
        Optional<Instant> newFinished = finishedAt;
        if (newStatus == WorkUnitStatus.FINISHED || newStatus == WorkUnitStatus.FAILED) {
            newFinished = Optional.of(Instant.now());
        }
        return new BatchState(batchId, newStatus, startedAt, newFinished);
    }

    public BatchState updateRetry() {
        return new BatchState(batchId, status, startedAt, finishedAt, retry + 1);
    }
}
