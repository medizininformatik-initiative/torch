package de.medizininformatikinitiative.torch.diagnostics;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Objects.requireNonNull;

/**
 * Thread-safe accumulator for collecting exclusion diagnostics during batch processing.
 *
 * <p>Call {@link #incPatientsExcluded} and {@link #incResourcesExcluded} concurrently from
 * worker threads, then call {@link #snapshot()} once at the end to obtain an immutable
 * {@link BatchDiagnostics} record.
 */
public final class BatchDiagnosticsAcc {

    private final UUID jobId;
    private final UUID batchId;
    private final int cohortPatientsInBatch;

    private final AtomicInteger finalPatientsInBatch = new AtomicInteger(-1);
    private final ConcurrentHashMap<CriterionKey, CriterionCounts> criteria = new ConcurrentHashMap<>();

    /**
     * Creates a new accumulator for the given batch.
     *
     * @param jobId                 the job this batch belongs to
     * @param batchId               unique identifier of this batch
     * @param cohortPatientsInBatch number of patients in the cohort assigned to this batch; must be &gt;= 0
     * @throws NullPointerException     if {@code jobId} or {@code batchId} is {@code null}
     * @throws IllegalArgumentException if {@code cohortPatientsInBatch} is negative
     */
    public BatchDiagnosticsAcc(UUID jobId, UUID batchId, int cohortPatientsInBatch) {
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

    public int cohortPatientsInBatch() {
        return cohortPatientsInBatch;
    }

    /**
     * Records the number of patients that passed all exclusion checks in this batch.
     *
     * <p>Intended to be called once at the end of processing. If called multiple times, the last
     * write wins. When never called, {@link #snapshot()} falls back to {@code cohortPatientsInBatch}.
     *
     * @param finalPatients number of surviving patients; must be &gt;= 0
     * @throws IllegalArgumentException if {@code finalPatients} is negative
     */
    public void setFinalPatientsInBatch(int finalPatients) {
        if (finalPatients < 0) {
            throw new IllegalArgumentException("finalPatientsInBatch must be >= 0");
        }
        finalPatientsInBatch.set(finalPatients);
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

        criteria.compute(key, (k, v) -> {
            CriterionCounts cur = (v == null) ? CriterionCounts.empty() : v;
            return cur.plusDuration(durationNanos);
        });
    }

    /**
     * Returns an immutable {@link BatchDiagnostics} reflecting the current accumulated state.
     *
     * <p>If {@link #setFinalPatientsInBatch} was never called, {@code finalPatientsInBatch} defaults
     * to {@code cohortPatientsInBatch}. Can be called multiple times; each call produces an
     * independent snapshot.
     *
     * @return a point-in-time snapshot of batch diagnostics
     */
    public BatchDiagnostics snapshot() {
        int finalPatients = finalPatientsInBatch.get();
        if (finalPatients < 0) {
            finalPatients = cohortPatientsInBatch;
        }

        // Make it immutable-ish for downstream consumers
        Map<CriterionKey, CriterionCounts> copy = Collections.unmodifiableMap(new ConcurrentHashMap<>(criteria));

        return new BatchDiagnostics(
                jobId,
                batchId,
                cohortPatientsInBatch,
                finalPatients,
                copy
        );
    }
}
