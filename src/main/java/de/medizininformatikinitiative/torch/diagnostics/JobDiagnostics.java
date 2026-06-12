package de.medizininformatikinitiative.torch.diagnostics;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable diagnostics snapshot aggregated across all batches of one extraction job.
 *
 * <p>This summary contains job-wide patient totals and aggregated pipeline-stage
 * metrics. It intentionally does not aggregate exclusion reasons. Detailed exclusion
 * events are written separately to {@code exclusions.csv}, where each row preserves
 * the patient, resource, group, and attribute context needed for debugging and later
 * aggregation.</p>
 *
 * <p>Keeping event-level exclusions separate allows a future job summary to aggregate
 * exclusions at {@code attributeRef} level without losing information during batch
 * processing.</p>
 * <p>
 * // TODO: Reintroduce job-level exclusion aggregation from exclusions.csv at attributeRef granularity.
 *
 * @param jobId                 id of the extraction job
 * @param cohortPatientsTotal   total number of cohort patients across all batches
 * @param finalPatientsTotal    total number of patients remaining after all exclusion checks
 * @param stages                per-stage throughput and timing metrics aggregated across all batches
 * @param cohortQueryDurationMs wall-clock duration of the cohort query in milliseconds;
 *                              {@code 0} if not recorded
 */
public record JobDiagnostics(
        UUID jobId,
        long cohortPatientsTotal,
        long finalPatientsTotal,
        Map<PipelineStage, StageCounts> stages,
        long cohortQueryDurationMs
) {
    public JobDiagnostics {
        stages = stages != null ? Map.copyOf(stages) : Map.of();
    }

    /**
     * Aggregates batch diagnostics into one job-level diagnostics snapshot.
     *
     * <p>Patient totals are summed across batches. Stage metrics with the same
     * {@link PipelineStage} are added together. Detailed exclusion events are not read
     * or aggregated here; they are persisted independently to {@code exclusions.csv}.</p>
     *
     * @param jobId                 id of the extraction job
     * @param batches               batch-level diagnostics to aggregate
     * @param cohortQueryDurationMs wall-clock duration of the cohort query in milliseconds
     * @return immutable job-level diagnostics snapshot
     * @throws NullPointerException if {@code jobId} or {@code batches} is {@code null}
     */
    public static JobDiagnostics fromBatches(UUID jobId, List<BatchDiagnostics> batches,
                                             long cohortQueryDurationMs) {
        if (batches.isEmpty()) return new JobDiagnostics(jobId, 0L, 0L, Map.of(), cohortQueryDurationMs);

        long cohortTotal = 0;
        long finalTotal = 0;
        Map<PipelineStage, StageCounts> mergedStages = new EnumMap<>(PipelineStage.class);

        for (BatchDiagnostics b : batches) {
            cohortTotal += b.cohortPatientsInBatch();
            finalTotal += b.finalPatientsInBatch();
            b.stages().forEach((stage, counts) -> mergedStages.merge(stage, counts, StageCounts::add));
        }

        return new JobDiagnostics(jobId, cohortTotal, finalTotal, mergedStages, cohortQueryDurationMs);
    }
}
