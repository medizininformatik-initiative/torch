package de.medizininformatikinitiative.torch.diagnostics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Reads {@link ExclusionRecord}s from a CSV file written by {@link ExclusionCsvWriter}.
 *
 * <p>The header row is skipped. Rows with fewer than five columns or an empty reason column
 * are silently ignored. Rows with an unrecognised {@link ExclusionKind} value are skipped
 * with a warning.
 */
public final class ExclusionCsvReader {

    private static final Logger logger = LoggerFactory.getLogger(ExclusionCsvReader.class);

    private ExclusionCsvReader() {}

    public static List<ExclusionRecord> readAll(Stream<String> lines) {
        List<ExclusionRecord> result = new ArrayList<>();
        lines.skip(1).forEach(line -> {
            String[] cols = parseLine(line);
            if (cols.length < 5 || cols[1].isEmpty()) return;
            try {
                result.add(new ExclusionRecord(
                        nullIfEmpty(cols[0]),
                        ExclusionKind.valueOf(cols[1]),
                        nullIfEmpty(cols[2]),
                        nullIfEmpty(cols[3]),
                        nullIfEmpty(cols[4])
                ));
            } catch (IllegalArgumentException e) {
                logger.warn("Skipping malformed exclusions CSV row: {}", line);
            }
        });
        return List.copyOf(result);
    }

    static String[] parseLine(String line) {
        List<String> fields = new ArrayList<>();
        int i = 0;
        while (i <= line.length()) {
            if (i == line.length()) { fields.add(""); break; }
            if (line.charAt(i) == '"') {
                StringBuilder sb = new StringBuilder();
                i++;
                while (i < line.length()) {
                    char c = line.charAt(i);
                    if (c == '"') {
                        if (i + 1 < line.length() && line.charAt(i + 1) == '"') { sb.append('"'); i += 2; }
                        else { i++; break; }
                    } else { sb.append(c); i++; }
                }
                fields.add(sb.toString());
                if (i < line.length() && line.charAt(i) == ',') i++;
            } else {
                int end = line.indexOf(',', i);
                if (end < 0) { fields.add(line.substring(i)); break; }
                fields.add(line.substring(i, end));
                i = end + 1;
            }
        }
        return fields.toArray(new String[0]);
    }

    private static String nullIfEmpty(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }
}
