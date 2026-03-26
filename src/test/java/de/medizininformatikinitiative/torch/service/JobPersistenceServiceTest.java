package de.medizininformatikinitiative.torch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.medizininformatikinitiative.torch.TestUtils;
import de.medizininformatikinitiative.torch.exceptions.JobNotFoundException;
import de.medizininformatikinitiative.torch.exceptions.StateConflictException;
import de.medizininformatikinitiative.torch.jobhandling.BatchState;
import de.medizininformatikinitiative.torch.jobhandling.DefaultFileIO;
import de.medizininformatikinitiative.torch.jobhandling.FileIo;
import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.JobParameters;
import de.medizininformatikinitiative.torch.jobhandling.JobPriority;
import de.medizininformatikinitiative.torch.jobhandling.JobStatus;
import de.medizininformatikinitiative.torch.jobhandling.failure.Issue;
import de.medizininformatikinitiative.torch.jobhandling.failure.Severity;
import de.medizininformatikinitiative.torch.jobhandling.workunit.WorkUnit;
import de.medizininformatikinitiative.torch.jobhandling.workunit.WorkUnitState;
import de.medizininformatikinitiative.torch.jobhandling.workunit.WorkUnitStatus;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionId;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionResourceBundle;
import de.medizininformatikinitiative.torch.model.extraction.ResourceExtractionInfo;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobPersistenceServiceTest {

    static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule()).registerModule(new Jdk8Module()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    static final JobParameters EMPTY_PARAMETERS = TestUtils.emptyJobParams();


    static Job createJobWithBatches(UUID jobId) {
        BatchState s1 = BatchState.init();
        BatchState s2 = BatchState.init();
        return Job.init(jobId, EMPTY_PARAMETERS).withStatus(JobStatus.RUNNING_GET_COHORT).withBatchState(s1).withBatchState(s2);
    }


    // -------------------------------------------------------------------------
    // REAL FILESYSTEM TESTS
    // -------------------------------------------------------------------------
    @Nested
    class RealFileIOTests {

        @TempDir
        Path baseDir;

        JobPersistenceService persistenceService;

        @BeforeEach
        void setUp() throws IOException {
            persistenceService = new JobPersistenceService(new DefaultFileIO(), MAPPER, baseDir.toString(), 5);
            persistenceService.init();
        }

        @Test
        void saveBatchCreatesNdjson() throws IOException {
            UUID jobId = UUID.randomUUID();
            persistenceService.createJob(EMPTY_PARAMETERS.crtdl(), EMPTY_PARAMETERS.paramBatch());

            PatientBatch batch = new PatientBatch(List.of("A", "B"), UUID.randomUUID());
            persistenceService.saveBatch(batch, jobId);

            Path batchFile = baseDir.resolve(jobId.toString()).resolve("batches").resolve(batch.batchId() + ".ndjson");

            assertThat(batchFile).exists();


            try (var files = Files.list(baseDir)) {
                assertThat(files.filter(x -> x.endsWith("tmp"))).isEmpty();
            }
        }

        @Test
        void init_SkipsDirectory_WhenJobFileIsUnreadable() throws IOException {
            // GIVEN
            UUID jobId = UUID.randomUUID();
            Path jobDir = baseDir.resolve(jobId.toString());
            Path jobFile = jobDir.resolve("job.json");

            // We need to ensure the directory discovery works but the file reading fails
            FileIo spyIo = spy(new DefaultFileIO());
            Files.createDirectories(jobDir);
            Files.writeString(jobFile, "{ \"corrupt\": \"json\" }"); // Create the file so it exists

            // Force an IOException when trying to read this specific job file
            doThrow(new IOException("Read error")).when(spyIo).newBufferedReader(jobFile);

            JobPersistenceService serviceWithSpy = new JobPersistenceService(spyIo, MAPPER, baseDir.toString(), 5);

            // WHEN
            serviceWithSpy.init(); // This calls loadAllJobs -> loadJobFromDirectory

            // THEN
            // The job should not be in the registry
            assertThat(serviceWithSpy.getJob(jobId)).isEmpty();
            // Verify it attempted to read it
            verify(spyIo).newBufferedReader(jobFile);
        }

        @Test
        void testCreateJobPersistOnIO() throws IOException {
            Instant before = Instant.now();

            UUID jobId = persistenceService.createJob(EMPTY_PARAMETERS.crtdl(), EMPTY_PARAMETERS.paramBatch());

            Instant after = Instant.now();

            // simulate "process restart"
            JobPersistenceService reloaded = new JobPersistenceService(new DefaultFileIO(), MAPPER, baseDir.toString(), 5);
            reloaded.init();

            Job actual = reloaded.getJob(jobId).orElseThrow();


            // identity
            assertThat(actual.id()).isEqualTo(jobId);

            // initial job state
            assertThat(actual.status()).isEqualTo(JobStatus.PENDING);
            assertThat(actual.cohortSize()).isZero();
            assertThat(actual.batches()).isEmpty();
            assertThat(actual.priority()).isEqualTo(JobPriority.NORMAL);
            assertThat(actual.parameters()).isEqualTo(EMPTY_PARAMETERS);

            // work unit states exist and are INIT
            assertThat(actual.cohortState().status()).isEqualTo(WorkUnitStatus.INIT);
            assertThat(actual.coreState().status()).isEqualTo(WorkUnitStatus.INIT);

            // timestamps: created once, sane, not mutated
            assertThat(actual.startedAt()).isBetween(before, after);
            assertThat(actual.updatedAt()).isBetween(before, after);
            assertThat(actual.finishedAt()).isEmpty();

            assertThat(actual.cohortState().startedAt()).isBetween(before, after);
            assertThat(actual.coreState().startedAt()).isBetween(before, after);
        }

        @Test
        void testSaveAndLoadUnfinishedBatches() throws IOException {
            UUID jobId = UUID.randomUUID();

            PatientBatch pb1 = new PatientBatch(List.of("A", "B"), UUID.randomUUID());
            PatientBatch pb2 = new PatientBatch(List.of("X", "Y"), UUID.randomUUID());

            persistenceService.saveBatch(pb1, jobId);
            persistenceService.saveBatch(pb2, jobId);

            assertThat(persistenceService.loadBatch(jobId, pb2.batchId())).isEqualTo(pb2);
        }

        @Test
        void loadAllJobs() throws IOException {
            UUID j1 = UUID.randomUUID();
            UUID j2 = UUID.randomUUID();

            Files.createDirectories(baseDir.resolve(j1.toString()));
            Files.createDirectories(baseDir.resolve(j2.toString()));

            Files.writeString(baseDir.resolve(j1.toString()).resolve("job.json"), MAPPER.writeValueAsString(createJobWithBatches(j1)));
            Files.writeString(baseDir.resolve(j2.toString()).resolve("job.json"), MAPPER.writeValueAsString(createJobWithBatches(j2)));
            persistenceService.init();
            assertThat(persistenceService.getJob(j1)).isPresent();
            assertThat(persistenceService.getJob(j2)).isPresent();
        }

        @Test
        void saveCoreBatch_and_loadCoreInfo_shouldPersistAndReloadSingleCoreBundle() throws IOException {

            UUID jobId = UUID.randomUUID();
            persistenceService.createJob(EMPTY_PARAMETERS.crtdl(), EMPTY_PARAMETERS.paramBatch());

            ResourceExtractionInfo rei = new ResourceExtractionInfo(Set.of("G1"), Map.of("Patient.name", Set.of(ExtractionId.fromRelativeUrl("r/rid-1"))));

            ExtractionResourceBundle cb = new ExtractionResourceBundle(new ConcurrentHashMap<>(Map.of(ExtractionId.fromRelativeUrl("r/rid-1"), rei)), new ConcurrentHashMap<>());

            persistenceService.saveCoreBatch(jobId, UUID.randomUUID(), cb);

            ExtractionResourceBundle merged = persistenceService.loadCoreInfo(jobId);

            assertThat(merged.extractionInfoMap()).containsOnlyKeys(ExtractionId.fromRelativeUrl("r/rid-1"));
            assertThat(merged.extractionInfoMap().get(ExtractionId.fromRelativeUrl("r/rid-1")).groups()).containsExactly("G1");
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class MockedIOTests {
        @TempDir
        Path baseDir;
        @Mock
        FileIo io;

        JobPersistenceService service;

        @BeforeEach
        void init() {
            service = new JobPersistenceService(io, MAPPER, "Any", 10);
        }

        @Test
        void failsWhenSaveJobFailsWithIOException() throws IOException {
            doNothing().when(io).createDirectories(any());
            when(io.newBufferedWriter(any())).thenThrow(new IOException("boom"));

            assertThatThrownBy(() -> service.createJob(EMPTY_PARAMETERS.crtdl(), EMPTY_PARAMETERS.paramBatch())).isInstanceOf(IOException.class).hasMessageContaining("Failed to initialize job");
        }

        @Test
        void failsWhenAtomicMoveFails() throws IOException {
            BufferedWriter writer = mock(BufferedWriter.class);
            when(io.newBufferedWriter(any())).thenReturn(writer);

            doThrow(new IOException("atomic move failed")).when(io).atomicMove(any(), any());

            assertThatThrownBy(() -> service.createJob(EMPTY_PARAMETERS.crtdl(), EMPTY_PARAMETERS.paramBatch())).isInstanceOf(IOException.class).hasMessageContaining("Failed to initialize job");
        }

        @Test
        void failsWhenBatchWritingFails() throws IOException {
            PatientBatch batch = new PatientBatch(List.of("1", "2"), UUID.randomUUID());

            doNothing().when(io).createDirectories(any());
            when(io.newBufferedWriter(any())).thenThrow(new IOException("boom"));

            assertThatThrownBy(() -> service.saveBatch(batch, UUID.randomUUID())).isInstanceOf(IOException.class).hasMessageContaining("boom");
        }

        @Test
        void loadAllJobs_ReturnsEmpty_WhenBaseDirDoesNotExist() throws IOException {
            when(io.exists(baseDir)).thenReturn(false);
            JobPersistenceService serviceWithMock = new JobPersistenceService(io, MAPPER, baseDir.toString(), 5);

            // WHEN - init calls loadAllJobs
            serviceWithMock.init();

            // THEN
            verify(io).createDirectories(baseDir);
            assertThat(serviceWithMock.getJob(UUID.randomUUID())).isEmpty();
        }

        @Test
        void failsWhenCreatingJobDirFails() throws IOException {
            doThrow(new IOException("mkdir fail")).when(io).createDirectories(any());

            assertThatThrownBy(() -> service.createJob(EMPTY_PARAMETERS.crtdl(), EMPTY_PARAMETERS.paramBatch())).isInstanceOf(IOException.class).hasMessageContaining("Failed to initialize job");
        }

        @Test
        void savingWritesToDir() throws IOException {
            doNothing().when(io).createDirectories(any());

            BufferedWriter writer = mock(BufferedWriter.class);
            when(io.newBufferedWriter(any())).thenReturn(writer);
            doNothing().when(io).atomicMove(any(), any());

            UUID jobId = service.createJob(EMPTY_PARAMETERS.crtdl(), EMPTY_PARAMETERS.paramBatch());

            verify(io).newBufferedWriter(argThat(path -> path.toString().contains(jobId.toString())));
        }
    }

    @Nested
    class ResumeAfterRestartTests {

        @TempDir
        Path baseDir;

        private JobPersistenceService restart() throws IOException {
            JobPersistenceService s = new JobPersistenceService(new DefaultFileIO(), MAPPER, baseDir.toString(), 5);
            s.init();
            return s;
        }

        private Path prepareJobDir(UUID jobId) throws IOException {
            Path jobDir = baseDir.resolve(jobId.toString());
            Files.createDirectories(jobDir);
            Files.createDirectories(jobDir.resolve("batches"));
            Files.createDirectories(jobDir.resolve("core_batches"));
            return jobDir;
        }

        @Test
        void resumeAfterRestart_coreOnly_rollsBackAndPersists_thenCanAcquire() throws IOException, JobNotFoundException {
            UUID jobId = UUID.randomUUID();
            Path jobDir = prepareJobDir(jobId);

            Job crashed = Job.init(jobId, EMPTY_PARAMETERS).withStatus(JobStatus.RUNNING_PROCESS_CORE).withCoreState(WorkUnitState.startNow());

            Files.writeString(jobDir.resolve("job.json"), MAPPER.writeValueAsString(crashed));

            Job before = MAPPER.readValue(Files.readString(jobDir.resolve("job.json")), Job.class);
            assertThat(before.coreState().status()).isEqualTo(WorkUnitStatus.IN_PROGRESS);

            JobPersistenceService restarted = restart();

            Job resumed = restarted.getJob(jobId).orElseThrow();
            assertThat(resumed.coreState().status()).isEqualTo(WorkUnitStatus.INIT);

            Job after = MAPPER.readValue(Files.readString(jobDir.resolve("job.json")), Job.class);
            assertThat(after.coreState().status()).isEqualTo(WorkUnitStatus.INIT);

            assertThat(restarted.tryMarkCoreInProgress(jobId)).isTrue();
            assertThat(restarted.getJob(jobId).orElseThrow().coreState().status()).isEqualTo(WorkUnitStatus.IN_PROGRESS);
        }

        @Test
        void resumeAfterRestart_batchInProgress_rollsBackToInit_andPersists() throws IOException {
            UUID jobId = UUID.randomUUID();
            UUID batchId = UUID.randomUUID();
            Path jobDir = prepareJobDir(jobId);

            // batch file exists so later resume can actually load it
            Files.writeString(jobDir.resolve("batches").resolve(batchId + ".ndjson"), "A\nB\n");

            BatchState inProgress = new BatchState(batchId, WorkUnitState.startNow());

            Job crashed = Job.init(jobId, EMPTY_PARAMETERS).withBatchState(inProgress).withStatus(JobStatus.RUNNING_PROCESS_BATCH);

            Files.writeString(jobDir.resolve("job.json"), MAPPER.writeValueAsString(crashed));

            JobPersistenceService restarted = restart();

            Job resumed = restarted.getJob(jobId).orElseThrow();
            assertThat(resumed.batches().get(batchId).status()).isEqualTo(WorkUnitStatus.INIT);

            Job persisted = MAPPER.readValue(Files.readString(jobDir.resolve("job.json")), Job.class);
            assertThat(persisted.batches().get(batchId).status()).isEqualTo(WorkUnitStatus.INIT);

            PatientBatch pb = restarted.loadBatch(jobId, batchId);
            assertThat(pb.ids()).containsExactly("A", "B");

            Job afterTake = restarted.getJob(jobId).orElseThrow();
            assertThat(afterTake.batches().get(batchId).status()).isEqualTo(WorkUnitStatus.INIT);
        }

        @Test
        void resumeAfterRestart_batchInit_isNotChanged() throws IOException {
            UUID jobId = UUID.randomUUID();
            UUID batchId = UUID.randomUUID();
            Path jobDir = prepareJobDir(jobId);

            BatchState init = new BatchState(batchId, WorkUnitState.initNow());
            Job job = Job.init(jobId, EMPTY_PARAMETERS).withBatchState(init).withStatus(JobStatus.RUNNING_PROCESS_BATCH);

            Files.writeString(jobDir.resolve("job.json"), MAPPER.writeValueAsString(job));

            JobPersistenceService restarted = restart();

            Job loaded = restarted.getJob(jobId).orElseThrow();
            assertThat(loaded.batches().get(batchId).status()).isEqualTo(WorkUnitStatus.INIT);

            Job persisted = MAPPER.readValue(Files.readString(jobDir.resolve("job.json")), Job.class);
            assertThat(persisted.batches().get(batchId).status()).isEqualTo(WorkUnitStatus.INIT);
        }

        @Test
        void resumeAfterRestart_finalJob_isNotResumed_coreCannotBeAcquired() throws IOException {
            UUID jobId = UUID.randomUUID();
            Path jobDir = prepareJobDir(jobId);

            Job completedButHasCore = Job.init(jobId, EMPTY_PARAMETERS).withStatus(JobStatus.COMPLETED);


            Files.writeString(jobDir.resolve("job.json"), MAPPER.writeValueAsString(completedButHasCore));

            JobPersistenceService restarted = restart();

            Job loaded = restarted.getJob(jobId).orElseThrow();
            assertThat(loaded.status()).isEqualTo(JobStatus.COMPLETED);
        }

        @Test
        void resumeAfterRestart_missingCoreState_behavesLikeInitForAcquire() throws IOException, JobNotFoundException {
            UUID jobId = UUID.randomUUID();
            Path jobDir = prepareJobDir(jobId);

            Job noCoreState = Job.init(jobId, EMPTY_PARAMETERS).withStatus(JobStatus.RUNNING_PROCESS_CORE);

            Files.writeString(jobDir.resolve("job.json"), MAPPER.writeValueAsString(noCoreState));

            JobPersistenceService restarted = restart();

            // tryMarkCoreInProgress treats missing as INIT (your code does .orElse(INIT))
            assertThat(restarted.tryMarkCoreInProgress(jobId)).isTrue();
            assertThat(restarted.getJob(jobId).orElseThrow().coreState().status()).isEqualTo(WorkUnitStatus.IN_PROGRESS);
        }
    }

    @Nested
    class OnBatchErrorTests {

        @TempDir
        Path baseDir;

        JobPersistenceService service;

        @BeforeEach
        void setUp() throws IOException {
            service = new JobPersistenceService(new DefaultFileIO(), MAPPER, baseDir.toString(), 5);
            service.init();
        }

        private Path prepareJobDir(UUID jobId) throws IOException {
            Path jobDir = baseDir.resolve(jobId.toString());
            Files.createDirectories(jobDir);
            Files.createDirectories(jobDir.resolve("batches"));
            Files.createDirectories(jobDir.resolve("core_batches"));
            return jobDir;
        }

        private void persistJob(Path jobDir, Job job) throws IOException {
            Files.writeString(jobDir.resolve("job.json"), MAPPER.writeValueAsString(job));
            service.putJobForTest(job);
        }

        @Test
        void onBatchErrorTempFailedEscalatesJob() throws IOException, JobNotFoundException {
            UUID jobId = UUID.randomUUID();
            UUID batchId = UUID.randomUUID();
            Path jobDir = prepareJobDir(jobId);

            BatchState tempFailed = new BatchState(batchId, WorkUnitState.startNow().markTempFailed());

            Job job = Job.init(jobId, EMPTY_PARAMETERS).withBatchState(tempFailed).withStatus(JobStatus.RUNNING_PROCESS_BATCH);


            persistJob(jobDir, job);

            service.onBatchError(jobId, batchId, List.of(), new IOException("still flaky"));
            service.onBatchError(jobId, batchId, List.of(), new IOException("still flaky"));
            service.onBatchError(jobId, batchId, List.of(), new IOException("still flaky"));
            service.onBatchError(jobId, batchId, List.of(), new IOException("still flaky"));

            Job updated = service.getJob(jobId).orElseThrow();

            assertThat(updated.batches().get(batchId).status()).isEqualTo(WorkUnitStatus.TEMP_FAILED);

            // escalation
            assertThat(updated.status()).isEqualTo(JobStatus.TEMP_FAILED); // rename if needed

            Job persisted = MAPPER.readValue(Files.readString(jobDir.resolve("job.json")), Job.class);
            assertThat(persisted.status()).isEqualTo(JobStatus.TEMP_FAILED);
            assertThat(persisted.batches().get(batchId).status()).isEqualTo(WorkUnitStatus.TEMP_FAILED);
        }

        @Test
        void onBatchErrorNonRetryableEscalatesJobImmediately() throws IOException, JobNotFoundException {
            UUID jobId = UUID.randomUUID();
            UUID batchId = UUID.randomUUID();
            Path jobDir = prepareJobDir(jobId);

            BatchState init = new BatchState(batchId, WorkUnitState.initNow());

            Job job = Job.init(jobId, EMPTY_PARAMETERS).withBatchState(init).withStatus(JobStatus.RUNNING_PROCESS_BATCH);

            persistJob(jobDir, job);

            service.onBatchError(jobId, batchId, List.of(), new IllegalArgumentException("bad input"));

            Job updated = service.getJob(jobId).orElseThrow();

            assertThat(updated.batches().get(batchId).status()).isEqualTo(WorkUnitStatus.FAILED);
            assertThat(updated.status()).isEqualTo(JobStatus.FAILED);

            Job persisted = MAPPER.readValue(Files.readString(jobDir.resolve("job.json")), Job.class);
            assertThat(persisted.status()).isEqualTo(JobStatus.FAILED);
            assertThat(persisted.batches().get(batchId).status()).isEqualTo(WorkUnitStatus.FAILED);
        }
    }

    @Nested
    class StateTransitionTests {
        @TempDir
        Path baseDir;
        JobPersistenceService service;
        UUID jobId;

        @BeforeEach
        void setUp() throws IOException {
            service = new JobPersistenceService(new DefaultFileIO(), MAPPER, baseDir.toString(), 2);
            service.init();
            jobId = service.createJob(EMPTY_PARAMETERS.crtdl(), List.of());
        }

        @Test
        void onCohortSuccess_CreatesBatchesAndUpdatesState() throws JobNotFoundException {
            service.putJobForTest(service.getJob(jobId).orElseThrow().withStatus(JobStatus.RUNNING_GET_COHORT).withCohortState(WorkUnitState.startNow()));

            List<String> patientIds = List.of("P1", "P2", "P3");

            service.onCohortSuccess(jobId, patientIds);

            Job job = service.getJob(jobId).orElseThrow();
            assertThat(job.cohortSize()).isEqualTo(3);
            assertThat(job.batches()).hasSize(2);
            assertThat(job.status()).isEqualTo(JobStatus.RUNNING_PROCESS_BATCH);
        }

        @Test
        void onCohortSuccess_EmptyCohort_TransitionsToCore() throws JobNotFoundException {
            service.putJobForTest(service.getJob(jobId).orElseThrow().withStatus(JobStatus.RUNNING_GET_COHORT).withCohortState(WorkUnitState.startNow()));

            service.onCohortSuccess(jobId, List.of());

            Job job = service.getJob(jobId).orElseThrow();
            assertThat(job.status()).isEqualTo(JobStatus.RUNNING_PROCESS_CORE);
            assertThat(job.issues()).extracting("msg").contains("Empty cohort");
        }

        @Test
        void tryStartBatch_TransitionsStatus() throws JobNotFoundException {
            service.putJobForTest(service.getJob(jobId).orElseThrow().withStatus(JobStatus.RUNNING_GET_COHORT).withCohortState(WorkUnitState.startNow()));

            service.onCohortSuccess(jobId, List.of("P1"));
            UUID batchId = service.getJob(jobId).get().batches().keySet().iterator().next();

            boolean started = service.tryStartBatch(jobId, batchId);

            assertThat(started).isTrue();
            assertThat(service.getJob(jobId).get().batches().get(batchId).status()).isEqualTo(WorkUnitStatus.IN_PROGRESS);
        }

        @Test
        void selectNextWorkUnit_OrdersByPriority() throws IOException {
            // Job 1: Normal Priority
            UUID firstJob = service.createJob(EMPTY_PARAMETERS.crtdl(), List.of());
            // Job 2: High Priority
            UUID j2 = UUID.randomUUID();
            Job highPrio = Job.init(j2, EMPTY_PARAMETERS).withPriority(JobPriority.HIGH);
            service.putJobForTest(highPrio);

            Optional<WorkUnit> wu = service.selectNextWorkUnit();
            System.out.println(firstJob);
            assertThat(wu).isPresent();
            assertThat(wu.get().job().id()).isEqualTo(j2);
        }

        @Test
        void onBatchError_PersistsIssues() throws JobNotFoundException {
            service.putJobForTest(service.getJob(jobId).orElseThrow().withStatus(JobStatus.RUNNING_GET_COHORT).withCohortState(WorkUnitState.startNow()));

            service.onCohortSuccess(jobId, List.of("P1"));
            UUID batchId = service.getJob(jobId).get().batches().keySet().iterator().next();

            service.tryStartBatch(jobId, batchId);

            service.onBatchError(jobId, batchId, List.of(new Issue(Severity.ERROR, "Fail", "Detail")), new RuntimeException("Error"));

            Job job = service.getJob(jobId).get();
            assertThat(job.batches().get(batchId).status()).isEqualTo(WorkUnitStatus.FAILED);
            assertThat(job.issues()).isNotEmpty();
        }

        @Test
        void onCoreErrorFailsCore() throws IOException, JobNotFoundException {
            UUID jobId = service.createJob(EMPTY_PARAMETERS.crtdl(), List.of());

            Job runningCore = service.getJob(jobId).orElseThrow().withStatus(JobStatus.RUNNING_PROCESS_CORE).withCoreState(WorkUnitState.startNow());

            service.putJobForTest(runningCore);

            service.onCoreError(jobId, List.of(new Issue(Severity.ERROR, "Core failed", "details")), new IllegalArgumentException("boom"));

            Job updated = service.getJob(jobId).orElseThrow();

            assertThat(updated.status()).isEqualTo(JobStatus.FAILED);
            assertThat(updated.coreState().status()).isEqualTo(WorkUnitStatus.FAILED);
            assertThat(updated.issues()).isNotEmpty();
        }

        @Test
        void updateJobAndReturn_HandlesExceptionsGracefully() throws JobNotFoundException {
            service.putJobForTest(service.getJob(jobId).orElseThrow().withStatus(JobStatus.RUNNING_GET_COHORT).withCohortState(WorkUnitState.startNow()));

            service.onCohortError(jobId, List.of(), new RuntimeException("Unexpected crash"));

            Job job = service.getJob(jobId).get();
            assertThat(job.status()).isEqualTo(JobStatus.FAILED);
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class) // Ensures @Mock fields are initialized
    class UnhappyPathTests {

        // Use a real mapper since the service relies on it for JSON logic
        private final ObjectMapper mapper = MAPPER;
        @TempDir
        Path baseDir;
        @Mock
        FileIo mockIo;
        JobPersistenceService service;

        @BeforeEach
        void setUp() {
            // Line 65 in your service is requireNonNull.
            // We must pass the mock and the mapper explicitly here.
            service = new JobPersistenceService(mockIo, mapper, baseDir.toString(), 5);
        }

        @Test
        void init_HandlesListingFailureGracefully() throws IOException {
            // GIVEN: io.exists returns true but listing files fails
            when(mockIo.exists(any())).thenReturn(true);
            when(mockIo.list(any())).thenThrow(new IOException("FileSystem unreachable"));

            // WHEN/THEN
            assertThatThrownBy(() -> service.init()).isInstanceOf(IOException.class).hasMessageContaining("FileSystem unreachable");
        }

        @Test
        void loadBatch_ThrowsWhenFileMissing() {
            // GIVEN
            UUID jobId = UUID.randomUUID();
            UUID batchId = UUID.randomUUID();
            when(mockIo.exists(any())).thenReturn(false);

            // WHEN/THEN
            assertThatThrownBy(() -> service.loadBatch(jobId, batchId)).isInstanceOf(IOException.class).hasMessageContaining("Batch file missing");
        }

        @Test
        void updateJobAndReturn_HandlesPersistenceFailure() throws IOException, JobNotFoundException {
            // GIVEN: A job exists in the registry
            UUID jobId = UUID.randomUUID();
            Job job = createJobWithBatches(jobId);
            service.putJobForTest(job);

            // Simulate a failure during the atomic move / write
            when(mockIo.newBufferedWriter(any())).thenThrow(new IOException("Disk Full"));

            // WHEN: We trigger an update (e.g., via onCohortError)
            service.onCohortError(jobId, List.of(), new RuntimeException("Original error"));

            // THEN: The service should have tried to save, failed,
            assertThat(service.getJob(jobId).get().status()).isEqualTo(JobStatus.TEMP_FAILED);
        }

        @Test
        void loadAllCoreBatchParts_HandlesCorruptedJson() throws IOException {
            // GIVEN: A directory exists but contains malformed JSON
            Path corruptFile = baseDir.resolve("corrupt.json");
            // We use a real file path but mock the behavior of io.list and io.newBufferedReader
            when(mockIo.exists(any())).thenReturn(true);
            when(mockIo.list(any())).thenReturn(Stream.of(corruptFile));

            // Mock a reader that returns garbage
            BufferedReader reader = new BufferedReader(new StringReader("!!not json!!"));
            when(mockIo.newBufferedReader(corruptFile)).thenReturn(reader);

            // WHEN/THEN
            assertThatThrownBy(() -> service.loadCoreInfo(UUID.randomUUID())).isInstanceOf(IOException.class).hasMessageContaining("Failed to load core batch file");
        }
    }

    @Nested
    class GranularCoverageTests {
        @TempDir
        Path baseDir;
        JobPersistenceService service;

        @BeforeEach
        void setUp() throws IOException {
            service = new JobPersistenceService(new DefaultFileIO(), MAPPER, baseDir.toString(), 10);
            service.init();
        }

        @Test
        void tryStartBatch_ReturnsFalse_WhenStatusNotInit() throws JobNotFoundException {
            UUID jobId = UUID.randomUUID();
            UUID batchId = UUID.randomUUID();
            // Create a job where the batch is already IN_PROGRESS
            BatchState inProgress = new BatchState(batchId, WorkUnitState.startNow());
            Job job = createJobWithBatches(jobId).withBatchState(inProgress);
            service.putJobForTest(job);

            // Branch: bs.status() != WorkUnitStatus.INIT
            boolean started = service.tryStartBatch(jobId, batchId);
            assertThat(started).isFalse();
        }

        @Test
        void tryMarkCoreInProgress_ReturnsFalse_WhenAlreadyStarted() throws JobNotFoundException {
            UUID jobId = UUID.randomUUID();
            Job job = createJobWithBatches(jobId).withCoreState(WorkUnitState.startNow());
            service.putJobForTest(job);

            // Branch: job.coreState().status() != WorkUnitStatus.INIT
            boolean result = service.tryMarkCoreInProgress(jobId);
            assertThat(result).isFalse();
        }

        @Test
        void selectNextWorkUnit_ReturnsEmpty_WhenNoSchedulableJobs() {
            // Either registry is empty, or all jobs are COMPLETED (final)
            UUID jobId = UUID.randomUUID();
            Job completedJob = createJobWithBatches(jobId).withStatus(JobStatus.COMPLETED);
            service.putJobForTest(completedJob);

            // Branch: filter(job -> !job.status().isFinal()) results in empty stream
            Optional<WorkUnit> result = service.selectNextWorkUnit();
            assertThat(result).isEmpty();
        }

        @Test
        void updateJobAndReturnThrowsWhenJobMissing() {
            UUID missingId = UUID.randomUUID();

            assertThatThrownBy(() -> service.tryStartBatch(missingId, UUID.randomUUID()))
                    .isInstanceOf(JobNotFoundException.class)
                    .hasMessageContaining(missingId.toString());
        }
    }

    @Nested
    class InternalBranchTests {
        @TempDir
        Path tempDir;

        @Test
        void init_ContinueLoop_WhenSaveJobFails() throws IOException {
            // GIVEN: A directory with one valid job
            UUID j1 = UUID.randomUUID();
            Path j1Dir = tempDir.resolve(j1.toString());
            Files.createDirectories(j1Dir);
            Job job = createJobWithBatches(j1);
            Files.writeString(j1Dir.resolve("job.json"), MAPPER.writeValueAsString(job));

            // Use a Spy of the real implementation so we can use real logic for loading
            FileIo spyIo = spy(new DefaultFileIO());

            doThrow(new IOException("Disk Full")).when(spyIo).newBufferedWriter(argThat(p -> p.toString().endsWith(".tmp")));

            JobPersistenceService s = new JobPersistenceService(spyIo, MAPPER, tempDir.toString(), 5);

            // WHEN
            // This hits the loop: loads job -> tries to save reconciled state -> fails -> logs warn
            s.init();

            // THEN: Registry should be empty because the save failed
            assertThat(s.getJob(j1)).isEmpty();
        }

        @Test
        void loadAllCoreBatchParts_ReturnsEmpty_WhenDirMissing() throws IOException {
            FileIo mockIo = mock(FileIo.class);
            JobPersistenceService s = new JobPersistenceService(mockIo, MAPPER, tempDir.toString(), 5);

            when(mockIo.exists(any())).thenReturn(false);

            ExtractionResourceBundle result = s.loadCoreInfo(UUID.randomUUID());
            assertThat(result.extractionInfoMap()).isEmpty();
        }
    }

    @Nested
    class UpdateJobAndReturnBranchTests {

        @TempDir
        Path baseDir;

        JobPersistenceService persistenceService;
        UUID jobId;

        @BeforeEach
        void setUp() throws IOException {
            persistenceService = new JobPersistenceService(new DefaultFileIO(), MAPPER, baseDir.toString(), 5);
            persistenceService.init();
            jobId = persistenceService.createJob(EMPTY_PARAMETERS.crtdl(), List.of());
        }

        @Test
        void testUpdateJobAndReturn_Branch_IOExceptionInLogic() throws JobNotFoundException {
            String result = persistenceService.updateJobAndReturn(jobId, job -> {
                throw new IOException("Simulated logic-level IO failure");
            });

            assertThat(result).isNull();
            Job updated = persistenceService.getJob(jobId).orElseThrow();
            assertThat(updated.status()).isEqualTo(JobStatus.TEMP_FAILED);
        }

        @Test
        void testUpdateJobAndReturn_Branch_RuntimeExceptionInLogic() {
            assertThatThrownBy(() -> persistenceService.updateJobAndReturn(jobId, job -> {
                throw new RuntimeException("Unexpected runtime bug");
            })).isInstanceOf(RuntimeException.class).hasMessageContaining("Unexpected runtime bug");
        }

        @Test
        void testUpdateJobAndReturn_Branch_NoChangeDetected() throws JobNotFoundException {
            Job currentJob = persistenceService.getJob(jobId).orElseThrow();

            String result = persistenceService.updateJobAndReturn(jobId, job -> new JobPersistenceService.JobAndResult<>(currentJob, "identities-match"));

            assertThat(result).isEqualTo("identities-match");
        }

        @Test
        void testUpdateJobAndReturn_Branch_SaveJobFails() throws IOException, JobNotFoundException {
            FileIo spyIo = org.mockito.Mockito.spy(new DefaultFileIO());
            JobPersistenceService serviceWithSpy = new JobPersistenceService(spyIo, MAPPER, baseDir.toString(), 5);
            serviceWithSpy.putJobForTest(persistenceService.getJob(jobId).get().withStatus(JobStatus.RUNNING_GET_COHORT));
            org.mockito.Mockito.doThrow(new IOException("Disk quota exceeded")).when(spyIo).newBufferedWriter(org.mockito.ArgumentMatchers.argThat(p -> p.toString().endsWith(".tmp")));

            serviceWithSpy.onCohortError(jobId, List.of(), new Exception("Trigger save"));

            assertThat(serviceWithSpy.getJob(jobId)).isPresent();
            assertThat(serviceWithSpy.getJob(jobId).orElseThrow().status()).isEqualTo(JobStatus.TEMP_FAILED);
        }
    }

    @Nested
    class DeleteJobTests {

        @TempDir
        Path baseDir;

        JobPersistenceService service;
        UUID jobId;

        @BeforeEach
        void setUp() throws IOException {
            service = new JobPersistenceService(new DefaultFileIO(), MAPPER, baseDir.toString(), 5);
            service.init();
            jobId = service.createJob(EMPTY_PARAMETERS.crtdl(), List.of());
        }

        @Test
        void removesJobFromRegistryAndDeletesDirectory() throws IOException, JobNotFoundException {
            assertThat(service.getJob(jobId)).isPresent();
            assertThat(baseDir.resolve(jobId.toString())).exists();

            service.deleteJob(jobId);

            assertThat(service.getJob(jobId)).isEmpty();
            assertThat(baseDir.resolve(jobId.toString())).doesNotExist();
        }

        @Test
        void deleteJob_unknownJob_throwsJobNotFoundException() {
            UUID unknownJobId = UUID.randomUUID();

            assertThatThrownBy(() -> service.deleteJob(unknownJobId))
                    .isInstanceOf(JobNotFoundException.class)
                    .hasMessageContaining(unknownJobId.toString());
        }

    }

    @Nested
    class SelectNextWorkUnitTests {

        @TempDir
        Path baseDir;

        JobPersistenceService service;

        @BeforeEach
        void setUp() throws IOException {
            service = new JobPersistenceService(new DefaultFileIO(), MAPPER, baseDir.toString(), 5);
            service.init();
        }

        @Test
        void emptyWhenNoJobCanBeSelected() {
            assertThat(service.selectNextWorkUnit()).isEmpty();
        }
    }

    @Nested
    class SelectNextInternalTests {

        @TempDir
        Path baseDir;

        JobPersistenceService service;

        @BeforeEach
        void setUp() throws IOException {
            service = new JobPersistenceService(new DefaultFileIO(), MAPPER, baseDir.toString(), 5);
            service.init();
        }

        @Test
        void returnsEmptyWhenJobMissing() {
            UUID missingJobId = UUID.randomUUID();

            assertThat(service.selectNextInternal(missingJobId)).isEmpty();
        }

        @Test
        void returnsEmptyWhenJobHasNoNextWorkUnit() {
            UUID jobId = UUID.randomUUID();
            Job completedJob = Job.init(jobId, EMPTY_PARAMETERS)
                    .withStatus(JobStatus.COMPLETED);

            service.putJobForTest(completedJob);

            assertThat(service.selectNextInternal(jobId)).isEmpty();
        }

        @Test
        void returnsWorkUnitWhenJobSchedulable() throws IOException {
            UUID jobId = service.createJob(EMPTY_PARAMETERS.crtdl(), List.of());

            Optional<WorkUnit> result = service.selectNextInternal(jobId);

            assertThat(result).isPresent();
        }
    }

    @Nested
    class PauseJobTests {

        @TempDir
        Path baseDir;

        JobPersistenceService service;
        UUID jobId;

        @BeforeEach
        void setUp() throws IOException {
            service = new JobPersistenceService(new DefaultFileIO(), MAPPER, baseDir.toString(), 5);
            service.init();
            jobId = service.createJob(EMPTY_PARAMETERS.crtdl(), List.of());
        }

        @Test
        void changed() throws JobNotFoundException {
            Job job = service.getJob(jobId).orElseThrow().withStatus(JobStatus.RUNNING_PROCESS_BATCH);
            service.putJobForTest(job);

            Job result = service.pauseJob(jobId);

            assertThat(result.status()).isEqualTo(JobStatus.PAUSED);
            assertThat(service.getJob(jobId).orElseThrow().status()).isEqualTo(JobStatus.PAUSED);
        }

        @Test
        void noOp() throws JobNotFoundException {
            Job job = service.getJob(jobId).orElseThrow().withStatus(JobStatus.PAUSED);
            service.putJobForTest(job);

            Job result = service.pauseJob(jobId);

            assertThat(result.status()).isEqualTo(JobStatus.PAUSED);
            assertThat(service.getJob(jobId).orElseThrow().status()).isEqualTo(JobStatus.PAUSED);
        }

        @Test
        void conflict() throws JobNotFoundException {
            Job job = service.getJob(jobId).orElseThrow().withStatus(JobStatus.COMPLETED);
            service.putJobForTest(job);

            assertThatThrownBy(() -> service.pauseJob(jobId))
                    .isInstanceOf(StateConflictException.class)
                    .hasMessageContaining(job.status().display());
        }

        @Test
        void throwsUnknownJobException() {
            assertThatThrownBy(() -> service.pauseJob(UUID.randomUUID()))
                    .isInstanceOf(JobNotFoundException.class);
        }
    }

    @Nested
    class CancelJobTests {

        @TempDir
        Path baseDir;

        JobPersistenceService service;
        UUID jobId;

        @BeforeEach
        void setUp() throws IOException {
            service = new JobPersistenceService(new DefaultFileIO(), MAPPER, baseDir.toString(), 5);
            service.init();
            jobId = service.createJob(EMPTY_PARAMETERS.crtdl(), List.of());
        }

        @Test
        void changed() throws JobNotFoundException {
            Job job = service.getJob(jobId).orElseThrow()
                    .withStatus(JobStatus.RUNNING_PROCESS_CORE);
            service.putJobForTest(job);

            Job result = service.cancelJob(jobId);

            assertThat(result.status()).isEqualTo(JobStatus.CANCELLED);
            assertThat(service.getJob(jobId).orElseThrow().status()).isEqualTo(JobStatus.CANCELLED);
        }

        @Test
        void noOp() throws JobNotFoundException {
            Job job = service.getJob(jobId).orElseThrow()
                    .withStatus(JobStatus.CANCELLED);
            service.putJobForTest(job);

            Job result = service.cancelJob(jobId);

            assertThat(result.status()).isEqualTo(JobStatus.CANCELLED);
            assertThat(service.getJob(jobId).orElseThrow().status()).isEqualTo(JobStatus.CANCELLED);
        }

        @Test
        void conflict() {
            Job job = service.getJob(jobId).orElseThrow()
                    .withStatus(JobStatus.FAILED);
            service.putJobForTest(job);

            assertThatThrownBy(() -> service.cancelJob(jobId))
                    .isInstanceOf(StateConflictException.class)
                    .hasMessageContaining(job.status().display());
        }

        @Test
        void throwsUnknownJobException() {
            assertThatThrownBy(() -> service.cancelJob(UUID.randomUUID()))
                    .isInstanceOf(JobNotFoundException.class);
        }
    }

    @Nested
    class ResumeJobTests {

        @TempDir
        Path baseDir;

        JobPersistenceService service;
        UUID jobId;

        @BeforeEach
        void setUp() throws IOException {
            service = new JobPersistenceService(new DefaultFileIO(), MAPPER, baseDir.toString(), 5);
            service.init();
            jobId = service.createJob(EMPTY_PARAMETERS.crtdl(), List.of());
        }

        @Test
        void changedFromPaused() throws JobNotFoundException {
            Job job = service.getJob(jobId).orElseThrow()
                    .withStatus(JobStatus.PAUSED)
                    .withCohortState(WorkUnitState.initNow().finishNow(WorkUnitStatus.FINISHED))
                    .withCoreState(WorkUnitState.initNow());
            service.putJobForTest(job);

            Job result = service.resumeJob(jobId);

            assertThat(result.status()).isEqualTo(JobStatus.RUNNING_PROCESS_CORE);
            assertThat(service.getJob(jobId).orElseThrow().status()).isEqualTo(JobStatus.RUNNING_PROCESS_CORE);
        }

        @Test
        void changedFromTempFailed() throws JobNotFoundException {
            UUID batchId = UUID.randomUUID();

            Job job = service.getJob(jobId).orElseThrow()
                    .withStatus(JobStatus.TEMP_FAILED)
                    .withCohortState(WorkUnitState.initNow().finishNow(WorkUnitStatus.FINISHED))
                    .withBatchState(new BatchState(batchId, WorkUnitState.startNow().markTempFailed()));
            service.putJobForTest(job);

            Job result = service.resumeJob(jobId);

            assertThat(result.status()).isEqualTo(JobStatus.RUNNING_PROCESS_BATCH);
            assertThat(result.batches().get(batchId).status()).isEqualTo(WorkUnitStatus.INIT);

            Job updated = service.getJob(jobId).orElseThrow();
            assertThat(updated.status()).isEqualTo(JobStatus.RUNNING_PROCESS_BATCH);
            assertThat(updated.batches().get(batchId).status()).isEqualTo(WorkUnitStatus.INIT);
        }

        @Test
        void noOp() throws JobNotFoundException {
            Job job = service.getJob(jobId).orElseThrow()
                    .withStatus(JobStatus.RUNNING_PROCESS_BATCH);
            service.putJobForTest(job);

            Job result = service.resumeJob(jobId);

            assertThat(result.status()).isEqualTo(JobStatus.RUNNING_PROCESS_BATCH);
            assertThat(service.getJob(jobId).orElseThrow().status()).isEqualTo(JobStatus.RUNNING_PROCESS_BATCH);
        }

        @Test
        void conflict() throws JobNotFoundException {
            Job job = service.getJob(jobId).orElseThrow()
                    .withStatus(JobStatus.FAILED);
            service.putJobForTest(job);

            assertThatThrownBy(() -> service.resumeJob(jobId))
                    .isInstanceOf(StateConflictException.class)
                    .hasMessageContaining(job.status().display());
        }

        @Test
        void throwsUnknownJobException() {
            assertThatThrownBy(() -> service.resumeJob(UUID.randomUUID()))
                    .isInstanceOf(JobNotFoundException.class);
        }
    }
}
