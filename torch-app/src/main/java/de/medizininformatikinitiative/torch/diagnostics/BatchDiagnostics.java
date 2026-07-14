package de.medizininformatikinitiative.torch.diagnostics;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable snapshot of diagnostics collected for a single processing batch.
 *
 * <p>Produced by {@link BatchDiagnosticsAcc#snapshot(long)} at the end of batch processing.
 *
 * @param jobId                 the job this batch belongs to
 * @param batchId               unique identifier of this batch
 * @param cohortPatientsInBatch number of patients in the cohort assigned to this batch
 * @param finalPatientsInBatch  number of patients remaining after all exclusions in this batch
 * @param criteria              per-criterion exclusion counts as an ordered list of entries
 * @param stages                per-stage throughput metrics; may be empty
 */
public record BatchDiagnostics(
        UUID jobId,
        UUID batchId,
        long cohortPatientsInBatch,
        long finalPatientsInBatch,
        List<CriterionEntry> criteria,
        Map<PipelineStage, StageCounts> stages
) {

    public BatchDiagnostics {
        criteria = criteria != null ? List.copyOf(criteria) : List.of();
        stages = stages != null ? Map.copyOf(stages) : Map.of();
    }

    /** Constructor for callers that don't supply stage timings. */
    public BatchDiagnostics(UUID jobId, UUID batchId, long cohortPatientsInBatch,
                            long finalPatientsInBatch, List<CriterionEntry> criteria) {
        this(jobId, batchId, cohortPatientsInBatch, finalPatientsInBatch, criteria, Map.of());
    }

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
