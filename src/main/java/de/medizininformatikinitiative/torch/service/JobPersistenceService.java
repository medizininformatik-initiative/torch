package de.medizininformatikinitiative.torch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.config.TorchProperties;
import de.medizininformatikinitiative.torch.jobhandling.BatchState;
import de.medizininformatikinitiative.torch.jobhandling.FileIO;
import de.medizininformatikinitiative.torch.jobhandling.Issue;
import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.JobStatus;
import de.medizininformatikinitiative.torch.jobhandling.WorkUnitStatus;
import de.medizininformatikinitiative.torch.jobhandling.result.BatchResult;
import de.medizininformatikinitiative.torch.jobhandling.workunit.WorkUnit;
import de.medizininformatikinitiative.torch.model.management.CachelessResourceBundle;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

@Service
public class JobPersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(JobPersistenceService.class);
    private static final String JOB_FILE_NAME = "job.json";
    private static final String BATCH_DIR_NAME = "batches";
    private static final String TEMP_DIR_NAME = "temp";
    private static final String CORE_BATCH_DIR_NAME = "core_batches";

    private final FileIO io;
    private final ObjectMapper mapper;
    private final Path baseDir;

    private final Map<UUID, Job> jobRegistry = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ReentrantLock> jobLocks = new ConcurrentHashMap<>();

    public JobPersistenceService(FileIO io, ObjectMapper mapper, TorchProperties properties) {
        this.io = io;
        this.mapper = mapper;
        this.baseDir = Paths.get(properties.results().dir()).toAbsolutePath();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Init
    // ─────────────────────────────────────────────────────────────────────────────

    @PostConstruct
    public void init() throws IOException {
        if (!io.exists(baseDir)) {
            io.createDirectories(baseDir);
        }
        loadAllJobs().forEach(job -> jobRegistry.put(job.id(), job));
        logger.info("Loaded {} jobs from {}", jobRegistry.size(), baseDir);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Locking and filesystem helpers
    // ─────────────────────────────────────────────────────────────────────────────

    private ReentrantLock getLock(UUID jobId) {
        return jobLocks.computeIfAbsent(jobId, id -> new ReentrantLock());
    }

    private Path jobDir(UUID jobId) {
        return baseDir.resolve(jobId.toString());
    }

    private Path batchDir(UUID jobId) {
        return jobDir(jobId).resolve(BATCH_DIR_NAME);
    }

    private Path tempDir(UUID jobId) {
        return jobDir(jobId).resolve(TEMP_DIR_NAME);
    }

    private Path coreBatchDir(UUID jobId) {
        return jobDir(jobId).resolve(CORE_BATCH_DIR_NAME);
    }

    private void ensureStructure(UUID jobId) throws IOException {
        io.createDirectories(jobDir(jobId));
        io.createDirectories(batchDir(jobId));
        io.createDirectories(tempDir(jobId));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Core atomic job mutation mechanism
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Atomic per-job update:
     * - acquire per-job lock
     * - compute new job value inside registry.compute()
     * - persist new job via doSaveJob
     * - return caller-defined result
     */
    private <T> T updateJobAndReturn(UUID jobId, JobUpdate<T> fn) throws IOException {
        AtomicReference<T> resultRef = new AtomicReference<>();
        AtomicReference<IOException> exRef = new AtomicReference<>();

        jobRegistry.computeIfPresent(jobId, (id, current) -> {
            try {
                JobAndResult<T> jr = fn.apply(current);
                doSaveJob(jr.job());
                resultRef.set(jr.result());
                return jr.job();
            } catch (IOException e) {
                exRef.set(e);
                resultRef.set(null);
                return current;
            }
        });

        if (exRef.get() != null) {
            throw exRef.get();
        }
        return resultRef.get();
    }

    private void doSaveJob(Job job) throws IOException {
        requireNonNull(job);

        UUID jobId = job.id();
        ensureStructure(jobId);

        Path tmp = tempDir(jobId).resolve(JOB_FILE_NAME + ".tmp");
        Path finalFile = jobDir(jobId).resolve(JOB_FILE_NAME);

        try (Writer out = io.newBufferedWriter(tmp)) {
            mapper.writeValue(out, job);
        }

        io.move(tmp, finalFile,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }

    public List<Job> loadAllJobs() throws IOException {
        if (!io.exists(baseDir)) {
            return List.of();
        }

        try (Stream<Path> dirs = io.list(baseDir)) {
            return dirs
                    .filter(io::isDirectory)
                    .map(dir -> {
                        try {
                            return loadJobFromDirectory(dir);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .toList();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Low-level IO
    // ─────────────────────────────────────────────────────────────────────────────

    private Optional<WorkUnit> selectNextInternal(UUID jobId) {
        try {
            return Optional.ofNullable(
                    updateJobAndReturn(jobId, job -> {

                        Optional<WorkUnit> maybeWU = job.selectNextWorkUnit();
                        if (maybeWU.isEmpty()) {
                            return new JobAndResult<>(job, null);
                        }

                        WorkUnit wu = maybeWU.get();
                        Job updated = wu.job(); // next stage job

                        return new JobAndResult<>(updated, wu);
                    })
            );
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Job loading
    // ─────────────────────────────────────────────────────────────────────────────

    public Optional<WorkUnit> selectNextWorkUnit() {
        return jobRegistry.values().stream()
                .filter(job -> !job.status().isFinal())
                .sorted(Comparator.comparing(Job::priority).reversed()
                        .thenComparing(Job::startedAt))
                .flatMap(job -> selectNextInternal(job.id()).stream())
                .findFirst();
    }

    private Job loadJobFromDirectory(Path jobDir) throws IOException {
        Path jobFile = jobDir.resolve(JOB_FILE_NAME);
        try (var reader = io.newBufferedReader(jobFile)) {
            return mapper.readValue(reader, Job.class);
        }
    }

    public Job loadJob(UUID jobId) throws IOException {
        return loadJobFromDirectory(jobDir(jobId));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Work unit selection via unified updateJobAndReturn
    // ─────────────────────────────────────────────────────────────────────────────

    public void saveBatch(PatientBatch batch, UUID jobId) throws IOException {
        ReentrantLock lock = getLock(jobId);
        lock.lock();
        try {
            ensureStructure(jobId);

            Path outFile = batchDir(jobId).resolve(batch.batchId() + ".ndjson");
            Path tmpFile = tempDir(jobId).resolve(batch.batchId() + ".ndjson.tmp");

            try (BufferedWriter writer = io.newBufferedWriter(tmpFile)) {
                for (String id : batch.ids()) {
                    writer.write(id);
                    writer.newLine();
                }
            }

            io.move(tmpFile, outFile,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } finally {
            lock.unlock();
        }
    }

    public PatientBatch loadBatch(UUID jobId, UUID batchId) throws IOException {
        ReentrantLock lock = getLock(jobId);
        lock.lock();
        try {
            Path file = batchDir(jobId).resolve(batchId + ".ndjson");

            if (!io.exists(file)) {
                throw new IOException("Batch file missing: " + file);
            }

            try (Stream<String> lines = io.lines(file)) {
                return new PatientBatch(lines.toList(), batchId);
            }
        } finally {
            lock.unlock();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Batch saving and loading
    // ─────────────────────────────────────────────────────────────────────────────

    public void saveCoreBatch(UUID jobId, UUID batchId, CachelessResourceBundle cb) throws IOException {
        ReentrantLock lock = getLock(jobId);
        lock.lock();
        try {
            ensureStructure(jobId);
            io.createDirectories(coreBatchDir(jobId));

            Path tmp = coreBatchDir(jobId).resolve(batchId + ".json.tmp");
            Path finalFile = coreBatchDir(jobId).resolve(batchId + ".json");

            try (Writer out = io.newBufferedWriter(tmp)) {
                mapper.writeValue(out, cb);
            }

            io.move(tmp, finalFile,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } finally {
            lock.unlock();
        }
    }

    private List<CachelessResourceBundle> loadAllCoreBatchParts(UUID jobId) throws IOException {
        Path dir = coreBatchDir(jobId);
        if (!io.exists(dir)) return List.of();

        try (Stream<Path> files = io.list(dir)) {
            return files
                    .filter(path -> path.toString().endsWith(".json"))
                    .map(path -> {
                        try (var reader = io.newBufferedReader(path)) {
                            return mapper.readValue(reader, CachelessResourceBundle.class);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .toList();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Core batch saving and merging
    // ─────────────────────────────────────────────────────────────────────────────

    public ResourceBundle loadCoreInfo(Job job) {
        requireNonNull(job);
        UUID jobId = job.id();

        ReentrantLock lock = getLock(jobId);
        lock.lock();
        try {
            List<CachelessResourceBundle> parts = loadAllCoreBatchParts(jobId);

            ResourceBundle merged = new ResourceBundle();
            parts.forEach(merged::merge);
            return merged;

        } catch (IOException e) {
            throw new RuntimeException("Failed to load merged core info for job " + jobId, e);
        } finally {
            lock.unlock();
        }
    }

    public void saveBatches(UUID jobId, List<PatientBatch> batches) {
        try {
            updateJobAndReturn(jobId, job -> {

                Map<UUID, BatchState> map = new HashMap<>();
                for (PatientBatch batch : batches) {
                    saveBatch(batch, jobId);
                    map.put(batch.batchId(),
                            new BatchState(batch.batchId(), WorkUnitStatus.INIT,
                                    Optional.empty(), Optional.empty()));
                }

                Job updated = job.initBatches(map);
                return new JobAndResult<>(updated, null);
            });

        } catch (IOException e) {
            throw new RuntimeException("Failed to save batches for job " + jobId, e);
        }
    }

    public void updateBatchInfo(UUID jobId, BatchResult result) throws IOException {
        updateJobAndReturn(jobId, job -> {

            // update core bundle if present
            result.resultCoreBundle().ifPresent(cb -> {
                try {
                    saveCoreBatch(jobId, result.batchState().batchId(), cb);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            });

            // update batch state
            BatchState old = job.batches().get(result.batchState().batchId());
            if (old == null) {
                throw new IllegalStateException("Missing batchState " +
                        result.batchState().batchId() + " in job " + jobId);
            }

            BatchState updatedState = old.updateStatus(result.batchState().status());
            Job updated = job.updateBatch(updatedState)
                    .updateIssues(result.issues());

            return new JobAndResult<>(updated, null);
        });
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // High-level job updates using updateJobAndReturn
    // ─────────────────────────────────────────────────────────────────────────────

    public void updateJob(UUID jobId, JobStatus newStatus, List<Issue> issues) {
        try {
            updateJobAndReturn(jobId, job -> {
                Job updated = job
                        .updateStatus(newStatus)
                        .updateIssues(issues);
                return new JobAndResult<>(updated, null);
            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to update job " + jobId, e);
        }
    }

    public void initJob(Job initialJob) {
        requireNonNull(initialJob);
        UUID jobId = initialJob.id();
        ReentrantLock lock = getLock(jobId);

        lock.lock();
        try {
            // Prevent overwriting an existing job
            if (jobRegistry.containsKey(jobId)) {
                throw new IllegalStateException("Job " + jobId + " already exists");
            }

            // Create directories and persist job
            doSaveJob(initialJob);

            // Register in in-memory state
            jobRegistry.put(jobId, initialJob);

            logger.info("Initialized new job {}", jobId);

        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize job " + jobId, e);
        } finally {
            lock.unlock();
        }
    }

    public void increaseJobRetry(UUID jobId) {
        try {
            updateJobAndReturn(jobId, job -> {
                Job updated = job.updateRetry();
                return new JobAndResult<>(updated, null);
            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to finish job " + jobId, e);
        }
    }

    @FunctionalInterface
    public interface JobUpdate<T> {
        JobAndResult<T> apply(Job job) throws IOException;
    }

    public record JobAndResult<T>(Job job, T result) {
    }
}
