package de.medizininformatikinitiative.torch.util;


import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionPatientBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Manager for the Job processing
 */
public class ResultFileManager {

    private static final Logger logger = LoggerFactory.getLogger(ResultFileManager.class);
    private static final String NDJSON = ".ndjson";

    private final Path resultsDirPath;
    private final FhirContext fhirContext;

    public ResultFileManager(String resultsDir, FhirContext fhirContext) {
        this.resultsDirPath = Paths.get(resultsDir).toAbsolutePath();
        this.fhirContext = fhirContext;
    }

    public Path getJobDirectory(String jobId) {
        return resultsDirPath.resolve(String.valueOf(jobId));
    }

    public void saveBatchToNDJSON(String jobId, ExtractionPatientBatch batch) throws IOException {
        requireNonNull(jobId);
        requireNonNull(batch);
        if (batch.isEmpty()) {
            logger.trace("Attempted to save empty batch for id: {}", jobId);
            return;
        }
        var ndJsonFile = createNdJsonFile(jobId, batch);

        try (BufferedWriter out = Files.newBufferedWriter(ndJsonFile)) {
            batch.writeToFhirBundles(fhirContext, out, jobId);
        }
    }

    private Path createNdJsonFile(String jobId, ExtractionPatientBatch batch) throws IOException {
        Path jobDir = getJobDirectory(jobId);
        Files.createDirectories(jobDir);

        Path ndjsonFile;
        if (batch.bundles().keySet().equals(Set.of("CORE"))) {
            ndjsonFile = jobDir.resolve("core.ndjson");
            Files.deleteIfExists(ndjsonFile);
        } else {
            ndjsonFile = jobDir.resolve(batch + NDJSON);
        }
        return ndjsonFile;
    }
}
