package de.medizininformatikinitiative.torch.diagnostics;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable snapshot of diagnostics aggregated across all batches of a job.
 *
 * @param jobId                    the job these diagnostics belong to
 * @param cohortPatientsTotal      total number of patients in the cohort across all batches
 * @param finalPatientsTotal       total number of patients remaining after all exclusions
 * @param criteria                 per-criterion exclusion counts aggregated across all batches
 * @param stages                 per-stage throughput metrics aggregated across all batches
 * @param cohortQueryDurationMs  wall-clock time for the cohort query (Flare/CQL) in milliseconds; 0 if not recorded
 */
public record JobDiagnostics(
        UUID jobId,
        long cohortPatientsTotal,
        long finalPatientsTotal,
        List<CriterionEntry> criteria,
        Map<PipelineStage, StageCounts> stages,
        long cohortQueryDurationMs
) {

    public JobDiagnostics {
        criteria = criteria != null ? List.copyOf(criteria) : List.of();
        stages = stages != null ? Map.copyOf(stages) : Map.of();
    }

    /** Backward-compatible constructor for callers that don't supply stage timings. */
    public JobDiagnostics(UUID jobId, long cohortPatientsTotal, long finalPatientsTotal, List<CriterionEntry> criteria) {
        this(jobId, cohortPatientsTotal, finalPatientsTotal, criteria, Map.of(), 0L);
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

    /** Aggregates a list of batch diagnostics into a single job-level snapshot. */
    public static JobDiagnostics fromBatches(UUID jobId, List<BatchDiagnostics> batches,
                                             long cohortQueryDurationMs) {
        if (batches.isEmpty()) return new JobDiagnostics(jobId, 0L, 0L, List.of(), Map.of(), cohortQueryDurationMs);

        long cohortTotal = 0;
        long finalTotal = 0;
        Map<CriterionKey, CriterionCounts> merged = new HashMap<>();
        Map<PipelineStage, StageCounts> mergedStages = new EnumMap<>(PipelineStage.class);

        for (BatchDiagnostics b : batches) {
            cohortTotal += b.cohortPatientsInBatch();
            finalTotal += b.finalPatientsInBatch();
            for (CriterionEntry e : b.criteria()) {
                merged.merge(e.key(), e.counts(), CriterionCounts::add);
            }
            b.stages().forEach((stage, counts) -> mergedStages.merge(stage, counts, StageCounts::add));
        }

        List<CriterionEntry> criteriaEntries = merged.entrySet().stream()
                .map(e -> new CriterionEntry(e.getKey(), e.getValue()))
                .toList();
        return new JobDiagnostics(jobId, cohortTotal, finalTotal, criteriaEntries, mergedStages, cohortQueryDurationMs);
    }
}
