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

    private final Path resultsDirPath;
    private final IParser parser;
    private Duration duration;
    public ConcurrentHashMap<String, String> jobStatusMap = new ConcurrentHashMap<>();


    public ResultFileManager(String resultsDir, String duration, IParser parser) {
        this.resultsDirPath = Paths.get(resultsDir).toAbsolutePath();
        this.parser = parser;


        this.duration = Duration.parse(duration);


        logger.info(" Duration of persistence{}", this.duration);
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

    public void loadExistingResults() {
        try {
            Files.list(resultsDirPath)
                    .filter(Files::isDirectory)
                    .forEach(jobDir -> {
                        String jobId = jobDir.getFileName().toString();
                        Path bundleFilePath = jobDir.resolve("bundle.json");

                        if (Files.exists(bundleFilePath)) {

                                logger.info("Loaded existing job with jobId: {}", jobId);
                                jobStatusMap.put(jobId, "Completed");

                                logger.info("Status set {}",jobStatusMap.get(jobId));

                        } else {
                            logger.warn("No bundle.json found for jobId: {}", jobId);
                        }
                    });
        } catch (IOException e) {
            logger.error("Error loading existing results from {}: {}", resultsDirPath, e.getMessage());
        }
        logger.info("Status Map Size {}",getSize());
    }

    public Mono<Void> saveBundleToFileSystem(String jobId, Bundle bundle) {
        return Mono.fromRunnable(() -> {
                    logger.info("Started Saving {} ", jobId);
                    try {
                        Path jobDir = resultsDirPath.resolve(jobId);
                        Files.createDirectories(jobDir);

                        String bundleJson = parser.setPrettyPrint(true).encodeResourceToString(bundle);
                        Path bundleFile = jobDir.resolve("bundle.json");

                        Files.writeString(bundleFile, bundleJson, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    } catch (IOException e) {
                        logger.error("Failed to save bundle for jobId {}: {}", jobId, e.getMessage());
                        throw new RuntimeException("Failed to save bundle", e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())  // Run this on the boundedElastic scheduler
                .doOnSuccess(unused -> jobStatusMap.put(jobId, "Completed"))
                .then();  // Ensures Mono<Void> is returned
    }

    public void setStatus(String jobId, String status){
        jobStatusMap.put(jobId, status);
    }

    public String getStatus(String jobId){
        return jobStatusMap.get(jobId);
    }

    public int getSize(){
        return jobStatusMap.size();
    }




    public Map<String, Object> loadBundleFromFileSystem(String jobId) {
        Map<String, Object> response = new HashMap<>();
        try {
            Path jobDir = resultsDirPath.resolve(jobId);
            if (Files.exists(jobDir) && Files.isDirectory(jobDir)) {
                List<Map<String, String>> outputFiles = new ArrayList<>();
                List<Map<String, String>> deletedFiles = new ArrayList<>();
                List<Map<String, String>> errorFiles = new ArrayList<>();

                Files.list(jobDir).forEach(file -> {
                    if (file.toString().endsWith(".json")) {
                        String type = "Bundle"; // All files are bundles
                        String url = "https://example.com/output/" + file.getFileName().toString();

                        Map<String, String> fileEntry = new HashMap<>();
                        fileEntry.put("type", type);
                        fileEntry.put("url", url);

                        if (file.getFileName().toString().contains("err")) {
                            errorFiles.add(fileEntry);
                        } else if (file.getFileName().toString().contains("del")) {
                            deletedFiles.add(fileEntry);
                        } else {
                            outputFiles.add(fileEntry);
                        }
                    }
                });

                response.put("transactionTime", "2021-01-01T00:00:00Z");
                response.put("request", "https://example.com/fhir/Patient/$export?_type=Patient,Observation");
                response.put("requiresAccessToken", true);
                response.put("output", outputFiles);
                response.put("deleted", deletedFiles);
                response.put("error", errorFiles);
                response.put("extension", Collections.singletonMap("https://example.com/extra-property", true));
            } else {
                logger.warn("Job directory does not exist or is not a directory for jobId: {}", jobId);
            }
        } catch (IOException e) {
            logger.error("Failed to load bundles for jobId {}: {}", jobId, e.getMessage());
        }

        return response.isEmpty() ? null : response;
    }
}

