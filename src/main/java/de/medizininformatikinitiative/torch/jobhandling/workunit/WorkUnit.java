package de.medizininformatikinitiative.torch.jobhandling.workunit;

import de.medizininformatikinitiative.torch.jobhandling.Job;

public sealed interface WorkUnit
        permits CreateBatchesWorkUnit, FailedWorkUnit, ProcessBatchWorkUnit, ProcessCoreWorkUnit {
    Job job();
}
