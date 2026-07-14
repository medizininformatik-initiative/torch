package de.medizininformatikinitiative.torch.diagnostics;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticsCsvRoundTripTest {

    static final UUID JOB_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    static final UUID BATCH_A = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");
    static final UUID BATCH_B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    private static DiagnosticsCsvReader.CsvData roundTrip(UUID jobId,
                                                           List<BatchDiagnostics> batches,
                                                           long cohortMs) throws IOException {
        StringWriter sw = new StringWriter();
        try (BufferedWriter bw = new BufferedWriter(sw)) {
            bw.write(DiagnosticsCsvWriter.HEADER);
            bw.newLine();
            for (BatchDiagnostics b : batches) DiagnosticsCsvWriter.writeBatch(bw, b);
            if (cohortMs > 0) DiagnosticsCsvWriter.writeCohort(bw, cohortMs);
        }
        String csv = sw.toString();
        return DiagnosticsCsvReader.readAll(jobId, csv.lines());
    }

    @Nested
    class EmptyBatch {

        @Test
        void emptyBatch_roundTrips() throws IOException {
            var diag = new BatchDiagnostics(JOB_ID, BATCH_A, 10, 8, List.of());

            DiagnosticsCsvReader.CsvData data = roundTrip(JOB_ID, List.of(diag), 0);

            assertThat(data.batches()).hasSize(1);
            BatchDiagnostics loaded = data.batches().get(0);
            assertThat(loaded.batchId()).isEqualTo(BATCH_A);
            assertThat(loaded.cohortPatientsInBatch()).isEqualTo(10);
            assertThat(loaded.finalPatientsInBatch()).isEqualTo(8);
            assertThat(loaded.criteria()).isEmpty();
            assertThat(loaded.stages()).isEmpty();
            assertThat(data.cohortQueryDurationMs()).isZero();
        }
    }

    @Nested
    class CriterionRows {

        @Test
        void singleCriterion_roundTrips() throws IOException {
            var key = new CriterionKey(ExclusionKind.MUST_HAVE, "Obs.code", "grp-1", "Observation.code");
            var counts = new CriterionCounts(3, 5, 200, 2);
            var diag = new BatchDiagnostics(JOB_ID, BATCH_A, 10, 7,
                    List.of(new CriterionEntry(key, counts)));

            DiagnosticsCsvReader.CsvData data = roundTrip(JOB_ID, List.of(diag), 0);

            BatchDiagnostics loaded = data.batches().get(0);
            assertThat(loaded.criteria()).hasSize(1);
            CriterionEntry entry = loaded.criteria().get(0);
            assertThat(entry.key().kind()).isEqualTo(ExclusionKind.MUST_HAVE);
            assertThat(entry.key().id()).isEqualTo("Obs.code");
            assertThat(entry.key().groupRef()).isEqualTo("grp-1");
            assertThat(entry.key().attributeRef()).isEqualTo("Observation.code");
            assertThat(entry.counts().patientsExcluded()).isEqualTo(3);
            assertThat(entry.counts().resourcesExcluded()).isEqualTo(5);
            assertThat(entry.counts().totalDurationMs()).isEqualTo(200);
            assertThat(entry.counts().invocations()).isEqualTo(2);
        }

        @Test
        void nullFields_roundTripAsNull() throws IOException {
            var key = new CriterionKey(ExclusionKind.REFERENCE_NOT_FOUND, null, null, null);
            var diag = new BatchDiagnostics(JOB_ID, BATCH_A, 5, 4,
                    List.of(new CriterionEntry(key, new CriterionCounts(1, 0))));

            DiagnosticsCsvReader.CsvData data = roundTrip(JOB_ID, List.of(diag), 0);

            CriterionKey loaded = data.batches().get(0).criteria().get(0).key();
            assertThat(loaded.id()).isNull();
            assertThat(loaded.groupRef()).isNull();
            assertThat(loaded.attributeRef()).isNull();
        }

        @Test
        void commaInIdField_escapedAndRoundTrips() throws IOException {
            var key = new CriterionKey(ExclusionKind.CONSENT, "a,b", null, null);
            var diag = new BatchDiagnostics(JOB_ID, BATCH_A, 1, 0,
                    List.of(new CriterionEntry(key, new CriterionCounts(1, 0))));

            DiagnosticsCsvReader.CsvData data = roundTrip(JOB_ID, List.of(diag), 0);

            assertThat(data.batches().get(0).criteria().get(0).key().id()).isEqualTo("a,b");
        }
    }

    @Nested
    class StageRows {

        @Test
        void stageCountsRoundTrip() throws IOException {
            var stages = Map.of(
                    PipelineStage.DIRECT_LOAD, new StageCounts(1000, 50),
                    PipelineStage.CASCADING_DELETE, new StageCounts(2000, 30));
            var diag = new BatchDiagnostics(JOB_ID, BATCH_A, 10, 10, List.of(), stages);

            DiagnosticsCsvReader.CsvData data = roundTrip(JOB_ID, List.of(diag), 0);

            Map<PipelineStage, StageCounts> loaded = data.batches().get(0).stages();
            assertThat(loaded).containsKey(PipelineStage.DIRECT_LOAD);
            assertThat(loaded.get(PipelineStage.DIRECT_LOAD).durationMs()).isEqualTo(1000);
            assertThat(loaded.get(PipelineStage.DIRECT_LOAD).resourcesProcessed()).isEqualTo(50);
            assertThat(loaded.get(PipelineStage.CASCADING_DELETE).durationMs()).isEqualTo(2000);
        }
    }

    @Nested
    class MultipleBatches {

        @Test
        void twoBatches_keptSeparate() throws IOException {
            var key = new CriterionKey(ExclusionKind.CONSENT, "c", null, null);
            var batchA = new BatchDiagnostics(JOB_ID, BATCH_A, 5, 4,
                    List.of(new CriterionEntry(key, new CriterionCounts(1, 0))));
            var batchB = new BatchDiagnostics(JOB_ID, BATCH_B, 3, 3,
                    List.of(new CriterionEntry(key, new CriterionCounts(0, 2))));

            DiagnosticsCsvReader.CsvData data = roundTrip(JOB_ID, List.of(batchA, batchB), 0);

            assertThat(data.batches()).hasSize(2);
        }
    }

    @Nested
    class CohortRow {

        @Test
        void cohortDurationRoundTrips() throws IOException {
            DiagnosticsCsvReader.CsvData data = roundTrip(JOB_ID, List.of(), 5000);
            assertThat(data.cohortQueryDurationMs()).isEqualTo(5000);
        }

        @Test
        void zeroCohortDuration_returnedAsZero() throws IOException {
            DiagnosticsCsvReader.CsvData data = roundTrip(JOB_ID, List.of(), 0);
            assertThat(data.cohortQueryDurationMs()).isZero();
        }
    }

    @Nested
    class CoreRow {

        @Test
        void coreRow_roundTrips() throws IOException {
            var key = new CriterionKey(ExclusionKind.MUST_HAVE, "Obs.code", "grp-core", null);
            var diag = new BatchDiagnostics(JOB_ID, JOB_ID, 0, 0,
                    List.of(new CriterionEntry(key, new CriterionCounts(0, 3))));

            StringWriter sw = new StringWriter();
            try (BufferedWriter bw = new BufferedWriter(sw)) {
                bw.write(DiagnosticsCsvWriter.HEADER);
                bw.newLine();
                DiagnosticsCsvWriter.writeCore(bw, diag);
            }
            DiagnosticsCsvReader.CsvData data = DiagnosticsCsvReader.readAll(JOB_ID, sw.toString().lines());

            assertThat(data.batches()).hasSize(1);
            BatchDiagnostics loaded = data.batches().get(0);
            assertThat(loaded.batchId()).isEqualTo(JOB_ID);
            assertThat(loaded.cohortPatientsInBatch()).isZero();
            assertThat(loaded.finalPatientsInBatch()).isZero();
            assertThat(loaded.criteria()).hasSize(1);
            assertThat(loaded.criteria().get(0).counts().resourcesExcluded()).isEqualTo(3);
        }

        @Test
        void coreRowAppearsAlongsideBatchRows() throws IOException {
            var batchDiag = new BatchDiagnostics(JOB_ID, BATCH_A, 5, 4, List.of());
            var coreDiag = new BatchDiagnostics(JOB_ID, JOB_ID, 0, 0, List.of());

            StringWriter sw = new StringWriter();
            try (BufferedWriter bw = new BufferedWriter(sw)) {
                bw.write(DiagnosticsCsvWriter.HEADER);
                bw.newLine();
                DiagnosticsCsvWriter.writeBatch(bw, batchDiag);
                DiagnosticsCsvWriter.writeCore(bw, coreDiag);
            }
            DiagnosticsCsvReader.CsvData data = DiagnosticsCsvReader.readAll(JOB_ID, sw.toString().lines());

            assertThat(data.batches()).hasSize(2);
        }
    }

    @Nested
    class EscapeLogic {

        @Test
        void plainValue_notQuoted() {
            assertThat(DiagnosticsCsvWriter.escape("hello")).isEqualTo("hello");
        }

        @Test
        void valueWithComma_quoted() {
            assertThat(DiagnosticsCsvWriter.escape("a,b")).isEqualTo("\"a,b\"");
        }

        @Test
        void valueWithQuote_doubled() {
            assertThat(DiagnosticsCsvWriter.escape("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        }

        @Test
        void emptyValue_notQuoted() {
            assertThat(DiagnosticsCsvWriter.escape("")).isEqualTo("");
        }
    }

    @Nested
    class ParseLine {

        @Test
        void simpleFields() {
            String[] cols = DiagnosticsCsvReader.parseLine("a,b,c");
            assertThat(cols).containsExactly("a", "b", "c");
        }

        @Test
        void quotedFieldWithComma() {
            String[] cols = DiagnosticsCsvReader.parseLine("a,\"x,y\",c");
            assertThat(cols).containsExactly("a", "x,y", "c");
        }

        @Test
        void emptyFields() {
            String[] cols = DiagnosticsCsvReader.parseLine("a,,c");
            assertThat(cols).containsExactly("a", "", "c");
        }

        @Test
        void headerLineParsesToExpectedColumnCount() {
            String[] cols = DiagnosticsCsvReader.parseLine(DiagnosticsCsvWriter.HEADER);
            assertThat(cols).hasSize(15);
        }
    }
}
