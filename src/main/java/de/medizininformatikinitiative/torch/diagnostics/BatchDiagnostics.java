package de.medizininformatikinitiative.torch.diagnostics;

import java.util.Map;
import java.util.UUID;

/**
 * Immutable diagnostics snapshot for a single processing batch.
 *
 * <p>This record describes how many patients entered and left the batch and how much
 * work was performed by each pipeline stage. It deliberately does not contain
 * exclusion reasons, patient identifiers, or resource identifiers. Those event-level
 * details are stored separately in {@code exclusions.csv} as {@link ExclusionRecord}s.</p>
 *
 * <p>Future job-level exclusion summaries can be derived from those event records,
 * for example at {@code attributeRef} granularity.</p>
 *
 * @param jobId                 id of the extraction job this batch belongs to
 * @param batchId               unique id of this batch
 * @param cohortPatientsInBatch number of cohort patients assigned to this batch
 * @param finalPatientsInBatch  number of patients remaining after all exclusion checks
 * @param stages                per-stage throughput and timing metrics; may be empty
 */
public record BatchDiagnostics(
        UUID jobId,
        UUID batchId,
        long cohortPatientsInBatch,
        long finalPatientsInBatch,
        Map<PipelineStage, StageCounts> stages
) {
    public BatchDiagnostics {
        stages = stages != null ? Map.copyOf(stages) : Map.of();
    }
}
