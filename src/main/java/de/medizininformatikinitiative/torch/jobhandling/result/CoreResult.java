package de.medizininformatikinitiative.torch.jobhandling.result;

import de.medizininformatikinitiative.torch.jobhandling.failure.Issue;
import de.medizininformatikinitiative.torch.jobhandling.workunit.WorkUnitStatus;

import java.util.List;
import java.util.UUID;

/**
 * Result of processing the core (non-batch) part of a job.
 *
 * <p>Captures the final {@link WorkUnitStatus} and any {@link Issue}s
 * produced while processing the core resources.</p>
 */
public record CoreResult(UUID jobId, List<Issue> issues, WorkUnitStatus status) {
}
