package de.medizininformatikinitiative.torch.diagnostics;

import java.util.Map;
import java.util.UUID;

public record JobDiagnostics(
        UUID jobId,
        int cohortPatientsTotal,
        int finalPatientsTotal,
        Map<CriterionKey, CriterionCounts> criteria
) {
}
