package de.medizininformatikinitiative.torch.jobhandling;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
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
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedCrtdl;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedDataExtraction;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JobTest {

    static final JobParameters EMPTY_PARAMETERS =
            new JobParameters(
                    new AnnotatedCrtdl(
                            JsonNodeFactory.instance.objectNode(),
                            new AnnotatedDataExtraction(List.of()),
                            Optional.empty()
                    ),
                    List.of()
            );

    public static Job job(UUID jobId,
                          JobStatus status,
                          WorkUnitState cohortState,
                          Map<UUID, BatchState> batches,
                          WorkUnitState coreState) {
        Instant now = Instant.now();
        return new Job(
                jobId,
                status,
                cohortState,
                0,
                batches,
                now,
                now,
                Optional.empty(),
                List.of(),
                EMPTY_PARAMETERS,
                JobPriority.NORMAL,
                coreState
        );
    }

    private static Job job(JobStatus status,
                           WorkUnitState cohortState,
                           Map<UUID, BatchState> batches,
                           WorkUnitState coreState) {
        return job(UUID.randomUUID(), status, cohortState, batches, coreState);
    }

    // -------------------------------------------------------------------------
    // State machine: selectNextWorkUnit()
    // -------------------------------------------------------------------------

    @Nested
    class SelectNextWorkUnitTests {

        @Test
        void pending_returnsGetCohort_andStagesJobToRunningGetCohort() {
            Job j = job(
                    JobStatus.PENDING,
                    WorkUnitState.initNow(),
                    Map.of(),
                    WorkUnitState.initNow()
            );

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

            // Use LinkedHashMap so "first" is deterministic.
            Map<UUID, BatchState> batches = new LinkedHashMap<>();
            batches.put(b1, new BatchState(b1, WorkUnitState.initNow()));       // INIT => eligible
            batches.put(b2, new BatchState(b2, WorkUnitState.startNow()));      // IN_PROGRESS => not eligible

            Job j = job(
                    JobStatus.RUNNING_PROCESS_BATCH,
                    WorkUnitState.initNow(),
                    batches,
                    WorkUnitState.initNow()
            );

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

            Job j = job(
                    JobStatus.RUNNING_PROCESS_BATCH,
                    WorkUnitState.initNow(),
                    Map.of(b1, finished),
                    WorkUnitState.initNow()
            );

            assertThat(j.selectNextWorkUnit()).isEmpty();
        }

        @Test
        void runningProcessCore_withCoreInit_returnsProcessCore_andStagesCoreToInProgress() {
            Job j = job(
                    JobStatus.RUNNING_PROCESS_CORE,
                    WorkUnitState.initNow(),
                    Map.of(),
                    WorkUnitState.initNow()  // INIT => eligible
            );

            Optional<WorkUnit> wuOpt = j.selectNextWorkUnit();

            assertThat(wuOpt).isPresent();
            assertThat(wuOpt.get()).isInstanceOf(ProcessCoreWorkUnit.class);

            ProcessCoreWorkUnit wu = (ProcessCoreWorkUnit) wuOpt.get();
            assertThat(wu.job().status()).isEqualTo(JobStatus.RUNNING_PROCESS_CORE);
            assertThat(wu.job().coreState().status()).isEqualTo(WorkUnitStatus.IN_PROGRESS);
        }

        @Test
        void runningProcessCore_withCoreNotInit_returnsEmpty() {
            Job j = job(
                    JobStatus.RUNNING_PROCESS_CORE,
                    WorkUnitState.initNow(),
                    Map.of(),
                    WorkUnitState.startNow() // already IN_PROGRESS => no new unit
            );

            assertThat(j.calculateBatchProgress()).isZero();
            assertThat(j.selectNextWorkUnit()).isEmpty();
        }

        @Test
        void finalStates_returnEmpty() {
            Job failed = job(JobStatus.FAILED, WorkUnitState.initNow(), Map.of(), WorkUnitState.initNow());
            Job tempFailed = job(JobStatus.TEMP_FAILED, WorkUnitState.initNow(), Map.of(), WorkUnitState.initNow());
            Job completed = job(JobStatus.COMPLETED, WorkUnitState.initNow(), Map.of(), WorkUnitState.initNow());

            assertThat(failed.selectNextWorkUnit()).isEmpty();
            assertThat(tempFailed.selectNextWorkUnit()).isEmpty();
            assertThat(completed.selectNextWorkUnit()).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // Transition: onBatchProcessingSuccess()
    // -------------------------------------------------------------------------

    @Nested
    class OnBatchProcessingSuccessTests {

        @Test
        void whenLastBatchFinishes_transitionsToRunningProcessCore_andResetsCoreStateToInit() {
            UUID b1 = UUID.randomUUID();
            UUID b2 = UUID.randomUUID();

            BatchState init1 = new BatchState(b1, WorkUnitState.initNow());
            BatchState init2 = new BatchState(b2, WorkUnitState.initNow());

            Map<UUID, BatchState> batches = new LinkedHashMap<>();
            batches.put(b1, init1);
            batches.put(b2, init2);

            // coreState intentionally non-INIT to ensure it gets reset on transition
            Job j = job(
                    JobStatus.RUNNING_PROCESS_BATCH,
                    WorkUnitState.initNow(),
                    batches,
                    WorkUnitState.startNow()
            );

            // finish b1 => not all done yet
            BatchResult r1 = mock(BatchResult.class);
            when(r1.batchState()).thenReturn(init1.finishNow(WorkUnitStatus.FINISHED));
            when(r1.issues()).thenReturn(List.of());

            Job after1 = j.onBatchProcessingSuccess(r1);
            assertThat(after1.status()).isEqualTo(JobStatus.RUNNING_PROCESS_BATCH);

            // finish b2 => now all done => move to core
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

            Map<UUID, BatchState> batches = new LinkedHashMap<>();
            batches.put(b1, init1);
            batches.put(b2, init2);

            Job j = job(
                    JobStatus.RUNNING_PROCESS_BATCH,
                    WorkUnitState.initNow(),
                    batches,
                    WorkUnitState.initNow()
            );

            // finish only b1
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
            Job j = job(
                    JobStatus.RUNNING_GET_COHORT,
                    WorkUnitState.initNow(),
                    Map.of(),
                    WorkUnitState.initNow()
            );

            Job rolled = j.rollback();

            assertThat(rolled.status()).isEqualTo(JobStatus.PENDING);
            // cohortState should still be INIT (unless you change it)
            assertThat(rolled.cohortState().status()).isEqualTo(WorkUnitStatus.INIT);
        }

        @Test
        void rollback_runningGetCohort_withCohortInProgress_resetsCohortStateToInit_andStatusToPending() {
            Job j = job(
                    JobStatus.RUNNING_GET_COHORT,
                    WorkUnitState.startNow(),     // IN_PROGRESS
                    Map.of(),
                    WorkUnitState.initNow()
            );

            Job rolled = j.rollback();

            assertThat(rolled.status()).isEqualTo(JobStatus.PENDING);
            assertThat(rolled.cohortState().status()).isEqualTo(WorkUnitStatus.INIT);
        }

        @Test
        void rollback_runningProcessCore_withCoreInProgress_resetsCoreToInit_butKeepsStage() {
            Job j = job(
                    JobStatus.RUNNING_PROCESS_CORE,
                    WorkUnitState.initNow(),
                    Map.of(),
                    WorkUnitState.startNow() // IN_PROGRESS
            );

            Job rolled = j.rollback();

            // stage stays core-processing so selectNextWorkUnit can re-emit core unit by INIT substate
            assertThat(rolled.status()).isEqualTo(JobStatus.RUNNING_PROCESS_CORE);
            assertThat(rolled.coreState().status()).isEqualTo(WorkUnitStatus.INIT);
        }

        @Test
        void rollback_runningProcessBatch_rerollsAllInProgressBatchesToInit_andKeepsStage() {
            UUID b1 = UUID.randomUUID();
            UUID b2 = UUID.randomUUID();

            Map<UUID, BatchState> batches = new LinkedHashMap<>();
            batches.put(b1, new BatchState(b1, WorkUnitState.startNow())); // IN_PROGRESS
            batches.put(b2, new BatchState(b2, WorkUnitState.initNow()));  // INIT

            Job j = job(
                    JobStatus.RUNNING_PROCESS_BATCH,
                    WorkUnitState.initNow(),
                    batches,
                    WorkUnitState.initNow()
            );

            Job rolled = j.rollback();

            assertThat(rolled.status()).isEqualTo(JobStatus.RUNNING_PROCESS_BATCH);
            assertThat(rolled.batches().get(b1).status()).isEqualTo(WorkUnitStatus.INIT);
            assertThat(rolled.batches().get(b2).status()).isEqualTo(WorkUnitStatus.INIT);
        }

        @Test
        void rollback_doesNotChangeInitStates() {
            UUID b1 = UUID.randomUUID();

            Map<UUID, BatchState> batches = Map.of(
                    b1, new BatchState(b1, WorkUnitState.initNow())
            );

            Job j = job(
                    JobStatus.RUNNING_PROCESS_BATCH,
                    WorkUnitState.initNow(),
                    batches,
                    WorkUnitState.initNow()
            );

            Job rolled = j.rollback();

            assertThat(rolled.status()).isEqualTo(JobStatus.RUNNING_PROCESS_BATCH);
            assertThat(rolled.cohortState().status()).isEqualTo(WorkUnitStatus.INIT);
            assertThat(rolled.coreState().status()).isEqualTo(WorkUnitStatus.INIT);
            assertThat(rolled.batches().get(b1).status()).isEqualTo(WorkUnitStatus.INIT);
        }

        @Test
        void rollback_thenSelectNextWorkUnit_reemitsCohortWorkUnit() {
            // This is the key behavior you want:
            // crash left you in RUNNING_GET_COHORT, rollback must put you into PENDING,
            // because only PENDING emits ProcessCohortWorkUnit.
            Job crashed = job(
                    JobStatus.RUNNING_GET_COHORT,
                    WorkUnitState.startNow(), // IN_PROGRESS
                    Map.of(),
                    WorkUnitState.initNow()
            );

            Job rolled = crashed.rollback();
            assertThat(rolled.status()).isEqualTo(JobStatus.PENDING);

            Optional<WorkUnit> wuOpt = rolled.selectNextWorkUnit();
            assertThat(wuOpt).isPresent();
            assertThat(wuOpt.get()).isInstanceOf(ProcessCohortWorkUnit.class);
        }

        @Test
        void rollback_thenSelectNextWorkUnit_reemitsCoreWorkUnit_whenCoreWasInProgress() {
            Job crashed = job(
                    JobStatus.RUNNING_PROCESS_CORE,
                    WorkUnitState.initNow(),
                    Map.of(),
                    WorkUnitState.startNow() // IN_PROGRESS
            );

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

            Map<UUID, BatchState> batches = new LinkedHashMap<>();
            batches.put(b1, new BatchState(b1, WorkUnitState.startNow()));
            batches.put(b2, new BatchState(b2, WorkUnitState.initNow().finishNow(WorkUnitStatus.FINISHED)));

            Job crashed = job(
                    JobStatus.RUNNING_PROCESS_BATCH,
                    WorkUnitState.initNow(),
                    batches,
                    WorkUnitState.initNow()
            );

            Job rolled = crashed.rollback();

            // both INIT now; selection should pick first INIT (LinkedHashMap order)
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

            Job j = job(
                    JobStatus.TEMP_FAILED,
                    failed(), // cohort terminal failure
                    Map.of(b1, new BatchState(b1, done())), // batches done
                    done() // core done
            );

            Job rolled = j.rollback();

            assertThat(rolled.status()).isEqualTo(JobStatus.FAILED);
        }

        @Test
        void infer_failedFromCore() {
            UUID b1 = UUID.randomUUID();

            Job j = job(
                    JobStatus.TEMP_FAILED,
                    done(), // cohort done
                    Map.of(b1, new BatchState(b1, done())), // batches done
                    failed() // core terminal failure
            );

            Job rolled = j.rollback();

            assertThat(rolled.status()).isEqualTo(JobStatus.FAILED);
        }

        @Test
        void infer_failedFromBatch() {
            UUID b1 = UUID.randomUUID();

            Job j = job(
                    JobStatus.TEMP_FAILED,
                    done(), // cohort done
                    Map.of(b1, new BatchState(b1, failed())), // batch terminal failure
                    done() // core done
            );

            Job rolled = j.rollback();

            assertThat(rolled.status()).isEqualTo(JobStatus.FAILED);
        }

        @Test
        void tempFailedToPending_cohortTempFailed_incrementsCohortRetry() {
            Job j = job(
                    JobStatus.TEMP_FAILED,
                    WorkUnitState.initNow().markTempFailed(), // cohort TEMP_FAILED
                    Map.of(),
                    WorkUnitState.initNow()                   // core INIT
            );

            assertThat(j.cohortState().retry()).isZero();

            Job rolled = j.rollback();

            assertThat(rolled.cohortState().retry()).isEqualTo(1);
            assertThat(rolled.status()).isEqualTo(JobStatus.PENDING);
        }

        @Test
        void exhaustCohort() {
            WorkUnitState cohortTempFailedExhaustedSoon =
                    new WorkUnitState(WorkUnitStatus.TEMP_FAILED, Instant.now(), Optional.empty(), 4);

            Job j = job(
                    JobStatus.TEMP_FAILED,
                    cohortTempFailedExhaustedSoon,
                    Map.of(),
                    WorkUnitState.initNow()
            );

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

            Job j = job(
                    JobStatus.TEMP_FAILED,
                    done(),
                    Map.of(),
                    coreTempFailedExhaustedSoon
            );

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

            Map<UUID, BatchState> batches = Map.of(
                    b1, new BatchState(b1, batchTempFailedExhaustedSoon)
            );

            Job j = job(
                    JobStatus.TEMP_FAILED,
                    done(),
                    batches,
                    done()
            );

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

            Map<UUID, BatchState> batches = new LinkedHashMap<>();
            batches.put(b1, new BatchState(b1, done()));              // done
            batches.put(b2, new BatchState(b2, WorkUnitState.initNow())); // remaining

            Job j = job(
                    JobStatus.TEMP_FAILED,
                    done(),          // cohort done
                    batches,
                    WorkUnitState.initNow()
            );

            Job rolled = j.rollback();

            assertThat(rolled.status()).isEqualTo(JobStatus.RUNNING_PROCESS_BATCH);
        }

        @Test
        void tempFailedToCore() {
            UUID b1 = UUID.randomUUID();

            Map<UUID, BatchState> batches = Map.of(
                    b1, new BatchState(b1, done())
            );

            Job j = job(
                    JobStatus.TEMP_FAILED,
                    done(),          // cohort done
                    batches,         // batches done
                    WorkUnitState.initNow() // core not done
            );

            Job rolled = j.rollback();

            assertThat(rolled.status()).isEqualTo(JobStatus.RUNNING_PROCESS_CORE);
        }

        @Test
        void tempFailedToCompleted() {
            UUID b1 = UUID.randomUUID();

            Map<UUID, BatchState> batches = Map.of(
                    b1, new BatchState(b1, done())
            );

            Job j = job(
                    JobStatus.TEMP_FAILED,
                    done(),
                    batches,
                    done()
            );

            Job rolled = j.rollback();

            assertThat(rolled.status()).isEqualTo(JobStatus.COMPLETED);
        }

        @Test
        void cohortFailedToFailed() {
            Job j = job(
                    JobStatus.TEMP_FAILED,
                    failed(), // terminal failure
                    Map.of(),
                    WorkUnitState.initNow()
            );

            Job rolled = j.rollback();

            assertThat(rolled.status()).isEqualTo(JobStatus.FAILED);
        }

        @Test
        void coreFailedToFailed() {
            Job j = job(
                    JobStatus.TEMP_FAILED,
                    done(),
                    Map.of(),
                    failed() // terminal failure
            );

            Job rolled = j.rollback();

            assertThat(rolled.status()).isEqualTo(JobStatus.FAILED);
        }

        @Test
        void batchFailedToFailed() {
            UUID b1 = UUID.randomUUID();

            Map<UUID, BatchState> batches = Map.of(
                    b1, new BatchState(b1, failed())
            );

            Job j = job(
                    JobStatus.TEMP_FAILED,
                    done(),
                    batches,
                    WorkUnitState.initNow()
            );

            Job rolled = j.rollback();

            assertThat(rolled.status()).isEqualTo(JobStatus.FAILED);
        }

        @Test
        void rerollUnitTempFailed() {
            UUID b1 = UUID.randomUUID();

            WorkUnitState cohortTempFailed = WorkUnitState.initNow().markTempFailed();
            WorkUnitState coreTempFailed = WorkUnitState.initNow().markTempFailed();
            BatchState batchTempFailed = new BatchState(b1, WorkUnitState.initNow().markTempFailed());

            Job j = job(
                    JobStatus.TEMP_FAILED,
                    cohortTempFailed,
                    Map.of(b1, batchTempFailed),
                    coreTempFailed
            );

            Job rolled = j.rollback();

            assertThat(rolled.cohortState().status()).isEqualTo(WorkUnitStatus.INIT);
            assertThat(rolled.coreState().status()).isEqualTo(WorkUnitStatus.INIT);
            assertThat(rolled.batches().get(b1).status()).isEqualTo(WorkUnitStatus.INIT);

            // after reroll, cohort not done => pending
            assertThat(rolled.status()).isEqualTo(JobStatus.PENDING);
        }
    }

    @Nested
    class ErrorPathTests {

        private final List<Issue> sampleIssues = List.of(new Issue(Severity.WARNING, "Test Issue", ""));

        @Test
        void onCohortError_retryable_transitionsToTempFailed() {
            Job j = job(JobStatus.RUNNING_GET_COHORT, WorkUnitState.startNow(), Map.of(), WorkUnitState.initNow());
            Exception retryableEx = new IOException("Retryable"); // Assuming default is retryable

            Job updated = j.onCohortError(retryableEx, sampleIssues);

            assertThat(updated.status()).isEqualTo(JobStatus.TEMP_FAILED);
            assertThat(updated.cohortState().status()).isEqualTo(WorkUnitStatus.TEMP_FAILED);
            assertThat(updated.issues()).containsAll(sampleIssues);
        }

        @Test
        void onBatchError_missingBatchId_returnsFailedWithNewIssue() {
            UUID unknownId = UUID.randomUUID();
            Job j = job(JobStatus.RUNNING_PROCESS_BATCH, WorkUnitState.initNow(), Map.of(), WorkUnitState.initNow());

            Job updated = j.onBatchError(unknownId, new Exception("Boom"), List.of());

            assertThat(updated.status()).isEqualTo(JobStatus.FAILED);
            assertThat(updated.issues()).anyMatch(i -> i.msg().contains("Missing batch") && i.msg().contains(unknownId.toString()));
        }

        @Test
        void onBatchError_retryable_updatesBatchStateAndTempFails() {
            UUID b1 = UUID.randomUUID();
            BatchState bs = new BatchState(b1, WorkUnitState.startNow());
            Job j = job(JobStatus.RUNNING_PROCESS_BATCH, WorkUnitState.initNow(), Map.of(b1, bs), WorkUnitState.initNow());

            Job updated = j.onBatchError(b1, new IOException("Retryable"), sampleIssues);

            assertThat(updated.status()).isEqualTo(JobStatus.TEMP_FAILED);
            assertThat(updated.batches().get(b1).status()).isEqualTo(WorkUnitStatus.TEMP_FAILED);
            assertThat(updated.issues()).containsAll(sampleIssues);
        }

        @Test
        void onCoreError_retryable_addsAutoGeneratedIssueAndTempFails() {
            Job j = job(JobStatus.RUNNING_PROCESS_CORE, WorkUnitState.initNow(), Map.of(), WorkUnitState.startNow());
            String errorMsg = "Core failed message";
            Exception ex = new IOException(errorMsg);

            Job updated = j.onCoreError(ex, sampleIssues);

            assertThat(updated.status()).isEqualTo(JobStatus.TEMP_FAILED);
            assertThat(updated.coreState().status()).isEqualTo(WorkUnitStatus.TEMP_FAILED);
            // Verify both passed issues and the one generated inside onCoreError are present
            assertThat(updated.issues()).hasSize(2);
            assertThat(updated.issues()).anyMatch(i -> i.msg().contains("CoreState failed: " + errorMsg));
        }

        @Test
        void onCoreError_nonRetryable_failsImmediately() {
            Job j = job(JobStatus.RUNNING_PROCESS_CORE, WorkUnitState.initNow(), Map.of(), WorkUnitState.startNow());
            Job updated = j.onCoreError(new Error("Fatal"), List.of());

            assertThat(updated.status()).isEqualTo(JobStatus.FAILED);
            assertThat(updated.coreState().status()).isEqualTo(WorkUnitStatus.FAILED);
        }

        @Test
        void onJobError_retryable_addsIssueAndTempFails() {
            Job j = job(JobStatus.RUNNING_PROCESS_BATCH, WorkUnitState.initNow(), Map.of(), WorkUnitState.initNow());
            Exception infraEx = new IOException("DB Connection Timeout");

            Job updated = j.onJobError(infraEx, List.of());

            assertThat(updated.status()).isEqualTo(JobStatus.TEMP_FAILED);
            assertThat(updated.issues()).anyMatch(i -> i.severity() == Severity.WARNING
                    && i.msg().contains("Infrastructure/persistence error"));
        }

        @Test
        void onJobError_nonRetryable_addsErrorIssueAndFails() {
            Job j = job(JobStatus.RUNNING_PROCESS_BATCH, WorkUnitState.initNow(), Map.of(), WorkUnitState.initNow());
            Error fatal = new Error("Disk Full");

            Job updated = j.onJobError(fatal, List.of());

            assertThat(updated.status()).isEqualTo(JobStatus.FAILED);
            assertThat(updated.issues()).anyMatch(i -> i.severity() == Severity.ERROR
                    && i.msg().contains("Infrastructure/persistence error"));
        }
    }

    @Nested
    class CoreSuccessTests {

        @Test
        void transitionsToCompleted() {
            // Arrange
            Job j = job(
                    JobStatus.RUNNING_PROCESS_CORE,
                    WorkUnitState.initNow().finishNow(WorkUnitStatus.FINISHED), // Cohort done
                    Map.of(),                                                  // No batches
                    WorkUnitState.startNow()                                   // Core in progress
            );

            List<Issue> coreIssues = List.of(new Issue(Severity.INFO, "Core stats: 100 records processed", ""));
            CoreResult result = mock(CoreResult.class);
            when(result.status()).thenReturn(WorkUnitStatus.FINISHED);
            when(result.issues()).thenReturn(coreIssues);

            // Act
            Job completedJob = j.onCoreSuccess(result);

            // Assert
            assertThat(completedJob.status()).isEqualTo(JobStatus.COMPLETED);
            assertThat(completedJob.coreState().status()).isEqualTo(WorkUnitStatus.FINISHED);
            assertThat(completedJob.issues()).containsAll(coreIssues);
            // Verify core state was actually updated via finishNow (timestamp should be present)
            assertThat(completedJob.coreState().finishedAt()).isPresent();
        }
    }

}
