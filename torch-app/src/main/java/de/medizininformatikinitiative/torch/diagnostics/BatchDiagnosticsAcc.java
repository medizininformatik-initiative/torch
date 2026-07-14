package de.medizininformatikinitiative.torch.diagnostics;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;

/**
 * Thread-safe accumulator for collecting exclusion diagnostics during batch processing.
 *
 * <p>Call {@link #incPatientsExcluded}, {@link #incResourcesExcluded}, and
 * {@link #recordDuration} concurrently from worker threads, then call
 * {@link #snapshot(long)} once at the end to obtain an immutable {@link BatchDiagnostics} record.
 *
 * <p>Use {@link #noop()} to obtain a shared instance whose write methods are no-ops, for code
 * paths where diagnostics are not needed.
 */
public class BatchDiagnosticsAcc {

    private final UUID jobId;
    private final UUID batchId;
    private final long cohortPatientsInBatch;

    private final ConcurrentHashMap<CriterionKey, CriterionCounts> criteria = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<PipelineStage, StageCounts> stages = new ConcurrentHashMap<>();

    /**
     * Creates a new accumulator for the given batch.
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
     * Increments the patient exclusion count for the given criterion.
     *
     * @param key   the criterion that caused the exclusion
     * @param delta number of patients to add; zero and negative values are silently ignored
     * @throws NullPointerException if {@code key} is {@code null}
     */
    public void incPatientsExcluded(CriterionKey key, int delta) {
        requireNonNull(key, "key");
        if (delta <= 0) return;

        criteria.compute(key, (k, v) -> {
            CriterionCounts cur = (v == null) ? CriterionCounts.empty() : v;
            return cur.plusPatients(delta);
        });
    }

    /**
     * Increments the resource exclusion count for the given criterion.
     *
     * @param key   the criterion that caused the exclusion
     * @param delta number of resources to add; zero and negative values are silently ignored
     * @throws NullPointerException if {@code key} is {@code null}
     */
    public void incResourcesExcluded(CriterionKey key, int delta) {
        requireNonNull(key, "key");
        if (delta <= 0) return;

        criteria.compute(key, (k, v) -> {
            CriterionCounts cur = (v == null) ? CriterionCounts.empty() : v;
            return cur.plusResources(delta);
        });
    }

    /**
     * Records the processing duration for the given criterion.
     *
     * <p>Call this after each operation (e.g. consent check, reference resolution) regardless of
     * whether it resulted in an exclusion. Zero and negative values are silently ignored.
     *
     * @param key           the criterion whose processing time is being recorded
     * @param durationNanos elapsed nanoseconds for the operation; zero and negative values are ignored
     * @throws NullPointerException if {@code key} is {@code null}
     */
    public void recordDuration(CriterionKey key, long durationNanos) {
        requireNonNull(key, "key");
        if (durationNanos <= 0) return;

        long durationMs = durationNanos / 1_000_000;
        if (durationMs == 0) return;

        criteria.compute(key, (k, v) -> {
            CriterionCounts cur = (v == null) ? CriterionCounts.empty() : v;
            return cur.plusDuration(durationMs);
        });
    }

    /**
     * Records throughput for a pipeline stage.
     *
     * <p>Multiple calls for the same stage are additive (useful when a stage runs per-group).
     * Zero and negative values are silently ignored.
     *
     * @param stage              the pipeline stage
     * @param durationNanos      elapsed nanoseconds; zero and negative values are ignored
     * @param resourcesProcessed number of resources (or patients) that passed through the stage
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
     * Returns a shared no-op accumulator whose write methods do nothing.
     *
     * <p>Use this for code paths that need an {@code acc} parameter but do not collect diagnostics
     * (e.g. callers that forward to an acc-aware overload without an accumulator of their own).
     * Never call {@link #snapshot(long)} on the returned instance.
     */
    public static BatchDiagnosticsAcc noop() {
        return Noop.INSTANCE;
    }

    /**
     * Returns an immutable {@link BatchDiagnostics} reflecting the current accumulated state.
     *
     * <p>Can be called multiple times; each call produces an independent snapshot.
     *
     * @param finalPatientsInBatch number of patients that survived all exclusion checks; must be &gt;= 0
     * @return a point-in-time snapshot of batch diagnostics
     * @throws IllegalArgumentException if {@code finalPatientsInBatch} is negative
     */
    public BatchDiagnostics snapshot(long finalPatientsInBatch) {
        if (finalPatientsInBatch < 0) {
            throw new IllegalArgumentException("finalPatientsInBatch must be >= 0");
        }

        List<CriterionEntry> entries = criteria.entrySet().stream()
                .map(e -> new CriterionEntry(e.getKey(), e.getValue()))
                .toList();

        Map<PipelineStage, StageCounts> stagesSnapshot = new EnumMap<>(PipelineStage.class);
        stagesSnapshot.putAll(stages);

        return new BatchDiagnostics(
                jobId,
                batchId,
                cohortPatientsInBatch,
                finalPatientsInBatch,
                List.copyOf(entries),
                Map.copyOf(stagesSnapshot)
        );
    }

    private static final class Noop extends BatchDiagnosticsAcc {
        private static final UUID NOOP_ID = new UUID(0L, 0L);
        static final Noop INSTANCE = new Noop();

        private Noop() {
            super(NOOP_ID, NOOP_ID, 0);
        }

        @Override public void incPatientsExcluded(CriterionKey key, int delta) {}
        @Override public void incResourcesExcluded(CriterionKey key, int delta) {}
        @Override public void recordDuration(CriterionKey key, long durationNanos) {}
        @Override public void recordStage(PipelineStage stage, long durationNanos, long resourcesProcessed) {}
    }
}
