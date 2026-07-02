package de.medizininformatikinitiative.torch.diagnostics;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;

/**
 * Thread-safe accumulator for diagnostics collected while processing one batch.
 *
 * <p>This accumulator tracks batch-level throughput and timing information only.
 * Detailed exclusion events are recorded separately through {@link ExclusionAcc}
 * and later written to {@code exclusions.csv}. Keeping these concerns separate
 * preserves patient/resource-level exclusion context while allowing batch diagnostics
 * to remain small and stable.</p>
 *
 * <p>Call {@link #recordStage(PipelineStage, long, long)} while processing the batch,
 * then call {@link #snapshot(long)} once processing is complete to obtain an immutable
 * {@link BatchDiagnostics} instance.</p>
 *
 * <p>Use {@link #noop()} for code paths that accept diagnostics parameters but should
 * not collect metrics.</p>
 */
public class BatchDiagnosticsAcc {

    private final UUID jobId;
    private final UUID batchId;
    private final long cohortPatientsInBatch;

    private final ConcurrentHashMap<PipelineStage, StageCounts> stages = new ConcurrentHashMap<>();

    /**
     * Creates an accumulator for one batch.
     *
     * @param jobId                 the job this batch belongs to
     * @param batchId               unique identifier of this batch
     * @param cohortPatientsInBatch number of patients in the cohort assigned to this batch; must be &gt;= 0
     * @throws NullPointerException     if {@code jobId} or {@code batchId} is {@code null}
     * @throws IllegalArgumentException if {@code cohortPatientsInBatch} is negative
     */
    public BatchDiagnosticsAcc(UUID jobId, UUID batchId, long cohortPatientsInBatch) {
        this.jobId = requireNonNull(jobId, "jobId");
        this.batchId = requireNonNull(batchId, "batchId");
        if (cohortPatientsInBatch < 0) {
            throw new IllegalArgumentException("cohortPatientsInBatch must be >= 0");
        }
        this.cohortPatientsInBatch = cohortPatientsInBatch;
    }

    /**
     * Returns an accumulator that intentionally drops all recorded diagnostics.
     *
     * <p>This is useful for tests or code paths where diagnostics collection is optional,
     * but method signatures still require an accumulator.</p>
     *
     * @return no-op diagnostics accumulator
     */
    public static BatchDiagnosticsAcc noop() {
        return Noop.INSTANCE;
    }

    public UUID jobId() {
        return jobId;
    }

    public UUID batchId() {
        return batchId;
    }

    public long cohortPatientsInBatch() {
        return cohortPatientsInBatch;
    }

    /**
     * Records throughput and timing information for one pipeline stage.
     *
     * <p>Multiple calls for the same stage are additive. This is useful for stages that
     * run once per resource group, resolver invocation, or similar sub-step. Zero and
     * negative values are ignored, so callers can safely pass measured values without
     * pre-filtering them.</p>
     *
     * @param stage              pipeline stage being measured
     * @param durationNanos      elapsed time in nanoseconds; zero and negative values are ignored
     * @param resourcesProcessed number of patients or resources processed by this stage;
     *                           zero and negative values are ignored
     * @throws NullPointerException if {@code stage} is {@code null}
     */
    public void recordStage(PipelineStage stage, long durationNanos, long resourcesProcessed) {
        requireNonNull(stage, "stage");
        if (durationNanos <= 0 && resourcesProcessed <= 0) return;

        long durationMs = Math.max(0, durationNanos) / 1_000_000;
        long safeResources = Math.max(0, resourcesProcessed);
        stages.merge(stage, new StageCounts(durationMs, safeResources), StageCounts::add);
    }

    /**
     * Returns an immutable snapshot of the currently accumulated batch diagnostics.
     *
     * <p>The snapshot contains stage-level metrics and the number of patients that
     * survived processing in this batch. Detailed exclusion events are not aggregated
     * here; they are recorded separately as {@link ExclusionRecord}s through
     * {@link ExclusionAcc}.</p>
     *
     * <p>This method may be called multiple times. Each call returns an independent
     * point-in-time snapshot.</p>
     *
     * @param finalPatientsInBatch number of patients remaining after all exclusion checks
     *                             in this batch
     * @return immutable batch diagnostics snapshot
     * @throws IllegalArgumentException if {@code finalPatientsInBatch} is negative
     */
    public BatchDiagnostics snapshot(long finalPatientsInBatch) {
        if (finalPatientsInBatch < 0) {
            throw new IllegalArgumentException("finalPatientsInBatch must be >= 0");
        }
        Map<PipelineStage, StageCounts> stagesSnapshot = new EnumMap<>(PipelineStage.class);
        stagesSnapshot.putAll(stages);
        return new BatchDiagnostics(jobId, batchId, cohortPatientsInBatch, finalPatientsInBatch, Map.copyOf(stagesSnapshot));
    }

    private static final class Noop extends BatchDiagnosticsAcc {
        private static final UUID NOOP_ID = new UUID(0L, 0L);
        static final Noop INSTANCE = new Noop();

        private Noop() {
            super(NOOP_ID, NOOP_ID, 0);
        }

        @Override
        public void recordStage(PipelineStage stage, long durationNanos, long resourcesProcessed) {
        }
    }
}
