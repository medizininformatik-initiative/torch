package de.medizininformatikinitiative.torch.jobhandling.failure;

import de.medizininformatikinitiative.torch.jobhandling.workunit.WorkUnitState;

public record FailureOutcome(WorkUnitState state, boolean exhausted) {
}
