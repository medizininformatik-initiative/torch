package de.medizininformatikinitiative.torch.diagnostics;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.Map;
import java.util.UUID;

/**
 * Immutable snapshot of diagnostics collected for a single processing batch.
 *
 * <p>Produced by {@link BatchDiagnosticsAcc#snapshot()} at the end of batch processing.
 *
 * @param jobId                 the job this batch belongs to
 * @param batchId               unique identifier of this batch
 * @param cohortPatientsInBatch number of patients in the cohort assigned to this batch
 * @param finalPatientsInBatch  number of patients remaining after all exclusions in this batch
 * @param criteria              per-criterion exclusion counts, keyed by {@link CriterionKey}
 */
public record BatchDiagnostics(
        UUID jobId,
        UUID batchId,
        int cohortPatientsInBatch,
        int finalPatientsInBatch,
        @JsonSerialize(keyUsing = CriterionKeySerializer.class)
        @JsonDeserialize(keyUsing = CriterionKeyDeserializer.class)
        Map<CriterionKey, CriterionCounts> criteria
) {
}
