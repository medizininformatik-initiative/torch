package de.medizininformatikinitiative.torch.diagnostics;

import java.util.Map;
import java.util.UUID;

public record BatchDiagnostics(
        UUID jobId,
        UUID batchId,
        int cohortPatientsInBatch,
        int finalPatientsInBatch,
        Map<CriterionKey, CriterionCounts> criteria
) {
}
