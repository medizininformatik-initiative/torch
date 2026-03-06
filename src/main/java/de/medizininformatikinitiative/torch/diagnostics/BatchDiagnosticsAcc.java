package de.medizininformatikinitiative.torch.diagnostics;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Objects.requireNonNull;

public final class BatchDiagnosticsAcc {

    private final UUID jobId;
    private final UUID batchId;
    private final int cohortPatientsInBatch;

    private final AtomicInteger finalPatientsInBatch = new AtomicInteger(-1);
    private final ConcurrentHashMap<CriterionKey, CriterionCounts> criteria = new ConcurrentHashMap<>();

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
     * Set once at the end of processing. If called multiple times, last write wins.
     */
    public void setFinalPatientsInBatch(int finalPatients) {
        if (finalPatients < 0) {
            throw new IllegalArgumentException("finalPatientsInBatch must be >= 0");
        }
        finalPatientsInBatch.set(finalPatients);
    }

    public void incPatientsExcluded(CriterionKey key, int delta) {
        requireNonNull(key, "key");
        if (delta <= 0) return;

        criteria.compute(key, (k, v) -> {
            CriterionCounts cur = (v == null) ? CriterionCounts.empty() : v;
            return cur.plusPatients(delta);
        });
    }

    public void incResourcesExcluded(CriterionKey key, int delta) {
        requireNonNull(key, "key");
        if (delta <= 0) return;

        criteria.compute(key, (k, v) -> {
            CriterionCounts cur = (v == null) ? CriterionCounts.empty() : v;
            return cur.plusResources(delta);
        });
    }

    /**
     * Snapshot for persistence / returning in BatchResult.
     * If finalPatientsInBatch was never set, it falls back to cohortPatientsInBatch.
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
