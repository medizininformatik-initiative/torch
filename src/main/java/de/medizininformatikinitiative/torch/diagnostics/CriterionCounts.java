package de.medizininformatikinitiative.torch.diagnostics;

/**
 * Immutable counts of patients and resources excluded for a single {@link CriterionKey},
 * together with cumulative processing-time metrics.
 *
 * @param patientsExcluded   number of patients excluded by this criterion
 * @param resourcesExcluded  number of resources excluded by this criterion
 * @param totalDurationNanos total wall-clock time (in nanoseconds) across all recorded invocations
 * @param invocations        number of times a duration was recorded for this criterion
 */
public record CriterionCounts(
        long patientsExcluded,
        long resourcesExcluded,
        long totalDurationNanos,
        long invocations
) {

    private static final CriterionCounts EMPTY = new CriterionCounts(0, 0, 0, 0);

    /**
     * Convenience constructor that leaves timing fields at zero.
     */
    public CriterionCounts(long patientsExcluded, long resourcesExcluded) {
        this(patientsExcluded, resourcesExcluded, 0L, 0L);
    }

    /**
     * Compact canonical constructor that enforces non-negative values.
     */
    public CriterionCounts {
        if (patientsExcluded < 0)
            throw new IllegalArgumentException("patientsExcluded must not be negative: " + patientsExcluded);
        if (resourcesExcluded < 0)
            throw new IllegalArgumentException("resourcesExcluded must not be negative: " + resourcesExcluded);
        if (totalDurationNanos < 0)
            throw new IllegalArgumentException("totalDurationNanos must not be negative: " + totalDurationNanos);
        if (invocations < 0)
            throw new IllegalArgumentException("invocations must not be negative: " + invocations);
    }

    /**
     * Returns a {@link CriterionCounts} with all fields set to zero.
     *
     * @return an empty counts instance
     */
    public static CriterionCounts empty() {
        return EMPTY;
    }

    /**
     * Returns the average processing time per recorded invocation, or {@code 0} if no
     * invocations have been recorded yet.
     *
     * @return {@code totalDurationNanos / invocations}, or {@code 0} when {@code invocations == 0}
     */
    public long averageDurationNanos() {
        return invocations > 0 ? totalDurationNanos / invocations : 0L;
    }

    /**
     * Returns a new {@link CriterionCounts} whose fields are the sum of this and {@code other}.
     *
     * @param other the counts to add
     * @return combined counts
     * @throws ArithmeticException if any addition overflows
     */
    public CriterionCounts add(CriterionCounts other) {
        return new CriterionCounts(
                Math.addExact(this.patientsExcluded, other.patientsExcluded),
                Math.addExact(this.resourcesExcluded, other.resourcesExcluded),
                Math.addExact(this.totalDurationNanos, other.totalDurationNanos),
                Math.addExact(this.invocations, other.invocations)
        );
    }

    /**
     * Returns a new {@link CriterionCounts} with {@code delta} added to {@code patientsExcluded}.
     * Returns this instance unchanged when {@code delta} is zero.
     *
     * @param delta number of patients to add; must not be negative
     * @return updated counts
     * @throws IllegalArgumentException if {@code delta} is negative
     * @throws ArithmeticException      if the addition overflows
     */
    public CriterionCounts plusPatients(long delta) {
        if (delta == 0) return this;
        if (delta < 0) throw new IllegalArgumentException("delta must not be negative: " + delta);
        return new CriterionCounts(Math.addExact(patientsExcluded, delta), resourcesExcluded, totalDurationNanos, invocations);
    }

    /**
     * Returns a new {@link CriterionCounts} with {@code delta} added to {@code resourcesExcluded}.
     * Returns this instance unchanged when {@code delta} is zero.
     *
     * @param delta number of resources to add; must not be negative
     * @return updated counts
     * @throws IllegalArgumentException if {@code delta} is negative
     * @throws ArithmeticException      if the addition overflows
     */
    public CriterionCounts plusResources(long delta) {
        if (delta == 0) return this;
        if (delta < 0) throw new IllegalArgumentException("delta must not be negative: " + delta);
        return new CriterionCounts(patientsExcluded, Math.addExact(resourcesExcluded, delta), totalDurationNanos, invocations);
    }

    /**
     * Returns a new {@link CriterionCounts} with {@code durationNanos} added to
     * {@code totalDurationNanos} and {@code invocations} incremented by one.
     * Returns this instance unchanged when {@code durationNanos} is zero.
     *
     * @param durationNanos nanoseconds to add; must not be negative
     * @return updated counts
     * @throws IllegalArgumentException if {@code durationNanos} is negative
     * @throws ArithmeticException      if the addition overflows
     */
    public CriterionCounts plusDuration(long durationNanos) {
        if (durationNanos == 0) return this;
        if (durationNanos < 0) throw new IllegalArgumentException("durationNanos must not be negative: " + durationNanos);
        return new CriterionCounts(patientsExcluded, resourcesExcluded,
                Math.addExact(totalDurationNanos, durationNanos), Math.addExact(invocations, 1));
    }
}
