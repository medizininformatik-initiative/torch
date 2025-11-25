package de.medizininformatikinitiative.torch.jobhandling.workunit;

import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

public record ProcessCohortWorkUnit(Job job) implements WorkUnit {
    private static final Logger logger = LoggerFactory.getLogger(ProcessCohortWorkUnit.class);

    /**
     * Executes the CCDL using FLARE or CQL to create PatientBatches.
     * <p>
     * Executed if no patient were given parameters, the CCDL embedded in the CRTDL is executed.
     * Otherwise, persists given parameters as batches directly.
     *
     * @param ctx for the execution
     */
    @Override
    public Mono<Void> execute(JobExecutionContext ctx) {
        logger.debug("Starting Job creation");

        Mono<List<String>> patientIdsMono =
                job.parameters().paramBatch().isEmpty()
                        ? ctx.cohortQueryService()
                        .runCohortQuery(job.parameters().crtdl())
                        : Mono.just(job.parameters().paramBatch());

        return patientIdsMono
                .flatMap(ids ->
                        Mono.fromRunnable(() ->
                                ctx.persistence()
                                        .onCohortSuccess(job.id(), ids)
                        ).subscribeOn(Schedulers.boundedElastic())
                )
                .onErrorResume(e ->
                        Mono.fromRunnable(() ->
                                ctx.persistence()
                                        .onCohortError(job.id(), List.of(), (Exception) e)
                        )
                ).then();
    }
}
