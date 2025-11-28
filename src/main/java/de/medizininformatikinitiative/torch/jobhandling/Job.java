package de.medizininformatikinitiative.torch.jobhandling;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.medizininformatikinitiative.torch.jobhandling.workunit.CreateBatchesWorkUnit;
import de.medizininformatikinitiative.torch.jobhandling.workunit.ProcessBatchWorkUnit;
import de.medizininformatikinitiative.torch.jobhandling.workunit.ProcessCoreWorkUnit;
import de.medizininformatikinitiative.torch.jobhandling.workunit.WorkUnit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Job(UUID id, JobStatus status, Map<UUID, BatchState> batches, Instant startedAt, Instant updatedAt,
                  Optional<Instant> finishedAt, List<Issue> issues,
                  JobParameters parameters, JobPriority priority, int retry) {

    public Job {
        Objects.requireNonNull(id);
        Objects.requireNonNull(status);
        batches = Map.copyOf(batches);
        Objects.requireNonNull(startedAt);
        Objects.requireNonNull(updatedAt);
        Objects.requireNonNull(finishedAt);
        issues = List.copyOf(issues);
        Objects.requireNonNull(parameters);
    }

    public Job initBatches(Map<UUID, BatchState> batches) {
        return new Job(id, JobStatus.RUNNING_PROCESS_BATCH, batches, startedAt, Instant.now(), finishedAt, issues, parameters, priority, 0);
    }

    public Job updateBatch(BatchState batch) {
        Map<UUID, BatchState> newBatches = new HashMap<>(batches);
        newBatches.put(batch.batchId(), batch);

        JobStatus newStatus = status;

        boolean anyFailed = newBatches.values().stream()
                .anyMatch(bs -> bs.status() == WorkUnitStatus.FAILED);

        if (anyFailed) {
            newStatus = JobStatus.FAILED;
        } else {

            boolean allDone = newBatches.values().stream()
                    .allMatch(bs ->
                            bs.status() == WorkUnitStatus.FINISHED ||
                                    bs.status() == WorkUnitStatus.SKIPPED
                    );

            if (allDone) {
                newStatus = JobStatus.RUNNING_PROCESS_CORE;
            }
        }

        return new Job(id, newStatus, newBatches, startedAt, Instant.now(), finishedAt, issues, parameters, priority, 0);
    }

    public Job updateRetry() {
        return new Job(id, status, batches, startedAt, updatedAt, finishedAt, issues, parameters, priority, retry + 1);
    }

    public Job updateStatus(JobStatus newStatus) {
        Optional<Instant> newFinishedAt = finishedAt;
        if (newStatus.equals(JobStatus.COMPLETED)) {
            newFinishedAt = Optional.of(Instant.now());
        }
        return new Job(id, newStatus, batches, startedAt, Instant.now(), newFinishedAt, issues, parameters, priority, 0);
    }

    public boolean hasBatches() {
        return batches != null && !batches.isEmpty();
    }

    public boolean allBatchesFinished() {
        return hasBatches() && batches.values().stream()
                .allMatch(b -> b.status() == WorkUnitStatus.FINISHED);
    }

    public boolean hasUnfinishedBatches() {
        return hasBatches() && batches.values().stream()
                .anyMatch(b -> b.status() != WorkUnitStatus.FINISHED);
    }

    public Job updateIssues(List<Issue> newIssues) {
        List<Issue> merged = new ArrayList<>(issues);  // old issues
        merged.addAll(newIssues);
        return new Job(id, status, batches, startedAt, Instant.now(), finishedAt, merged, parameters, priority, retry);
    }

    public Optional<BatchState> getNextBatch() {
        return batches().values().stream()
                .filter(state -> state.status() != WorkUnitStatus.IN_PROGRESS)
                .filter(state -> state.status() != WorkUnitStatus.FINISHED)
                .findFirst();
    }

    public Optional<WorkUnit> selectNextWorkUnit() {
        return switch (status) {
            case PENDING, RUNNING_CREATE_BATCHES ->
                    Optional.of(new CreateBatchesWorkUnit(updateStatus(JobStatus.RUNNING_CREATE_BATCHES)));
            case RUNNING_PROCESS_BATCH ->
                    Optional.of(new ProcessBatchWorkUnit(updateStatus(JobStatus.RUNNING_PROCESS_BATCH), getNextBatch().get()));
            case RUNNING_PROCESS_CORE ->
                    Optional.of(new ProcessCoreWorkUnit(updateStatus(JobStatus.RUNNING_PROCESS_CORE)));
            default -> Optional.empty();
        };
    }

}
