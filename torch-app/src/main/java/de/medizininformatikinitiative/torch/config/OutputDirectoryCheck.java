package de.medizininformatikinitiative.torch.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
@Profile("!test")
public class OutputDirectoryCheck implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(OutputDirectoryCheck.class);

    private final Path outputDir;

    @Autowired
    public OutputDirectoryCheck(TorchProperties torchProperties) {
        this(Paths.get(torchProperties.results().dir()).toAbsolutePath());
    }

    OutputDirectoryCheck(Path outputDir) {
        this.outputDir = outputDir;
    }

    @Override
    public void run(ApplicationArguments args) {
        logger.info("Checking output directory is writable: {}", outputDir);
        try {
            Files.createDirectories(outputDir);
            Path probe = outputDir.resolve(".write-check");
            Files.deleteIfExists(probe);
            Files.createFile(probe);
            Files.delete(probe);
            logger.info("Output directory is writable: {}", outputDir);
        } catch (IOException e) {
            logger.warn("Output directory is not writable: {} — jobs will fail to write results. " +
                    "Check ownership and permissions (torch runs as uid 1000). Error: {}", outputDir, e.getMessage());
        }
    }
}
