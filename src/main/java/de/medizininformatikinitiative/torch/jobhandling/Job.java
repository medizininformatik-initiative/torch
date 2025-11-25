package de.medizininformatikinitiative.torch.jobhandling;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.medizininformatikinitiative.torch.jobhandling.failure.Issue;
import de.medizininformatikinitiative.torch.jobhandling.failure.RetryabilityUtil;
import de.medizininformatikinitiative.torch.jobhandling.failure.Severity;
import de.medizininformatikinitiative.torch.jobhandling.result.BatchResult;
import de.medizininformatikinitiative.torch.jobhandling.result.CoreResult;
import de.medizininformatikinitiative.torch.jobhandling.workunit.ProcessBatchWorkUnit;
import de.medizininformatikinitiative.torch.jobhandling.workunit.ProcessCohortWorkUnit;
import de.medizininformatikinitiative.torch.jobhandling.workunit.ProcessCoreWorkUnit;
import de.medizininformatikinitiative.torch.jobhandling.workunit.WorkUnit;
import de.medizininformatikinitiative.torch.jobhandling.workunit.WorkUnitState;
import de.medizininformatikinitiative.torch.jobhandling.workunit.WorkUnitStatus;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedCrtdl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static de.medizininformatikinitiative.torch.jobhandling.failure.Issue.merge;
import static java.util.Objects.requireNonNull;

