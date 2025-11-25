package de.medizininformatikinitiative.torch.jobhandling.workunit;

import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.JobExecutionContext;
import de.medizininformatikinitiative.torch.jobhandling.result.BatchResult;
import de.medizininformatikinitiative.torch.jobhandling.result.BatchSelection;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;

import java.util.List;
import java.util.UUID;

public record ProcessBatchWorkUnit(Job job, UUID batchId) implements WorkUnit {
    @Override
    public void execute(JobExecutionContext ctx) {
        try {
            PatientBatch batch = ctx.persistence().loadBatch(job.id(), batchId);
            BatchResult result = ctx.extract()
                    .processBatch(new BatchSelection(job, batch))
                    .block();
            assert result != null;
            ctx.persistence().onBatchProcessingSuccess(result);
        } catch (Exception e) {
            ctx.persistence().onBatchError(job.id(), batchId, List.of(), e);
        }
    }
}
