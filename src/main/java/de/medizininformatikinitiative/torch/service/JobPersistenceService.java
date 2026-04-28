package de.medizininformatikinitiative.torch.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.exceptions.JobNotFoundException;
import de.medizininformatikinitiative.torch.exceptions.VersionConflictException;
import de.medizininformatikinitiative.torch.jobhandling.BatchState;
import de.medizininformatikinitiative.torch.jobhandling.FileIo;
import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.JobParameters;
import de.medizininformatikinitiative.torch.jobhandling.JobPriority;
import de.medizininformatikinitiative.torch.jobhandling.JobStatus;
import de.medizininformatikinitiative.torch.jobhandling.failure.Issue;
import de.medizininformatikinitiative.torch.jobhandling.failure.Severity;
import de.medizininformatikinitiative.torch.jobhandling.result.BatchResult;
import de.medizininformatikinitiative.torch.jobhandling.result.CoreResult;
import de.medizininformatikinitiative.torch.jobhandling.workunit.WorkUnit;
import de.medizininformatikinitiative.torch.jobhandling.workunit.WorkUnitState;
import de.medizininformatikinitiative.torch.jobhandling.workunit.WorkUnitStatus;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedCrtdl;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionId;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionResourceBundle;
import de.medizininformatikinitiative.torch.model.extraction.ResourceExtractionInfo;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

