package de.medizininformatikinitiative.torch.jobhandling;

import de.medizininformatikinitiative.torch.config.TorchProperties;
import de.medizininformatikinitiative.torch.jobhandling.result.BatchResult;
import de.medizininformatikinitiative.torch.jobhandling.result.BatchSelection;
import de.medizininformatikinitiative.torch.jobhandling.result.CoreResult;
import de.medizininformatikinitiative.torch.jobhandling.workunit.CreateBatchesWorkUnit;
import de.medizininformatikinitiative.torch.jobhandling.workunit.ProcessBatchWorkUnit;
import de.medizininformatikinitiative.torch.jobhandling.workunit.ProcessCoreWorkUnit;
import de.medizininformatikinitiative.torch.jobhandling.workunit.WorkUnit;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedCrtdl;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.service.CohortQueryService;
import de.medizininformatikinitiative.torch.service.ExtractDataService;
import de.medizininformatikinitiative.torch.service.JobPersistenceService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.Exceptions;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class JobManagerService {
    private static final Logger logger = LoggerFactory.getLogger(JobManagerService.class);
    private static final int MAX_BATCH_RETRIES = 3;
    private static final int MAX_JOB_RETRIES = 2;

    private final ExecutorService executor;
    private final JobPersistenceService jobPersistenceService;
    private final ExtractDataService extractDataService;
    private final CohortQueryService cohortQueryService;
    private final int batchsize;
    private final int maxConcurrency;
    private boolean running;

    public JobManagerService(JobPersistenceService jobPersistenceService,
                             ExtractDataService extractDataService,
                             CohortQueryService cohortQueryService, TorchProperties properties) throws IOException {
        this.jobPersistenceService = jobPersistenceService;
        this.extractDataService = extractDataService;
        this.cohortQueryService = cohortQueryService;
        this.batchsize = properties.batchsize();
        this.maxConcurrency = properties.maxConcurrency();
        this.executor = Executors.newFixedThreadPool(maxConcurrency);
    }

    @PostConstruct
    void init() {
        running = true;
        for (int i = 0; i < maxConcurrency; i++) {
            executor.submit(this::workerLoop);
        }
    }

    @PreDestroy
    void shutdown() throws InterruptedException {
        running = false;
        if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
            logger.warn("Forcing shutdown after timeout");
            executor.shutdownNow();
        }
    }

    private void workerLoop() {
        while (running) {
            Optional<WorkUnit> maybe = jobPersistenceService.selectNextWorkUnit();
            if (maybe.isEmpty()) {
                logger.trace("Waiting 1s to acquire new work units to process");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                }
            } else {
                WorkUnit wu = maybe.get();
                execute(wu);
            }

        }
    }

    private void execute(WorkUnit wu) {
        try {
            switch (wu) {
                case CreateBatchesWorkUnit cb -> executeCreateBatches(cb);
                case ProcessBatchWorkUnit pb -> executeProcessBatch(pb);
                case ProcessCoreWorkUnit pc -> executeProcessCore(pc);
                default -> throw new IllegalStateException("Unexpected WorkUnit type: " + wu);
            }
        } catch (Throwable e) {
            handleWorkUnitFailure(wu, e);
        }

    }

    private void handleWorkUnitFailure(WorkUnit wu, Throwable e) {
        Job job = wu.job();
        if (isRetriableError(e) && job.retry() < MAX_JOB_RETRIES) {
            jobPersistenceService.increaseJobRetry(job.id());
        } else {
            jobPersistenceService.updateJob(job.id(), JobStatus.FAILED, List.of());
        }
    }

    private boolean isRetriableError(Throwable e) {
        return e instanceof IOException;
    }


    /**
     * Executes the CCDL using FLARE or CQL to create PatientBatches.
     * <p>
     * Executed if no patient were given parameters, the CCDL embedded in the CRTDL is executed.
     * Otherwise, persists batches directly.
     *
     * @param workUnit to be executed
     */
    public void executeCreateBatches(CreateBatchesWorkUnit workUnit) throws Throwable {
        Job job = workUnit.job();
        try {
            List<PatientBatch> batches =
                    PatientBatch.of(
                            job.parameters().paramBatch().isEmpty()
                                    ? cohortQueryService.runCohortQuery(job.parameters().crtdl()).block()
                                    : job.parameters().paramBatch()).split(batchsize);
            jobPersistenceService.saveBatches(job.id(), batches);
        } catch (Exception t) {
            throw reactor.core.Exceptions.unwrap(t);
        }

    }

    private void executeProcessBatch(ProcessBatchWorkUnit unit) throws IOException {
        Job job = unit.job();
        UUID jobId = job.id();
        UUID batchId = unit.nextBatch().batchId();

        BatchState oldState = unit.job().batches().get(batchId);

        PatientBatch nextBatch;

        if (oldState.retry() >= MAX_BATCH_RETRIES) {
            BatchResult failureResult = new BatchResult(
                    oldState.updateStatus(WorkUnitStatus.FAILED),
                    Optional.empty(),   // no core bundle
                    List.of(new Issue(Severity.ERROR, "Retries exhausted" + batchId)));
            jobPersistenceService.updateBatchInfo(jobId, failureResult);
            jobPersistenceService.updateJob(jobId, JobStatus.FAILED, List.of(new Issue(Severity.ERROR, "Retries exhausted on Batch " + batchId)));
            return;
        }

        try {
            nextBatch = jobPersistenceService.loadBatch(jobId, batchId);
        } catch (IOException e) {
            logger.error("I/O error loading batch {} for job {}: {}", batchId, jobId, e.getMessage(), e);

            BatchResult retryResult = new BatchResult(
                    oldState.updateRetry(),
                    Optional.empty(),
                    List.of(new Issue(Severity.ERROR, "Failed to load batch marking for retry " + e.getMessage(), e)));
            try {
                jobPersistenceService.updateBatchInfo(jobId, retryResult);
            } catch (IOException ex) {
                logger.error("Failed to persist FAILED state for batch {} in job {}", batchId, jobId, ex);
            }

            return;
        }


        BatchSelection batchSelection = new BatchSelection(job, nextBatch);

        BatchResult result;
        try {
            result = extractDataService
                    .processBatch(batchSelection)
                    .block();
            jobPersistenceService.updateBatchInfo(jobId, result);
        } catch (Exception t) {

            Throwable real = Exceptions.unwrap(t);

            // Fatal or logical errors: mark batch FAILED, fail job
            if (real instanceof IOException ioException) {
                // IO exception inside extraction: retry batch
                BatchResult retry = new BatchResult(
                        oldState.updateRetry(),
                        Optional.empty(),
                        List.of(new Issue(Severity.ERROR, "Processing batch failed: " + real.getMessage(), ioException))
                );
                jobPersistenceService.updateBatchInfo(jobId, retry);
                return;
            }


            BatchResult failure = new BatchResult(
                    oldState.updateStatus(WorkUnitStatus.FAILED),
                    Optional.empty(),
                    List.of(new Issue(Severity.ERROR, real.getMessage(), (Exception) real))
            );
            jobPersistenceService.updateBatchInfo(jobId, failure);
            jobPersistenceService.updateJob(jobId, JobStatus.FAILED, failure.issues());
        }
    }


    /**
     * Add CoreResult
     **/
    private void executeProcessCore(ProcessCoreWorkUnit unit) throws Throwable {
        //TODO load persisted Core Info from Job
        Job job = unit.job();
        try {
            CoreResult result = extractDataService
                    .processCore(job, new ResourceBundle())
                    .block();
            jobPersistenceService.updateJob(job.id(), JobStatus.COMPLETED, result.issues());
        } catch (Exception t) {
            throw reactor.core.Exceptions.unwrap(t);
        }

    }

    // --- 1. Job Creation and Initialization ---

    /**
     * Entry point for a new job via FHIR $extract-data request.
     */
    public void createJob(AnnotatedCrtdl crtdl, List<String> patientIds, UUID jobId) throws IOException {
        Job initialJob = new Job(
                jobId,
                JobStatus.PENDING,
                Map.of(),
                Instant.now(),
                Instant.now(),
                Optional.empty(),
                List.of(),
                new JobParameters(crtdl, patientIds),
                JobPriority.NORMAL, 0);
        jobPersistenceService.initJob(initialJob);
    }
}
