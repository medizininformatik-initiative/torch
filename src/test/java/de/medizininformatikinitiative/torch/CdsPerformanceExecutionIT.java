package de.medizininformatikinitiative.torch;

import de.medizininformatikinitiative.torch.assertions.BundleAssert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * In order for the tests to work locally, a torch image must be built:
 * => mvn clean package -DskipTests && docker build -t torch:latest . && mvn -P blackbox-integration-tests -B verify
 */
@Testcontainers
public class CdsPerformanceExecutionIT {

    private static final Logger logger = LoggerFactory.getLogger(CdsPerformanceExecutionIT.class);

    private static PerformanceIntegrationTestEnv environment;
    private static TorchClient torchClient;
    private static FileServerClient fileServerClient;

    @BeforeAll
    static void setUp() {
        environment = new PerformanceIntegrationTestEnv(logger);
        environment.start();

        torchClient = environment.torchClient();
        fileServerClient = environment.fileServerClient();
    }

    @AfterAll
    static void tearDown() {
        environment.stop();
    }

    @Test
    public void testWithoutReferences() throws IOException {
        var statusUrl = torchClient.executeExtractData(TestUtils.loadCrtdl("CRTDL_test_it-kds-perf-wo-ref.json")).block();
        assertThat(statusUrl).isNotNull();

        var statusResponse = torchClient.pollStatus(statusUrl).block();
        assertThat(statusResponse).isNotNull();

        var coreBundles = statusResponse.coreBundleUrl().stream().flatMap(fileServerClient::fetchBundles).toList();
        var patientBundles = statusResponse.patientBundleUrls().stream().flatMap(fileServerClient::fetchBundles).toList();

        assertThat(coreBundles, BundleAssert.class).singleElement().containsNEntries(0);
        assertThat(patientBundles).hasSize(25000);
    }

    @Test
    public void testWithReferences() throws IOException {
        var statusUrl = torchClient.executeExtractData(TestUtils.loadCrtdl("CRTDL_test_it-kds-perf-w-ref.json")).block();
        assertThat(statusUrl).isNotNull();

        var statusResponse = torchClient.pollStatus(statusUrl).block();
        assertThat(statusResponse).isNotNull();

        var coreBundles = statusResponse.coreBundleUrl().stream().flatMap(fileServerClient::fetchBundles).toList();
        var patientBundles = statusResponse.patientBundleUrls().stream().flatMap(fileServerClient::fetchBundles).toList();

        assertThat(coreBundles, BundleAssert.class).singleElement().containsNEntries(25000);
        assertThat(patientBundles).hasSize(25000);
    }
}
