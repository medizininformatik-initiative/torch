package de.medizininformatikinitiative.torch.jobhandling.workunit;

import de.medizininformatikinitiative.torch.exceptions.JobNotFoundException;
import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.JobExecutionContext;
import de.medizininformatikinitiative.torch.jobhandling.failure.RetryabilityUtil;
import de.medizininformatikinitiative.torch.jobhandling.result.BatchResult;
import de.medizininformatikinitiative.torch.jobhandling.result.BatchSelection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

public record ProcessBatchWorkUnit(Job job, UUID batchId) implements WorkUnit {
    private static final Logger logger = LoggerFactory.getLogger(ProcessBatchWorkUnit.class);

    @Override
    public Mono<Void> execute(JobExecutionContext ctx) {
        return tryClaim(ctx)
                .flatMap(claimed -> claimed ? runClaimed(ctx) : Mono.empty());
    }

    /**
     * Claims the batch by transitioning INIT -> IN_PROGRESS.
     */
    private Mono<Boolean> tryClaim(JobExecutionContext ctx) {
        return Mono.fromCallable(() -> ctx.persistence().tryStartBatch(job.id(), batchId))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(JobNotFoundException.class, e -> Mono.just(false))
                .map(Boolean.TRUE::equals);
    }

    private Mono<Void> runClaimed(JobExecutionContext ctx) {
        return loadSelection(ctx)
                .flatMap(selection ->
                        ctx.extract().processBatch(selection)
                                .onErrorResume(t -> {
                                    if (RetryabilityUtil.isRetryable(t)) {
                                        logger.warn("Batch {} for job {} failed (retryable): {}", batchId, job.id(), RetryabilityUtil.rootCauseMessage(t));
                                    } else {
                                        logger.error("Batch {} for job {} failed", batchId, job.id(), t);
                                    }
                                    return onBatchError(ctx, t).then(Mono.empty());
                                })
                )
                .flatMap(result -> persistBatchSuccess(ctx, result))
                .onErrorResume(JobNotFoundException.class, e -> Mono.empty())
                .onErrorResume(NoSuchElementException.class, e -> Mono.empty())
                .then();
    }

    private Mono<BatchSelection> loadSelection(JobExecutionContext ctx) {
        Mono<Job> jobMono = Mono.fromCallable(() -> ctx.persistence().getJob(job.id()))
                .subscribeOn(Schedulers.boundedElastic())
                .map(opt -> opt.orElseThrow(() ->
                        new NoSuchElementException("Job " + job.id() + " not found")));

        return jobMono.flatMap(j ->
                Mono.fromCallable(() -> ctx.persistence().loadBatch(j.id(), batchId))
                        .subscribeOn(Schedulers.boundedElastic())
                        .map(batch -> new BatchSelection(j, batch))
        );
    }

    private Mono<Void> persistBatchSuccess(JobExecutionContext ctx, BatchResult result) {
        return Mono.fromCallable(() -> {
                    ctx.persistence().onBatchProcessingSuccess(result);
                    return true;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(JobNotFoundException.class, e -> Mono.empty())
                .then();
    }

    private Mono<Void> onBatchError(JobExecutionContext ctx, Throwable t) {
        return Mono.fromCallable(() -> {
                    ctx.persistence().onBatchError(job.id(), batchId, List.of(), t);
                    return 0;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(JobNotFoundException.class, e -> Mono.empty())
                .then();
    }
}
