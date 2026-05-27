package de.medizininformatikinitiative.torch.diagnostics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

public final class DiagnosticsCsvReader {

    private static final Logger logger = LoggerFactory.getLogger(DiagnosticsCsvReader.class);

    private DiagnosticsCsvReader() {}

    public record CsvData(List<BatchDiagnostics> batches, long cohortQueryDurationMs) {}

    public static CsvData readAll(UUID jobId, Stream<String> lines) {
        Map<UUID, long[]> batchPatients = new LinkedHashMap<>();
        Map<UUID, List<CriterionEntry>> batchCriteria = new LinkedHashMap<>();
        Map<UUID, Map<PipelineStage, StageCounts>> batchStages = new LinkedHashMap<>();
        long[] cohortMs = {0L};

        lines.skip(1).forEach(line -> {
            String[] cols = parseLine(line);
            if (cols.length < 1 || cols[0].isEmpty()) return;
            try {
                switch (cols[0]) {
                    case "batch", "core" -> {
                        UUID id = UUID.fromString(cols[1]);
                        batchPatients.put(id, new long[]{
                                Long.parseLong(cols[6]), Long.parseLong(cols[7])});
                    }
                    case "criterion" -> {
                        UUID id = UUID.fromString(cols[1]);
                        CriterionKey key = new CriterionKey(
                                ExclusionKind.valueOf(cols[2]),
                                nullIfEmpty(cols[3]), nullIfEmpty(cols[4]), nullIfEmpty(cols[5]));
                        CriterionCounts counts = new CriterionCounts(
                                Long.parseLong(cols[8]), Long.parseLong(cols[9]),
                                Long.parseLong(cols[10]), Long.parseLong(cols[11]));
                        batchCriteria.computeIfAbsent(id, k -> new ArrayList<>())
                                     .add(new CriterionEntry(key, counts));
                    }
                    case "stage" -> {
                        UUID id = UUID.fromString(cols[1]);
                        PipelineStage stage = PipelineStage.valueOf(cols[12]);
                        StageCounts sc = new StageCounts(
                                Long.parseLong(cols[10]), Long.parseLong(cols[13]));
                        batchStages.computeIfAbsent(id, k -> new EnumMap<>(PipelineStage.class))
                                   .put(stage, sc);
                    }
                    case "cohort" -> cohortMs[0] = Long.parseLong(cols[14]);
                }
            } catch (IllegalArgumentException e) {
                logger.warn("Skipping malformed diagnostics CSV row: {}", line);
            }
        });

        Set<UUID> allIds = new LinkedHashSet<>(batchPatients.keySet());
        List<BatchDiagnostics> result = new ArrayList<>();
        for (UUID batchId : allIds) {
            long[] p = batchPatients.getOrDefault(batchId, new long[]{0, 0});
            result.add(new BatchDiagnostics(jobId, batchId, p[0], p[1],
                    batchCriteria.getOrDefault(batchId, List.of()),
                    batchStages.getOrDefault(batchId, Map.of())));
        }
        return new CsvData(result, cohortMs[0]);
    }

    static String[] parseLine(String line) {
        List<String> fields = new ArrayList<>();
        int i = 0;
        while (i <= line.length()) {
            if (i == line.length()) {
                fields.add("");
                break;
            }
            if (line.charAt(i) == '"') {
                StringBuilder sb = new StringBuilder();
                i++;
                while (i < line.length()) {
                    char c = line.charAt(i);
                    if (c == '"') {
                        if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                            sb.append('"');
                            i += 2;
                        } else {
                            i++;
                            break;
                        }
                    } else {
                        sb.append(c);
                        i++;
                    }
                }
                fields.add(sb.toString());
                if (i < line.length() && line.charAt(i) == ',') i++;
            } else {
                int end = line.indexOf(',', i);
                if (end < 0) {
                    fields.add(line.substring(i));
                    break;
                }
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
