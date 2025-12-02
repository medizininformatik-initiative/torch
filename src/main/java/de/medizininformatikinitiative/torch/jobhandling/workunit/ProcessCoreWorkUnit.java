package de.medizininformatikinitiative.torch.jobhandling.workunit;

import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.JobExecutionContext;
import de.medizininformatikinitiative.torch.jobhandling.result.CoreResult;

import java.util.List;

public record ProcessCoreWorkUnit(Job job) implements WorkUnit {
    @Override
    public void execute(JobExecutionContext ctx) {
        try {
            CoreResult result = ctx.extract()
                    .processCore(job, ctx.persistence().loadCoreInfo(job.id()))
                    .block();
            assert result != null;
            ctx.persistence().onCoreSuccess(result);
        } catch (Exception e) {
            ctx.persistence().onJobError(job.id(), List.of(), e);
        }
    }
}
