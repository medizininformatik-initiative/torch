package de.medizininformatikinitiative.torch.diagnostics;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Map;

/**
 * Writes batch and stage metrics to a CSV file.
 *
 * <p>The file contains three row types identified by the first column:
 * {@code batch} / {@code core} (patient counts), {@code stage} (throughput per pipeline stage),
 * and {@code cohort} (cohort-query duration). Multiple batches can be appended to the same file;
 * use {@link MetricsCsvReader} to read them back.
 */
public final class MetricsCsvWriter {

    public static final String HEADER =
            "type,batchId,cohortPatients,finalPatients,durationMs,stage,resourcesProcessed,cohortQueryDurationMs";

    private MetricsCsvWriter() {}

    public static void writeBatch(BufferedWriter w, BatchDiagnostics diag) throws IOException {
        writeSummaryRow(w, "batch", diag);
    }

    public static void writeCore(BufferedWriter w, BatchDiagnostics diag) throws IOException {
        writeSummaryRow(w, "core", diag);
    }

    private static void writeSummaryRow(BufferedWriter w, String type, BatchDiagnostics diag) throws IOException {
        w.write(row(type, diag.batchId().toString(),
                Long.toString(diag.cohortPatientsInBatch()), Long.toString(diag.finalPatientsInBatch()),
                "", "", "", ""));
        w.newLine();

        for (Map.Entry<PipelineStage, StageCounts> entry : diag.stages().entrySet()) {
            StageCounts sc = entry.getValue();
            w.write(row("stage", diag.batchId().toString(),
                    "", "", Long.toString(sc.durationMs()), entry.getKey().name(),
                    Long.toString(sc.resourcesProcessed()), ""));
            w.newLine();
        }
    }

    public static void writeCohort(BufferedWriter w, long cohortQueryDurationMs) throws IOException {
        w.write(row("cohort", "", "", "", "", "", "", Long.toString(cohortQueryDurationMs)));
        w.newLine();
    }

    private static String row(String... cols) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cols.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(cols[i]);
        }
        return sb.toString();
    }
}