/**
 * Persistent job state for a single $extract-data run.
 *
 * <p>The job acts as the orchestration state machine:
 * it tracks overall {@link JobStatus}, batch states ({@link BatchState}),
 * core state ({@link WorkUnitState}), and accumulated {@link Issue}s.</p>
 *
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Job(
        UUID id,
        JobStatus status,
        WorkUnitState cohortState,
        int cohortSize,
        Map<UUID, BatchState> batches,
        Instant startedAt,
        Instant updatedAt,
        Optional<Instant> finishedAt,
        List<Issue> issues,
        JobParameters parameters,
        JobPriority priority,
        WorkUnitState coreState
) {

    // -------------------- ctor invariants --------------------

    public Job {
        requireNonNull(id);
        requireNonNull(status);
        requireNonNull(cohortState);
        requireNonNull(batches);
        requireNonNull(startedAt);
        requireNonNull(updatedAt);
        requireNonNull(finishedAt);
        requireNonNull(issues);
        requireNonNull(parameters);
        requireNonNull(priority);
        requireNonNull(coreState);

        batches = Map.copyOf(batches);
        issues = List.copyOf(issues);
    }

    // -------------------- factories --------------------

    /**
     * Creates the initial job state for a new extraction run.
     *
     * @param crtdl      the annotated CRTDL definition
     * @param patientIds the initial cohort patient IDs
     * @param jobId      the job identifier
     * @return a new job in {@link JobStatus#PENDING} state
     */
    public static Job createInitialJob(AnnotatedCrtdl crtdl, List<String> patientIds, UUID jobId) {
        Instant now = Instant.now();
        return new Job(
                jobId,
                JobStatus.PENDING,
                WorkUnitState.initNow(),
                0,
                Map.of(),
                now,
                now,
                Optional.empty(),
                List.of(),
                new JobParameters(crtdl, patientIds),
                JobPriority.NORMAL,
                WorkUnitState.initNow()
        );
    }

    // -------------------- domain transitions (happy path) --------------------

    /**
     * Initializes batch states and transitions the job into batch processing.
     *
     * @param initialStates initial batch states
     * @param cohortSize    size of the cohort
     * @return updated job in {@link JobStatus#RUNNING_PROCESS_BATCH}
     */
    public Job onBatchesCreated(Map<UUID, BatchState> initialStates, int cohortSize) {
        return initBatches(initialStates, cohortSize)
                .withStatus(JobStatus.RUNNING_PROCESS_BATCH);
    }

    /**
     * Records a successful batch processing result.
     *
     * <p>If all batches are finished or skipped, the job transitions to
     * {@link JobStatus#RUNNING_PROCESS_CORE}.</p>
     *
     * @param result the batch processing result
     * @return updated job state
     */
    public Job onBatchProcessingSuccess(BatchResult result) {
        Job updated = withBatchState(result.batchState())
                .withIssuesAdded(result.issues());

        if (allBatchesDone(updated.batches())) {
            return updated.withStatus(JobStatus.RUNNING_PROCESS_CORE)
                    .withCoreState(WorkUnitState.initNow());
        }
        return updated;
    }

    /**
     * Records successful core processing and completes the job.
     *
     * @param result the core processing result
     * @return updated job in {@link JobStatus#COMPLETED}
     */
    public Job onCoreSuccess(CoreResult result) {
        return withStatus(JobStatus.COMPLETED)
                .withIssuesAdded(result.issues())
                .withCoreState(coreState.finishNow(result.status()));
    }

    // -------------------- domain transitions (error path) --------------------

    /**
     * Records a cohort-level failure and updates cohort and job state accordingly.
     *
     * <p>Non-retryable errors fail the job immediately. Retryable errors
     * transition the batch state via {@link WorkUnitState#onFailure(boolean)}.
     * If retries are exhausted, the job is escalated to {@link JobStatus#TEMP_FAILED}.</p>
     *
     * @param e      the failure cause
     * @param issues domain issues to attach
     * @return updated job state
     */
    public Job onCohortError(Exception e, List<Issue> issues) {
        boolean retryable = RetryabilityUtil.isRetryable(e);
        WorkUnitState out = cohortState.onFailure(retryable);
        Job updated = withCohortState(out)
                .withIssuesAdded(issues);
        if (!retryable) return updated.withStatus(JobStatus.FAILED);
        return updated.withStatus(JobStatus.TEMP_FAILED);
    }


    /**
     * Records a batch-level failure and updates batch and job state accordingly.
     *
     * <p>Non-retryable errors fail the job immediately. Retryable errors
     * transition the batch state via {@link WorkUnitState#onFailure(boolean)}.
     * If retries are exhausted, the job is escalated to {@link JobStatus#TEMP_FAILED}.</p>
     *
     * @param batchId the failed batch identifier
     * @param e       the failure cause
     * @param issues  domain issues to attach
     * @return updated job state
     */
    public Job onBatchError(UUID batchId, Throwable e, List<Issue> issues) {
        Job updated = withIssuesAdded(issues);

        BatchState bs = updated.batches().get(batchId);
        if (bs == null) {
            Issue issue = Issue.fromException(
                    Severity.ERROR,
                    "Missing batch " + batchId + " in job " + id,
                    e
            );
            return updated.withIssuesAdded(List.of(issue))
                    .withStatus(JobStatus.FAILED);
        }

        boolean retryable = RetryabilityUtil.isRetryable(e);
        BatchState out = bs.onFailure(retryable);

        Job withBatch = updated.withBatchState(out);

        if (!retryable) {
            return withBatch.withStatus(JobStatus.FAILED);
        }
        return withBatch.withStatus(JobStatus.TEMP_FAILED);
    }

    /**
     * Records a core-level failure.
     *
     * <p>Non-retryable errors fail the job immediately. Retryable errors may
     * escalate the job to {@link JobStatus#TEMP_FAILED} when retries are exhausted.</p>
     *
     * @param e      the failure cause
     * @param issues domain issues to attach
     * @return updated job state
     */
    public Job onCoreError(Throwable e, List<Issue> issues) {
        boolean retryable = RetryabilityUtil.isRetryable(e);

        WorkUnitState out = coreState.onFailure(retryable);

        List<Issue> mergedIssues = merge(
                issues,
                List.of(Issue.fromException(
                        retryable ? Severity.WARNING : Severity.ERROR,
                        "CoreState failed: " + e.getMessage(),
                        e
                ))
        );

        Job updated = withCoreState(out).withIssuesAdded(mergedIssues);

        if (!retryable) {
            return updated.withStatus(JobStatus.FAILED);
        }
        return updated.withStatus(JobStatus.TEMP_FAILED);
    }

    /**
     * Handles job-level errors that occur outside work-unit execution
     * (e.g. persistence or orchestration failures like saveCoreBatch/job.json).
     *
     * <p>Retryable infrastructure errors increment the job retry counter and move the job to
     * {@link JobStatus#TEMP_FAILED} to be resumed later by the scheduler.</p>
     *
     * <p>Non-retryable infrastructure errors fail the job immediately.</p>
     *
     * @param e      the failure cause
     * @param issues domain issues to attach
     * @return updated job state
     */
    public Job onJobError(Throwable e, List<Issue> issues) {
        boolean retryable = RetryabilityUtil.isRetryable(e);

        Severity sev = retryable ? Severity.WARNING : Severity.ERROR;

        Job updated = withIssuesAdded(merge(
                issues,
                List.of(Issue.fromException(
                        sev,
                        "Infrastructure/persistence error: " + e.getMessage(),
                        e
                ))
        ));

        if (!retryable) {
            return updated.withStatus(JobStatus.FAILED);
        }
        return updated.withStatus(JobStatus.TEMP_FAILED);
    }

    // -------------------- scheduling / selection --------------------

    public Optional<UUID> getNextBatch() {
        return batches.values().stream()
                .filter(bs -> bs.status() == WorkUnitStatus.INIT)
                .map(BatchState::batchId)
                .findFirst();
    }

    public Optional<WorkUnit> selectNextWorkUnit() {
        return switch (status) {
            case PENDING -> Optional.of(new ProcessCohortWorkUnit(
                    withCohortState(WorkUnitState.initNow()).withStatus(JobStatus.RUNNING_GET_COHORT)
            ));

            case RUNNING_PROCESS_BATCH -> {
                Optional<UUID> next = getNextBatch();
                if (next.isPresent()) {
                    yield Optional.of(new ProcessBatchWorkUnit(
                            withStatus(JobStatus.RUNNING_PROCESS_BATCH),
                            next.get()
                    ));
                }
                yield Optional.empty();
            }

            case RUNNING_PROCESS_CORE -> {
                if (coreState.status() == WorkUnitStatus.INIT) {
                    yield Optional.of(new ProcessCoreWorkUnit(
                            withStatus(JobStatus.RUNNING_PROCESS_CORE)
                                    .withCoreState(WorkUnitState.startNow())
                    ));
                }
                yield Optional.empty();
            }

            default -> Optional.empty();
        };
    }

    // -------------------- restart job --------------------

    /**
     * Rolls back unstable work after restart (or before retry).
     *
     * <p>Rules:
     * <ul>
     *   <li>If job is in {@link JobStatus#RUNNING_GET_COHORT}, it is rolled back to {@link JobStatus#PENDING}
     *       so {@link #selectNextWorkUnit()} re-emits the cohort work unit deterministically.</li>
     *   <li>Any unit substate left in {@link WorkUnitStatus#IN_PROGRESS} is rerolled to {@code INIT}.</li>
     * </ul>
     *
     * @return rolled-back job
     */
    public Job rollback() {
        if (status.isFinal()) return this;

        // 0) stable terminal substates should win (as-is)
        if (cohortState.status().isTerminalFailure()) return withStatus(JobStatus.FAILED);
        if (coreState.status().isTerminalFailure()) return withStatus(JobStatus.FAILED);
        if (batches.values().stream().anyMatch(bs -> bs.status().isTerminalFailure())) {
            return withStatus(JobStatus.FAILED);
        }

        Job updated = this;

        // 1) reroll only unstable substates:
        //    - TEMP_FAILED => retry++ and INIT (or FAILED if exhausted)
        //    - IN_PROGRESS (and other "unstable") => INIT (no retry++)
        if (updated.cohortState.status() == WorkUnitStatus.TEMP_FAILED) {
            updated = updated.withCohortState(updated.cohortState.rerollFromTempFailed());
        } else if (updated.cohortState.status().shouldRerollToInit()) {
            updated = updated.withCohortState(WorkUnitState.initNow());
        }

        if (updated.coreState.status() == WorkUnitStatus.TEMP_FAILED) {
            updated = updated.withCoreState(updated.coreState.rerollFromTempFailed());
        } else if (updated.coreState.status().shouldRerollToInit()) {
            updated = updated.withCoreState(WorkUnitState.initNow());
        }

        for (BatchState bs : updated.batches.values()) {
            if (bs.status() == WorkUnitStatus.TEMP_FAILED) {
                // requires BatchState.rerollFromTempFailed() (see note below)
                updated = updated.withBatchState(bs.rerollFromTempFailed());
            } else if (bs.status().shouldRerollToInit()) {
                updated = updated.withBatchState(bs.rerollToInit());
            }
        }

        Optional<Job> failed = failIfRetriesExhaustedAfterReroll(updated);
        if (failed.isPresent()) return failed.get();

        // 2) cohort stage always rerolls to PENDING
        if (updated.status == JobStatus.RUNNING_GET_COHORT) {
            return updated.withStatus(JobStatus.PENDING);
        }

        // 3) TEMP_FAILED resumes into inferred stage (including COMPLETED/FAILED)
        if (updated.status == JobStatus.TEMP_FAILED) {
            JobStatus inferred = updated.inferRunnableStatusFromSubstates();
            return updated.withStatus(inferred);
        }

        // 4) otherwise keep stage (but substates may now allow reselection)
        return updated;
    }

    /**
     * Fails the job if any work unit transitioned to a terminal failure state
     * as a result of a reroll (i.e. retry exhaustion).
     *
     * <p>This method is intended to be called <em>after</em> rerolling
     * {@link WorkUnitStatus#TEMP_FAILED} units via
     * {@link WorkUnitState#rerollFromTempFailed()}.</p>
     *
     * <p>If at least one substate (cohort, core, or batch) is now in a terminal
     * failure state, the job is escalated to {@link JobStatus#FAILED} and an
     * {@link Issue} is attached explaining that retries were exhausted.</p>
     *
     * <p>If no terminal failures are detected, the job is returned unchanged.</p>
     *
     * @param updated the job state after rerolling unstable substates
     * @return an {@link Optional} containing a failed job if retries were exhausted,
     * or {@link Optional#empty()} if the job may continue
     */
    private Optional<Job> failIfRetriesExhaustedAfterReroll(Job updated) {
        List<String> exhausted = new ArrayList<>();

        if (updated.cohortState.status().isTerminalFailure()) {
            exhausted.add("cohort");
        }
        if (updated.coreState.status().isTerminalFailure()) {
            exhausted.add("core");
        }
        for (BatchState bs : updated.batches.values()) {
            if (bs.status().isTerminalFailure()) {
                exhausted.add("batch " + bs.batchId());
            }
        }

        if (exhausted.isEmpty()) {
            return Optional.empty();
        }

        Issue issue = Issue.fromException(
                Severity.ERROR,
                "Retries exhausted after reroll: " + String.join(", ", exhausted),
                null
        );

        return Optional.of(
                updated.withIssuesAdded(List.of(issue))
                        .withStatus(JobStatus.FAILED)
        );
    }


    /**
     * Infers which runnable status the job should be in based purely on substates.
     * Order: cohort -> batches -> core.
     */
    private JobStatus inferRunnableStatusFromSubstates() {
        // any permanent failure => failed
        if (cohortState.status().isTerminalFailure()) return JobStatus.FAILED;
        if (coreState.status().isTerminalFailure()) return JobStatus.FAILED;
        if (batches.values().stream().anyMatch(bs -> bs.status().isTerminalFailure())) return JobStatus.FAILED;

        // cohort not done => PENDING
        if (!cohortState.status().isDone()) return JobStatus.PENDING;

        // batches not all done => process batches
        boolean batchesRemain = batches.values().stream().anyMatch(bs -> !bs.status().isDone());
        if (batchesRemain) return JobStatus.RUNNING_PROCESS_BATCH;

        // core not done => process core
        if (!coreState.status().isDone()) return JobStatus.RUNNING_PROCESS_CORE;

        return JobStatus.COMPLETED;
    }


    // -------------------- internal helpers (copy / withers) --------------------


    private boolean allBatchesDone(Map<UUID, BatchState> b) {
        return b.values().stream().allMatch(bs ->
                bs.status() == WorkUnitStatus.FINISHED || bs.status() == WorkUnitStatus.SKIPPED
        );
    }

    public Job initBatches(Map<UUID, BatchState> initialBatches, int cohortSize) {
        return new Job(
                id,
                JobStatus.RUNNING_PROCESS_BATCH,
                cohortState.finishNow(WorkUnitStatus.FINISHED),
                cohortSize,
                initialBatches,
                startedAt,
                Instant.now(),
                finishedAt,
                issues,
                parameters,
                priority,
                coreState
        );
    }

    public Job withBatchState(BatchState batch) {
        Map<UUID, BatchState> newBatches = new HashMap<>(batches);
        newBatches.put(batch.batchId(), batch);
        return new Job(id, status, cohortState, cohortSize, newBatches, startedAt, Instant.now(), finishedAt, issues, parameters, priority, coreState
        );
    }

    public Job withCoreState(WorkUnitState newState) {
        return new Job(id, status, cohortState, cohortSize, batches, startedAt, Instant.now(),
                finishedAt, issues, parameters, priority, newState);
    }

    public Job withCohortState(WorkUnitState newState) {
        return new Job(id, status, newState, cohortSize, batches, startedAt, Instant.now(),
                finishedAt, issues, parameters, priority, coreState);
    }

    public Job withIssuesAdded(List<Issue> newIssues) {
        if (newIssues.isEmpty()) return this;
        List<Issue> merged = new ArrayList<>(issues);
        merged.addAll(newIssues);
        return new Job(id, status, cohortState, cohortSize, batches, startedAt, Instant.now(),
                finishedAt, merged, parameters, priority, coreState);
    }

    public Job withStatus(JobStatus newStatus) {
        Optional<Instant> newFinishedAt = finishedAt;
        if (newStatus.isFinal()) {
            newFinishedAt = Optional.of(Instant.now());
        }
        return new Job(id, newStatus, cohortState, cohortSize, batches, startedAt, Instant.now(),
                newFinishedAt, issues, parameters, priority, coreState);
    }

    public double calculateBatchProgress() {
        if (!batches.isEmpty()) {
            long completedBatches = batches.values().stream()
                    .filter(bs -> bs.status().isDone())
                    .count();
            return ((double) completedBatches / batches.size()) * 100;
        }
        return 0;
    }

    public Job withPriority(JobPriority jobPriority) {
        return new Job(id, status, cohortState, cohortSize, batches, startedAt, Instant.now(), finishedAt, issues, parameters, jobPriority, coreState);
    }
}
