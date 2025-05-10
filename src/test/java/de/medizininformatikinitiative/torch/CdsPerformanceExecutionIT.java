package de.medizininformatikinitiative.torch;

import de.medizininformatikinitiative.torch.assertions.BundleAssert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * In order for the tests to work locally, a torch image must be built:
 * => mvn clean package -DskipTests && docker build -t torch:latest . && mvn -P blackbox-integration-tests -B verify
 */
@Testcontainers
public class CdsPerformanceExecutionIT {

    private static final Logger logger = LoggerFactory.getLogger(CdsPerformanceExecutionIT.class);
    private static final int NUM_BUNDLE_LOADS = 1000;

    private static BlackBoxIntegrationTestEnv environment;
    private static TorchClient torchClient;
    private static FileServerClient fileServerClient;

    @BeforeAll
    static void setUp() throws IOException {
        environment = new BlackBoxIntegrationTestEnv(logger);
        environment.start();

        torchClient = environment.torchClient();
        fileServerClient = environment.fileServerClient();
        var blazeClient = environment.blazeClient();

        logger.info("Uploading test data {} times...", NUM_BUNDLE_LOADS);
        var bundle = Files.readString(Path.of("target/Bundle-mii-exa-test-data-bundle.json"));
        Flux.fromStream(IntStream.range(0, NUM_BUNDLE_LOADS).boxed())
                .flatMap(i -> blazeClient.transact(bundle).flux())
                .blockLast();
    }

    @AfterAll
    static void tearDown() {
        environment.stop();
    }

    @Test
    public void testExamples() throws IOException {
        var statusUrl = torchClient.executeExtractData(TestUtils.loadCrtdl("CRTDL_test_it-kds-perf-crtdl.json"));
        // TODO: remove replacing /fhir if #220 is fixed
        var statusResponse = torchClient.pollStatus(statusUrl.replace("/fhir", "")).block();

        var coreBundles = statusResponse.coreBundleUrl().stream().flatMap(fileServerClient::fetchBundles).toList();
        var patientBundles = statusResponse.patientBundleUrls().stream().flatMap(fileServerClient::fetchBundles).toList();

        assertThat(coreBundles, BundleAssert.class).singleElement().containsNEntries(0);
        assertThat(patientBundles).hasSize(3 * NUM_BUNDLE_LOADS);
    }
}
