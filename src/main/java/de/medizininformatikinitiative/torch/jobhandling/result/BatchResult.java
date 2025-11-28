package de.medizininformatikinitiative.torch.jobhandling.result;

import de.medizininformatikinitiative.torch.jobhandling.BatchState;
import de.medizininformatikinitiative.torch.jobhandling.Issue;
import de.medizininformatikinitiative.torch.model.management.CachelessResourceBundle;

import java.util.List;
import java.util.Optional;

public record BatchResult(BatchState batchState, Optional<CachelessResourceBundle> resultCoreBundle,
                          List<Issue> issues) {
}