@Service
public class JobPersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(JobPersistenceService.class);

    private static final String JOB_FILE_NAME = "job.json";
    private static final String BATCH_DIR_NAME = "batches";
    private static final String CORE_DIR_NAME = "core_batches";

    private final FileIo io;
    private final ObjectMapper mapper;
    private final Path baseDir;
    private final Map<UUID, Job> jobRegistry = new ConcurrentHashMap<>();
    private final int batchSize;

    public JobPersistenceService(
            FileIo io,
            ObjectMapper mapper,
            @Value("${torch.results.dir}") String dir,
            @Value("${torch.batchsize}") int batchSize
    ) {
        this.io = requireNonNull(io);
        this.mapper = requireNonNull(mapper);
        this.baseDir = Paths.get(dir).toAbsolutePath();
        this.batchSize = batchSize;
    }

    // TEST-ONLY. Package-private on purpose.
    void putJobForTest(Job job) {
        jobRegistry.put(job.id(), job);
    }

    /**
     * Initializes the persistence layer and in-memory registry.
     *
     * <p>Ensures {@code baseDir} exists, loads jobs from disk
     * persists reconciled state, and registers jobs in memory.</p>
     *
     * @throws IOException if base directory creation or listing fails
     */
    @PostConstruct
    public void init() throws IOException {
        if (!io.exists(baseDir)) {
            io.createDirectories(baseDir);
        }

        for (Job loaded : loadAllJobs()) {
            if (loaded.status() == JobStatus.DELETED) {
                continue;
            }

            Job reconciled = loaded.status().isFinal()
                    ? loaded
                    : loaded.rollback();

            try {
                saveJob(reconciled);
                jobRegistry.put(reconciled.id(), reconciled);
            } catch (IOException e) {
                logger.warn("Skip loading job {} because persisting reconciled state failed: {}",
                        reconciled.id(), e.getMessage(), e);
            }
        }

        logger.info("Loaded {} jobs from {}", jobRegistry.size(), baseDir);
    }


    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Creates a new job and persists its initial state.
     *
     * @param crtdl      annotated CRTDL
     * @param patientIds initial cohort patient ids
     * @return created job id
     * @throws IOException if persistence fails
     */
    public UUID createJob(AnnotatedCrtdl crtdl, List<String> patientIds) throws IOException {
        UUID jobId = UUID.randomUUID();
        Job initial = Job.init(jobId, new JobParameters(crtdl, patientIds));
        initJob(initial);
        return jobId;
    }

    /**
     * Returns the in-memory job state (if present).
     *
     * @param jobId job id
     * @return optional job
     */
    public Optional<Job> getJob(UUID jobId) {
        return Optional.ofNullable(jobRegistry.get(jobId));
    }


    /**
     * Marks a job as deleted to be removed from the registry by GC.
     *
     * <p>The {@link JobStatus#DELETED} marker is written to disk before the job is removed
     * from the in-memory registry. This ensures that a concurrent job update cannot resurrect
     * the job after deletion, and that the job is not reloaded on the next TORCH startup even
     * if the GC sweep has not yet run. The actual removal of the job directory is handled
     * asynchronously by {@link #gcDeletedJobs()}.</p>
     *
     * @param jobId job id
     * @throws JobNotFoundException if Job is unknown to the registry
     */
    public void deleteJob(UUID jobId) throws JobNotFoundException {
        updateJobAndReturn(jobId, job -> {
            Job deleted = job.delete();
            return new JobAndResult<>(deleted, deleted);
        });
        logger.info("Marked job {} as deleted – directory will be removed by GC", jobId);
    }

    /**
     * Removes directories of deleted jobs from disk.
     *
     * <p>Runs on a fixed delay. Directories of jobs marked {@link JobStatus#DELETED}
     * are removed; failures are logged and retried on the next sweep.</p>
     */
    @Scheduled(fixedDelayString = "${torch.gc.job-deletion-delay-ms:86400000}")
    void gcDeletedJobs() {
        List<Job> jobs;
        try {
            jobs = loadAllJobs();
        } catch (IOException e) {
            logger.warn("GC: failed to list job directories: {}", e.getMessage(), e);
            return;
        }
        for (Job job : jobs) {
            if (job.status() == JobStatus.DELETED) {
                try {
                    io.deleteDir(jobDir(job.id()));
                    logger.info("GC: removed job directory from file system {}", job.id());
                    jobRegistry.remove(job.id());
                } catch (IOException e) {
                    logger.warn("GC: failed to delete job directory {}: {}", job.id(), e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Pauses a job.
     *
     * <p>If the job is already in a final state, the request has no effect.</p>
     *
     * @param jobId job id
     * @return result containing the updated job and previous status
     * @throws JobNotFoundException if Job is unknown to the registry
     */
    public Job pauseJob(UUID jobId) throws JobNotFoundException {
        return updateJobAndReturn(jobId, job -> {
            Job result = job.pause();
            return new JobAndResult<>(result, result);
        });
    }

    /**
     * Cancels a job.
     *
     * <p>If the job is already in a final state, the request has no effect.</p>
     *
     * @param jobId job id
     * @return result containing the updated job and previous status
     * @throws JobNotFoundException if Job is unknown to the registry
     */
    public Job cancelJob(UUID jobId) throws JobNotFoundException {
        return updateJobAndReturn(jobId, job -> {
            Job result = job.cancel();
            return new JobAndResult<>(result, result);
        });
    }

    /**
     * Resumes a job.
     *
     * <p>If the job is not in a resumable state, the request has no effect.</p>
     *
     * @param jobId job id
     * @return result containing the updated job and previous status
     * @throws JobNotFoundException if Job is unknown to the registry
     */
    public Job resumeJob(UUID jobId) throws JobNotFoundException {
        return updateJobAndReturn(jobId, job -> {
            Job result = job.resume();
            return new JobAndResult<>(result, result);
        });
    }

    /**
     * Changes the priority of a job.
     *
     * <p>The {@code expectedVersion} is used for optimistic concurrency: if the job's
     * current version differs, a {@link VersionConflictException} is thrown.</p>
     *
     * @param jobId           job id
     * @param priority        the new priority
     * @param expectedVersion version the caller last observed
     * @return the updated job
     * @throws JobNotFoundException     if the job is unknown to the registry
     * @throws VersionConflictException if the current version does not match {@code expectedVersion}
     */
    public Job changePriority(UUID jobId, JobPriority priority, long expectedVersion) throws JobNotFoundException {
        return updateJobAndReturn(jobId, job -> {
            if (job.version() != expectedVersion) {
                throw new VersionConflictException(jobId, expectedVersion, job.version());
            }
            Job result = job.withPriority(priority);
            return new JobAndResult<>(result, result);
        });
    }

    /**
     * Returns all jobs matching the given filters.
     *
     * <p>Both filters are optional: an empty {@code ids} set means "any id";
     * an empty {@code statuses} set means "any status".</p>
     *
     * @param ids      job IDs to include (empty = all)
     * @param statuses job statuses to include (empty = all)
     * @return matching jobs, ordered by start time descending
     */
    public List<Job> findJobs(Set<UUID> ids, Set<JobStatus> statuses) {
        return jobRegistry.values().stream()
                .filter(job -> job.status() != JobStatus.DELETED)
                .filter(job -> ids.isEmpty() || ids.contains(job.id()))
                .filter(job -> statuses.isEmpty() || statuses.contains(job.status()))
                .sorted(Comparator.comparing(Job::startedAt).reversed())
                .toList();
    }

    /**
     * Selects the next schedulable work unit across all non-final jobs.
     *
     * @return optional work unit
     */
    public Optional<WorkUnit> selectNextWorkUnit() {
        return jobRegistry.values().stream()
                .filter(job -> !job.status().isFinal())
                .sorted(Comparator
                        .comparing(Job::priority).reversed()
                        .thenComparing(Job::startedAt, Comparator.reverseOrder()))
                .flatMap(job -> selectNextInternal(job.id()).stream())
                .findFirst();
    }

    /**
     * Attempts to claim a batch by transitioning INIT -> IN_PROGRESS.
     *
     * @param jobId   job id
     * @param batchId batch id
     * @return true if claimed; false otherwise
     * @throws JobNotFoundException when job is not unknown
     */
    public boolean tryStartBatch(UUID jobId, UUID batchId) throws JobNotFoundException {
        return Boolean.TRUE.equals(updateJobAndReturn(jobId, job -> {
            BatchState bs = job.batches().get(batchId);
            if (bs == null) {
                throw new IllegalStateException("Missing batch " + batchId);
            }
            if (bs.status() != WorkUnitStatus.INIT) {
                return new JobAndResult<>(job, false);
            }
            return new JobAndResult<>(job.withBatchState(bs.startNow()), true);
        }));
    }

    /**
     * Loads a persisted patient batch from disk.
     *
     * @param jobId   owning job id
     * @param batchId batch id
     * @return loaded patient batch
     * @throws IOException if missing or unreadable
     */
    public PatientBatch loadBatch(UUID jobId, UUID batchId) throws IOException {
        Path file = batchDir(jobId).resolve(batchId + ".ndjson");
        if (!io.exists(file)) {
            throw new IOException("Batch file missing: " + file);
        }
        try (Stream<String> lines = io.lines(file)) {
            return new PatientBatch(lines.toList(), batchId);
        }
    }

    /**
     * Persists a patient batch as NDJSON (one patient id per line).
     *
     * @param batch patient batch
     * @param jobId owning job id
     * @throws IOException if persistence fails
     */
    public void saveBatch(PatientBatch batch, UUID jobId) throws IOException {
        ensureDirectoryStructure(jobId);

        Path file = batchDir(jobId).resolve(batch.batchId() + ".ndjson");
        Path tmp = file.resolveSibling(batch.batchId() + ".tmp");

        try (BufferedWriter writer = io.newBufferedWriter(tmp)) {
            for (String id : batch.ids()) {
                writer.write(id);
                writer.newLine();
            }
        }

        io.atomicMove(tmp, file);
    }

    /**
     * Applies cohort success: persists batches and advances job state.
     *
     * @param jobId job id
     * @param ids   cohort ids
     * @throws JobNotFoundException if Job is unknown to the registry
     */
    public void onCohortSuccess(UUID jobId, List<String> ids) throws JobNotFoundException {
        List<PatientBatch> batches = PatientBatch.of(ids).split(batchSize);

        updateJobAndReturn(jobId, job -> {
            Map<UUID, BatchState> stateMap = new HashMap<>();
            for (PatientBatch b : batches) {
                saveBatch(b, jobId);
                stateMap.put(b.batchId(), new BatchState(b.batchId(), WorkUnitState.initNow()));
            }

            Job updated = job.onCohortSuccess(stateMap, ids.size());

            return new JobAndResult<>(updated, null);
        });
    }

    /**
     * Applies cohort error transition.
     *
     * @param jobId  job id
     * @param issues issues to attach
     * @param e      cause
     * @throws JobNotFoundException if Job is unknown to the registry
     */
    public void onCohortError(UUID jobId, List<Issue> issues, Exception e) throws JobNotFoundException {
        updateJobAndReturn(jobId, job -> new JobAndResult<>(job.onCohortError(e, issues), null));
    }


    /**
     * Applies batch success transition and persists the core-batch part.
     *
     * @param result batch result
     * @throws JobNotFoundException if Job is unknown to the registry
     */
    public void onBatchProcessingSuccess(BatchResult result) throws JobNotFoundException {
        UUID jobId = result.jobId();

        updateJobAndReturn(jobId, job -> {
            if (result.resultCoreBundle().isPresent()) {
                saveCoreBatch(
                        jobId,
                        result.batchState().batchId(),
                        result.resultCoreBundle().get()
                );
            }

            return new JobAndResult<>(
                    job.onBatchProcessingSuccess(result),
                    null
            );
        });
    }

    /**
     * Applies batch error transition.
     *
     * @param jobId   job id
     * @param batchId batch id
     * @param issues  issues to attach
     * @param e       cause
     * @throws JobNotFoundException if Job is unknown to the registry
     */
    public void onBatchError(UUID jobId, UUID batchId, List<Issue> issues, Throwable e) throws JobNotFoundException {
        updateJobAndReturn(jobId, job -> new JobAndResult<>(job.onBatchError(batchId, e, issues), null));
    }

    /**
     * Applies core success transition.
     *
     * @param result core result
     * @throws JobNotFoundException if Job is unknown to the registry
     */
    public void onCoreSuccess(CoreResult result) throws JobNotFoundException {
        updateJobAndReturn(result.jobId(), job -> new JobAndResult<>(job.onCoreSuccess(result), null));
    }

    /**
     * Applies core error transition.
     *
     * @param jobId  job id
     * @param issues issues to attach
     * @param e      cause
     * @throws JobNotFoundException if Job is unknown to the registry
     */
    public void onCoreError(UUID jobId, List<Issue> issues, Throwable e) throws JobNotFoundException {
        updateJobAndReturn(jobId, job -> new JobAndResult<>(job.onCoreError(e, issues), null));
    }

    /**
     * Applies orchestration/persistence error transition.
     *
     * @param jobId  job id
     * @param issues issues to attach
     * @param t      cause
     * @throws JobNotFoundException if Job is unknown to the registry
     */
    public void onJobError(UUID jobId, List<Issue> issues, Throwable t) throws JobNotFoundException {
        updateJobAndReturn(jobId, job -> new JobAndResult<>(job.onJobError(t, issues), null));
    }

    /**
     * Attempts to mark core processing INIT -> IN_PROGRESS.
     *
     * @param jobId job id
     * @return true if claimed; false otherwise
     * @throws JobNotFoundException if Job is unknown to the registry
     */
    public boolean tryMarkCoreInProgress(UUID jobId) throws JobNotFoundException {
        return Boolean.TRUE.equals(updateJobAndReturn(jobId, job -> {
            if (job.coreState().status() != WorkUnitStatus.INIT) {
                return new JobAndResult<>(job, false);
            }
            return new JobAndResult<>(job.withCoreState(WorkUnitState.startNow()), true);
        }));
    }

    /**
     * Loads and merges all core-batch parts into a single bundle.
     *
     * @param jobId job id
     * @return merged bundle
     * @throws IOException if loading fails
     */
    public ExtractionResourceBundle loadCoreInfo(UUID jobId) throws IOException {
        List<ExtractionResourceBundle> parts = loadAllCoreBatchParts(jobId);
        ExtractionResourceBundle merged = new ExtractionResourceBundle();
        for (ExtractionResourceBundle part : parts) {
            merged = merged.merge(part);
        }
        return merged;
    }

    /**
     * Persists a core-batch extraction result for a batch.
     *
     * @param jobId   job id
     * @param batchId batch id
     * @param cb      core extraction bundle
     * @throws IOException if persistence fails
     */
    public void saveCoreBatch(UUID jobId, UUID batchId, ExtractionResourceBundle cb) throws IOException {
        ensureDirectoryStructure(jobId);

        Path file = coreBatchDir(jobId).resolve(batchId + ".json");
        Path tmp = file.resolveSibling(batchId + ".json.tmp");

        try (Writer writer = io.newBufferedWriter(tmp)) {
            mapper.writeValue(writer, cb.extractionInfoMap());
        }

        io.atomicMove(tmp, file);
    }

    // -------------------------------------------------------------------------
    // Atomic update primitive
    // -------------------------------------------------------------------------

    /**
     * Atomically updates a job in the registry and persists {@code job.json}
     * if the job changed by applying the function fn to the job.
     * <p>
     * Package private for testing
     *
     * @param jobId job id
     * @param fn    update function
     * @param <T>   result type
     * @return result or {@code null} if update/persist failed
     * @throws JobNotFoundException if Job is unknown to the registry
     */
    <T> T updateJobAndReturn(UUID jobId, JobUpdate<T> fn) throws JobNotFoundException {
        requireNonNull(jobId);
        requireNonNull(fn);

        AtomicReference<T> resultRef = new AtomicReference<>();

        Job result = jobRegistry.computeIfPresent(jobId, (id, current) -> {

            Job updatedJob;
            try {
                JobAndResult<T> jr = fn.apply(current);
                updatedJob = jr.job();
                resultRef.set(jr.result());
            } catch (IOException e) {
                Issue issue = Issue.fromException(
                        Severity.ERROR,
                        "Job update/persistence failed: " + e.getMessage(),
                        e
                );
                updatedJob = current.onJobError(e, List.of(issue));
            }
            if (current.equals(updatedJob)) {
                return current;
            }
            try {
                Job saved = saveJob(updatedJob.incrementVersion());
                // If the caller set result == job, replace it with the version-incremented saved job
                if (resultRef.get() == updatedJob) {
                    @SuppressWarnings("unchecked")
                    T savedAsT = (T) saved;
                    resultRef.set(savedAsT);
                }
                return saved;
            } catch (IOException e) {
                logger.error("Failed to save job.json for {}: {}", id, e.getMessage(), e);
                return current.onJobError(e, List.of());
            }
        });
        if (result == null) {
            throw new JobNotFoundException(jobId);
        }
        return resultRef.get();
    }

    private void initJob(Job initialJob) throws IOException {
        requireNonNull(initialJob);

        UUID jobId = initialJob.id();
        if (jobRegistry.containsKey(jobId)) {
            throw new IllegalStateException("Job " + jobId + " already exists");
        }

        try {
            ensureDirectoryStructure(jobId);
            saveJob(initialJob);
            jobRegistry.put(jobId, initialJob);
            logger.debug("Initialized new job {}", jobId);
        } catch (IOException e) {
            throw new IOException("Failed to initialize job " + jobId, e);
        }
    }


    private Job saveJob(Job job) throws IOException {
        UUID jobId = job.id();
        Path jobFile = jobDir(jobId).resolve(JOB_FILE_NAME);
        Path jobTmpFile = jobFile.resolveSibling(JOB_FILE_NAME + ".tmp");

        try {
            try (Writer out = io.newBufferedWriter(jobTmpFile)) {
                mapper.writeValue(out, job);
            }
            io.atomicMove(jobTmpFile, jobFile);
            return job;
        } catch (IOException e) {
            throw new IOException(
                    "Failed to initialize job " + jobId,
                    e
            );
        }
    }

    // -------------------------------------------------------------------------
    // Persistence internals
    // -------------------------------------------------------------------------

    Optional<WorkUnit> selectNextInternal(UUID jobId) {
        try {
            return Optional.ofNullable(updateJobAndReturn(jobId, job -> {
                Optional<WorkUnit> maybeWU = job.selectNextWorkUnit();
                if (maybeWU.isEmpty()) {
                    return new JobAndResult<>(job, null);
                }
                WorkUnit wu = maybeWU.get();
                return new JobAndResult<>(wu.job(), wu);
            }));
        } catch (JobNotFoundException e) {
            return Optional.empty();
        }
    }

    private Path jobDir(UUID jobId) {
        return baseDir.resolve(jobId.toString());
    }

    private Optional<Job> loadJobFromDirectory(Path dir) {
        Path jobFile = dir.resolve(JOB_FILE_NAME);
        try (var reader = io.newBufferedReader(jobFile)) {
            return Optional.ofNullable(mapper.readValue(reader, Job.class));
        } catch (IOException e) {
            logger.warn("Skipping job directory {}: failed to read {}", dir, jobFile, e);
            return Optional.empty();
        }
    }

    private List<Job> loadAllJobs() throws IOException {
        if (!io.exists(baseDir)) {
            return List.of();
        }
        try (Stream<Path> files = io.list(baseDir)) {
            return files
                    .filter(io::isDirectory)
                    .map(this::loadJobFromDirectory)
                    .flatMap(Optional::stream)
                    .toList();
        }
    }

    private Path batchDir(UUID jobId) {
        return jobDir(jobId).resolve(BATCH_DIR_NAME);
    }

    private Path coreBatchDir(UUID jobId) {
        return jobDir(jobId).resolve(CORE_DIR_NAME);
    }

    private void ensureDirectoryStructure(UUID jobId) throws IOException {
        io.createDirectories(jobDir(jobId));
        io.createDirectories(batchDir(jobId));
        io.createDirectories(coreBatchDir(jobId));
    }

    @FunctionalInterface
    interface JobUpdate<T> {
        JobAndResult<T> apply(Job job) throws IOException;
    }

    record JobAndResult<T>(Job job, T result) {
        JobAndResult {
            requireNonNull(job);
        }
    }

    private List<ExtractionResourceBundle> loadAllCoreBatchParts(UUID jobId) throws IOException {
        Path dir = coreBatchDir(jobId);
        if (!io.exists(dir)) return List.of();

        try (Stream<Path> files = io.list(dir)) {
            List<ExtractionResourceBundle> results = new ArrayList<>();
            for (Path path : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                try (var reader = io.newBufferedReader(path)) {
                    Map<ExtractionId, ResourceExtractionInfo> infoMap =
                            mapper.readValue(reader, new TypeReference<>() {
                            });
                    results.add(new ExtractionResourceBundle(
                            new ConcurrentHashMap<>(infoMap),
                            new ConcurrentHashMap<>()
                    ));
                } catch (Exception e) {
                    throw new IOException("Failed to load core batch file: " + path, e);
                }
            }
            return results;
        }
    }
}
