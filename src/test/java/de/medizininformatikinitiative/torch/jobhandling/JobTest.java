package de.medizininformatikinitiative.torch.jobhandling;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.medizininformatikinitiative.torch.TestUtils;
import de.medizininformatikinitiative.torch.exceptions.StateConflictException;
import de.medizininformatikinitiative.torch.jobhandling.failure.Issue;
import de.medizininformatikinitiative.torch.jobhandling.failure.Severity;
import de.medizininformatikinitiative.torch.jobhandling.result.BatchResult;
import de.medizininformatikinitiative.torch.jobhandling.result.CoreResult;
import de.medizininformatikinitiative.torch.jobhandling.workunit.ProcessBatchWorkUnit;
import de.medizininformatikinitiative.torch.jobhandling.workunit.ProcessCohortWorkUnit;
import de.medizininformatikinitiative.torch.jobhandling.workunit.ProcessCoreWorkUnit;
import de.medizininformatikinitiative.torch.jobhandling.workunit.WorkUnit;
import de.medizininformatikinitiative.torch.jobhandling.workunit.WorkUnitState;
import de.medizininformatikinitiative.torch.jobhandling.workunit.WorkUnitStatus;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JobTest {

    public static Job job(UUID id, JobStatus status, WorkUnitState cohortState,
                          Map<UUID, BatchState> batches, WorkUnitState coreState) {
        Instant now = Instant.now();
        return new Job(id, status, cohortState, 0, batches, now, now,
                Optional.empty(), List.of(),
                new JobParameters(
                        new de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedCrtdl(
                                new com.fasterxml.jackson.databind.node.JsonNodeFactory(false).objectNode(),
                                new de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedDataExtraction(List.of()),
                                Optional.empty()
                        ),
                        List.of()
                ),
                JobPriority.NORMAL, coreState, 0L);
    }

    private static BatchResult finishedBatch(Job job, BatchState state) {
        return new BatchResult(
                job.id(),
                state.batchId(),
                state.finishNow(WorkUnitStatus.FINISHED),
                Optional.empty(),
                Optional.empty(),
                List.of()
        );
    }

    private static CoreResult finishedCore(Job job) {
        return new CoreResult(
                job.id(),
                List.of(),
                WorkUnitStatus.FINISHED
        );
    }

    @Test
    void deserializingJobWithoutVersion() throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .registerModule(new Jdk8Module());

        Job original = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams());

        ObjectNode tree = mapper.valueToTree(original);
        tree.remove("version");

        Job deserialized = mapper.treeToValue(tree, Job.class);

        assertThat(deserialized.version()).isZero();
    }

    @Test
    void negativeVersion_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new Job(
                UUID.randomUUID(),
                JobStatus.PENDING,
                WorkUnitState.initNow(),
                0,
                java.util.Map.of(),
                Instant.now(),
                Instant.now(),
                Optional.empty(),
                List.of(),
                TestUtils.emptyJobParams(),
                JobPriority.NORMAL,
                WorkUnitState.initNow(),
                -1L
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("version must not be negative");
    }

    @Nested
    class SelectNextWorkUnitTests {

        @Test
        void pending_returnsGetCohort_andStagesJobToRunningGetCohort() {
            Job j = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams());

            Optional<WorkUnit> wuOpt = j.selectNextWorkUnit();

            assertThat(wuOpt).isPresent();
            assertThat(wuOpt.get()).isInstanceOf(ProcessCohortWorkUnit.class);

            ProcessCohortWorkUnit wu = (ProcessCohortWorkUnit) wuOpt.get();
            assertThat(wu.job().status()).isEqualTo(JobStatus.RUNNING_GET_COHORT);
            assertThat(wu.job().cohortState().status()).isEqualTo(WorkUnitStatus.INIT);
        }

        @Test
        void runningProcessBatch_withInitBatch_returnsProcessBatch_forFirstInitBatch() {
            UUID b1 = UUID.randomUUID();
            UUID b2 = UUID.randomUUID();

            Job j = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.RUNNING_PROCESS_BATCH)
                    .withBatchState(new BatchState(b1, WorkUnitState.initNow()))
                    .withBatchState(new BatchState(b2, WorkUnitState.startNow()));

            assertThat(j.calculateBatchProgress()).isZero();

            Optional<WorkUnit> wuOpt = j.selectNextWorkUnit();

            assertThat(wuOpt).isPresent();
            assertThat(wuOpt.get()).isInstanceOf(ProcessBatchWorkUnit.class);

            ProcessBatchWorkUnit wu = (ProcessBatchWorkUnit) wuOpt.get();
            assertThat(wu.batchId()).isEqualTo(b1);
            assertThat(wu.job().status()).isEqualTo(JobStatus.RUNNING_PROCESS_BATCH);
        }

        @Test
        void runningProcessBatch_withoutInitBatch_returnsEmpty() {
            UUID b1 = UUID.randomUUID();
            BatchState finished = new BatchState(b1, WorkUnitState.initNow().finishNow(WorkUnitStatus.FINISHED));

            Job j = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.RUNNING_PROCESS_BATCH)
                    .withBatchState(finished);

            assertThat(j.selectNextWorkUnit()).isEmpty();
        }

        @Test
        void runningProcessCore_withCoreInit_returnsProcessCore_andStagesCoreToInProgress() {
            Job j = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.RUNNING_PROCESS_CORE);

            Optional<WorkUnit> wuOpt = j.selectNextWorkUnit();

            assertThat(wuOpt).isPresent();
            assertThat(wuOpt.get()).isInstanceOf(ProcessCoreWorkUnit.class);

            ProcessCoreWorkUnit wu = (ProcessCoreWorkUnit) wuOpt.get();
            assertThat(wu.job().status()).isEqualTo(JobStatus.RUNNING_PROCESS_CORE);
            assertThat(wu.job().coreState().status()).isEqualTo(WorkUnitStatus.IN_PROGRESS);
        }

        @Test
        void runningProcessCore_withCoreNotInit_returnsEmpty() {
            Job j = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.RUNNING_PROCESS_CORE)
                    .withCoreState(WorkUnitState.startNow());

            assertThat(j.calculateBatchProgress()).isZero();
            assertThat(j.selectNextWorkUnit()).isEmpty();
        }

        @Test
        void finalStates_returnEmpty() {
            Job failed = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams()).withStatus(JobStatus.FAILED);
            Job tempFailed = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams()).withStatus(JobStatus.TEMP_FAILED);
            Job completed = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams()).withStatus(JobStatus.COMPLETED);

            assertThat(failed.selectNextWorkUnit()).isEmpty();
            assertThat(tempFailed.selectNextWorkUnit()).isEmpty();
            assertThat(completed.selectNextWorkUnit()).isEmpty();
        }
    }

    @Nested
    class OnBatchProcessingSuccessTests {

        @Test
        void whenStatusIsNotRunningProcessBatch_noop() {
            UUID batchId = UUID.randomUUID();
            BatchState init = new BatchState(batchId, WorkUnitState.initNow());

            Job job = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.TEMP_FAILED)
                    .withBatchState(init);

            BatchResult result = finishedBatch(job, init);

            Job updated = job.onBatchProcessingSuccess(result);

            assertThat(updated).isSameAs(job);
        }

        @Test
        void whenLastBatchFinishes_transitionsToRunningProcessCore_andResetsCoreStateToInit() {
            UUID b1 = UUID.randomUUID();
            UUID b2 = UUID.randomUUID();

            BatchState init1 = new BatchState(b1, WorkUnitState.initNow());
            BatchState init2 = new BatchState(b2, WorkUnitState.initNow());

            Job j = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.RUNNING_PROCESS_BATCH)
                    .withCoreState(WorkUnitState.startNow())
                    .withBatchState(init1)
                    .withBatchState(init2);
            BatchResult r1 = finishedBatch(j, init1);
            BatchResult r2 = finishedBatch(j, init2);


            Job after1 = j.onBatchProcessingSuccess(r1);
            assertThat(after1.status()).isEqualTo(JobStatus.RUNNING_PROCESS_BATCH);

            Job after2 = after1.onBatchProcessingSuccess(r2);
            assertThat(after2.calculateBatchProgress()).isEqualTo(100);
            assertThat(after2.status()).isEqualTo(JobStatus.RUNNING_PROCESS_CORE);
            assertThat(after2.coreState().status()).isEqualTo(WorkUnitStatus.INIT);
        }

        @Test
        void whenNotAllBatchesDone_staysInRunningProcessBatch() {
            UUID b1 = UUID.randomUUID();
            UUID b2 = UUID.randomUUID();

            BatchState init1 = new BatchState(b1, WorkUnitState.initNow());
            BatchState init2 = new BatchState(b2, WorkUnitState.initNow());

            Job j = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.RUNNING_PROCESS_BATCH)
                    .withBatchState(init1)
                    .withBatchState(init2);

            BatchResult r1 = finishedBatch(j, init1);
            Job after = j.onBatchProcessingSuccess(r1);

            assertThat(after.status()).isEqualTo(JobStatus.RUNNING_PROCESS_BATCH);
            assertThat(after.batches().get(b1).status()).isEqualTo(WorkUnitStatus.FINISHED);
            assertThat(after.batches().get(b2).status()).isEqualTo(WorkUnitStatus.INIT);
        }
    }

    @Nested
    class Rollback {

        private static WorkUnitState done() {
            return WorkUnitState.initNow().finishNow(WorkUnitStatus.FINISHED);
        }

        private static WorkUnitState failed() {
            return WorkUnitState.initNow().markFailed();
        }

        @Test
        void runningGetCohortRollsBackToPending() {
            Job j = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.RUNNING_GET_COHORT);

            Job rolled = j.rollback();

            assertThat(rolled.status()).isEqualTo(JobStatus.PENDING);
            assertThat(rolled.cohortState().status()).isEqualTo(WorkUnitStatus.INIT);
        }

        @Test
        void resetsCohortStateToInitAndStatusToPending() {
            Job j = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.RUNNING_GET_COHORT)
                    .withCohortState(WorkUnitState.startNow());

            Job rolled = j.rollback();

            assertThat(rolled.status()).isEqualTo(JobStatus.PENDING);
            assertThat(rolled.cohortState().status()).isEqualTo(WorkUnitStatus.INIT);
        }

        @Test
        void resetsCoreToInitButKeepsStage() {
            Job j = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.RUNNING_PROCESS_CORE)
                    .withCoreState(WorkUnitState.startNow());

            Job rolled = j.rollback();

            assertThat(rolled.status()).isEqualTo(JobStatus.RUNNING_PROCESS_CORE);
            assertThat(rolled.coreState().status()).isEqualTo(WorkUnitStatus.INIT);
        }

        @Test
        void rerollsAllInProgressBatchesToInitAndKeepsStage() {
            UUID b1 = UUID.randomUUID();
            UUID b2 = UUID.randomUUID();

            Job j = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.RUNNING_PROCESS_BATCH)
                    .withBatchState(new BatchState(b1, WorkUnitState.startNow()))
                    .withBatchState(new BatchState(b2, WorkUnitState.initNow()));

            Job rolled = j.rollback();

            assertThat(rolled.status()).isEqualTo(JobStatus.RUNNING_PROCESS_BATCH);
            assertThat(rolled.batches().get(b1).status()).isEqualTo(WorkUnitStatus.INIT);
            assertThat(rolled.batches().get(b2).status()).isEqualTo(WorkUnitStatus.INIT);
        }

        @Test
        void doesNotChangeInitStates() {
            UUID b1 = UUID.randomUUID();

            Job j = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.RUNNING_PROCESS_BATCH)
                    .withBatchState(new BatchState(b1, WorkUnitState.initNow()));

            Job rolled = j.rollback();

            assertThat(rolled.status()).isEqualTo(JobStatus.RUNNING_PROCESS_BATCH);
            assertThat(rolled.cohortState().status()).isEqualTo(WorkUnitStatus.INIT);
            assertThat(rolled.coreState().status()).isEqualTo(WorkUnitStatus.INIT);
            assertThat(rolled.batches().get(b1).status()).isEqualTo(WorkUnitStatus.INIT);
        }

        @Test
        void reemitsCohortWorkUnit() {
            Job crashed = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.RUNNING_GET_COHORT)
                    .withCohortState(WorkUnitState.startNow());

            Job rolled = crashed.rollback();
            assertThat(rolled.status()).isEqualTo(JobStatus.PENDING);

            Optional<WorkUnit> wuOpt = rolled.selectNextWorkUnit();
            assertThat(wuOpt).isPresent();
            assertThat(wuOpt.get()).isInstanceOf(ProcessCohortWorkUnit.class);
        }

        @Test
        void reemitsCoreWorkUnitwhenCoreWasInProgress() {
            Job crashed = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.RUNNING_PROCESS_CORE)
                    .withCoreState(WorkUnitState.startNow());

            Job rolled = crashed.rollback();
            assertThat(rolled.coreState().status()).isEqualTo(WorkUnitStatus.INIT);

            Optional<WorkUnit> wuOpt = rolled.selectNextWorkUnit();
            assertThat(wuOpt).isPresent();
            assertThat(wuOpt.get()).isInstanceOf(ProcessCoreWorkUnit.class);
        }

        @Test
        void reemitsBatchWorkUnitWhenBatchWasInProgress() {
            UUID b1 = UUID.randomUUID();
            UUID b2 = UUID.randomUUID();

            Job crashed = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.RUNNING_PROCESS_BATCH)
                    .withBatchState(new BatchState(b1, WorkUnitState.startNow()))
                    .withBatchState(new BatchState(b2, WorkUnitState.initNow().finishNow(WorkUnitStatus.FINISHED)));

            Job rolled = crashed.rollback();

            Optional<WorkUnit> wuOpt = rolled.selectNextWorkUnit();
            assertThat(wuOpt).isPresent();
            assertThat(wuOpt.get()).isInstanceOf(ProcessBatchWorkUnit.class);

            ProcessBatchWorkUnit wu = (ProcessBatchWorkUnit) wuOpt.get();
            assertThat(wu.batchId()).isEqualTo(b1);
        }

        @Test
        void cohortTempFailedIncrementsCohortRetry() {
            Job j = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.TEMP_FAILED)
                    .withCohortState(WorkUnitState.initNow().markTempFailed());

            assertThat(j.cohortState().retry()).isZero();

            Job rolled = j.rollback();

            assertThat(rolled.cohortState().retry()).isEqualTo(1);
            assertThat(rolled.status()).isEqualTo(JobStatus.PENDING);
        }

        @Test
        void exhaustCohort() {
            WorkUnitState cohortTempFailedExhaustedSoon =
                    new WorkUnitState(WorkUnitStatus.TEMP_FAILED, Instant.now(), Optional.empty(), 4);

            Job j = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.TEMP_FAILED)
                    .withCohortState(cohortTempFailedExhaustedSoon);

            Job rolled = j.rollback();

            assertThat(rolled.status()).isEqualTo(JobStatus.FAILED);
            assertThat(rolled.cohortState().status()).isEqualTo(WorkUnitStatus.FAILED);
            assertThat(rolled.issues())
                    .anyMatch(i -> i.msg().contains("Retries exhausted after reroll")
                            && i.msg().contains("cohort"));
        }

        @Test
        void exhaustCore() {
            WorkUnitState coreTempFailedExhaustedSoon =
                    new WorkUnitState(WorkUnitStatus.TEMP_FAILED, Instant.now(), Optional.empty(), 4);

            Job j = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.TEMP_FAILED)
                    .withCohortState(done())
                    .withCoreState(coreTempFailedExhaustedSoon);

            Job rolled = j.rollback();

            assertThat(rolled.status()).isEqualTo(JobStatus.FAILED);
            assertThat(rolled.coreState().status()).isEqualTo(WorkUnitStatus.FAILED);
            assertThat(rolled.issues())
                    .anyMatch(i -> i.msg().contains("Retries exhausted after reroll")
                            && i.msg().contains("core"));
        }

        @Test
        void exhaustBatch() {
            UUID b1 = UUID.randomUUID();
            WorkUnitState batchTempFailedExhaustedSoon =
                    new WorkUnitState(WorkUnitStatus.TEMP_FAILED, Instant.now(), Optional.empty(), 4);

            Job j = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.TEMP_FAILED)
                    .withCohortState(done())
                    .withCoreState(done())
                    .withBatchState(new BatchState(b1, batchTempFailedExhaustedSoon));

            Job rolled = j.rollback();

            assertThat(rolled.status()).isEqualTo(JobStatus.FAILED);
            assertThat(rolled.batches().get(b1).status()).isEqualTo(WorkUnitStatus.FAILED);
            assertThat(rolled.issues())
                    .anyMatch(i -> i.msg().contains("Retries exhausted after reroll")
                            && i.msg().contains("batch " + b1));
        }

        @Test
        void tempFailedToBatch() {
            UUID b1 = UUID.randomUUID();
            UUID b2 = UUID.randomUUID();

            Job j = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.TEMP_FAILED)
                    .withCohortState(done())
                    .withBatchState(new BatchState(b1, done()))
                    .withBatchState(new BatchState(b2, WorkUnitState.initNow()));

            Job rolled = j.rollback();

            assertThat(rolled.status()).isEqualTo(JobStatus.RUNNING_PROCESS_BATCH);
        }

        @Test
        void tempFailedToCore() {
            UUID b1 = UUID.randomUUID();

            Job j = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.TEMP_FAILED)
                    .withCohortState(done())
                    .withBatchState(new BatchState(b1, done()));

            Job rolled = j.rollback();

            assertThat(rolled.status()).isEqualTo(JobStatus.RUNNING_PROCESS_CORE);
        }

        @Test
        void tempFailedToCompleted() {
            UUID b1 = UUID.randomUUID();

            Job j = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.TEMP_FAILED)
                    .withCohortState(done())
                    .withCoreState(done())
                    .withBatchState(new BatchState(b1, done()));

            Job rolled = j.rollback();

            assertThat(rolled.status()).isEqualTo(JobStatus.COMPLETED);
        }

        @Test
        void cohortFailedToFailed() {
            Job j = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.TEMP_FAILED)
                    .withCohortState(failed());

            Job rolled = j.rollback();

            assertThat(rolled.status()).isEqualTo(JobStatus.FAILED);
        }

        @Test
        void coreFailedToFailed() {
            Job j = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.TEMP_FAILED)
                    .withCohortState(done())
                    .withCoreState(failed());

            Job rolled = j.rollback();

            assertThat(rolled.status()).isEqualTo(JobStatus.FAILED);
        }

        @Test
        void batchFailedToFailed() {
            UUID b1 = UUID.randomUUID();

            Job j = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.TEMP_FAILED)
                    .withCohortState(done())
                    .withBatchState(new BatchState(b1, failed()));

            Job rolled = j.rollback();

            assertThat(rolled.status()).isEqualTo(JobStatus.FAILED);
        }

        @Test
        void rerollUnitTempFailed() {
            UUID b1 = UUID.randomUUID();

            Job j = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.TEMP_FAILED)
                    .withCohortState(WorkUnitState.initNow().markTempFailed())
                    .withCoreState(WorkUnitState.initNow().markTempFailed())
                    .withBatchState(new BatchState(b1, WorkUnitState.initNow().markTempFailed()));

            Job rolled = j.rollback();

            assertThat(rolled.cohortState().status()).isEqualTo(WorkUnitStatus.INIT);
            assertThat(rolled.coreState().status()).isEqualTo(WorkUnitStatus.INIT);
            assertThat(rolled.batches().get(b1).status()).isEqualTo(WorkUnitStatus.INIT);
            assertThat(rolled.status()).isEqualTo(JobStatus.PENDING);
        }
    }

    @Nested
    class ErrorPathTests {

        private final List<Issue> sampleIssues = List.of(new Issue(Severity.WARNING, "Test Issue", ""));

        @Test
        void onCohortError_retryable_transitionsToTempFailed() {
            Job j = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.RUNNING_GET_COHORT)
                    .withCohortState(WorkUnitState.startNow());

            Job updated = j.onCohortError(new IOException("Retryable"), sampleIssues);

            assertThat(updated.status()).isEqualTo(JobStatus.TEMP_FAILED);
            assertThat(updated.cohortState().status()).isEqualTo(WorkUnitStatus.TEMP_FAILED);
            assertThat(updated.issues()).containsAll(sampleIssues);
        }

        @Test
        void onBatchError_missingBatchId_returnsFailedWithNewIssue() {
            UUID unknownId = UUID.randomUUID();

            Job j = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.RUNNING_PROCESS_BATCH);

            Job updated = j.onBatchError(unknownId, new Exception("Boom"), List.of());

            assertThat(updated.status()).isEqualTo(JobStatus.FAILED);
            assertThat(updated.issues())
                    .anyMatch(i -> i.msg().contains("Missing batch") && i.msg().contains(unknownId.toString()));
        }

        @Test
        void onBatchError_retryable_updatesBatchStateAndTempFails() {
            UUID b1 = UUID.randomUUID();

            Job j = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.RUNNING_PROCESS_BATCH)
                    .withBatchState(new BatchState(b1, WorkUnitState.startNow()));

            Job updated = j.onBatchError(b1, new IOException("Retryable"), sampleIssues);

            assertThat(updated.status()).isEqualTo(JobStatus.TEMP_FAILED);
            assertThat(updated.batches().get(b1).status()).isEqualTo(WorkUnitStatus.TEMP_FAILED);
            assertThat(updated.issues()).containsAll(sampleIssues);
        }

        @Test
        void onCoreError_retryable_addsAutoGeneratedIssueAndTempFails() {
            String errorMsg = "Core failed message";

            Job j = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.RUNNING_PROCESS_CORE)
                    .withCoreState(WorkUnitState.startNow());

            Job updated = j.onCoreError(new IOException(errorMsg), sampleIssues);

            assertThat(updated.status()).isEqualTo(JobStatus.TEMP_FAILED);
            assertThat(updated.coreState().status()).isEqualTo(WorkUnitStatus.TEMP_FAILED);
            assertThat(updated.issues()).hasSize(2);
            assertThat(updated.issues()).anyMatch(i -> i.msg().contains("CoreState failed: " + errorMsg));
        }

        @Test
        void onCoreError_nonRetryable_failsImmediately() {
            Job j = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.RUNNING_PROCESS_CORE)
                    .withCoreState(WorkUnitState.startNow());

            Job updated = j.onCoreError(new Error("Fatal"), List.of());

            assertThat(updated.status()).isEqualTo(JobStatus.FAILED);
            assertThat(updated.coreState().status()).isEqualTo(WorkUnitStatus.FAILED);
        }

        @Test
        void onJobError_retryable_addsIssueAndTempFails() {
            Job j = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.RUNNING_PROCESS_BATCH);

            Job updated = j.onJobError(new IOException("DB Connection Timeout"), List.of());

            assertThat(updated.status()).isEqualTo(JobStatus.TEMP_FAILED);
            assertThat(updated.issues()).anyMatch(i ->
                    i.severity() == Severity.WARNING
                            && i.msg().contains("Infrastructure/persistence error"));
        }

        @Test
        void onJobError_nonRetryable_addsErrorIssueAndFails() {
            Job j = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.RUNNING_PROCESS_BATCH);

            Job updated = j.onJobError(new Error("Disk Full"), List.of());

            assertThat(updated.status()).isEqualTo(JobStatus.FAILED);
            assertThat(updated.issues()).anyMatch(i ->
                    i.severity() == Severity.ERROR
                            && i.msg().contains("Infrastructure/persistence error"));
        }

        @Test
        void onCohortErrorNoop() {
            Job job = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams());

            Job updated = job.onCohortError(
                    new IllegalArgumentException("boom"),
                    List.of()
            );

            assertThat(updated).isSameAs(job);
        }

        @Test
        void onBatchErrorNoop() {
            UUID batchId = UUID.randomUUID();

            Job job = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.TEMP_FAILED)
                    .withBatchState(new BatchState(batchId, WorkUnitState.startNow()));

            Job updated = job.onBatchError(
                    batchId,
                    new IllegalArgumentException("boom"),
                    List.of()
            );

            assertThat(updated).isSameAs(job);
        }

        @Test
        void onCoreErrorNoop() {
            Job job = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.COMPLETED);

            Job updated = job.onCoreError(
                    new IllegalArgumentException("boom"),
                    List.of()
            );

            assertThat(updated).isSameAs(job);
        }
    }

    @Nested
    class CoreSuccessTests {

        @Test
        void whenStatusIsNotRunningProcessCore_noop() {
            Job job = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.FAILED);

            CoreResult result = finishedCore(job);

            Job updated = job.onCoreSuccess(result);

            assertThat(updated).isSameAs(job);
        }

        @Test
        void transitionsToCompleted() {
            Job j = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.RUNNING_PROCESS_CORE)
                    .withCohortState(WorkUnitState.initNow().finishNow(WorkUnitStatus.FINISHED))
                    .withCoreState(WorkUnitState.startNow());
            CoreResult result = finishedCore(j);

            Job completedJob = j.onCoreSuccess(result);

            assertThat(completedJob.status()).isEqualTo(JobStatus.COMPLETED);
            assertThat(completedJob.coreState().status()).isEqualTo(WorkUnitStatus.FINISHED);
            assertThat(completedJob.coreState().finishedAt()).isPresent();
        }
    }

    @Nested
    class OnCohortSuccessTests {

        @Test
        void cohortSuccessApplies() {
            UUID batchId = UUID.randomUUID();

            Job job = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.RUNNING_GET_COHORT)
                    .withCohortState(WorkUnitState.startNow());

            Job updated = job.onCohortSuccess(
                    java.util.Map.of(batchId, new BatchState(batchId, WorkUnitState.initNow())),
                    1
            );

            assertThat(updated).isNotSameAs(job);
            assertThat(updated.status()).isEqualTo(JobStatus.RUNNING_PROCESS_BATCH);
            assertThat(updated.cohortSize()).isEqualTo(1);
            assertThat(updated.batches()).containsKey(batchId);
        }

        @Test
        void cohortSuccessEmptySkipsToCore() {
            Job job = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.RUNNING_GET_COHORT)
                    .withCohortState(WorkUnitState.startNow());

            Job updated = job.onCohortSuccess(java.util.Map.of(), 0);

            assertThat(updated).isNotSameAs(job);
            assertThat(updated.status()).isEqualTo(JobStatus.RUNNING_PROCESS_CORE);
            assertThat(updated.issues()).anyMatch(i -> i.msg().contains("Empty cohort"));
        }

        @Test
        void cohortSuccessNoop() {
            UUID batchId = UUID.randomUUID();

            Job job = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams());

            Job updated = job.onCohortSuccess(
                    java.util.Map.of(batchId, new BatchState(batchId, WorkUnitState.initNow())),
                    1
            );

            assertThat(updated).isSameAs(job);
        }
    }

    @Nested
    class Pause {

        @ParameterizedTest
        @EnumSource(value = JobStatus.class, names = {
                "PENDING",
                "RUNNING_GET_COHORT",
                "RUNNING_PROCESS_BATCH",
                "RUNNING_PROCESS_CORE",
                "TEMP_FAILED"
        })
        void changed(JobStatus before) throws StateConflictException {
            Job job = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(before);

            Job result = job.pause();

            assertThat(result).isNotSameAs(job);
            assertThat(result.status()).isEqualTo(JobStatus.PAUSED);
        }

        @Test
        void noop() throws StateConflictException {
            Job job = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.PAUSED);

            Job result = job.pause();

            assertThat(result).isSameAs(job);
        }

        @ParameterizedTest
        @EnumSource(value = JobStatus.class, names = {
                "COMPLETED",
                "FAILED",
                "CANCELLED",
                "DELETED"
        })
        void conflict(JobStatus before) {
            Job job = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(before);

            assertThatThrownBy(job::pause)
                    .isInstanceOf(StateConflictException.class)
                    .hasMessageContaining(before.display());

            assertThat(job.status()).isEqualTo(before);
        }
    }

    @Nested
    class Cancel {

        @ParameterizedTest
        @EnumSource(value = JobStatus.class, names = {
                "PENDING",
                "RUNNING_GET_COHORT",
                "RUNNING_PROCESS_BATCH",
                "RUNNING_PROCESS_CORE",
                "TEMP_FAILED",
                "PAUSED"
        })
        void changed(JobStatus before) {
            Job job = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(before);

            Job result = job.cancel();
            assertThat(result.status()).isEqualTo(JobStatus.CANCELLED);
        }

        @Test
        void noop() {
            Job job = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.CANCELLED);

            Job result = job.cancel();

            assertThat(result.status()).isEqualTo(JobStatus.CANCELLED);
        }

        @ParameterizedTest
        @EnumSource(value = JobStatus.class, names = {
                "COMPLETED",
                "FAILED",
                "DELETED"
        })
        void conflict(JobStatus before) {
            Job job = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(before);

            assertThatThrownBy(job::cancel)
                    .isInstanceOf(StateConflictException.class)
                    .hasMessageContaining(job.status().display());
        }
    }

    @Nested
    class Delete {

        @Test
        void setsStatusToDeleted() {
            Job job = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams());

            Job result = job.delete();

            assertThat(result.status()).isEqualTo(JobStatus.DELETED);
        }

        @ParameterizedTest
        @EnumSource(value = JobStatus.class)
        void canDeleteFromAnyStatus(JobStatus before) {
            Job job = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams()).withStatus(before);

            assertThat(job.delete().status()).isEqualTo(JobStatus.DELETED);
        }
    }

    @Nested
    class Resume {

        @Test
        void changedFromPausedToPending() {
            Job job = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.PAUSED);

            Job result = job.resume();

            assertThat(result.status()).isEqualTo(JobStatus.PENDING);
        }

        @Test
        void changedFromPausedToBatch() {
            Job job = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.PAUSED)
                    .withCohortState(WorkUnitState.initNow().finishNow(WorkUnitStatus.FINISHED))
                    .withBatchState(new BatchState(UUID.randomUUID(), WorkUnitState.initNow()));

            Job result = job.resume();


            assertThat(result.status()).isEqualTo(JobStatus.RUNNING_PROCESS_BATCH);
        }

        @Test
        void changedFromPausedToCore() {
            Job job = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.PAUSED)
                    .withCohortState(WorkUnitState.initNow().finishNow(WorkUnitStatus.FINISHED));

            Job result = job.resume();

            assertThat(result.status()).isEqualTo(JobStatus.RUNNING_PROCESS_CORE);
        }

        @Test
        void changedFromPausedToCompleted() {
            Job job = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.PAUSED)
                    .withCohortState(WorkUnitState.initNow().finishNow(WorkUnitStatus.FINISHED))
                    .withCoreState(WorkUnitState.initNow().finishNow(WorkUnitStatus.FINISHED));

            Job result = job.resume();

            assertThat(result.status()).isEqualTo(JobStatus.COMPLETED);
        }

        @Test
        void changedFromTempFailed() {
            Job job = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.TEMP_FAILED)
                    .withCohortState(WorkUnitState.initNow().finishNow(WorkUnitStatus.FINISHED));

            Job result = job.resume();

            assertThat(result.status()).isEqualTo(JobStatus.RUNNING_PROCESS_CORE);
        }

        @ParameterizedTest
        @EnumSource(value = JobStatus.class, names = {
                "PENDING",
                "RUNNING_GET_COHORT",
                "RUNNING_PROCESS_BATCH",
                "RUNNING_PROCESS_CORE"
        })
        void noop(JobStatus before) {
            Job job = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(before);

            Job result = job.resume();

            assertThat(result.status()).isEqualTo(before);
        }

        @ParameterizedTest
        @EnumSource(value = JobStatus.class, names = {
                "COMPLETED",
                "FAILED",
                "CANCELLED",
                "DELETED"
        })
        void conflict(JobStatus before) {
            Job job = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(before);

            assertThatThrownBy(job::resume)
                    .isInstanceOf(StateConflictException.class)
                    .hasMessageContaining(job.status().display());
        }
    }
}
