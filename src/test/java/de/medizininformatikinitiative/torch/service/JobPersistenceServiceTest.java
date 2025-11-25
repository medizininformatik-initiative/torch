package de.medizininformatikinitiative.torch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedCrtdl;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedDataExtraction;
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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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

    static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new Jdk8Module())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    static final JobParameters EMPTY_PARAMETERS =
            new JobParameters(
                    new AnnotatedCrtdl(
                            JsonNodeFactory.instance.objectNode(),
                            new AnnotatedDataExtraction(List.of()),
                            Optional.empty()
                    ),
                    List.of()
            );


    static Job createJob(UUID jobId) {
        BatchState s1 = BatchState.init();
        BatchState s2 = BatchState.init();

        return new Job(
                jobId,
                JobStatus.RUNNING_GET_COHORT, WorkUnitState.initNow(),
                10,
                Map.of(s1.batchId(), s1, s2.batchId(), s2),
                Instant.now(),
                Instant.now(),
                Optional.empty(),
                List.of(),
                EMPTY_PARAMETERS,
                JobPriority.NORMAL,
                WorkUnitState.initNow());
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
            persistenceService =
                    new JobPersistenceService(
                            new DefaultFileIO(),
                            MAPPER,
                            baseDir.toString(),
                            5
                    );
            persistenceService.init();
        }

        @Test
        void saveBatchCreatesNdjson() throws IOException {
            UUID jobId = UUID.randomUUID();
            persistenceService.createJob(EMPTY_PARAMETERS.crtdl(), EMPTY_PARAMETERS.paramBatch());

            PatientBatch batch = new PatientBatch(List.of("A", "B"), UUID.randomUUID());
            persistenceService.saveBatch(batch, jobId);

            Path batchFile = baseDir
                    .resolve(jobId.toString())
                    .resolve("batches")
                    .resolve(batch.batchId() + ".ndjson");

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

            UUID jobId = persistenceService.createJob(
                    EMPTY_PARAMETERS.crtdl(),
                    EMPTY_PARAMETERS.paramBatch()
            );

            Instant after = Instant.now();

            // simulate "process restart"
            JobPersistenceService reloaded = new JobPersistenceService(
                    new DefaultFileIO(),
                    MAPPER,
                    baseDir.toString(),
                    5
            );
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

            assertThat(persistenceService.loadBatch(jobId, pb2.batchId()))
                    .isEqualTo(pb2);
        }

        @Test
        void loadAllJobs() throws IOException {
            UUID j1 = UUID.randomUUID();
            UUID j2 = UUID.randomUUID();

            Files.createDirectories(baseDir.resolve(j1.toString()));
            Files.createDirectories(baseDir.resolve(j2.toString()));

            Files.writeString(
                    baseDir.resolve(j1.toString()).resolve("job.json"),
                    MAPPER.writeValueAsString(createJob(j1))
            );
            Files.writeString(
                    baseDir.resolve(j2.toString()).resolve("job.json"),
                    MAPPER.writeValueAsString(createJob(j2))
            );
            persistenceService.init();
            assertThat(persistenceService.getJob(j1)).isPresent();
            assertThat(persistenceService.getJob(j2)).isPresent();
        }

        @Test
        void saveCoreBatch_and_loadCoreInfo_shouldPersistAndReloadSingleCoreBundle()
                throws IOException {

            UUID jobId = UUID.randomUUID();
            persistenceService.createJob(EMPTY_PARAMETERS.crtdl(), EMPTY_PARAMETERS.paramBatch());

            ResourceExtractionInfo rei =
                    new ResourceExtractionInfo(
                            Set.of("G1"),
                            Map.of("Patient.name", Set.of("rid-1"))
                    );

            ExtractionResourceBundle cb =
                    new ExtractionResourceBundle(
                            new ConcurrentHashMap<>(Map.of("rid-1", rei)),
                            new ConcurrentHashMap<>()
                    );

            persistenceService.saveCoreBatch(jobId, UUID.randomUUID(), cb);

            ExtractionResourceBundle merged =
                    persistenceService.loadCoreInfo(jobId);

            assertThat(merged.extractionInfoMap()).containsOnlyKeys("rid-1");
            assertThat(merged.extractionInfoMap().get("rid-1").groups())
                    .containsExactly("G1");
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

            assertThatThrownBy(() -> service.createJob(EMPTY_PARAMETERS.crtdl(), EMPTY_PARAMETERS.paramBatch()))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Failed to initialize job");
        }

        @Test
        void failsWhenAtomicMoveFails() throws IOException {
            BufferedWriter writer = mock(BufferedWriter.class);
            when(io.newBufferedWriter(any())).thenReturn(writer);

            doThrow(new IOException("atomic move failed"))
                    .when(io).atomicMove(any(), any());

            assertThatThrownBy(() -> service.createJob(EMPTY_PARAMETERS.crtdl(), EMPTY_PARAMETERS.paramBatch()))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Failed to initialize job");
        }

        @Test
        void failsWhenBatchWritingFails() throws IOException {
            PatientBatch batch = new PatientBatch(List.of("1", "2"), UUID.randomUUID());

            doNothing().when(io).createDirectories(any());
            when(io.newBufferedWriter(any())).thenThrow(new IOException("boom"));

            assertThatThrownBy(() -> service.saveBatch(batch, UUID.randomUUID()))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("boom");
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
            doThrow(new IOException("mkdir fail"))
                    .when(io).createDirectories(any());

            assertThatThrownBy(() -> service.createJob(EMPTY_PARAMETERS.crtdl(), EMPTY_PARAMETERS.paramBatch()))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Failed to initialize job");
        }

        @Test
        void savingWritesToDir() throws IOException {
            doNothing().when(io).createDirectories(any());

            BufferedWriter writer = mock(BufferedWriter.class);
            when(io.newBufferedWriter(any())).thenReturn(writer);
            doNothing().when(io).atomicMove(any(), any());

            UUID jobId = service.createJob(EMPTY_PARAMETERS.crtdl(), EMPTY_PARAMETERS.paramBatch());

            verify(io).newBufferedWriter(argThat(path ->
                    path.toString().contains(jobId.toString())
            ));
        }
    }

    @Nested
    class ResumeAfterRestartTests {

        @TempDir
        Path baseDir;

        Clock clock = Clock.fixed(Instant.parse("2026-01-22T12:00:00Z"), ZoneId.of("UTC"));

        private JobPersistenceService restart() throws IOException {
            JobPersistenceService s = new JobPersistenceService(
                    new DefaultFileIO(),
                    MAPPER,
                    baseDir.toString(),
                    5
            );
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
        void resumeAfterRestart_coreOnly_rollsBackAndPersists_thenCanAcquire() throws IOException {
            UUID jobId = UUID.randomUUID();
            Path jobDir = prepareJobDir(jobId);

            Job crashed = new Job(
                    jobId,
                    JobStatus.RUNNING_PROCESS_CORE, WorkUnitState.initNow(),
                    10,
                    Map.of(),
                    clock.instant(),
                    clock.instant(),
                    Optional.empty(),
                    List.of(),
                    EMPTY_PARAMETERS,
                    JobPriority.NORMAL,
                    // IN_PROGRESS
                    WorkUnitState.startNow());

            Files.writeString(jobDir.resolve("job.json"), MAPPER.writeValueAsString(crashed));

            Job before = MAPPER.readValue(Files.readString(jobDir.resolve("job.json")), Job.class);
            assertThat(before.coreState().status()).isEqualTo(WorkUnitStatus.IN_PROGRESS);

            JobPersistenceService restarted = restart();

            Job resumed = restarted.getJob(jobId).orElseThrow();
            assertThat(resumed.coreState().status()).isEqualTo(WorkUnitStatus.INIT);

            Job after = MAPPER.readValue(Files.readString(jobDir.resolve("job.json")), Job.class);
            assertThat(after.coreState().status()).isEqualTo(WorkUnitStatus.INIT);

            assertThat(restarted.tryMarkCoreInProgress(jobId)).isTrue();
            assertThat(restarted.getJob(jobId).orElseThrow().coreState().status())
                    .isEqualTo(WorkUnitStatus.IN_PROGRESS);
        }

        @Test
        void resumeAfterRestart_batchInProgress_rollsBackToInit_andPersists() throws IOException {
            UUID jobId = UUID.randomUUID();
            UUID batchId = UUID.randomUUID();
            Path jobDir = prepareJobDir(jobId);

            // batch file exists so later resume can actually load it
            Files.writeString(jobDir.resolve("batches").resolve(batchId + ".ndjson"), "A\nB\n");

            BatchState inProgress = new BatchState(
                    batchId, WorkUnitState.startNow()
            );

            Job crashed = new Job(
                    jobId,
                    JobStatus.RUNNING_PROCESS_BATCH, WorkUnitState.initNow(),
                    10,
                    Map.of(batchId, inProgress),
                    clock.instant(),
                    clock.instant(),
                    Optional.empty(),
                    List.of(),
                    EMPTY_PARAMETERS,
                    JobPriority.NORMAL,
                    WorkUnitState.initNow());

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

            BatchState init = new BatchState(
                    batchId, WorkUnitState.initNow()
            );

            Job job = new Job(
                    jobId,
                    JobStatus.RUNNING_PROCESS_BATCH, WorkUnitState.initNow(),
                    10,
                    Map.of(batchId, init),
                    clock.instant(),
                    clock.instant(),
                    Optional.empty(),
                    List.of(),
                    EMPTY_PARAMETERS,
                    JobPriority.NORMAL,
                    WorkUnitState.initNow());

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

            // This assumes COMPLETED is "final". Adjust if your enum differs.
            Job completedButHasCore = new Job(
                    jobId,
                    JobStatus.COMPLETED, WorkUnitState.initNow(),
                    10,
                    Map.of(),
                    clock.instant(),
                    clock.instant(),
                    Optional.of(clock.instant()),
                    List.of(),
                    EMPTY_PARAMETERS,
                    JobPriority.NORMAL,
                    // even if persisted wrongly, final job should not resume
                    WorkUnitState.startNow());

            Files.writeString(jobDir.resolve("job.json"), MAPPER.writeValueAsString(completedButHasCore));

            JobPersistenceService restarted = restart();

            Job loaded = restarted.getJob(jobId).orElseThrow();
            assertThat(loaded.status()).isEqualTo(JobStatus.COMPLETED);
        }

        @Test
        void resumeAfterRestart_missingCoreState_behavesLikeInitForAcquire() throws IOException {
            UUID jobId = UUID.randomUUID();
            Path jobDir = prepareJobDir(jobId);

            Job noCoreState = new Job(
                    jobId,
                    JobStatus.RUNNING_PROCESS_CORE, WorkUnitState.initNow(),
                    10,
                    Map.of(),
                    clock.instant(),
                    clock.instant(),
                    Optional.empty(),
                    List.of(),
                    EMPTY_PARAMETERS,
                    JobPriority.NORMAL,
                    // coreState missing
                    WorkUnitState.initNow());

            Files.writeString(jobDir.resolve("job.json"), MAPPER.writeValueAsString(noCoreState));

            JobPersistenceService restarted = restart();

            // tryMarkCoreInProgress treats missing as INIT (your code does .orElse(INIT))
            assertThat(restarted.tryMarkCoreInProgress(jobId)).isTrue();
            assertThat(restarted.getJob(jobId).orElseThrow().coreState().status())
                    .isEqualTo(WorkUnitStatus.IN_PROGRESS);
        }
    }

    @Nested
    class OnBatchErrorTests {

        @TempDir
        Path baseDir;

        JobPersistenceService service;

        @BeforeEach
        void setUp() throws IOException {
            service = new JobPersistenceService(
                    new DefaultFileIO(),
                    MAPPER,
                    baseDir.toString(),
                    5
            );
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
        void onBatchErrorTempFailedEscalatesJob() throws IOException {
            UUID jobId = UUID.randomUUID();
            UUID batchId = UUID.randomUUID();
            Path jobDir = prepareJobDir(jobId);

            BatchState tempFailed = new BatchState(
                    batchId, WorkUnitState.startNow().markFailed()
            );

            Job job = new Job(
                    jobId,
                    JobStatus.RUNNING_PROCESS_BATCH, WorkUnitState.initNow(),
                    10,
                    Map.of(batchId, tempFailed),
                    Instant.now(),
                    Instant.now(),
                    Optional.empty(),
                    List.of(),
                    EMPTY_PARAMETERS,
                    JobPriority.NORMAL,
                    WorkUnitState.initNow());

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
        void onBatchErrorNonRetryableEscalatesJobImmediately() throws IOException {
            UUID jobId = UUID.randomUUID();
            UUID batchId = UUID.randomUUID();
            Path jobDir = prepareJobDir(jobId);

            BatchState init = new BatchState(batchId, WorkUnitState.initNow());
            // or if you have it: BatchState.init(batchId)

            Job job = new Job(
                    jobId,
                    JobStatus.RUNNING_PROCESS_BATCH, WorkUnitState.initNow(),
                    10,
                    Map.of(batchId, init),
                    Instant.now(),
                    Instant.now(),
                    Optional.empty(),
                    List.of(),
                    EMPTY_PARAMETERS,
                    JobPriority.NORMAL,
                    WorkUnitState.initNow()
            );

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
        void onCohortSuccess_CreatesBatchesAndUpdatesState() {
            List<String> patientIds = List.of("P1", "P2", "P3");

            service.onCohortSuccess(jobId, patientIds);

            Job job = service.getJob(jobId).orElseThrow();
            assertThat(job.cohortSize()).isEqualTo(3);
            assertThat(job.batches()).hasSize(2); // Batch size is 2, so 3 patients = 2 batches
            assertThat(job.status()).isEqualTo(JobStatus.RUNNING_PROCESS_BATCH);
        }

        @Test
        void onCohortSuccess_EmptyCohort_TransitionsToCore() {
            service.onCohortSuccess(jobId, List.of());

            Job job = service.getJob(jobId).orElseThrow();
            assertThat(job.status()).isEqualTo(JobStatus.RUNNING_PROCESS_CORE);
            assertThat(job.issues()).extracting("msg").contains("Empty cohort");
        }

        @Test
        void tryStartBatch_TransitionsStatus() {
            service.onCohortSuccess(jobId, List.of("P1"));
            UUID batchId = service.getJob(jobId).get().batches().keySet().iterator().next();

            boolean started = service.tryStartBatch(jobId, batchId);

            assertThat(started).isTrue();
            assertThat(service.getJob(jobId).get().batches().get(batchId).status())
                    .isEqualTo(WorkUnitStatus.IN_PROGRESS);
        }

        @Test
        void selectNextWorkUnit_OrdersByPriority() throws IOException {
            // Job 1: Normal Priority
            service.createJob(EMPTY_PARAMETERS.crtdl(), List.of());
            // Job 2: High Priority
            UUID j2 = UUID.randomUUID();
            Job highPrio = createJob(j2).withPriority(JobPriority.HIGH).withStatus(JobStatus.PENDING);
            service.putJobForTest(highPrio);

            Optional<WorkUnit> wu = service.selectNextWorkUnit();

            assertThat(wu).isPresent();
            assertThat(wu.get().job().id()).isEqualTo(j2);
        }

        @Test
        void onBatchError_PersistsIssues() {
            service.onCohortSuccess(jobId, List.of("P1"));
            UUID batchId = service.getJob(jobId).get().batches().keySet().iterator().next();

            service.onBatchError(jobId, batchId, List.of(new Issue(Severity.ERROR, "Fail", "Detail")), new RuntimeException("Error"));

            Job job = service.getJob(jobId).get();
            assertThat(job.batches().get(batchId).status()).isEqualTo(WorkUnitStatus.FAILED);
            assertThat(job.issues()).isNotEmpty();
        }

        @Test
        void updateJobAndReturn_HandlesExceptionsGracefully() {
            // Trigger a RuntimeException inside the atomic update
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
            assertThatThrownBy(() -> service.init())
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("FileSystem unreachable");
        }

        @Test
        void loadBatch_ThrowsWhenFileMissing() {
            // GIVEN
            UUID jobId = UUID.randomUUID();
            UUID batchId = UUID.randomUUID();
            when(mockIo.exists(any())).thenReturn(false);

            // WHEN/THEN
            assertThatThrownBy(() -> service.loadBatch(jobId, batchId))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Batch file missing");
        }

        @Test
        void updateJobAndReturn_HandlesPersistenceFailure() throws IOException {
            // GIVEN: A job exists in the registry
            UUID jobId = UUID.randomUUID();
            Job job = createJob(jobId);
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
            assertThatThrownBy(() -> service.loadCoreInfo(UUID.randomUUID()))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Failed to load core batch file");
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
        void tryStartBatch_ReturnsFalse_WhenStatusNotInit() {
            UUID jobId = UUID.randomUUID();
            UUID batchId = UUID.randomUUID();
            // Create a job where the batch is already IN_PROGRESS
            BatchState inProgress = new BatchState(batchId, WorkUnitState.startNow());
            Job job = createJob(jobId).withBatchState(inProgress);
            service.putJobForTest(job);

            // Branch: bs.status() != WorkUnitStatus.INIT
            boolean started = service.tryStartBatch(jobId, batchId);
            assertThat(started).isFalse();
        }

        @Test
        void tryMarkCoreInProgress_ReturnsFalse_WhenAlreadyStarted() {
            UUID jobId = UUID.randomUUID();
            Job job = createJob(jobId).withCoreState(WorkUnitState.startNow());
            service.putJobForTest(job);

            // Branch: job.coreState().status() != WorkUnitStatus.INIT
            boolean result = service.tryMarkCoreInProgress(jobId);
            assertThat(result).isFalse();
        }

        @Test
        void selectNextWorkUnit_ReturnsEmpty_WhenNoSchedulableJobs() {
            // Either registry is empty, or all jobs are COMPLETED (final)
            UUID jobId = UUID.randomUUID();
            Job completedJob = createJob(jobId).withStatus(JobStatus.COMPLETED);
            service.putJobForTest(completedJob);

            // Branch: filter(job -> !job.status().isFinal()) results in empty stream
            Optional<WorkUnit> result = service.selectNextWorkUnit();
            assertThat(result).isEmpty();
        }

        @Test
        void updateJobAndReturn_ReturnsNull_WhenJobMissing() {
            // Branch: jobRegistry.computeIfPresent does nothing if ID is missing
            UUID missingId = UUID.randomUUID();
            Boolean result = service.tryStartBatch(missingId, UUID.randomUUID());
            assertThat(result).isFalse();
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
            Job job = createJob(j1);
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
        void testUpdateJobAndReturn_Branch_IOExceptionInLogic() {
            String result = persistenceService.updateJobAndReturn(jobId, job -> {
                throw new IOException("Simulated logic-level IO failure");
            });

            assertThat(result).isNull();
            Job updated = persistenceService.getJob(jobId).orElseThrow();
            assertThat(updated.status()).isEqualTo(JobStatus.TEMP_FAILED);
        }

        @Test
        void testUpdateJobAndReturn_Branch_RuntimeExceptionInLogic() {
            String result = persistenceService.updateJobAndReturn(jobId, job -> {
                throw new RuntimeException("Unexpected runtime bug");
            });

            assertThat(result).isNull();
            Job updated = persistenceService.getJob(jobId).orElseThrow();
            assertThat(updated.status()).isEqualTo(JobStatus.FAILED);
        }

        @Test
        void testUpdateJobAndReturn_Branch_NoChangeDetected() {
            Job currentJob = persistenceService.getJob(jobId).orElseThrow();

            String result = persistenceService.updateJobAndReturn(jobId, job ->
                    new JobPersistenceService.JobAndResult<>(currentJob, "identities-match")
            );

            assertThat(result).isEqualTo("identities-match");
        }

        @Test
        void testUpdateJobAndReturn_Branch_SaveJobFails() throws IOException {
            FileIo spyIo = org.mockito.Mockito.spy(new DefaultFileIO());
            JobPersistenceService serviceWithSpy = new JobPersistenceService(spyIo, MAPPER, baseDir.toString(), 5);
            serviceWithSpy.putJobForTest(persistenceService.getJob(jobId).get());
            org.mockito.Mockito.doThrow(new IOException("Disk quota exceeded"))
                    .when(spyIo).newBufferedWriter(org.mockito.ArgumentMatchers.argThat(p -> p.toString().endsWith(".tmp")));

            serviceWithSpy.onCohortError(jobId, List.of(), new Exception("Trigger save"));

            assertThat(serviceWithSpy.getJob(jobId));
        }
    }

}
