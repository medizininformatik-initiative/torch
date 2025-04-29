package de.medizininformatikinitiative.torch.util;


import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.management.OperationOutcomeCreator;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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

/**
 * Manager for the Job processing
 */


public class ResultFileManager {

    private static final Logger logger = LoggerFactory.getLogger(ResultFileManager.class);

    private final Path resultsDirPath;
    private final FhirContext fhirContext;
    private final String hostname;
    private final String fileServerName;
    public final ConcurrentHashMap<String, HttpStatus> jobStatusMap = new ConcurrentHashMap<>();

    public ResultFileManager(String resultsDir, String duration, FhirContext fhirContext, String hostname, String fileServerName) {
        this.resultsDirPath = Paths.get(resultsDir).toAbsolutePath();
        this.fhirContext = fhirContext;


        Duration duration1 = Duration.parse(duration);
        this.hostname = hostname;
        this.fileServerName = fileServerName;


        logger.debug(" Duration of persistence{}", duration1);
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

    public void loadExistingResults() {
        try (Stream<Path> jobDirs = Files.list(resultsDirPath)) {
            jobDirs.filter(Files::isDirectory)
                    .forEach(jobDir -> {
                        boolean fatalIssuePresent = false;
                        String jobId = jobDir.getFileName().toString();
                        try (Stream<Path> files = Files.list(jobDir)) {
                            // Find any .ndjson files in the job directory
                            Path errorFilePath = jobDir.resolve("error.json");

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
                                            setStatus(jobId, HttpStatus.BAD_REQUEST);
                                        }
                                    }
                                } catch (IOException e) {
                                    logger.debug("Loading error.json failed for jobId: {}", jobId, e);
                                    setStatus(jobId, HttpStatus.NOT_FOUND);
                                }
                            }

                            if (!fatalIssuePresent) {
                                boolean ndjsonExists = files.anyMatch(file -> file.toString().endsWith(".ndjson"));

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
                        logger.error("Failed to initialize directory for jobId {}: {}", jobId, e.getMessage());
                        throw new RuntimeException("Failed to initialize job directory", e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic());  // Run this on a separate scheduler for I/O tasks
    }


    public Mono<Void> saveBatchToNDJSON(String jobId, Mono<PatientBatchWithConsent> batchMono) {
        Objects.requireNonNull(resultsDirPath, "resultsDirPath must not be null");
        Objects.requireNonNull(jobId, "jobId must not be null");
        Objects.requireNonNull(batchMono, "batchMono must not be null");


        return batchMono.flatMap(batch -> {
            Objects.requireNonNull(batch.bundles(), "batch.bundles() must not be null");
            logger.debug("Saving batch to {} {}", jobId, batch.keySet());

            return Mono.fromCallable(() -> {
                        Path jobDir = getJobDirectory(jobId);
                        Files.createDirectories(jobDir); // Ensure job directory exists

                        Path ndjsonFile;
                        if (batch.keySet().equals(Set.of("CORE"))) {
                            ndjsonFile = jobDir.resolve("core.ndjson");
                        } else {
                            ndjsonFile = jobDir.resolve(UUID.randomUUID() + ".ndjson");
                        }
                        Files.deleteIfExists(ndjsonFile);
                        return ndjsonFile;
                    })
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMap(ndjsonFile ->
                            Flux.fromIterable(batch.bundles().values()) // Process each bundle asynchronously
                                    .flatMap(bundle -> saveBundleToNDJSON(ndjsonFile, bundle.bundle().toFhirBundle()))
                                    .then()
                    );
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Void> saveErrorToJSON(String jobId, OperationOutcome outcome, HttpStatus status) {

        return Mono.fromRunnable(() -> {
                    Path jobDir = getJobDirectory(jobId);
                    try {
                        Files.createDirectories(jobDir); // Ensure job directory exists
                        String errorJson = fhirContext.newJsonParser().setPrettyPrint(false).encodeResourceToString(outcome);
                        Path errorFile;
                        errorFile = jobDir.resolve("error.json");
                        Files.writeString(errorFile, errorJson + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                        setStatus(jobId, status);
                    } catch (IOException e) {
                        logger.error("Failed to save errorFile for job {}: {}", jobId, e.getMessage());
                        throw new RuntimeException("Failed to save bundle", e);
                    }
                })
                .doOnError(e -> logger.error("Async write failed for job {}: {}", jobId, e.getMessage(), e))
                .subscribeOn(Schedulers.boundedElastic()).then();
    }

    public Mono<Void> saveBundleToNDJSON(Path ndjsonFile, Bundle bundle) {
        return Mono.fromRunnable(() -> {
                    try {
                        String bundleJson = fhirContext.newJsonParser().setPrettyPrint(false).encodeResourceToString(bundle);
                        Files.writeString(ndjsonFile, bundleJson + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    } catch (IOException e) {
                        logger.error("Failed to save bundle to {}: {}", ndjsonFile, e.getMessage());
                        throw new RuntimeException("Failed to save bundle", e);
                    }
                })
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
        Path errorFile = jobDir.resolve("error.json");

        try {
            return Files.readString(errorFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return fhirContext.newJsonParser().encodeResourceToString(OperationOutcomeCreator.createOperationOutcome(jobId, e));
        }
    }

    public Map<String, Object> loadBundleFromFileSystem(String jobId, String transactionTime) {
        Map<String, Object> response = new HashMap<>();
        try {
            Path jobDir = getJobDirectory(jobId);
            if (Files.exists(jobDir) && Files.isDirectory(jobDir)) {
                List<Map<String, String>> outputFiles = new ArrayList<>();
                List<Map<String, String>> deletedFiles = new ArrayList<>();
                List<Map<String, String>> errorFiles = new ArrayList<>();

                Files.list(jobDir).forEach(file -> {
                    String fileName = file.getFileName().toString();
                    String url = fileServerName + "/" + jobId + "/" + fileName;

                    Map<String, String> fileEntry = new HashMap<>();
                    fileEntry.put("url", url);

                    if (fileName.endsWith(".ndjson")) {
                        fileEntry.put("type", "NDJSON Bundle");
                        outputFiles.add(fileEntry);
                    } else if (fileName.equals("error.json")) {
                        fileEntry.put("type", "OperationOutcome");
                        errorFiles.add(fileEntry);
                    } else if (fileName.contains("del")) {
                        fileEntry.put("type", "Bundle");
                        deletedFiles.add(fileEntry);
                    }
                });

                response.put("transactionTime", transactionTime);
                response.put("request", hostname + "/fhir/$extract-data");
                response.put("requiresAccessToken", false);
                response.put("output", outputFiles);
                logger.debug("OutputFiles size {}", outputFiles.size());
                response.put("deleted", deletedFiles);
                response.put("error", errorFiles);
            } else {
                logger.warn("Job directory does not exist or is not a directory for jobId: {}", jobId);
            }
        } catch (IOException e) {
            logger.error("Failed to load bundles for jobId {}: {}", jobId, e.getMessage());
        }

        return response.isEmpty() ? null : response;
    }

}


