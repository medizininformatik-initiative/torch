package de.medizininformatikinitiative.torch.diagnostics;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BatchDiagnosticsAccTest {

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
    class RecordStage {

        @Test
        void recordsSingleStage() {
            BatchDiagnosticsAcc acc = new BatchDiagnosticsAcc(UUID.randomUUID(), UUID.randomUUID(), 5);

            acc.recordStage(PipelineStage.DIRECT_LOAD, 1_000_000_000L, 20); // 1000 ms in nanos

            assertThat(acc.snapshot(0).stages().get(PipelineStage.DIRECT_LOAD))
                    .isEqualTo(new StageCounts(1000, 20));
        }

        @Test
        void multipleCallsSameStage_accumulates() {
            BatchDiagnosticsAcc acc = new BatchDiagnosticsAcc(UUID.randomUUID(), UUID.randomUUID(), 5);

            acc.recordStage(PipelineStage.CASCADING_DELETE, 100_000_000L, 5);  // 100 ms
            acc.recordStage(PipelineStage.CASCADING_DELETE, 200_000_000L, 10); // 200 ms

            assertThat(acc.snapshot(0).stages().get(PipelineStage.CASCADING_DELETE))
                    .isEqualTo(new StageCounts(300, 15));
        }

        @Test
        void differentStages_areIndependent() {
            BatchDiagnosticsAcc acc = new BatchDiagnosticsAcc(UUID.randomUUID(), UUID.randomUUID(), 5);

            acc.recordStage(PipelineStage.DIRECT_LOAD, 100_000_000L, 10);
            acc.recordStage(PipelineStage.REFERENCE_RESOLVE, 200_000_000L, 5);

            assertThat(acc.snapshot(0).stages()).containsOnlyKeys(
                    PipelineStage.DIRECT_LOAD, PipelineStage.REFERENCE_RESOLVE);
        }

        @Test
        void zeroDurationAndResources_isIgnored() {
            BatchDiagnosticsAcc acc = new BatchDiagnosticsAcc(UUID.randomUUID(), UUID.randomUUID(), 5);

            acc.recordStage(PipelineStage.COPY_REDACT, 0, 0);

            assertThat(acc.snapshot(0).stages()).isEmpty();
        }

        @Test
        void throwsWhenStageIsNull() {
            BatchDiagnosticsAcc acc = new BatchDiagnosticsAcc(UUID.randomUUID(), UUID.randomUUID(), 5);

            assertThatThrownBy(() -> acc.recordStage(null, 100, 10))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("stage");
        }
    }

    @Nested
    class Noop {

        @Test
        void recordStage_doesNothing() {
            BatchDiagnosticsAcc noop = BatchDiagnosticsAcc.noop();
            noop.recordStage(PipelineStage.DIRECT_LOAD, 1_000_000_000L, 20);
            assertThat(noop.snapshot(0).stages()).isEmpty();
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
            assertThat(snapshot.stages()).isEmpty();
        }

        @Test
        void throwsWhenFinalPatientsIsNegative() {
            BatchDiagnosticsAcc acc = new BatchDiagnosticsAcc(UUID.randomUUID(), UUID.randomUUID(), 5);

            assertThatThrownBy(() -> acc.snapshot(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("finalPatientsInBatch must be >= 0");
        }

        @Test
        void isIndependentFromFutureChanges() {
            BatchDiagnosticsAcc acc = new BatchDiagnosticsAcc(UUID.randomUUID(), UUID.randomUUID(), 5);
            acc.recordStage(PipelineStage.DIRECT_LOAD, 100_000_000L, 10);
            var first = acc.snapshot(0);
            acc.recordStage(PipelineStage.DIRECT_LOAD, 200_000_000L, 5);
            var second = acc.snapshot(0);

            assertThat(first.stages().get(PipelineStage.DIRECT_LOAD)).isEqualTo(new StageCounts(100, 10));
            assertThat(second.stages().get(PipelineStage.DIRECT_LOAD)).isEqualTo(new StageCounts(300, 15));
        }
    }
}
