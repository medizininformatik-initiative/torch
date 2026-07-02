package de.medizininformatikinitiative.torch.diagnostics;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JobDiagnosticsTest {

    static final UUID JOB_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    static final UUID BATCH_A = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");
    static final UUID BATCH_B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    @Nested
    class FromBatches {

        @Test
        void emptyBatches_producesZeroTotals() {
            var result = JobDiagnostics.fromBatches(JOB_ID, List.of(), 0L);
            assertThat(result.cohortPatientsTotal()).isZero();
            assertThat(result.finalPatientsTotal()).isZero();
            assertThat(result.stages()).isEmpty();
            assertThat(result.cohortQueryDurationMs()).isZero();
        }

        @Test
        void singleBatch_sumsCounts() {
            var batch = new BatchDiagnostics(JOB_ID, BATCH_A, 10, 8, Map.of());
            var result = JobDiagnostics.fromBatches(JOB_ID, List.of(batch), 0L);
            assertThat(result.cohortPatientsTotal()).isEqualTo(10);
            assertThat(result.finalPatientsTotal()).isEqualTo(8);
        }

        @Test
        void multipleBatches_accumulateCounts() {
            var a = new BatchDiagnostics(JOB_ID, BATCH_A, 5, 4, Map.of());
            var b = new BatchDiagnostics(JOB_ID, BATCH_B, 3, 3, Map.of());
            var result = JobDiagnostics.fromBatches(JOB_ID, List.of(a, b), 0L);
            assertThat(result.cohortPatientsTotal()).isEqualTo(8);
            assertThat(result.finalPatientsTotal()).isEqualTo(7);
        }

        @Test
        void stagesAreMergedAcrossBatches() {
            var stagesA = Map.of(PipelineStage.DIRECT_LOAD, new StageCounts(1000, 50));
            var stagesB = Map.of(PipelineStage.DIRECT_LOAD, new StageCounts(500, 30));
            var a = new BatchDiagnostics(JOB_ID, BATCH_A, 5, 5, stagesA);
            var b = new BatchDiagnostics(JOB_ID, BATCH_B, 3, 3, stagesB);

            var result = JobDiagnostics.fromBatches(JOB_ID, List.of(a, b), 0L);

            assertThat(result.stages()).containsOnlyKeys(PipelineStage.DIRECT_LOAD);
            assertThat(result.stages().get(PipelineStage.DIRECT_LOAD).durationMs()).isEqualTo(1500);
            assertThat(result.stages().get(PipelineStage.DIRECT_LOAD).resourcesProcessed()).isEqualTo(80);
        }

        @Test
        void cohortQueryDurationMs_isPassedThrough() {
            var result = JobDiagnostics.fromBatches(JOB_ID, List.of(), 5000L);
            assertThat(result.cohortQueryDurationMs()).isEqualTo(5000L);
        }
    }
}
