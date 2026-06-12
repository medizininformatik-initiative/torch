package de.medizininformatikinitiative.torch.diagnostics;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static java.util.Objects.requireNonNull;

/**
 * Thread-safe accumulator for detailed exclusion events recorded during extraction.
 *
 * <p>Each call to {@link #record(ExclusionRecord)} represents one patient- or
 * resource-level exclusion event that should later appear in {@code exclusions.csv}.
 * The writer is deliberately separate from {@link BatchDiagnosticsAcc}: batch
 * diagnostics track throughput and stage timing, while this class preserves the
 * information needed to explain why specific patients or resources were excluded.</p>
 *
 * <p>Call {@link #snapshot()} after processing to obtain an immutable copy for
 * persistence. The returned list is independent from this writer and can be safely
 * written even if more records are added afterwards.</p>
 *
 * <p>The writer is safe to use from parallel processing steps within one batch.
 * The order of records is not part of the diagnostics contract and should not be
 * relied on by consumers.</p>
 *
 * <p>Use {@link #noop()} for code paths where exclusion tracking is intentionally
 * disabled.</p>
 */
public class ExclusionAcc {

    private final Queue<ExclusionRecord> records = new ConcurrentLinkedQueue<>();

    /**
     * Returns a writer that intentionally drops all recorded exclusion events.
     *
     * <p>This is useful for tests or code paths where exclusion tracking is optional,
     * but method signatures still require a writer.</p>
     *
     * @return no-op exclusion writer
     */
    public static ExclusionAcc noop() {
        return Noop.INSTANCE;
    }

    /**
     * Records one exclusion event.
     *
     * @param record exclusion event to append
     * @throws NullPointerException if {@code record} is {@code null}
     */
    public void record(ExclusionRecord record) {
        records.add(requireNonNull(record));
    }

    /**
     * Returns an immutable snapshot of all recorded exclusion events.
     *
     * <p>The returned list is independent from this writer. Later calls to
     * {@link #record(ExclusionRecord)} do not modify previously returned snapshots.
     * If records are added concurrently while the snapshot is created, the snapshot
     * contains a weakly consistent point-in-time view.</p>
     *
     * @return immutable snapshot of recorded exclusion events
     */
    public List<ExclusionRecord> snapshot() {
        return List.copyOf(records);
    }

    private static final class Noop extends ExclusionAcc {
        static final Noop INSTANCE = new Noop();

        /**
         * Drops the exclusion event.
         *
         * @param record exclusion event that would normally be appended
         */
        @Override
        public void record(ExclusionRecord record) {
            // intentionally ignored
        }

        /**
         * Returns an empty snapshot.
         *
         * @return empty list
         */
        @Override
        public List<ExclusionRecord> snapshot() {
            return List.of();
        }
    }
}
