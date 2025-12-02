package de.medizininformatikinitiative.torch.jobhandling.workunit;

import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.JobExecutionContext;

public sealed interface WorkUnit
        permits CreateBatchesWorkUnit, ProcessBatchWorkUnit, ProcessCoreWorkUnit {
    Job job();

    void execute(JobExecutionContext ctx);
}
