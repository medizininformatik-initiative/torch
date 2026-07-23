package de.medizininformatikinitiative.torch;

import de.medizininformatikinitiative.torch.diagnostics.PipelineStage;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.PatientExclusionEvent;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.PatientExclusionStage;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.ResourceExclusionEvent;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.ResourceExclusionReason;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * In order for the tests to work locally, a torch image must be built:
 * => mvn clean package -DskipTests && docker build -t torch:latest .
 */
public class DiagnosticsBlackBoxIT {

    private static final Logger logger = LoggerFactory.getLogger(SpecificBlackBoxIT.class);

    private static BlackBoxIntegrationTestEnv environment;
    private static TorchClient torchClient;
    private static FhirClient blazeClient;
    private static FileServerClient fileServerClient;

    @BeforeAll
    static void setUp() throws IOException {
        environment = new BlackBoxIntegrationTestEnv(logger, false);
        environment.start();

        torchClient = environment.torchClient();
        blazeClient = environment.blazeClient();
        fileServerClient = environment.fileServerClient();

        uploadTestData("src/test/resources/DirectLoadBlackBoxIT/bundle.json");
        /*
            Structure of this bundle:
            pat1
            pat2
            pat3                    // has no consent at all
            orga1
            orga2
            med1->orga1
            med2->orga2
            med3->orga3 			// (orga 3 does not exist)
            med-adm-1->pat1, med-1, proc1
            med-adm-2->pat2, med-2  // not consented (because has no effectiveDateTime)
            proc1 -> pat1
            cons1 -> pat1
            cons2 -> pat2
         */
    }

    @AfterAll
    static void tearDown() {
        environment.stop();
    }

    static void uploadTestData(String bundleFilePath) throws IOException {
        logger.info("Uploading test data...for test {}", bundleFilePath);
        blazeClient.transact(Files.readString(Path.of(bundleFilePath))).block();
    }


    @Test
    void testConsentViolation() throws IOException {
        // - notes pat-3 as patient exclusion because this patient has no consent
        // - notes med-adm-2 because it fails the consent window because it has no effectiveDateTime (but does not note
        //   pat-2 because only the resource and not the whole patient is thrown away)

        var statusUrl = torchClient.executeExtractData(TestUtils.loadCrtdl("src/test/resources/DirectLoadBlackBoxIT/CRTDL_consent.json")).block();
        assertThat(statusUrl).isNotNull();

        var statusResponse = torchClient.pollStatus(statusUrl).block();
        assertThat(statusResponse).isNotNull();

        var optionalSummary = statusResponse.jobSummaryUrl().map(fileServerClient::fetchJobSummary);
        assertThat(optionalSummary).isPresent();

        var optionalPatientExclusions = statusResponse.patientExclusionsUrl();
        var optionalResourceExclusions = statusResponse.resourceExclusionsUrl();
        assertThat(optionalPatientExclusions).isPresent();
        assertThat(optionalResourceExclusions).isPresent();

        var exclusions = fileServerClient.fetchExclusions(optionalPatientExclusions.get(), optionalResourceExclusions.get());

        var jobSummary = optionalSummary.get();
        assertThat(jobSummary.numCohortPatients()).isEqualTo(3);
        assertThat(jobSummary.numFinalPatients()).isEqualTo(2);
        assertThat(jobSummary.durationSummaries().keySet()).containsExactlyInAnyOrder(PipelineStage.values());
        assertThat(jobSummary.durationSummaries().values()).allSatisfy(duration -> {
            assertThat(duration.averageMs()).isGreaterThanOrEqualTo(0);
            assertThat(duration.medianMs()).isGreaterThanOrEqualTo(0);
        });
        assertThat(jobSummary.patientSummaries()).containsExactlyInAnyOrderEntriesOf(Map.of(
                PatientExclusionStage.CASCADING_DELETE, 0,
                PatientExclusionStage.DIRECT_LOAD, 0,
                PatientExclusionStage.CONSENT_FETCH, 1));
        assertThat(jobSummary.resourceSummaries().keySet()).containsExactly("med-adm-group");
        assertThat(jobSummary.resourceSummaries().get("med-adm-group")).satisfies(groupSummary -> {
            assertThat(groupSummary.mustHaveExclusions()).isEmpty();
            assertThat(groupSummary.refNotFoundExclusions()).isEqualTo(0);
            assertThat(groupSummary.resOutsideBatchExclusions()).isEqualTo(0);
            assertThat(groupSummary.consentExclusions()).isEqualTo(1);
        });

        assertThat(exclusions.getPatientExclusions()).containsExactly(new PatientExclusionEvent(PatientExclusionStage.CONSENT_FETCH,
                "pat-3"));
        assertThat(exclusions.getResourceExclusions()).containsExactly(new ResourceExclusionEvent(ResourceExclusionReason.CONSENT,
                "med-adm-group", "MedicationAdministration/med-adm-2", "pat-2", ""));
    }

