package de.medizininformatikinitiative.torch.jobhandling.workunit;

import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.JobExecutionContext;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;

import java.util.List;

public record CreateBatchesWorkUnit(Job job) implements WorkUnit {

    /**
     * Executes the CCDL using FLARE or CQL to create PatientBatches.
     * <p>
     * Executed if no patient were given parameters, the CCDL embedded in the CRTDL is executed.
     * Otherwise, persists batches directly.
     *
     * @param ctx for the execution
     */
    @Override
    public void execute(JobExecutionContext ctx) {
        try {
            List<PatientBatch> batches =
                    PatientBatch.of(
                            job.parameters().paramBatch().isEmpty()
                                    ? ctx.cohortQueryService().runCohortQuery(job.parameters().crtdl()).block()
                                    : job.parameters().paramBatch()).split(ctx.batchsize());
            ctx.persistence().onCreateBatchesSuccess(job.id(), batches);
        } catch (Exception e) {
            ctx.persistence().onJobError(job.id(), List.of(), e);
        }
    }
}
