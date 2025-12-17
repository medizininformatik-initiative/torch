package de.medizininformatikinitiative.torch;

import de.medizininformatikinitiative.torch.assertions.Assertions;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.ResourceType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static de.medizininformatikinitiative.torch.assertions.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * In order for the tests to work locally, a torch image must be built:
 * => mvn clean package -DskipTests && docker build -t torch:latest .
 */
@Testcontainers
class SpecificBlackBoxIT {

    private static final Logger logger = LoggerFactory.getLogger(SpecificBlackBoxIT.class);

    private static BlackBoxIntegrationTestEnv environment;
    private static TorchClient torchClient;
    private static FhirClient blazeClient;
    private static FileServerClient fileServerClient;

    @BeforeAll
    static void setUp() {
        environment = new BlackBoxIntegrationTestEnv(logger);
        environment.start();

        torchClient = environment.torchClient();
        blazeClient = environment.blazeClient();
        fileServerClient = environment.fileServerClient();
    }

    @AfterAll
    static void tearDown() {
        environment.stop();
    }

    static void uploadTestData(String testName) throws IOException {
        logger.info("Uploading test data...for test {}", testName);
        var testDataFolder = "testdata/shorthand/fsh-generated/resources/";
        var testBundle = testDataFolder + "Bundle-test-" + testName + ".json";
        blazeClient.transact(Files.readString(Path.of(testBundle))).block();
    }

    public void executeStandardTests(List<Bundle> patientBundles) {

        assertThat(patientBundles).allSatisfy(bundle ->
                assertThat(bundle).allRequestsMatchResourceIdentity());

        // test if referenced IDs match the actual patient's IDs
        assertThat(patientBundles).allSatisfy(bundle -> Assertions.assertThat(bundle).extractOnlyPatient().satisfies(patient ->
                Assertions.assertThat(bundle).extractResourcesByType(ResourceType.Condition).allSatisfy(condition ->
                        Assertions.assertThat(condition).extractChildrenStringsAt("subject.reference").hasSize(1).first().isEqualTo(patient.getId()))));

        // test that IDs are properly formatted
        assertThat(patientBundles).allSatisfy(bundle ->
                assertThat(bundle).extractResourcesByType(ResourceType.Condition).allSatisfy(
                        r -> assertThat(r).extractChildrenStringsAt("subject.reference").satisfiesExactly(id -> assertThat(id).startsWith("Patient/"))
                ));
    }

    @Test
    void testDoubleRefResolve() throws IOException {

        /*
        Diabetes Condition .encounter -> Encounter
        Diabetes Encounter .diagnosis -> Conditions not diabetes
        One diagnosis not referenced by encounter

        CRTDL to extract Diabetes Condition, the Encounter, the diagnoses referenced by the Diabetes encounter
        and all resources linked to Patient via .subject

        Should extract all resources except the not from encounter referenced condition "torch-test-diag-enc-diag-diag-3"
        All resources should link to the patient
         */

        var testName = "diag-enc-diag";
        uploadTestData(testName);

        var statusUrl = torchClient.executeExtractData(TestUtils.loadCrtdl("src/test/resources/CrtdlItTests/CRTDL_test_" + testName + ".json")).block();
        assertThat(statusUrl).isNotNull();

        var statusResponse = torchClient.pollStatus(statusUrl).block();
        assertThat(statusResponse).isNotNull();

        var coreBundles = statusResponse.coreBundleUrl().stream().flatMap(fileServerClient::fetchBundles).toList();
        var patientBundles = statusResponse.patientBundleUrls().stream().flatMap(fileServerClient::fetchBundles).toList();

        assertThat(coreBundles).isEmpty();
        assertThat(patientBundles).hasSize(1);

        executeStandardTests(patientBundles);

        // all conditions in all patient bundles must have the given top fields
        assertThat(patientBundles).allSatisfy(b -> assertThat(b).extractResourcesByType(ResourceType.Condition)
                .allSatisfy(
                        r -> assertThat(r).extractTopElementNames()
                                .containsExactlyInAnyOrder("id", "resourceType", "meta", "clinicalStatus", "verificationStatus", "code", "subject", "recordedDate", "encounter")));

        // diab encounter included
        assertThat(patientBundles).satisfiesOnlyOnce(bundle ->
                assertThat(bundle).extractResourceById("Encounter", "torch-test-diag-enc-diag-enc-1").isNotNull());

        // diab condition included
        assertThat(patientBundles).satisfiesOnlyOnce(bundle ->
                assertThat(bundle).extractResourceById("Condition", "torch-test-diag-enc-diag-diag-1").isNotNull());

        // other linked condition included
        assertThat(patientBundles).satisfiesOnlyOnce(bundle ->
                assertThat(bundle).extractResourceById("Condition", "torch-test-diag-enc-diag-diag-2").isNotNull());

        // other not linked condition not included
        assertThat(patientBundles).noneSatisfy(bundle ->
                assertThat(bundle).extractResourceById("Condition", "torch-test-diag-enc-diag-diag-3"));


        // condition with given id must have a data absent reason at its recordedDate (primitive type)
        /*assertThat(patientBundles).satisfiesOnlyOnce(bundle ->
                assertThat(bundle).extractResourceById("Condition", "mii-exa-test-data-patient-4-diagnose-1").isNotNull()
                        .hasDataAbsentReasonAt("recordedDate", "masked"));

         */
    }

    @Test
    void testMustHaveResolve() throws IOException {
        var testName = "diag-no-enc-diag";
        uploadTestData(testName);
        var statusUrl = torchClient.executeExtractData(TestUtils.loadCrtdl("src/test/resources/CrtdlItTests/CRTDL_test_" + testName + ".json")).block();
        assertThat(statusUrl).isNotNull();

        var statusResponse = torchClient.pollStatus(statusUrl).block();
        assertThat(statusResponse).isNotNull();

        var coreBundles = statusResponse.coreBundleUrl().stream().flatMap(fileServerClient::fetchBundles).toList();
        var patientBundles = statusResponse.patientBundleUrls().stream().flatMap(fileServerClient::fetchBundles).toList();

        assertThat(coreBundles).isEmpty();
        assertThat(patientBundles).hasSize(1);

        executeStandardTests(patientBundles);
    }

    @AfterEach
    void cleanup() {
        blazeClient.deleteResources(Set.of("List", "Condition", "Encounter", "Patient"));
    }
}
