package de.medizininformatikinitiative.torch.jobhandling.workunit;

import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.JobExecutionContext;
import de.medizininformatikinitiative.torch.jobhandling.result.BatchResult;
import de.medizininformatikinitiative.torch.jobhandling.result.BatchSelection;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

public record ProcessBatchWorkUnit(Job job, UUID batchId) implements WorkUnit {


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
                .map(Boolean.TRUE::equals);
    }

    private Mono<Void> runClaimed(JobExecutionContext ctx) {
        return loadSelection(ctx)
                .flatMap(selection ->
                        ctx.extract().processBatch(selection)
                                // domain failure => record batch error, then stop this unit
                                .onErrorResume(t -> onBatchError(ctx, t).then(Mono.empty()))
                )
                .flatMap(result -> persistBatchSuccess(ctx, result))
                .then();
    }

    private Mono<BatchSelection> loadSelection(JobExecutionContext ctx) {
        Mono<Job> jobMono = Mono.fromCallable(() -> ctx.persistence().getJob(job.id()))
                .subscribeOn(Schedulers.boundedElastic())
                .map(opt -> opt.orElseThrow(() ->
                        new NoSuchElementException("Job " + job.id() + " not found")));

        return jobMono.flatMap(j ->
                Mono.fromCallable(() -> ctx.persistence().loadBatch(job.id(), batchId))
                        .subscribeOn(Schedulers.boundedElastic())
                        .map(batch -> new BatchSelection(j, batch))
        );
    }

    private Mono<Void> persistBatchSuccess(JobExecutionContext ctx, BatchResult result) {
        // let persistence errors bubble up -> worker loop will call onJobError(...)
        return Mono.fromCallable(() -> {
                    ctx.persistence().onBatchProcessingSuccess(result);
                    return true;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private Mono<Void> onBatchError(JobExecutionContext ctx, Throwable t) {
        return Mono.fromRunnable(() ->
                        ctx.persistence().onBatchError(job.id(), batchId, List.of(), t))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }
}
