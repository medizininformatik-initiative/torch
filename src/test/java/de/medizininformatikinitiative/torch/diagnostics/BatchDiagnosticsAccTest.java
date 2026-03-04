package de.medizininformatikinitiative.torch.diagnostics;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BatchDiagnosticsAccTest {

    private static final CriterionKey KEY_1 =
            new CriterionKey(ExclusionKind.MUST_HAVE, "group-id-1", "must-have-group", "groupRef-1", "attr-1");

    private static final CriterionKey KEY_2 =
            new CriterionKey(ExclusionKind.REFERENCE_NOT_FOUND, null, "reference-not-found", "groupRef-2", "attr-2");

    @Nested
    class Constructor {

        @Test
        void storesValues() {
            UUID jobId = UUID.randomUUID();
            UUID batchId = UUID.randomUUID();

            BatchDiagnosticsAcc acc = new BatchDiagnosticsAcc(jobId, batchId, 7);

            assertThat(acc.jobId()).isEqualTo(jobId);
            assertThat(acc.batchId()).isEqualTo(batchId);
            assertThat(acc.cohortPatientsInBatch()).isEqualTo(7);
        }

        @Test
        void throwsWhenJobIdIsNull() {
            UUID batchId = UUID.randomUUID();

            assertThatThrownBy(() -> new BatchDiagnosticsAcc(null, batchId, 1))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("jobId");
        }

        @Test
        void throwsWhenBatchIdIsNull() {
            UUID jobId = UUID.randomUUID();

            assertThatThrownBy(() -> new BatchDiagnosticsAcc(jobId, null, 1))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("batchId");
        }

        @Test
        void throwsWhenCohortPatientsIsNegative() {
            assertThatThrownBy(() -> new BatchDiagnosticsAcc(UUID.randomUUID(), UUID.randomUUID(), -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("cohortPatientsInBatch must be >= 0");
        }
    }

    @Nested
    class IncPatientsExcluded {

        @Test
        void addsNewCriterionAndAccumulates() {
            BatchDiagnosticsAcc acc = new BatchDiagnosticsAcc(UUID.randomUUID(), UUID.randomUUID(), 5);

            acc.incPatientsExcluded(KEY_1, 2);
            acc.incPatientsExcluded(KEY_1, 3);

            BatchDiagnostics snapshot = acc.snapshot(0);

            assertThat(snapshot.countsFor(KEY_1).get().patientsExcluded()).isEqualTo(5);
            assertThat(snapshot.countsFor(KEY_1).get().resourcesExcluded()).isEqualTo(0);
        }

        @Test
        void ignoresZeroAndNegativeDelta() {
            BatchDiagnosticsAcc acc = new BatchDiagnosticsAcc(UUID.randomUUID(), UUID.randomUUID(), 5);

            acc.incPatientsExcluded(KEY_1, 0);
            acc.incPatientsExcluded(KEY_1, -2);

            assertThat(acc.snapshot(0).countsFor(KEY_1)).isEmpty();
        }

        @Test
        void throwsWhenKeyIsNull() {
            BatchDiagnosticsAcc acc = new BatchDiagnosticsAcc(UUID.randomUUID(), UUID.randomUUID(), 5);

            assertThatThrownBy(() -> acc.incPatientsExcluded(null, 1))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("key");
        }
    }

    @Nested
    class IncResourcesExcluded {

        @Test
        void addsNewCriterionAndAccumulates() {
            BatchDiagnosticsAcc acc = new BatchDiagnosticsAcc(UUID.randomUUID(), UUID.randomUUID(), 5);

            acc.incResourcesExcluded(KEY_1, 2);
            acc.incResourcesExcluded(KEY_1, 4);

            BatchDiagnostics snapshot = acc.snapshot(0);

            assertThat(snapshot.countsFor(KEY_1).get().patientsExcluded()).isEqualTo(0);
            assertThat(snapshot.countsFor(KEY_1).get().resourcesExcluded()).isEqualTo(6);
        }

        @Test
        void ignoresZeroAndNegativeDelta() {
            BatchDiagnosticsAcc acc = new BatchDiagnosticsAcc(UUID.randomUUID(), UUID.randomUUID(), 5);

            acc.incResourcesExcluded(KEY_1, 0);
            acc.incResourcesExcluded(KEY_1, -2);

            assertThat(acc.snapshot(0).countsFor(KEY_1)).isEmpty();
        }

        @Test
        void throwsWhenKeyIsNull() {
            BatchDiagnosticsAcc acc = new BatchDiagnosticsAcc(UUID.randomUUID(), UUID.randomUUID(), 5);

            assertThatThrownBy(() -> acc.incResourcesExcluded(null, 1))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("key");
        }
    }

    @Nested
    class Increments {

        @Test
        void sameCriterionMergesPatientsAndResources() {
            BatchDiagnosticsAcc acc = new BatchDiagnosticsAcc(UUID.randomUUID(), UUID.randomUUID(), 5);

            acc.incPatientsExcluded(KEY_1, 1);
            acc.incResourcesExcluded(KEY_1, 2);
            acc.incPatientsExcluded(KEY_1, 3);
            acc.incResourcesExcluded(KEY_1, 4);

            BatchDiagnostics snapshot = acc.snapshot(0);

            assertThat(snapshot.countsFor(KEY_1).get().patientsExcluded()).isEqualTo(4);
            assertThat(snapshot.countsFor(KEY_1).get().resourcesExcluded()).isEqualTo(6);
        }

        @Test
        void differentCriteriaCreateSeparateEntries() {
            BatchDiagnosticsAcc acc = new BatchDiagnosticsAcc(UUID.randomUUID(), UUID.randomUUID(), 5);

            acc.incResourcesExcluded(KEY_1, 2);
            acc.incPatientsExcluded(KEY_2, 1);

            BatchDiagnostics snapshot = acc.snapshot(0);

            assertThat(snapshot.criteria()).hasSize(2);
            assertThat(snapshot.countsFor(KEY_1).get().resourcesExcluded()).isEqualTo(2);
            assertThat(snapshot.countsFor(KEY_1).get().patientsExcluded()).isEqualTo(0);
            assertThat(snapshot.countsFor(KEY_2).get().patientsExcluded()).isEqualTo(1);
            assertThat(snapshot.countsFor(KEY_2).get().resourcesExcluded()).isEqualTo(0);
        }
    }

    @Nested
    class RecordDuration {

        @Test
        void accumulatesDurationAndInvocationsPerKey() {
            BatchDiagnosticsAcc acc = new BatchDiagnosticsAcc(UUID.randomUUID(), UUID.randomUUID(), 5);

            acc.recordDuration(KEY_1, 100);
            acc.recordDuration(KEY_1, 200);

            CriterionCounts counts = acc.snapshot(0).countsFor(KEY_1).get();
            assertThat(counts.totalDurationNanos()).isEqualTo(300);
            assertThat(counts.invocations()).isEqualTo(2);
            assertThat(counts.averageDurationNanos()).isEqualTo(150);
        }

        @Test
        void ignoresZeroAndNegativeDuration() {
            BatchDiagnosticsAcc acc = new BatchDiagnosticsAcc(UUID.randomUUID(), UUID.randomUUID(), 5);

            acc.recordDuration(KEY_1, 0);
            acc.recordDuration(KEY_1, -50);

            assertThat(acc.snapshot(0).countsFor(KEY_1)).isEmpty();
        }

        @Test
        void independentFromExclusionCounts() {
            BatchDiagnosticsAcc acc = new BatchDiagnosticsAcc(UUID.randomUUID(), UUID.randomUUID(), 5);

            acc.incPatientsExcluded(KEY_1, 3);
            acc.recordDuration(KEY_1, 500);

            CriterionCounts counts = acc.snapshot(0).countsFor(KEY_1).get();
            assertThat(counts.patientsExcluded()).isEqualTo(3);
            assertThat(counts.totalDurationNanos()).isEqualTo(500);
            assertThat(counts.invocations()).isEqualTo(1);
        }

        @Test
        void throwsWhenKeyIsNull() {
            BatchDiagnosticsAcc acc = new BatchDiagnosticsAcc(UUID.randomUUID(), UUID.randomUUID(), 5);

            assertThatThrownBy(() -> acc.recordDuration(null, 100))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("key");
        }
    }

    @Nested
    class Snapshot {

        @Test
        void storesProvidedFinalPatientCount() {
            UUID jobId = UUID.randomUUID();
            UUID batchId = UUID.randomUUID();

            BatchDiagnosticsAcc acc = new BatchDiagnosticsAcc(jobId, batchId, 5);
            BatchDiagnostics snapshot = acc.snapshot(3);

            assertThat(snapshot.jobId()).isEqualTo(jobId);
            assertThat(snapshot.batchId()).isEqualTo(batchId);
            assertThat(snapshot.cohortPatientsInBatch()).isEqualTo(5);
            assertThat(snapshot.finalPatientsInBatch()).isEqualTo(3);
            assertThat(snapshot.criteria()).isEmpty();
        }

        @Test
        void throwsWhenFinalPatientsIsNegative() {
            BatchDiagnosticsAcc acc = new BatchDiagnosticsAcc(UUID.randomUUID(), UUID.randomUUID(), 5);

            assertThatThrownBy(() -> acc.snapshot(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("finalPatientsInBatch must be >= 0");
        }

        @Test
        void returnsUnmodifiableCriteriaList() {
            BatchDiagnosticsAcc acc = new BatchDiagnosticsAcc(UUID.randomUUID(), UUID.randomUUID(), 5);
            acc.incPatientsExcluded(KEY_1, 1);

            var snapshot = acc.snapshot(0);

            assertThatThrownBy(() -> snapshot.criteria().add(new CriterionEntry(KEY_2, CriterionCounts.empty())))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void isIndependentFromFutureChanges() {
            BatchDiagnosticsAcc acc = new BatchDiagnosticsAcc(UUID.randomUUID(), UUID.randomUUID(), 5);
            acc.incPatientsExcluded(KEY_1, 1);
            var first = acc.snapshot(0);
            acc.incPatientsExcluded(KEY_1, 2);
            acc.incResourcesExcluded(KEY_2, 4);
            var second = acc.snapshot(0);

            assertThat(first.criteria()).hasSize(1);
            assertThat(first.countsFor(KEY_1).get().patientsExcluded()).isEqualTo(1);
            assertThat(first.countsFor(KEY_1).get().resourcesExcluded()).isEqualTo(0);
            assertThat(first.countsFor(KEY_2)).isEmpty();
            assertThat(second.countsFor(KEY_1).get().patientsExcluded()).isEqualTo(3);
            assertThat(second.countsFor(KEY_2).get().resourcesExcluded()).isEqualTo(4);
        }
    }
}
