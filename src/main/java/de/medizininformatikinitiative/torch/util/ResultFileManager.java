package de.medizininformatikinitiative.torch.util;


import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.management.OperationOutcomeCreator;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * Manager for the Job processing
 */
public class ResultFileManager {

    private static final Logger logger = LoggerFactory.getLogger(ResultFileManager.class);
    private static final String NDJSON = ".ndjson";
    private static final String ERROR_JSON = "error.json";

    private final Path resultsDirPath;
    private final FhirContext fhirContext;
    private final String hostname;
    private final String fileServerName;
    private final ConcurrentHashMap<String, HttpStatus> jobStatusMap = new ConcurrentHashMap<>();

    public ResultFileManager(String resultsDir, String duration, FhirContext fhirContext, String hostname, String fileServerName) {
        this.resultsDirPath = Paths.get(resultsDir).toAbsolutePath();
        this.fhirContext = fhirContext;


        Duration duration1 = Duration.parse(duration);
        this.hostname = hostname;
        this.fileServerName = fileServerName;


        logger.debug("Duration of persistence {}", duration1);
        // Ensure the directory exists
        if (!Files.exists(resultsDirPath)) {
            try {
                Files.createDirectories(resultsDirPath);
            } catch (IOException e) {
                logger.error("Could not create results directory: {}", e.getMessage());
            }
        }
        loadExistingResults();
    }

    public Path getJobDirectory(String jobId) {
        return resultsDirPath.resolve(jobId);
    }

    public Map<String, HttpStatus> getJobStatusMap() {
        return jobStatusMap;
    }

