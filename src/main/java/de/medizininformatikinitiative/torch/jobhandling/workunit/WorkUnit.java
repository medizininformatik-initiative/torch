package de.medizininformatikinitiative.torch.jobhandling.workunit;

import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.JobExecutionContext;

import java.io.IOException;

/**
 * A single executable unit of work belonging to a {@link Job}.
 * <p>
 * Implementations represent one processing step (e.g. cohort retrieval,
 * batch processing, or core processing) and are executed asynchronously.
 */
public sealed interface WorkUnit
        permits ProcessCohortWorkUnit, ProcessBatchWorkUnit, ProcessCoreWorkUnit {
    Job job();

    reactor.core.publisher.Mono<Void> execute(JobExecutionContext ctx) throws IOException;

}
