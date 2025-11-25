package de.medizininformatikinitiative.torch.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.config.TorchProperties;
import de.medizininformatikinitiative.torch.jobhandling.BatchState;
import de.medizininformatikinitiative.torch.jobhandling.FileIO;
import de.medizininformatikinitiative.torch.jobhandling.Issue;
import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.WorkUnitStatus;
import de.medizininformatikinitiative.torch.jobhandling.result.BatchResult;
import de.medizininformatikinitiative.torch.jobhandling.result.CoreResult;
import de.medizininformatikinitiative.torch.jobhandling.workunit.WorkUnit;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedCrtdl;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionResourceBundle;
import de.medizininformatikinitiative.torch.model.extraction.ResourceExtractionInfo;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private static final String TEMP_DIR_NAME = "temp";
    private static final String CORE_DIR_NAME = "core_batches";

    private final FileIO io;
    private final ObjectMapper mapper;
    private final Path baseDir;

    private final Map<UUID, Job> jobRegistry = new ConcurrentHashMap<>();

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
    // Path helpers
    // ─────────────────────────────────────────────────────────────────────────────

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
        return jobDir(jobId).resolve(CORE_DIR_NAME);
    }

    private void ensureStructure(UUID jobId) throws IOException {
        io.createDirectories(jobDir(jobId));
        io.createDirectories(batchDir(jobId));
        io.createDirectories(tempDir(jobId));
        io.createDirectories(coreBatchDir(jobId));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Atomic job update
    // ─────────────────────────────────────────────────────────────────────────────

    private <T> T updateJobAndReturn(UUID jobId, JobUpdate<T> fn) {
        //Handle IO internally
        AtomicReference<T> resultRef = new AtomicReference<>();

        jobRegistry.computeIfPresent(jobId, (id, current) -> {
            try {
                JobAndResult<T> jr = fn.apply(current);
                saveJob(jr.job());
                resultRef.set(jr.result());
                return jr.job();
            } catch (IOException e) {
                Job result = Job.onJobError(jobRegistry.get(jobId), e, List.of());
                resultRef.set(null);
                return result;
            }
        });

        return resultRef.get();
    }

    private void saveJob(Job job) throws IOException {
        requireNonNull(job);
        UUID jobId = job.id();

        ensureStructure(jobId);

        Path tmp = tempDir(jobId).resolve(JOB_FILE_NAME + ".tmp");
        Path file = jobDir(jobId).resolve(JOB_FILE_NAME);

        try (Writer out = io.newBufferedWriter(tmp)) {
            mapper.writeValue(out, job);
        }

        io.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Job creation / loading
    // ─────────────────────────────────────────────────────────────────────────────

    public UUID createJob(AnnotatedCrtdl crtdl, List<String> patientIds, UUID jobId) throws IOException {
        Job initialJob = Job.createInitialJob(crtdl, patientIds, jobId);
        initJob(initialJob);
        return jobId;
    }

    public void initJob(Job initialJob) throws IOException {
        try {
            requireNonNull(initialJob);
            UUID jobId = initialJob.id();

            if (jobRegistry.containsKey(jobId)) {
                throw new IllegalStateException("Job " + jobId + " already exists");
            }

            ensureStructure(jobId);
            saveJob(initialJob);
            jobRegistry.put(jobId, initialJob);
            logger.debug("Initialized new job {}", jobId);
        } catch (IOException e) {
            throw new IOException("Failed to initialize job : " + e.getMessage(), e);
        }


    }

    private Job loadJobFromDirectory(Path dir) throws IOException {
        Path jobFile = dir.resolve(JOB_FILE_NAME);
        try (var reader = io.newBufferedReader(jobFile)) {
            return mapper.readValue(reader, Job.class);
        }
    }

    public List<Job> loadAllJobs() throws IOException {
        if (!io.exists(baseDir)) return List.of();

        try (Stream<Path> dirs = io.list(baseDir)) {
            return dirs.filter(io::isDirectory).map(path -> {
                try {
                    return loadJobFromDirectory(path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).toList();
        }
    }

    public Job loadJob(UUID jobId) throws IOException {
        Job cached = jobRegistry.get(jobId);
        if (cached != null) {
            return cached;
        }
        Path dir = jobDir(jobId);
        return loadJobFromDirectory(dir);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // WorkUnit selection
    // ─────────────────────────────────────────────────────────────────────────────

    private Optional<WorkUnit> selectNextInternal(UUID jobId) {
        return Optional.ofNullable(updateJobAndReturn(jobId, job -> {
            Optional<WorkUnit> maybeWU = job.selectNextWorkUnit();
            if (maybeWU.isEmpty()) {
                return new JobAndResult<>(job, null);
            }
            WorkUnit wu = maybeWU.get();
            Job updated = wu.job(); // job with next stage set
            return new JobAndResult<>(updated, wu);
        }));
    }

    public Optional<WorkUnit> selectNextWorkUnit() {
        return jobRegistry.values().stream().filter(job -> !job.status().isFinal()).sorted(Comparator.comparing(Job::priority).reversed().thenComparing(Job::startedAt)).flatMap(job -> selectNextInternal(job.id()).stream()).findFirst();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Batch saving / loading
    // ─────────────────────────────────────────────────────────────────────────────

    public void saveBatch(PatientBatch batch, UUID jobId) throws IOException {
        ensureStructure(jobId);

        Path tmp = tempDir(jobId).resolve(batch.batchId() + ".ndjson.tmp");
        Path file = batchDir(jobId).resolve(batch.batchId() + ".ndjson");

        try (BufferedWriter writer = io.newBufferedWriter(tmp)) {
            for (String id : batch.ids()) {
                writer.write(id);
                writer.newLine();
            }
        }

        io.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

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
     * Marks batch as IN_PROGRESS atomically and then loads the NDJSON.
     */
    public PatientBatch loadBatchForProcessing(UUID jobId, UUID batchId) throws IOException {
        updateJobAndReturn(jobId, job -> {
            BatchState old = job.batches().get(batchId);
            if (old == null) {
                throw new IllegalStateException("Missing batch" + batchId);
            }
            BatchState updated = old.updateStatus(WorkUnitStatus.IN_PROGRESS);
            Job newJob = job.updateBatch(updated);
            return new JobAndResult<>(newJob, null);
        });

        return loadBatch(jobId, batchId);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Core bundle saving / merging
    // ─────────────────────────────────────────────────────────────────────────────

    public void saveCoreBatch(UUID jobId, UUID batchId, ExtractionResourceBundle cb) throws IOException {
        ensureStructure(jobId);
        Path tmp = coreBatchDir(jobId).resolve(batchId + ".json.tmp");
        Path file = coreBatchDir(jobId).resolve(batchId + ".json");
        try (Writer writer = io.newBufferedWriter(tmp)) {
            mapper.writeValue(writer, cb.extractionInfoMap());
        }
        io.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private List<ExtractionResourceBundle> loadAllCoreBatchParts(UUID jobId) throws IOException {
        Path dir = coreBatchDir(jobId);
        if (!io.exists(dir)) return List.of();

        try (Stream<Path> files = io.list(dir)) {
            List<ExtractionResourceBundle> results = new ArrayList<>();
            for (Path path : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                try (var reader = io.newBufferedReader(path)) {
                    Map<String, ResourceExtractionInfo> infoMap =
                            mapper.readValue(reader, new TypeReference<Map<String, ResourceExtractionInfo>>() {
                            });
                    results.add(new ExtractionResourceBundle(
                            new ConcurrentHashMap<>(infoMap),
                            new ConcurrentHashMap<>()
                    ));
                } catch (Exception e) {
                    // wrap *any* error in IOException, as requested
                    throw new IOException("Failed to load core batch file: " + path, e);
                }
            }
            return results;
        }
    }

    public ExtractionResourceBundle loadCoreInfo(UUID jobId) throws IOException {
        List<ExtractionResourceBundle> parts = loadAllCoreBatchParts(jobId);
        ExtractionResourceBundle merged = new ExtractionResourceBundle();
        for (ExtractionResourceBundle part : parts) {
            merged = merged.merge(part);
        }
        return merged;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // High-level transitions – called by WorkUnits
    // ─────────────────────────────────────────────────────────────────────────────

    public void onCreateBatchesSuccess(UUID jobId, List<PatientBatch> batches) {
        updateJobAndReturn(jobId, job -> {
            Map<UUID, BatchState> stateMap = new HashMap<>();
            for (PatientBatch b : batches) {
                saveBatch(b, jobId);
                stateMap.put(b.batchId(), new BatchState(b.batchId(), WorkUnitStatus.INIT, Optional.empty(), Optional.empty()));
            }
            Job updated = Job.onBatchesCreated(job, stateMap);
            return new JobAndResult<>(updated, null);
        });
    }

    public void onBatchProcessingSuccess(BatchResult result) {
        UUID jobId = result.jobID();
        updateJobAndReturn(jobId, job -> {
            if (result.resultCoreBundle().isPresent()) {
                saveCoreBatch(jobId, result.batchState().batchId(), result.resultCoreBundle().get());
            }
            Job updated = Job.onBatchProcessingSuccess(job, result);
            return new JobAndResult<>(updated, null);
        });
    }

    public void onBatchError(UUID jobId, UUID batchId, List<Issue> issues, Exception e) {
        updateJobAndReturn(jobId, job -> {
            Job updated = Job.onBatchError(job, batchId, e, issues);
            return new JobAndResult<>(updated, null);
        });
    }

    public void onJobError(UUID jobId, List<Issue> issues, Exception e) {
        updateJobAndReturn(jobId, job -> {
            Job updated = Job.onJobError(job, e, issues);
            return new JobAndResult<>(updated, null);
        });
    }

    public void onCoreSuccess(CoreResult result) {
        UUID jobId = result.jobId();
        updateJobAndReturn(jobId, job -> {
            Job updated = Job.onCoreSuccess(job, result);
            return new JobAndResult<>(updated, null);
        });
    }

    @FunctionalInterface
    public interface JobUpdate<T> {
        JobAndResult<T> apply(Job job) throws IOException;
    }

    public record JobAndResult<T>(Job job, T result) {
    }
}
