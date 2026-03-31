package de.medizininformatikinitiative.torch.diagnostics;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.Map;
import java.util.UUID;

/**
 * Immutable snapshot of diagnostics aggregated across all batches of a job.
 *
 * @param jobId               the job these diagnostics belong to
 * @param cohortPatientsTotal total number of patients in the cohort across all batches
 * @param finalPatientsTotal  total number of patients remaining after all exclusions
 * @param criteria            per-criterion exclusion counts aggregated across all batches,
 *                            keyed by {@link CriterionKey}
 */
public record JobDiagnostics(
        UUID jobId,
        int cohortPatientsTotal,
        int finalPatientsTotal,
        @JsonSerialize(keyUsing = CriterionKeySerializer.class)
        @JsonDeserialize(keyUsing = CriterionKeyDeserializer.class)
        Map<CriterionKey, CriterionCounts> criteria
) {
}
