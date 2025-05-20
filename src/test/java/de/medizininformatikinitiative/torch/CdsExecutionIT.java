package de.medizininformatikinitiative.torch;

import de.medizininformatikinitiative.torch.assertions.BundleAssert;
import org.hl7.fhir.r4.model.ResourceType;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static de.medizininformatikinitiative.torch.TestUtils.nodeFromValueString;
import static de.medizininformatikinitiative.torch.assertions.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * In order for the tests to work locally, a torch image must be built:
 * => mvn clean package -DskipTests && docker build -t torch:latest .
 */
@Testcontainers
public class CdsExecutionIT {

    private static final Logger logger = LoggerFactory.getLogger(CdsExecutionIT.class);

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

        logger.info("Uploading test data...");
        blazeClient.transact(Files.readString(Path.of("target/kds-testdata-2024.0.1/resources/Bundle-mii-exa-test-data-bundle.json")));
    }

    @AfterAll
    static void tearDown() {
        environment.stop();
    }

    @Test
    public void testExamples() throws IOException {
        var statusUrl = torchClient.executeExtractData(TestUtils.loadCrtdl("CRTDL_test_it-kds-crtdl.json"));

        var statusResponse = torchClient.pollStatus(statusUrl.replace("8080", String.valueOf(environment.getTorchPort())));
        var coreBundles = statusResponse.coreBundleUrl().stream().flatMap(fileServerClient::fetchBundles).toList();
        var patientBundles = statusResponse.patientBundleUrls().stream().flatMap(fileServerClient::fetchBundles).toList();

        assertThat(coreBundles, BundleAssert.class).singleElement().containsNEntries(0);
        assertThat(patientBundles).hasSize(3);

        // test if referenced IDs match the actual patient's IDs
        assertThat(patientBundles).allSatisfy(bundle -> assertThat(bundle).extractOnlyPatient().satisfies(patient ->
                assertThat(bundle).extractResourcesByType(ResourceType.Condition).allSatisfy(condition ->
                        assertThat(condition).extractChildrenStringsAt("subject.reference").hasSize(1).first().isEqualTo(patient.getId()))));

        // this is equivalent to 'never exists' (and an example how to filter by resource type)
        assertThat(patientBundles).allSatisfy(b -> assertThat(b).extractResourcesByType(ResourceType.Condition)
                .allSatisfy(c -> assertThat(c).extractElementsAt("bodySite").isEmpty()));

        // opposite of 'never exists'
        assertThat(patientBundles).allSatisfy(b -> assertThat(b).extractResourcesByType(ResourceType.Condition)
                .allSatisfy(
                        r -> assertThat(r).extractElementsAt("code").isNotEmpty()
                ));

        // all conditions in all patient bundles must have the given top fields
        assertThat(patientBundles).allSatisfy(b -> assertThat(b).extractResourcesByType(ResourceType.Condition)
                .allSatisfy(
                        r -> assertThat(r).extractTopElementNames()
                                .containsExactlyInAnyOrder("resourceType", "subject", "meta", "_recordedDate", "code", "id")));

        // condition with given id must have a data absent reason at its recordedDate (primitive type)
        assertThat(patientBundles).satisfiesOnlyOnce(bundle ->
                assertThat(bundle).extractResourceById("Condition", "mii-exa-test-data-patient-4-diagnose-1").isNotNull()
                        .hasDataAbsentReasonAt("recordedDate", "masked"));

        // condition with given id must only have this one code
        assertThat(patientBundles).satisfiesOnlyOnce(bundle ->
                assertThat(bundle).extractResourceById("Condition", "mii-exa-test-data-patient-1-diagnose-2").isNotNull()
                        .extractElementsAt("code.coding.code").containsExactly(nodeFromValueString("H67.1")));

        // condition with given id must have the given codes (and not fewer or more codes)
        assertThat(patientBundles).satisfiesOnlyOnce(bundle ->
                assertThat(bundle).extractResourceById("Condition", "mii-exa-test-data-patient-1-diagnose-1").isNotNull()
                        .extractElementsAt("code.coding.code")
                        .containsExactlyInAnyOrder(nodeFromValueString("B05.3"), nodeFromValueString("I29578"), nodeFromValueString("13420004")));

        assertThat(patientBundles).satisfiesOnlyOnce(bundle ->
                assertThat(bundle).extractResourceById("Condition", "mii-exa-test-data-patient-3-diagnose-1").isNotNull()
                        .extractElementsAt("code.coding.code")
                        .containsExactlyInAnyOrder(nodeFromValueString("C21.8"), nodeFromValueString("447886005"), nodeFromValueString("I29975"), nodeFromValueString("8140/3")));

        assertThat(patientBundles).satisfiesOnlyOnce(bundle ->
                assertThat(bundle).extractResourceById("Condition", "mii-exa-test-data-patient-4-diagnose-1").isNotNull()
                        .extractElementsAt("code.coding.code")
                        .containsExactly(nodeFromValueString("C16.9")));

        // test that IDs are properly formatted
        assertThat(patientBundles).allSatisfy(bundle ->
                assertThat(bundle).extractResourcesByType(ResourceType.Condition).allSatisfy(
                        r -> assertThat(r).extractChildrenStringsAt("subject.reference").satisfiesExactly(id -> assertThat(id).startsWith("Patient/"))
                ));
    }
}
