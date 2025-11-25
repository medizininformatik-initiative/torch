package de.medizininformatikinitiative.torch.jobhandling.workunit;

import de.medizininformatikinitiative.torch.jobhandling.Job;

public record FailedWorkUnit(Job job, Throwable error) implements WorkUnit {
}
