package de.medizininformatikinitiative.torch.jobhandling.result;

import de.medizininformatikinitiative.torch.diagnostics.BatchDiagnostics;
import de.medizininformatikinitiative.torch.diagnostics.ExclusionRecord;
import de.medizininformatikinitiative.torch.jobhandling.failure.Issue;
import de.medizininformatikinitiative.torch.jobhandling.workunit.WorkUnitStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Result of processing the core (non-batch) part of a job.
 *
 * <p>Captures the final {@link WorkUnitStatus}, any {@link Issue}s produced while processing
 * the core resources, and optional {@link BatchDiagnostics} recording resource exclusions.</p>
 */
public record CoreResult(UUID jobId, List<Issue> issues, WorkUnitStatus status,
                         Optional<BatchDiagnostics> diagnostics, List<ExclusionRecord> exclusions) {
    public CoreResult {
        exclusions = exclusions != null ? List.copyOf(exclusions) : List.of();
    }

    public CoreResult(UUID jobId, List<Issue> issues, WorkUnitStatus status) {
        this(jobId, issues, status, Optional.empty(), List.of());
    }
}
