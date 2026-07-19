package de.medizininformatikinitiative.torch.diagnostics;

/**
 * Immutable throughput snapshot for a single pipeline stage.
 *
 * @param durationMs         wall-clock time spent in this stage, in milliseconds
 * @param resourcesProcessed number of resources that passed through the stage
 */
public record StageCounts(long durationMs, long resourcesProcessed) {

    public StageCounts {
        if (durationMs < 0)
            throw new IllegalArgumentException("durationMs must be >= 0");
        if (resourcesProcessed < 0)
            throw new IllegalArgumentException("resourcesProcessed must be >= 0");
    }

    /** Resources (or patients, depending on stage) processed per minute. */
    public long resourcesPerMinute() {
        if (durationMs == 0) return 0;
        return Math.round(resourcesProcessed * 60_000.0 / durationMs);
    }

    public StageCounts add(StageCounts other) {
        return new StageCounts(
                Math.addExact(this.durationMs, other.durationMs),
                Math.addExact(this.resourcesProcessed, other.resourcesProcessed)
        );
    }
}
