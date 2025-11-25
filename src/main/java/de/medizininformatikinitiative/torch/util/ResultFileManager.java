package de.medizininformatikinitiative.torch.util;


import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.jobhandling.FileIo;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionPatientBatch;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionResourceBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.util.Objects.requireNonNull;

/**
 * Manages persistence of extraction results as NDJSON files.
 * <p>
 * Each job gets its own result directory under the configured base directory.
 * Patient batches and the final merged core bundle are written in NDJSON format
 * using the provided {@link FhirContext} for serialization.
 */
public class ResultFileManager {

    private static final Logger logger = LoggerFactory.getLogger(ResultFileManager.class);
    private static final String NDJSON = ".ndjson";

    private final Path resultsDirPath;
    private final FhirContext fhirContext;
    private final FileIo fileIo;

    /**
     * Creates a new result file manager.
     *
     * @param resultsDir  base directory for all job result output.
     * @param fhirContext FHIR context used for JSON serialization.
     * @param fileIo      FileIO used for file interaction interface for easier mocking
     */
    public ResultFileManager(String resultsDir, FhirContext fhirContext, FileIo fileIo) {
        this.resultsDirPath = Paths.get(resultsDir).toAbsolutePath();
        this.fhirContext = fhirContext;
        this.fileIo = fileIo;
    }

    /**
     * Returns the result directory for a given job.
     *
     * @param jobId the job id.
     * @return the absolute path to the job's result directory.
     */
    public Path getJobDirectory(String jobId) {
        return resultsDirPath.resolve(jobId);
    }

    /**
     * Writes a patient batch result to an NDJSON file.
     * <p>
     * Each line contains a serialized FHIR bundle for a single patient.
     * Empty batches are ignored.
     *
     * @param jobId the job id.
     * @param batch the patient batch to persist.
     * @throws IOException if the output directory cannot be created or the file cannot be written.
     */
    public void saveBatchToNDJSON(String jobId, ExtractionPatientBatch batch) throws IOException {
        requireNonNull(jobId);
        requireNonNull(batch);

        if (batch.isEmpty()) {
            logger.trace("Attempted to save empty batch for job {}", jobId);
            return;
        }

        Path resultDir = getJobDirectory(jobId);
        fileIo.createDirectories(resultDir);

        Path target = resultDir.resolve(batch.id() + NDJSON);
        Path tmp = resultDir.resolve(batch.id() + "-" + NDJSON + ".tmp");
        logger.debug("Writing batch {} for job {} to {}", batch.id(), jobId, target);

        try (BufferedWriter out = fileIo.newBufferedWriter(tmp)) {
            batch.writeToFhirBundles(fhirContext, out, jobId);
        }
        fileIo.atomicMove(tmp, target);
    }


    /**
     * Writes the final merged core extraction bundle as NDJSON.
     * <p>
     * Each line contains a serialized FHIR resource.
     * Empty bundles are skipped.
     *
     * @param jobId  the job id.
     * @param bundle the merged extraction bundle.
     * @throws IOException if the output directory cannot be created or the file cannot be written.
     */
    public void saveCoreBundleToNDJSON(String jobId, ExtractionResourceBundle bundle) throws IOException {
        requireNonNull(jobId);
        requireNonNull(bundle);

        Path resultDir = getJobDirectory(jobId);
        fileIo.createDirectories(resultDir);
        fileIo.setPosixPermissionsIfSupported(resultDir, "rwxr-xr-x");

        if (bundle.isEmpty()) {
            logger.debug("Skipping core bundle for job {}, since it is empty", jobId);
            return;
        }

        Path target = resultDir.resolve("core" + NDJSON);

        Path tmp = resultDir.resolve("core-" + NDJSON + ".tmp");

        logger.debug("Writing core bundle for job {} to {}", jobId, target);

        try (Writer out = fileIo.newBufferedWriter(tmp)) {
            bundle.writeToFhirBundle(fhirContext, out, jobId);
        }

        fileIo.atomicMove(tmp, target);
    }
}
