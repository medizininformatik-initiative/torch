package de.medizininformatikinitiative.torch.util;



import org.hl7.fhir.r4.model.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import ca.uhn.fhir.parser.IParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Period;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class ResultFileManager {

    private static final Logger logger = LoggerFactory.getLogger(ResultFileManager.class);

    private final Path resultsDirPath;
    private final IParser parser;
    private Duration duration;

    public ResultFileManager(@Value("${torch.results.dir}") String resultsDir,String duration, IParser parser) {
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
    }

    public void loadExistingResults(ConcurrentHashMap<String, String> jobStatusMap, ConcurrentHashMap<String, Bundle> jobResultMap) {
        try {
            Files.list(resultsDirPath)
                    .filter(Files::isDirectory)
                    .forEach(jobDir -> {
                        String jobId = jobDir.getFileName().toString();
                        Path bundleFilePath = jobDir.resolve("bundle.json");

                        if (Files.exists(bundleFilePath)) {
                            try {
                                String bundleJson = Files.readString(bundleFilePath);
                                Bundle bundle = parser.parseResource(Bundle.class, bundleJson);
                                jobStatusMap.put(jobId, "Completed");
                                jobResultMap.put(jobId, bundle);
                                logger.info("Loaded existing job with jobId: {}", jobId);
                            } catch (IOException e) {
                                logger.error("Failed to load bundle for jobId {}: {}", jobId, e.getMessage());
                            }
                        } else {
                            logger.warn("No bundle.json found for jobId: {}", jobId);
                        }
                    });
        } catch (IOException e) {
            logger.error("Error loading existing results from {}: {}", resultsDirPath, e.getMessage());
        }
    }

    public void saveBundleToFileSystem(String jobId, Bundle finalBundle, ConcurrentHashMap<String, String> jobStatusMap) {
        logger.info("Started Saving {} ", jobId);
        try {
            Path jobDir = resultsDirPath.resolve(jobId);
            Files.createDirectories(jobDir);

            String bundleJson = parser.setPrettyPrint(true).encodeResourceToString(finalBundle);
            Path bundleFile = jobDir.resolve("bundle.json");

            Files.writeString(bundleFile, bundleJson, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            jobStatusMap.put(jobId, "Completed");
        } catch (IOException e) {
            logger.error("Failed to save bundle for jobId {}: {}", jobId, e.getMessage());
            throw new RuntimeException("Failed to save bundle", e);
        }
    }

    public String loadBundleFromFileSystem(String jobId) {
        try {
            Path bundleFile = resultsDirPath.resolve(jobId).resolve("bundle.json");
            if (Files.exists(bundleFile)) {
                return Files.readString(bundleFile);
            } else {
                logger.warn("Bundle file does not exist for jobId: {}", jobId);
            }
        } catch (IOException e) {
            logger.error("Failed to load bundle for jobId {}: {}", jobId, e.getMessage());
        }
        return null;
    }
}

