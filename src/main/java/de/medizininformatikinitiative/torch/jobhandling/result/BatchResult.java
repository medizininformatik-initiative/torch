package de.medizininformatikinitiative.torch.jobhandling.result;

import de.medizininformatikinitiative.torch.jobhandling.BatchState;
import de.medizininformatikinitiative.torch.jobhandling.failure.Issue;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionResourceBundle;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

/**
 * result object representing the outcome of processing a single batch within a job.
 *
 * <p>A {@code BatchResult} captures both the technical execution state and the semantic outcome:
 * <ul>
 *   <li>Identity of the owning job and batch</li>
 *   <li>The final {@link BatchState} after processing</li>
 *   <li>An optional core {@link ExtractionResourceBundle} produced by this batch</li>
 *   <li>A list of {@link Issue}s describing warnings or errors encountered</li>
 * </ul>
 */
public record BatchResult(UUID jobId, UUID batchId, BatchState batchState,
                          Optional<ExtractionResourceBundle> resultCoreBundle,
                          List<Issue> issues) {

    public BatchResult {
        requireNonNull(jobId);
        requireNonNull(batchId);
        requireNonNull(batchState);
        requireNonNull(resultCoreBundle);
        requireNonNull(issues);
    }
}
