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
                        ? ctx.cohortQueryService().runCohortQuery(job.parameters().crtdl())
                        : Mono.just(job.parameters().paramBatch());

        long cohortQueryStart = System.nanoTime();

        return patientIdsMono
                .flatMap(ids ->
                        Mono.fromCallable(() -> {
                                    ctx.persistence().onCohortSuccess(job.id(), ids, System.nanoTime() - cohortQueryStart);
                                    return 0;
                                })
                                .subscribeOn(Schedulers.boundedElastic())
                                .then()
                )
                .onErrorResume(JobNotFoundException.class, e -> {
                    logger.debug("Ignoring cohort result for deleted job {}", job.id());
                    return Mono.empty();
                })
                .onErrorResume(t -> {
                    if (RetryabilityUtil.isRetryable(t)) {
                        logger.warn("Cohort query for job {} failed (retryable): {}", job.id(), RetryabilityUtil.rootCauseMessage(t));
                    } else {
                        logger.error("Cohort query failed for job {}", job.id(), t);
                    }
                    return Mono.fromCallable(() -> {
                                Exception ex = (t instanceof Exception e) ? e : new RuntimeException(t);
                                ctx.persistence().onCohortError(job.id(), List.of(), ex);
                                return 0;
                            })
                            .subscribeOn(Schedulers.boundedElastic())
                            .onErrorResume(JobNotFoundException.class, e -> {
                                logger.debug("Ignoring cohort error for deleted job {}", job.id());
                                return Mono.empty();
                            })
                            .then();
                });
    }
}
