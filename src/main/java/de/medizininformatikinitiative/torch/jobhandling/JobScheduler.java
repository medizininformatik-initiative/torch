package de.medizininformatikinitiative.torch.jobhandling;


import de.medizininformatikinitiative.torch.config.TorchProperties;
import de.medizininformatikinitiative.torch.jobhandling.failure.RetryabilityUtil;
import de.medizininformatikinitiative.torch.jobhandling.workunit.WorkUnit;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Schedules and executes {@link WorkUnit}s using a fixed-size worker pool.
 * <p>
 * Workers continuously poll persistence for the next executable work unit
 * and execute it synchronously, respecting the configured maximum concurrency.
 */
@Service
public class JobScheduler {
    private static final Logger logger = LoggerFactory.getLogger(JobScheduler.class);

    private final int maxConcurrency;
    private final ExecutorService executor;
    private final JobExecutionContext ctx;
    private volatile boolean running = false;


    static long RETRY_SLEEP_MS = 1000;

    static int MAX_RETRYABLE_FAILURES = 30;

    /**
     * Creates a new scheduler with a fixed worker pool.
     *
     * @param properties configuration providing maximum concurrency
     * @param ctx        execution context passed to work units
     */
    public JobScheduler(TorchProperties properties, JobExecutionContext ctx) {
        this.maxConcurrency = properties.maxConcurrency();
        this.executor = Executors.newFixedThreadPool(maxConcurrency);
        this.ctx = ctx;
        logger.info("JobScheduler constructed");
    }

    /**
     * Starts worker threads after application start
     */
    @EventListener(ApplicationReadyEvent.class)
    void init() {
        logger.warn("JobScheduler starting with maxConcurrency={}", maxConcurrency);
        running = true;
        for (int i = 0; i < maxConcurrency; i++) {
            logger.info("Started worker {} ", i);
            executor.submit(this::workerLoop);
        }
    }

    /**
     * Stops workers and shuts down the executor on application shutdown.
     *
     * @throws InterruptedException if shutdown is interrupted
     */
    @PreDestroy
    void shutdown() throws InterruptedException {
        running = false;
        executor.shutdown();
        if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
            logger.warn("Forcing shutdown after timeout");
            executor.shutdownNow();
        }
    }

    /**
     * Main worker loop that continuously polls for and executes available work units.
     * <p>
     * If no work unit is available, the worker idles briefly before retrying.
     * The loop terminates on interruption or when the scheduler is shut down.
     * Interrupt signals are respected and propagated to allow graceful shutdown.
     */
    private void workerLoop() {
        logger.info("Worker loop started");
        int retryableFailures = 0;
        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                Optional<WorkUnit> maybe = ctx.persistence().selectNextWorkUnit();

                if (maybe.isEmpty()) {
                    Thread.sleep(200);
                    continue;
                }

                WorkUnit wu = maybe.get();

                try {
                    executeBlocking(wu);
                    retryableFailures = 0;
                } catch (Exception e) {
                    if (!RetryabilityUtil.isRetryable(e)) {
                        logger.error("WorkUnit execution failed for job {}", wu.job().id(), e);
                        throw new RuntimeException("Non retryable error encountered", e);
                    }
                    retryableFailures++;

                    logger.warn(
                            "Retryable failure ({} / {}), sleeping {}ms: {}",
                            retryableFailures,
                            MAX_RETRYABLE_FAILURES,
                            RETRY_SLEEP_MS,
                            RetryabilityUtil.rootCauseMessage(e)
                    );

                    if (retryableFailures >= MAX_RETRYABLE_FAILURES) {
                        throw new RuntimeException(
                                "Too many retryable failures, escalating",
                                e
                        );
                    }

                    Thread.sleep(RETRY_SLEEP_MS);
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.info("Worker loop interrupted, shutting down");
        } catch (Exception fatal) {
            logger.error("FATAL WORKER LOOP FAILURE - escalating", fatal);
            terminate();
        }
    }

    /**
     * Visible for testing - allows us to spy and prevent JVM exit during JUnit runs.
     */
    void terminate() {
        System.exit(1);
    }

    void executeBlocking(WorkUnit wu) throws IOException {
        wu.execute(ctx)
                .onErrorResume(t ->
                        Mono.fromRunnable(() -> ctx.persistence().onJobError(wu.job().id(), List.of(), t))
                                .subscribeOn(Schedulers.boundedElastic())
                                .then()
                ).block();
    }

}