    @Test
    void testMustHave() throws IOException {
        // - notes pat-1 as patient exclusion at CASCADING_DELETE because it has a 'partOf', but this procedure is invalid
        //   which invalidates the medication-administration and due to this being also must-have, the patient is invalid
        // - notes pat-2 and pat-3 as patient exclusions at DIRECT_LOAD because both have no procedure
        // - notes only med-adm-2 as resource exclusion due to MUST_HAVE because it has no 'partOf', whereas the med-adm-1
        //   does have a 'partOf', which gets only later excluded during cascading delete, where cannot be explicitly
        //   marked as resource exclusion

        var statusUrl = torchClient.executeExtractData(TestUtils.loadCrtdl("src/test/resources/DirectLoadBlackBoxIT/CRTDL_must-have.json")).block();
        assertThat(statusUrl).isNotNull();

        var statusResponse = torchClient.pollStatus(statusUrl).block();
        assertThat(statusResponse).isNotNull();

        var optionalSummary = statusResponse.jobSummaryUrl().map(fileServerClient::fetchJobSummary);
        assertThat(optionalSummary).isPresent();

        var optionalPatientExclusions = statusResponse.patientExclusionsUrl();
        var optionalResourceExclusions = statusResponse.resourceExclusionsUrl();
        assertThat(optionalPatientExclusions).isPresent();
        assertThat(optionalResourceExclusions).isPresent();

        var exclusions = fileServerClient.fetchExclusions(optionalPatientExclusions.get(), optionalResourceExclusions.get());

        var jobSummary = optionalSummary.get();
        assertThat(jobSummary.numCohortPatients()).isEqualTo(3);
        assertThat(jobSummary.numFinalPatients()).isEqualTo(0);
        assertThat(jobSummary.durationSummaries().keySet()).containsExactlyInAnyOrder(PipelineStage.values());
        assertThat(jobSummary.durationSummaries().values()).allSatisfy(duration -> {
            assertThat(duration.averageMs()).isGreaterThanOrEqualTo(0);
            assertThat(duration.medianMs()).isGreaterThanOrEqualTo(0);
        });
        assertThat(jobSummary.patientSummaries()).containsExactlyInAnyOrderEntriesOf(Map.of(
                PatientExclusionStage.CASCADING_DELETE, 1,
                PatientExclusionStage.DIRECT_LOAD, 2,
                PatientExclusionStage.CONSENT_FETCH, 0));
        assertThat(jobSummary.resourceSummaries().keySet()).containsExactly("med-adm-group");
        assertThat(jobSummary.resourceSummaries().get("med-adm-group")).satisfies(groupSummary -> {
            assertThat(groupSummary.refNotFoundExclusions()).isEqualTo(0);
            assertThat(groupSummary.resOutsideBatchExclusions()).isEqualTo(0);
            assertThat(groupSummary.consentExclusions()).isEqualTo(0);
            assertThat(groupSummary.mustHaveExclusions()).containsExactly(Map.entry("MedicationAdministration.partOf", 1));
        });

        assertThat(exclusions.getPatientExclusions()).containsExactlyInAnyOrder(
                new PatientExclusionEvent(PatientExclusionStage.CASCADING_DELETE, "pat-1"),
                new PatientExclusionEvent(PatientExclusionStage.DIRECT_LOAD, "pat-2"),
                new PatientExclusionEvent(PatientExclusionStage.DIRECT_LOAD, "pat-3"));
        assertThat(exclusions.getResourceExclusions()).containsExactly(new ResourceExclusionEvent(ResourceExclusionReason.MUST_HAVE,
                "med-adm-group", "MedicationAdministration/med-adm-2", "pat-2", "MedicationAdministration.partOf"));
    }

    @Test
    void testRefNotFound() throws IOException {
        // - notes no patient exclusions because nothing is must-have
        // - notes orga-3 as resource exclusion because it is referenced but does not exist in the bundle (works only
        //   because referential integrity is not fulfilled inside the bundle)

        var statusUrl = torchClient.executeExtractData(TestUtils.loadCrtdl("src/test/resources/DirectLoadBlackBoxIT/CRTDL_ref-not-found.json")).block();
        assertThat(statusUrl).isNotNull();

        var statusResponse = torchClient.pollStatus(statusUrl).block();
        assertThat(statusResponse).isNotNull();

        var optionalSummary = statusResponse.jobSummaryUrl().map(fileServerClient::fetchJobSummary);
        assertThat(optionalSummary).isPresent();

        var optionalPatientExclusions = statusResponse.patientExclusionsUrl();
        var optionalResourceExclusions = statusResponse.resourceExclusionsUrl();
        assertThat(optionalPatientExclusions).isPresent();
        assertThat(optionalResourceExclusions).isPresent();

        var exclusions = fileServerClient.fetchExclusions(optionalPatientExclusions.get(), optionalResourceExclusions.get());

        var jobSummary = optionalSummary.get();
        assertThat(jobSummary.numCohortPatients()).isEqualTo(3);
        assertThat(jobSummary.numFinalPatients()).isEqualTo(3);
        assertThat(jobSummary.durationSummaries().keySet()).containsExactlyInAnyOrder(PipelineStage.values());
        assertThat(jobSummary.durationSummaries().values()).allSatisfy(duration -> {
            assertThat(duration.averageMs()).isGreaterThanOrEqualTo(0);
            assertThat(duration.medianMs()).isGreaterThanOrEqualTo(0);
        });
        assertThat(jobSummary.patientSummaries()).containsExactlyInAnyOrderEntriesOf(Map.of(
                PatientExclusionStage.CASCADING_DELETE, 0,
                PatientExclusionStage.DIRECT_LOAD, 0,
                PatientExclusionStage.CONSENT_FETCH, 0));
        assertThat(jobSummary.resourceSummaries().keySet()).containsExactly("orga-group");
        assertThat(jobSummary.resourceSummaries().get("orga-group")).satisfies(groupSummary -> {
            assertThat(groupSummary.refNotFoundExclusions()).isEqualTo(1);
            assertThat(groupSummary.resOutsideBatchExclusions()).isEqualTo(0);
            assertThat(groupSummary.consentExclusions()).isEqualTo(0);
            assertThat(groupSummary.mustHaveExclusions()).isEmpty();
        });

        assertThat(exclusions.getPatientExclusions()).isEmpty();
        assertThat(exclusions.getResourceExclusions()).containsExactly(new ResourceExclusionEvent(ResourceExclusionReason.REFERENCE_NOT_FOUND,
                "orga-group", "Organization/orga-3", "", ""));
    }
}
