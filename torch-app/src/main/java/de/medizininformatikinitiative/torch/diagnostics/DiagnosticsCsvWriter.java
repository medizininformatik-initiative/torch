package de.medizininformatikinitiative.torch.diagnostics;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Map;

public final class DiagnosticsCsvWriter {

    public static final String HEADER =
            "type,batchId,exclusionKind,id,groupRef,attributeRef,cohortPatients,finalPatients," +
            "patientsExcluded,resourcesExcluded,durationMs,invocations,stage,resourcesProcessed,cohortQueryDurationMs";

    private DiagnosticsCsvWriter() {}

    public static void writeBatch(BufferedWriter w, BatchDiagnostics diag) throws IOException {
        writeSummaryRow(w, "batch", diag);
    }

    public static void writeCore(BufferedWriter w, BatchDiagnostics diag) throws IOException {
        writeSummaryRow(w, "core", diag);
    }

    private static void writeSummaryRow(BufferedWriter w, String type, BatchDiagnostics diag) throws IOException {
        w.write(row(type, diag.batchId().toString(), "", "", "", "",
                Long.toString(diag.cohortPatientsInBatch()), Long.toString(diag.finalPatientsInBatch()),
                "", "", "", "", "", "", ""));
        w.newLine();

        for (CriterionEntry entry : diag.criteria()) {
            CriterionKey key = entry.key();
            CriterionCounts counts = entry.counts();
            w.write(row("criterion", diag.batchId().toString(),
                    key.kind().name(),
                    nvl(key.id()), nvl(key.groupRef()), nvl(key.attributeRef()),
                    "", "",
                    Long.toString(counts.patientsExcluded()),
                    Long.toString(counts.resourcesExcluded()),
                    Long.toString(counts.totalDurationMs()),
                    Long.toString(counts.invocations()),
                    "", "", ""));
            w.newLine();
        }

        for (Map.Entry<PipelineStage, StageCounts> entry : diag.stages().entrySet()) {
            StageCounts sc = entry.getValue();
            w.write(row("stage", diag.batchId().toString(),
                    "", "", "", "", "", "", "", "",
                    Long.toString(sc.durationMs()),
                    "",
                    entry.getKey().name(),
                    Long.toString(sc.resourcesProcessed()),
                    ""));
            w.newLine();
        }
    }

    public static void writeCohort(BufferedWriter w, long cohortQueryDurationMs) throws IOException {
        w.write(row("cohort", "", "", "", "", "", "", "", "", "", "", "", "", "",
                Long.toString(cohortQueryDurationMs)));
        w.newLine();
    }

    private static String row(String... cols) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cols.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(escape(cols[i]));
        }
        return sb.toString();
    }

    static String escape(String value) {
        if (value == null || value.isEmpty()) return "";
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    private static String nvl(String v) {
        return v != null ? v : "";
    }
}
