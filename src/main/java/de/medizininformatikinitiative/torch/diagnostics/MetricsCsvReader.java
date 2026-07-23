package de.medizininformatikinitiative.torch.diagnostics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Reads batch and stage metrics from a CSV file written by {@link MetricsCsvWriter}.
 *
 * <p>Rows with an unrecognised type, a malformed UUID, or an invalid stage name are skipped
 * with a warning. The result is returned as {@link CsvData}.
 */
public final class MetricsCsvReader {

    private static final Logger logger = LoggerFactory.getLogger(MetricsCsvReader.class);

    private MetricsCsvReader() {}

    public record CsvData(List<BatchDiagnostics> batches, long cohortQueryDurationMs) {}

    public static CsvData readAll(UUID jobId, Stream<String> lines) {
        Map<UUID, long[]> batchPatients = new LinkedHashMap<>();
        Map<UUID, Map<PipelineStage, StageCounts>> batchStages = new LinkedHashMap<>();
        long[] cohortMs = {0L};

        lines.skip(1).forEach(line -> {
            String[] cols = line.split(",", -1);
            if (cols.length < 1 || cols[0].isEmpty()) return;
            try {
                switch (cols[0]) {
                    case "batch", "core" -> {
                        UUID id = UUID.fromString(cols[1]);
                        batchPatients.put(id, new long[]{
                                Long.parseLong(cols[2]), Long.parseLong(cols[3])});
                    }
                    case "stage" -> {
                        UUID id = UUID.fromString(cols[1]);
                        PipelineStage stage = PipelineStage.valueOf(cols[5]);
                        StageCounts sc = new StageCounts(Long.parseLong(cols[4]), Long.parseLong(cols[6]));
                        batchStages.computeIfAbsent(id, k -> new EnumMap<>(PipelineStage.class)).put(stage, sc);
                    }
                    case "cohort" -> cohortMs[0] = Long.parseLong(cols[7]);
                }
            } catch (IllegalArgumentException e) {
                logger.warn("Skipping malformed metrics CSV row: {}", line);
            }
        });

        List<BatchDiagnostics> result = new ArrayList<>();
        for (UUID batchId : batchPatients.keySet()) {
            long[] p = batchPatients.get(batchId);
            result.add(new BatchDiagnostics(jobId, batchId, p[0], p[1],
                    batchStages.getOrDefault(batchId, Map.of())));
        }
        return new CsvData(result, cohortMs[0]);
    }
}
