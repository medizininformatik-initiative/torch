package de.medizininformatikinitiative.torch.jobhandling;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.medizininformatikinitiative.torch.TestUtils;
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

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JobTest {

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

            BatchResult r1 = mock(BatchResult.class);
            when(r1.batchState()).thenReturn(init1.finishNow(WorkUnitStatus.FINISHED));
            when(r1.issues()).thenReturn(List.of());

            Job after1 = j.onBatchProcessingSuccess(r1);
            assertThat(after1.status()).isEqualTo(JobStatus.RUNNING_PROCESS_BATCH);

            BatchResult r2 = mock(BatchResult.class);
            when(r2.batchState()).thenReturn(init2.finishNow(WorkUnitStatus.FINISHED));
            when(r2.issues()).thenReturn(List.of());

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

            BatchResult r1 = mock(BatchResult.class);
            when(r1.batchState()).thenReturn(init1.finishNow(WorkUnitStatus.FINISHED));
            when(r1.issues()).thenReturn(List.of());

            Job after = j.onBatchProcessingSuccess(r1);

            assertThat(after.status()).isEqualTo(JobStatus.RUNNING_PROCESS_BATCH);
            assertThat(after.batches().get(b1).status()).isEqualTo(WorkUnitStatus.FINISHED);
            assertThat(after.batches().get(b2).status()).isEqualTo(WorkUnitStatus.INIT);
        }
    }

    @Nested
    class ResetJob {

        @Test
        void runningGetCohortRollsBackToPending() {
            Job j = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.RUNNING_GET_COHORT);

            Job rolled = j.rollback();

            assertThat(rolled.status()).isEqualTo(JobStatus.PENDING);
            assertThat(rolled.cohortState().status()).isEqualTo(WorkUnitStatus.INIT);
        }

        @Test
        void rollback_runningGetCohort_withCohortInProgress_resetsCohortStateToInit_andStatusToPending() {
            Job j = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.RUNNING_GET_COHORT)
                    .withCohortState(WorkUnitState.startNow());

            Job rolled = j.rollback();

            assertThat(rolled.status()).isEqualTo(JobStatus.PENDING);
            assertThat(rolled.cohortState().status()).isEqualTo(WorkUnitStatus.INIT);
        }

        @Test
        void rollback_runningProcessCore_withCoreInProgress_resetsCoreToInit_butKeepsStage() {
            Job j = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.RUNNING_PROCESS_CORE)
                    .withCoreState(WorkUnitState.startNow());

            Job rolled = j.rollback();

            assertThat(rolled.status()).isEqualTo(JobStatus.RUNNING_PROCESS_CORE);
            assertThat(rolled.coreState().status()).isEqualTo(WorkUnitStatus.INIT);
        }

        @Test
        void rollback_runningProcessBatch_rerollsAllInProgressBatchesToInit_andKeepsStage() {
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
        void rollback_doesNotChangeInitStates() {
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
        void rollback_thenSelectNextWorkUnit_reemitsCohortWorkUnit() {
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
        void rollback_thenSelectNextWorkUnit_reemitsCoreWorkUnit_whenCoreWasInProgress() {
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
        void rollback_thenSelectNextWorkUnit_reemitsBatchWorkUnit_whenBatchWasInProgress() {
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
    }

    @Nested
    class RollbackInferenceTests {

        private static WorkUnitState done() {
            return WorkUnitState.initNow().finishNow(WorkUnitStatus.FINISHED);
        }

        private static WorkUnitState failed() {
            return WorkUnitState.initNow().markFailed();
        }

        @Test
        void infer_failedFromCohort() {
            UUID b1 = UUID.randomUUID();

            Job j = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.TEMP_FAILED)
                    .withCohortState(failed())
                    .withCoreState(done())
                    .withBatchState(new BatchState(b1, done()));

            Job rolled = j.rollback();

            assertThat(rolled.status()).isEqualTo(JobStatus.FAILED);
        }

        @Test
        void infer_failedFromCore() {
            UUID b1 = UUID.randomUUID();

            Job j = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.TEMP_FAILED)
                    .withCohortState(done())
                    .withCoreState(failed())
                    .withBatchState(new BatchState(b1, done()));

            Job rolled = j.rollback();

            assertThat(rolled.status()).isEqualTo(JobStatus.FAILED);
        }

        @Test
        void infer_failedFromBatch() {
            UUID b1 = UUID.randomUUID();

            Job j = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.TEMP_FAILED)
                    .withCohortState(done())
                    .withCoreState(done())
                    .withBatchState(new BatchState(b1, failed()));

            Job rolled = j.rollback();

            assertThat(rolled.status()).isEqualTo(JobStatus.FAILED);
        }

        @Test
        void tempFailedToPending_cohortTempFailed_incrementsCohortRetry() {
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
    }

    @Nested
    class CoreSuccessTests {

        @Test
        void transitionsToCompleted() {
            Job j = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams())
                    .withStatus(JobStatus.RUNNING_PROCESS_CORE)
                    .withCohortState(WorkUnitState.initNow().finishNow(WorkUnitStatus.FINISHED))
                    .withCoreState(WorkUnitState.startNow());

            List<Issue> coreIssues = List.of(new Issue(Severity.INFO, "Core stats: 100 records processed", ""));
            CoreResult result = mock(CoreResult.class);
            when(result.status()).thenReturn(WorkUnitStatus.FINISHED);
            when(result.issues()).thenReturn(coreIssues);

            Job completedJob = j.onCoreSuccess(result);

            assertThat(completedJob.status()).isEqualTo(JobStatus.COMPLETED);
            assertThat(completedJob.coreState().status()).isEqualTo(WorkUnitStatus.FINISHED);
            assertThat(completedJob.issues()).containsAll(coreIssues);
            assertThat(completedJob.coreState().finishedAt()).isPresent();
        }
    }
}