    private void loadExistingResults() {
        try (Stream<Path> jobDirs = Files.list(resultsDirPath)) {
            jobDirs.filter(Files::isDirectory)
                    .forEach(jobDir -> {
                        boolean fatalIssuePresent = false;
                        String jobId = jobDir.getFileName().toString();
                        try (Stream<Path> files = Files.list(jobDir)) {
                            // Find any .ndjson files in the job directory
                            Path errorFilePath = jobDir.resolve(ERROR_JSON);

                            if (Files.exists(errorFilePath)) {
                                try (InputStream is = Files.newInputStream(errorFilePath)) {
                                    OperationOutcome error = (OperationOutcome) fhirContext
                                            .newJsonParser()
                                            .parseResource(is);
                                    Optional<OperationOutcome.OperationOutcomeIssueComponent> fatalIssue = error.getIssue().stream()
                                            .filter(issue -> issue.getSeverity() == OperationOutcome.IssueSeverity.FATAL)
                                            .findFirst();

                                    if (fatalIssue.isPresent()) {
                                        fatalIssuePresent = true;
                                        logger.debug("Fatal issue {}", fatalIssue.get().getCode());
                                        OperationOutcome.IssueType code = fatalIssue.get().getCode();
                                        if (code.equals(OperationOutcome.IssueType.INVALID)) {
                                            setStatus(jobId, HttpStatus.INTERNAL_SERVER_ERROR);
                                        }
                                    }
                                } catch (IOException e) {
                                    logger.debug("Loading error.json failed for jobId: {}", jobId, e);
                                    setStatus(jobId, HttpStatus.NOT_FOUND);
                                }
                            }

                            if (!fatalIssuePresent) {
                                boolean ndjsonExists = files.anyMatch(file -> file.toString().endsWith(NDJSON));

                                if (ndjsonExists) {
                                    logger.debug("Loaded existing job with jobId: {}", jobId);
                                    setStatus(jobId, HttpStatus.OK);
                                } else {
                                    logger.warn("No .ndjson file found for jobId: {}", jobId);
                                    setStatus(jobId, HttpStatus.NOT_FOUND);
                                }
                            }

                        } catch (IOException e) {
                            logger.error("Error reading job directory {}: {}", jobDir, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            logger.error("Error loading existing results from {}: {}", resultsDirPath, e.getMessage());
        }
    }

    public Mono<Path> initJobDir(String jobId) {
        return Mono.fromCallable(() -> {
                    logger.debug("Initializing directory for job with id: {} ", jobId);
                    try {
                        // Get the path to the job directory
                        Path jobDir = resultsDirPath.resolve(jobId);


                        if (!Files.exists(jobDir)) {
                            Files.createDirectories(jobDir);

                        }

                        return jobDir;
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to initialize job directory", e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic());  // Run this on a separate scheduler for I/O tasks
    }


    public void saveBatchToNDJSON(String jobId, PatientBatchWithConsent batch) throws IOException {
        requireNonNull(jobId);
        requireNonNull(batch);
        if (batch.isEmpty()) {
            logger.trace("Attempted to save empty batch for jobId: {}", jobId);
            return;
        }
        var ndJsonFile = createNdJsonFile(jobId, batch);

        try (BufferedWriter out = Files.newBufferedWriter(ndJsonFile)) {
            batch.writeFhirBundlesTo(fhirContext, out);
        }
    }

    private Path createNdJsonFile(String jobId, PatientBatchWithConsent batch) throws IOException {
        Path jobDir = getJobDirectory(jobId);
        Files.createDirectories(jobDir); // Ensure job directory exists

        Path ndjsonFile;
        if (batch.patientIds().equals(Set.of("CORE"))) {
            ndjsonFile = jobDir.resolve("core.ndjson");
            Files.deleteIfExists(ndjsonFile);
        } else {
            ndjsonFile = jobDir.resolve(UUID.randomUUID() + NDJSON);
        }
        return ndjsonFile;
    }

    public Mono<Void> saveErrorToJson(String jobId, OperationOutcome outcome, HttpStatus status) {

        return Mono.fromRunnable(() -> {
                    Path jobDir = getJobDirectory(jobId);
                    try {
                        Files.createDirectories(jobDir); // Ensure job directory exists
                        String errorJson = fhirContext.newJsonParser().setPrettyPrint(false).encodeResourceToString(outcome);
                        Path errorFile;
                        errorFile = jobDir.resolve(ERROR_JSON);
                        Files.writeString(errorFile, errorJson + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                        setStatus(jobId, status);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to save error file", e);
                    }
                })
                .doOnError(e -> logger.error("Async write failed for job {}: {}", jobId, e.getMessage(), e))
                .subscribeOn(Schedulers.boundedElastic()).then();
    }

    public void setStatus(String jobId, HttpStatus status) {
        jobStatusMap.put(jobId, status);
    }

    public HttpStatus getStatus(String jobId) {
        return jobStatusMap.get(jobId);
    }

    public int getSize() {
        return jobStatusMap.size();
    }

    public String loadErrorFromFileSystem(String jobId) {
        Path jobDir = getJobDirectory(jobId);
        Path errorFile = jobDir.resolve(ERROR_JSON);

        try {
            return Files.readString(errorFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return fhirContext.newJsonParser().encodeResourceToString(OperationOutcomeCreator.createOperationOutcome(jobId, e));
        }
    }

    public Map<String, Object> loadBundleFromFileSystem(String jobId, String transactionTime) {
        Map<String, Object> response = new HashMap<>();
        Path jobDir = getJobDirectory(jobId);
        if (Files.exists(jobDir) && Files.isDirectory(jobDir)) {
            List<Map<String, String>> outputFiles = new ArrayList<>();
            List<Map<String, String>> deletedFiles = new ArrayList<>();
            List<Map<String, String>> errorFiles = new ArrayList<>();

            try (Stream<Path> files = Files.list(jobDir)) {
                files.forEach(file -> {
                    String fileName = file.getFileName().toString();
                    String url = fileServerName + "/" + jobId + "/" + fileName;

                    Map<String, String> fileEntry = new HashMap<>();
                    fileEntry.put("url", url);

                    if (fileName.endsWith(NDJSON)) {
                        fileEntry.put("type", "NDJSON Bundle");
                        outputFiles.add(fileEntry);
                    } else if (fileName.equals(ERROR_JSON)) {
                        fileEntry.put("type", "OperationOutcome");
                        errorFiles.add(fileEntry);
                    } else if (fileName.contains("del")) {
                        fileEntry.put("type", "Bundle");
                        deletedFiles.add(fileEntry);
                    }
                });
            } catch (IOException e) {
                logger.error("Failed to load bundles for jobId {}: {}", jobId, e.getMessage());
            }

            logger.debug("OutputFiles size {}", outputFiles.size());

            response.put("transactionTime", transactionTime);
            response.put("request", hostname + "/fhir/$extract-data");
            response.put("requiresAccessToken", false);
            response.put("output", outputFiles);
            response.put("deleted", deletedFiles);
            response.put("error", errorFiles);
        } else {
            logger.warn("Job directory does not exist or is not a directory for jobId: {}", jobId);
        }
        return response;
    }
}
