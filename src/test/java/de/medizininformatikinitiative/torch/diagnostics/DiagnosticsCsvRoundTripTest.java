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

    private static MetricsCsvReader.CsvData roundTrip(UUID jobId,
                                                       List<BatchDiagnostics> batches,
                                                       long cohortMs) throws IOException {
        StringWriter sw = new StringWriter();
        try (BufferedWriter bw = new BufferedWriter(sw)) {
            bw.write(MetricsCsvWriter.HEADER);
            bw.newLine();
            for (BatchDiagnostics b : batches) MetricsCsvWriter.writeBatch(bw, b);
            if (cohortMs > 0) MetricsCsvWriter.writeCohort(bw, cohortMs);
        }
        return MetricsCsvReader.readAll(jobId, sw.toString().lines());
    }

    @Nested
    class EmptyBatch {

        @Test
        void emptyBatch_roundTrips() throws IOException {
            var diag = new BatchDiagnostics(JOB_ID, BATCH_A, 10, 8, Map.of());

            MetricsCsvReader.CsvData data = roundTrip(JOB_ID, List.of(diag), 0);

            assertThat(data.batches()).hasSize(1);
            BatchDiagnostics loaded = data.batches().get(0);
            assertThat(loaded.batchId()).isEqualTo(BATCH_A);
            assertThat(loaded.cohortPatientsInBatch()).isEqualTo(10);
            assertThat(loaded.finalPatientsInBatch()).isEqualTo(8);
            assertThat(loaded.stages()).isEmpty();
            assertThat(data.cohortQueryDurationMs()).isZero();
        }
    }

    @Nested
    class StageRows {

        @Test
        void stageCountsRoundTrip() throws IOException {
            var stages = Map.of(
                    PipelineStage.DIRECT_LOAD, new StageCounts(1000, 50),
                    PipelineStage.CASCADING_DELETE, new StageCounts(2000, 30));
            var diag = new BatchDiagnostics(JOB_ID, BATCH_A, 10, 10, stages);

            MetricsCsvReader.CsvData data = roundTrip(JOB_ID, List.of(diag), 0);

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
            var batchA = new BatchDiagnostics(JOB_ID, BATCH_A, 5, 4, Map.of());
            var batchB = new BatchDiagnostics(JOB_ID, BATCH_B, 3, 3, Map.of());

            MetricsCsvReader.CsvData data = roundTrip(JOB_ID, List.of(batchA, batchB), 0);

            assertThat(data.batches()).hasSize(2);
        }
    }

    @Nested
    class CohortRow {

        @Test
        void cohortDurationRoundTrips() throws IOException {
            MetricsCsvReader.CsvData data = roundTrip(JOB_ID, List.of(), 5000);
            assertThat(data.cohortQueryDurationMs()).isEqualTo(5000);
        }

        @Test
        void zeroCohortDuration_returnedAsZero() throws IOException {
            MetricsCsvReader.CsvData data = roundTrip(JOB_ID, List.of(), 0);
            assertThat(data.cohortQueryDurationMs()).isZero();
        }
    }

    @Nested
    class CoreRow {

        @Test
        void coreRow_roundTrips() throws IOException {
            var diag = new BatchDiagnostics(JOB_ID, JOB_ID, 0, 0, Map.of());

            StringWriter sw = new StringWriter();
            try (BufferedWriter bw = new BufferedWriter(sw)) {
                bw.write(MetricsCsvWriter.HEADER);
                bw.newLine();
                MetricsCsvWriter.writeCore(bw, diag);
            }
            MetricsCsvReader.CsvData data = MetricsCsvReader.readAll(JOB_ID, sw.toString().lines());

            assertThat(data.batches()).hasSize(1);
            BatchDiagnostics loaded = data.batches().get(0);
            assertThat(loaded.batchId()).isEqualTo(JOB_ID);
            assertThat(loaded.cohortPatientsInBatch()).isZero();
            assertThat(loaded.finalPatientsInBatch()).isZero();
        }

        @Test
        void coreRowAppearsAlongsideBatchRows() throws IOException {
            var batchDiag = new BatchDiagnostics(JOB_ID, BATCH_A, 5, 4, Map.of());
            var coreDiag = new BatchDiagnostics(JOB_ID, JOB_ID, 0, 0, Map.of());

            StringWriter sw = new StringWriter();
            try (BufferedWriter bw = new BufferedWriter(sw)) {
                bw.write(MetricsCsvWriter.HEADER);
                bw.newLine();
                MetricsCsvWriter.writeBatch(bw, batchDiag);
                MetricsCsvWriter.writeCore(bw, coreDiag);
            }
            MetricsCsvReader.CsvData data = MetricsCsvReader.readAll(JOB_ID, sw.toString().lines());

            assertThat(data.batches()).hasSize(2);
        }
    }

    @Nested
    class MalformedMetricsRows {

        @Test
        void unknownRowType_isIgnored() {
            String csv = MetricsCsvWriter.HEADER + "\nunknown_type,id,0,0,,,,\n";
            MetricsCsvReader.CsvData data = MetricsCsvReader.readAll(JOB_ID, csv.lines());
            assertThat(data.batches()).isEmpty();
            assertThat(data.cohortQueryDurationMs()).isZero();
        }

        @Test
        void batchRowWithMalformedUuid_isSkipped() {
            String csv = MetricsCsvWriter.HEADER + "\nbatch,not-a-uuid,10,8,,,,\n";
            MetricsCsvReader.CsvData data = MetricsCsvReader.readAll(JOB_ID, csv.lines());
            assertThat(data.batches()).isEmpty();
        }

        @Test
        void stageRowWithUnknownStageName_isSkipped() {
            String csv = MetricsCsvWriter.HEADER + "\nstage," + BATCH_A + ",,,100,UNKNOWN_STAGE,50,\n";
            MetricsCsvReader.CsvData data = MetricsCsvReader.readAll(JOB_ID, csv.lines());
            assertThat(data.batches()).isEmpty();
        }

        @Test
        void emptyFirstColumn_isIgnored() {
            String csv = MetricsCsvWriter.HEADER + "\n,batch,10,8,,,\n";
            MetricsCsvReader.CsvData data = MetricsCsvReader.readAll(JOB_ID, csv.lines());
            assertThat(data.batches()).isEmpty();
        }
    }

    @Nested
    class ExclusionCsvRoundTrip {

        @Test
        void plainValue_notQuoted() {
            assertThat(ExclusionCsvWriter.escape("hello")).isEqualTo("hello");
        }

        @Test
        void valueWithComma_quoted() {
            assertThat(ExclusionCsvWriter.escape("a,b")).isEqualTo("\"a,b\"");
        }

        @Test
        void valueWithQuote_doubled() {
            assertThat(ExclusionCsvWriter.escape("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        }

        @Test
        void emptyValue_notQuoted() {
            assertThat(ExclusionCsvWriter.escape("")).isEqualTo("");
        }

        @Test
        void simpleFields() {
            String[] cols = ExclusionCsvReader.parseLine("a,b,c");
            assertThat(cols).containsExactly("a", "b", "c");
        }

        @Test
        void quotedFieldWithComma() {
            String[] cols = ExclusionCsvReader.parseLine("a,\"x,y\",c");
            assertThat(cols).containsExactly("a", "x,y", "c");
        }

        @Test
        void emptyFields() {
            String[] cols = ExclusionCsvReader.parseLine("a,,c");
            assertThat(cols).containsExactly("a", "", "c");
        }

        @Test
        void headerLineParsesToExpectedColumnCount() {
            String[] cols = ExclusionCsvReader.parseLine(ExclusionCsvWriter.HEADER);
            assertThat(cols).hasSize(5);
        }

        @Test
        void nullValue_returnsEmpty() {
            assertThat(ExclusionCsvWriter.escape(null)).isEqualTo("");
        }

        @Test
        void quotedFieldContainingDoubleQuote() {
            String[] cols = ExclusionCsvReader.parseLine("a,\"say \"\"hi\"\"\",c");
            assertThat(cols).containsExactly("a", "say \"hi\"", "c");
        }

        @Test
        void trailingComma_producesEmptyLastField() {
            String[] cols = ExclusionCsvReader.parseLine("a,b,");
            assertThat(cols).containsExactly("a", "b", "");
        }

        @Test
        void readAll_skipsRowWithTooFewColumns() {
            String csv = ExclusionCsvWriter.HEADER + "\np1,CONSENT";
            assertThat(ExclusionCsvReader.readAll(csv.lines())).isEmpty();
        }

        @Test
        void readAll_skipsRowWithEmptyReasonColumn() {
            String csv = ExclusionCsvWriter.HEADER + "\np1,,grp,,";
            assertThat(ExclusionCsvReader.readAll(csv.lines())).isEmpty();
        }

        @Test
        void readAll_skipsRowWithUnrecognizedReason() {
            String csv = ExclusionCsvWriter.HEADER + "\np1,UNKNOWN_REASON,grp,,";
            assertThat(ExclusionCsvReader.readAll(csv.lines())).isEmpty();
        }

        @Test
        void exclusionRecord_roundTrips() throws IOException {
            var records = List.of(
                    new ExclusionRecord("p1", ExclusionKind.CONSENT, "grp-1", null, null),
                    new ExclusionRecord("p2", ExclusionKind.MUST_HAVE_RESOURCE, "grp-2", null, "attr;ref"),
                    new ExclusionRecord(null, ExclusionKind.REFERENCE_NOT_FOUND, null, "Observation/obs-1", null)
            );

            StringWriter sw = new StringWriter();
            try (BufferedWriter bw = new BufferedWriter(sw)) {
                bw.write(ExclusionCsvWriter.HEADER);
                bw.newLine();
                ExclusionCsvWriter.write(bw, records);
            }

            List<ExclusionRecord> loaded = ExclusionCsvReader.readAll(sw.toString().lines());

            assertThat(loaded).hasSize(3);
            assertThat(loaded.get(0).patientId()).isEqualTo("p1");
            assertThat(loaded.get(0).reason()).isEqualTo(ExclusionKind.CONSENT);
            assertThat(loaded.get(0).groupRef()).isEqualTo("grp-1");
            assertThat(loaded.get(0).resourceId()).isNull();
            assertThat(loaded.get(1).patientId()).isEqualTo("p2");
            assertThat(loaded.get(1).reason()).isEqualTo(ExclusionKind.MUST_HAVE_RESOURCE);
            assertThat(loaded.get(1).attributeRef()).isEqualTo("attr;ref");
            assertThat(loaded.get(2).patientId()).isNull();
            assertThat(loaded.get(2).resourceId()).isEqualTo("Observation/obs-1");
        }

        @Test
        void valueWithNewline_quoted() {
            assertThat(ExclusionCsvWriter.escape("line1\nline2")).isEqualTo("\"line1\nline2\"");
        }

        @Test
        void quotedFieldAtEndOfLine_noCommaAfterClosingQuote() {
            String[] cols = ExclusionCsvReader.parseLine("a,\"val\"");
            assertThat(cols).containsExactly("a", "val", "");
        }

        @Test
        void emptyInput_returnsSingleEmptyField() {
            String[] cols = ExclusionCsvReader.parseLine("");
            assertThat(cols).containsExactly("");
        }
    }
}
