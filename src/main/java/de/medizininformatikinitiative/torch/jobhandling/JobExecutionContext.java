package de.medizininformatikinitiative.torch.jobhandling;

import de.medizininformatikinitiative.torch.service.CohortQueryService;
import de.medizininformatikinitiative.torch.service.ExtractDataService;
import de.medizininformatikinitiative.torch.service.JobPersistenceService;

public record JobExecutionContext(
        JobPersistenceService persistence,
        ExtractDataService extract,
        CohortQueryService cohortQueryService,
        int batchsize,
        int maxBatchRetries,
        int maxJobRetries
) {
}

