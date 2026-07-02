package de.medizininformatikinitiative.torch.diagnostics;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.List;

/**
 * Writes {@link ExclusionRecord}s to the diagnostics CSV representation.
 *
 * <p>The CSV contains one data row per exclusion event. It is the detailed source for
 * explaining patient/resource exclusions and can later be aggregated into job-level
 * summaries, for example by {@code attributeRef}.</p>
 *
 * <p>The caller is responsible for writing {@link #HEADER} before calling
 * {@link #write(BufferedWriter, List)}. Fields that require quoting, such as commas,
 * double quotes, or newlines, are quoted according to RFC 4180. {@code null} fields
 * are written as empty strings.</p>
 */
public final class ExclusionCsvWriter {

    /**
     * CSV header for exclusion diagnostics.
     */
    public static final String HEADER = "patientId,reason,groupRef,resourceId,attributeRef";

    private ExclusionCsvWriter() {
    }

    /**
     * Writes exclusion records as CSV rows.
     *
     * <p>This method writes only data rows. The caller must write {@link #HEADER} before
     * calling this method if a complete CSV file is required.</p>
     *
     * @param writer  destination writer
     * @param records exclusion records to write
     * @throws IOException          if writing to {@code writer} fails
     * @throws NullPointerException if {@code writer} or {@code records} is {@code null}
     */
    public static void write(BufferedWriter writer, List<ExclusionRecord> records) throws IOException {
        for (ExclusionRecord r : records) {
            writer.write(row(
                    nvl(r.patientId()),
                    r.reason().name(),
                    nvl(r.groupRef()),
                    nvl(r.resourceId()),
                    nvl(r.attributeRef())
            ));
            writer.newLine();
        }
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
