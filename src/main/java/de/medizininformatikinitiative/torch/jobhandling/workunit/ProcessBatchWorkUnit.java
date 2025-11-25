package de.medizininformatikinitiative.torch.jobhandling.workunit;

import de.medizininformatikinitiative.torch.jobhandling.BatchState;
import de.medizininformatikinitiative.torch.jobhandling.Job;

public record ProcessBatchWorkUnit(Job job, BatchState nextBatch) implements WorkUnit {
}
