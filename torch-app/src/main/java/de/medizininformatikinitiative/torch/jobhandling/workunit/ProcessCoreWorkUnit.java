package de.medizininformatikinitiative.torch.jobhandling.workunit;

import de.medizininformatikinitiative.torch.exceptions.JobNotFoundException;
import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.JobExecutionContext;
import de.medizininformatikinitiative.torch.jobhandling.failure.RetryabilityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

public record ProcessCoreWorkUnit(Job job) implements WorkUnit {
    private static final Logger logger = LoggerFactory.getLogger(ProcessCoreWorkUnit.class);

    /**
     * Executes the core processing step.
     * <p>
     * Loads persisted core information,
     * processes the core, and updates job state accordingly. Errors are recorded
     * as job errors; no exception is propagated downstream.
     *
     * @param ctx execution context providing persistence and extraction services
     * @return a {@code Mono} that completes when core processing finishes or is skipped
     */
    @Override
    public Mono<Void> execute(JobExecutionContext ctx) {
        return Mono.fromCallable(() -> ctx.persistence().loadCoreInfo(job.id()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(coreBundle -> ctx.extract().processCore(job, coreBundle))
                .flatMap(result ->
                        Mono.fromCallable(() -> {
                                    ctx.persistence().onCoreSuccess(result);
                                    return 0;
                                })
                                .subscribeOn(Schedulers.boundedElastic())
                                .then()
                )
                .onErrorResume(JobNotFoundException.class, e -> Mono.empty())
                .onErrorResume(t -> {
                    if (RetryabilityUtil.isRetryable(t)) {
                        logger.warn("Core processing for job {} failed (retryable): {}", job.id(), RetryabilityUtil.rootCauseMessage(t));
                    } else {
                        logger.error("Core processing failed for job {}", job.id(), t);
                    }
                    return Mono.fromCallable(() -> {
                                ctx.persistence().onCoreError(job.id(), List.of(), t);
                                return 0;
                            })
                            .subscribeOn(Schedulers.boundedElastic())
                            .onErrorResume(JobNotFoundException.class, e -> Mono.empty())
                            .then();
                });
    }
}
