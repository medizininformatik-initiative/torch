package de.medizininformatikinitiative.torch.jobhandling;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.medizininformatikinitiative.torch.jobhandling.result.BatchResult;
import de.medizininformatikinitiative.torch.jobhandling.result.CoreResult;
import de.medizininformatikinitiative.torch.jobhandling.workunit.CreateBatchesWorkUnit;
import de.medizininformatikinitiative.torch.jobhandling.workunit.ProcessBatchWorkUnit;
import de.medizininformatikinitiative.torch.jobhandling.workunit.ProcessCoreWorkUnit;
import de.medizininformatikinitiative.torch.jobhandling.workunit.WorkUnit;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedCrtdl;

import java.io.IOException;
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

    static final int MAX_BATCH_PROCESSING_RETRIES = 3;
    static final int MAX_JOB_STORAGE_RETRIES = 2;

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

    public static Job createInitialJob(AnnotatedCrtdl crtdl, List<String> patientIds, UUID jobId) {
        return new Job(
                jobId,
                JobStatus.PENDING,
                Map.of(),
                Instant.now(),
                Instant.now(),
                Optional.empty(),
                List.of(),
                new JobParameters(crtdl, patientIds),
                JobPriority.NORMAL,
                0
        );
    }

    public static Job onBatchesCreated(Job job, Map<UUID, BatchState> initialStates) {
        // init batches and move to PROCESS_BATCH
        return job.initBatches(initialStates)
                .updateStatus(JobStatus.RUNNING_PROCESS_BATCH);
    }

    public static Job onBatchProcessingSuccess(Job job, BatchResult result) {
        BatchState updatedState = result.batchState();

        Job updated = job
                .updateBatch(updatedState)
                .updateIssues(result.issues());

        boolean allDone = updated.batches().values().stream()
                .allMatch(bs ->
                        bs.status() == WorkUnitStatus.FINISHED ||
                                bs.status() == WorkUnitStatus.SKIPPED
                );

        if (allDone) {
            return updated.updateStatus(JobStatus.RUNNING_PROCESS_CORE)
                    .updateBatch(new BatchState(UUID.fromString("core"), WorkUnitStatus.IN_PROGRESS, Optional.ofNullable(Instant.now()), Optional.empty()));
        }

        return updated;
    }

    public static Job onBatchError(Job job, UUID batchId, Exception e, List<Issue> issues) {
        BatchState bs = job.batches().get(batchId);
        if (bs == null) {
            throw new IllegalStateException("Missing batch " + batchId + " in job " + job.id());
        }

        boolean retriable = isRetriableError(e);

        if (retriable && bs.retry() + 1 < MAX_BATCH_PROCESSING_RETRIES) {
            issues.add(new Issue(Severity.WARNING,
                    "Retrying batch " + batchId + " due to " + e.getMessage(), e));
            BatchState incremented = bs.incrementRetry();
            return job.updateBatch(incremented).updateIssues(issues);
        }

        // retries exhausted OR non-retriable → fail job
        issues.add(new Issue(Severity.ERROR,
                "Batch " + batchId + " failed: " + e.getMessage(), e));

        return job.updateStatus(JobStatus.FAILED)
                .updateIssues(issues);
    }

    public static Job onJobError(Job job, Exception e, List<Issue> issues) {
        boolean retriable = isRetriableError(e);

        if (retriable && job.retry() + 1 < MAX_JOB_STORAGE_RETRIES) {
            issues.add(new Issue(Severity.WARNING,
                    "Retrying job " + job.id() + " due to " + e.getMessage(), e));
            return job.incrementRetry().updateIssues(issues);
        }

        issues.add(new Issue(Severity.ERROR,
                "Job " + job.id() + " failed: " + e.getMessage(), e));

        return job.updateStatus(JobStatus.FAILED)
                .updateIssues(issues);
    }

    public static Job onCoreSuccess(Job job, CoreResult result) {
        BatchState coreBatch = job.batches().get(UUID.fromString("core"));
        return job.updateStatus(JobStatus.COMPLETED).updateBatch(coreBatch.updateStatus(WorkUnitStatus.FINISHED)).updateIssues(result.issues());
    }

    private static boolean isRetriableError(Exception e) {
        return e instanceof IOException;
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

    public Job incrementRetry() {
        return new Job(id, status, batches, startedAt, updatedAt, finishedAt, issues, parameters, priority, retry + 1);
    }

    public Job updateStatus(JobStatus newStatus) {
        Optional<Instant> newFinishedAt = finishedAt;
        if (newStatus.equals(JobStatus.COMPLETED)) {
            newFinishedAt = Optional.of(Instant.now());
        }
        return new Job(id, newStatus, batches, startedAt, Instant.now(), newFinishedAt, issues, parameters, priority, 0);
    }

    public Job updateIssues(List<Issue> newIssues) {
        List<Issue> merged = new ArrayList<>(issues);  // old issues
        merged.addAll(newIssues);
        return new Job(id, status, batches, startedAt, Instant.now(), finishedAt, merged, parameters, priority, retry);
    }

    public Optional<UUID> getNextBatch() {
        return batches().values().stream()
                .filter(state -> state.status() != WorkUnitStatus.IN_PROGRESS)
                .filter(state -> state.status() != WorkUnitStatus.FINISHED)
                .map(BatchState::batchId)
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
