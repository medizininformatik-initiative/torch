package de.medizininformatikinitiative.torch.jobhandling;


import de.medizininformatikinitiative.torch.config.TorchProperties;
import de.medizininformatikinitiative.torch.jobhandling.workunit.WorkUnit;
import de.medizininformatikinitiative.torch.service.JobPersistenceService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class JobScheduler {
    private static final Logger logger = LoggerFactory.getLogger(JobScheduler.class);

    private final int maxConcurrency;
    private final ExecutorService executor;
    private final JobExecutionContext ctx;
    private final JobPersistenceService persistence;
    private boolean running = true;

    public JobScheduler(TorchProperties properties, JobExecutionContext ctx, JobPersistenceService persistence) {
        this.maxConcurrency = properties.maxConcurrency();
        this.executor = Executors.newFixedThreadPool(maxConcurrency);
        this.persistence = persistence;
        this.ctx = ctx;
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
            Optional<WorkUnit> maybe = persistence.selectNextWorkUnit();
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
        wu.execute(ctx);
    }


}
