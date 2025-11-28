package de.medizininformatikinitiative.torch.jobhandling.result;

import de.medizininformatikinitiative.torch.jobhandling.BatchState;
import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;

public record BatchSelection(Job job, PatientBatch batch) {

    public BatchState batchState() {
        return job.batches().get(batch.batchId());
    }

}
