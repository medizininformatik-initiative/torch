package de.medizininformatikinitiative.torch.util;


import org.hl7.fhir.r4.model.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ca.uhn.fhir.parser.IParser;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ResultFileManager {

    private static final Logger logger = LoggerFactory.getLogger(ResultFileManager.class);
    private static String operation;
    private final Path resultsDirPath;
    private final IParser parser;
    private Duration duration;
    private String hostname;
    public ConcurrentHashMap<String, String> jobStatusMap = new ConcurrentHashMap<>();


    public ResultFileManager(String resultsDir, String duration, IParser parser,String hostname) {
        this.resultsDirPath = Paths.get(resultsDir).toAbsolutePath();
        this.parser = parser;


        this.duration = Duration.parse(duration);
        this.hostname=hostname;

        logger.debug(" Duration of persistence{}", this.duration);
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

    public static void setOperation(String operation) {
        ResultFileManager.operation = operation;
    }

    public void loadExistingResults() {
        try {
            Files.list(resultsDirPath)
                    .filter(Files::isDirectory)
                    .forEach(jobDir -> {
                        String jobId = jobDir.getFileName().toString();
                        try {
                            // Find any .ndjson files in the job directory
                            boolean ndjsonExists = Files.list(jobDir)
                                    .anyMatch(file -> file.toString().endsWith(".ndjson"));

                            if (ndjsonExists) {
                                logger.debug("Loaded existing job with jobId: {}", jobId);
                                jobStatusMap.put(jobId, "Completed");

                                logger.debug("Status set {}", jobStatusMap.get(jobId));
                            } else {
                                logger.warn("No .ndjson file found for jobId: {}", jobId);
                            }
                        } catch (IOException e) {
                            logger.error("Error reading job directory {}: {}", jobDir, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            logger.error("Error loading existing results from {}: {}", resultsDirPath, e.getMessage());
        }
        logger.debug("Status Map Size {}", getSize());
    }

    public Mono<Void> initJobDir(String jobID) {
        return Mono.fromRunnable(() -> {
                    logger.debug("Initializing job directory for {} ", jobID);
                    try {
                        // Get the path to the job directory
                        Path jobDir = resultsDirPath.resolve(jobID);

                        // If the directory exists, delete it recursively
                        if (Files.exists(jobDir)) {
                            Files.walk(jobDir)
                                    .sorted(Comparator.reverseOrder())  // Sort to delete files first, then directories
                                    .forEach(path -> {
                                        try {
                                            Files.delete(path);
                                        } catch (IOException e) {
                                            logger.error("Failed to delete path {}: {}", path, e.getMessage());
                                        }
                                    });
                        }
                        logger.debug("Removed Dir {}", jobDir);
                        // Recreate the directory after cleanup
                        Files.createDirectories(jobDir);
                    } catch (IOException e) {
                        logger.error("Failed to initialize directory for jobId {}: {}", jobID, e.getMessage());
                        throw new RuntimeException("Failed to initialize job directory", e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())  // Run this on a separate scheduler for I/O tasks
                .then();  // Ensures Mono<Void> is returned
    }

    public Mono<Void> saveBundleToNDJSON(String jobID, String fileName, Bundle bundle) {
        return Mono.fromRunnable(() -> {
                    logger.info("Started Saving {} ", jobID);
                    try {
                        // Create or retrieve the job directory
                        Path jobDir = resultsDirPath.resolve(jobID);
                        Files.createDirectories(jobDir);

                        // Serialize the bundle to a JSON string without pretty printing (as per NDJSON)
                        String bundleJson = parser.setPrettyPrint(false).encodeResourceToString(bundle);

                        // Define the NDJSON file path (the file will have an .ndjson extension)
                        Path ndjsonFile = jobDir.resolve(fileName + ".ndjson");

                        // Append the serialized bundle to the NDJSON file followed by a newline
                        Files.writeString(ndjsonFile, bundleJson + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);

                    } catch (IOException e) {
                        logger.error("Failed to save bundle for jobId {}: {}", jobID, e.getMessage());
                        throw new RuntimeException("Failed to save bundle", e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())  // Run this on a separate scheduler for I/O tasks
                .then();  // Ensures Mono<Void> is returned
    }


    public void setStatus(String jobId, String status) {
        jobStatusMap.put(jobId, status);
    }

    public String getStatus(String jobId) {
        return jobStatusMap.get(jobId);
    }

    public int getSize() {
        return jobStatusMap.size();
    }


    public Map<String, Object> loadBundleFromFileSystem(String jobId, String requestUrl, String transactionTime) {
        Map<String, Object> response = new HashMap<>();
        try {
            Path jobDir = resultsDirPath.resolve(jobId);
            if (Files.exists(jobDir) && Files.isDirectory(jobDir)) {
                List<Map<String, String>> outputFiles = new ArrayList<>();
                List<Map<String, String>> deletedFiles = new ArrayList<>();
                List<Map<String, String>> errorFiles = new ArrayList<>();

                Files.list(jobDir).forEach(file -> {
                    String fileName = file.getFileName().toString();
                    String url = hostname+"/"+jobId+"/" + fileName;

                    Map<String, String> fileEntry = new HashMap<>();
                    fileEntry.put("url", url);

                    if (fileName.endsWith(".ndjson")) {
                        String type = determineFileType(fileName); // Helper method to determine the type
                        fileEntry.put("type", type);
                        outputFiles.add(fileEntry);
                    } else if (fileName.contains("err")) {
                        fileEntry.put("type", "OperationOutcome");
                        errorFiles.add(fileEntry);
                    } else if (fileName.contains("del")) {
                        fileEntry.put("type", "Bundle");
                        deletedFiles.add(fileEntry);
                    }
                });

                // Set the transactionTime and requestUrl into the response
                response.put("transactionTime", transactionTime);
                response.put("request", hostname+"/"+operation);
                response.put("requiresAccessToken", false);
                response.put("output", outputFiles);
                logger.debug("OutputFiles size {}",outputFiles.size());
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


    /**
     * Helper method to determine the type of file based on its name.
     * You can enhance this method to use actual file inspection if needed.
     */
    private String determineFileType(String fileName) {
        if (fileName.contains("patient")) {
            return "Patient";
        } else if (fileName.contains("observation")) {
            return "Observation";
        }
        // Add more logic as needed to handle other types
        return "Bundle"; // Default type if none matched
    }
}
