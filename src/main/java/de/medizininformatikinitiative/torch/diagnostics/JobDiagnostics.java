package de.medizininformatikinitiative.torch.diagnostics;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable snapshot of diagnostics aggregated across all batches of a job.
 *
 * @param jobId               the job these diagnostics belong to
 * @param cohortPatientsTotal total number of patients in the cohort across all batches
 * @param finalPatientsTotal  total number of patients remaining after all exclusions
 * @param criteria            per-criterion exclusion counts aggregated across all batches
 */
public record JobDiagnostics(
        UUID jobId,
        long cohortPatientsTotal,
        long finalPatientsTotal,
        List<CriterionEntry> criteria
) {

    /**
     * Returns the counts for the given criterion, or empty if no exclusions were recorded for it.
     *
     * @param key the criterion to look up
     * @return optional counts for the criterion
     */
    public Optional<CriterionCounts> countsFor(CriterionKey key) {
        return criteria.stream()
                .filter(e -> e.key().equals(key))
                .map(CriterionEntry::counts)
                .findFirst();
    }
}
