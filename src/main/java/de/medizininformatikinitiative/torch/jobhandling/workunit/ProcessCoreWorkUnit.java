package de.medizininformatikinitiative.torch.jobhandling.workunit;

import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.JobExecutionContext;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

public record ProcessCoreWorkUnit(Job job) implements WorkUnit {

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
                .flatMap(coreBundle ->
                        ctx.extract().processCore(job, coreBundle)
                )
                .flatMap(result ->
                        Mono.fromRunnable(() ->
                                ctx.persistence().onCoreSuccess(result)
                        ).subscribeOn(Schedulers.boundedElastic())
                )
                .onErrorResume(t ->
                        Mono.fromRunnable(() ->
                                ctx.persistence().onCoreError(
                                        job.id(),
                                        List.of(),
                                        t
                                )
                        ).subscribeOn(Schedulers.boundedElastic())
                )
                .then();
    }
}
