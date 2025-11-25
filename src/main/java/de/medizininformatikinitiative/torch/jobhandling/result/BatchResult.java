package de.medizininformatikinitiative.torch.jobhandling.result;

import de.medizininformatikinitiative.torch.jobhandling.BatchState;
import de.medizininformatikinitiative.torch.jobhandling.Issue;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionResourceBundle;

import java.util.List;
import java.util.Optional;

public record BatchResult(java.util.UUID jobID, java.util.UUID batchID, BatchState batchState,
                          Optional<ExtractionResourceBundle> resultCoreBundle,
                          List<Issue> issues) {
}
