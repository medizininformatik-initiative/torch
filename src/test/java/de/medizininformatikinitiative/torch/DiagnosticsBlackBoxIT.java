package de.medizininformatikinitiative.torch;

import de.medizininformatikinitiative.torch.diagnostics.PipelineStage;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.PatientExclusionEvent;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.PatientExclusionStage;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.ResourceExclusionEvent;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.ResourceExclusionReason;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * In order for the tests to work locally, a torch image must be built:
 * => mvn clean package -DskipTests && docker build -t torch:latest .
 */
public class DiagnosticsBlackBoxIT {

    private static final Logger logger = LoggerFactory.getLogger(SpecificBlackBoxIT.class);

    private static final PipelineStage[] PER_BATCH_STAGES = Arrays.stream(PipelineStage.values())
            .filter(stage -> stage != PipelineStage.COHORT_QUERY)
            .toArray(PipelineStage[]::new);

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

        uploadTestData("src/test/resources/DiagnosticsBlackBoxIT/bundle.json");
        /*
            Structure of this bundle:
            pat1
            pat2
            pat3                    // has no consent at all
            pat4                    // gender other
            orga1->orga3
            orga2
            med1->orga1
            med2->orga2
            med3->orga3 			// (orga 3 does not exist)
            med4->orga1
            med5->orga3
            med-adm-1->pat1, med1, proc1
            med-adm-2->pat2, med2  // not consented (because has no effectiveDateTime)
            med-adm-3->pat4, med3, proc2, cond99
            med-adm-4->pat5, med3, proc3
            proc1 -> pat1
            proc2 -> pat4
            proc3 -> pat5
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
        // - notes pat-3 and pat-5 as patient exclusion because this patient has no consent
        // - notes med-adm-2 because it fails the consent window because it has no effectiveDateTime (but does not note
        //   pat-2 because only the resource and not the whole patient is thrown away)
        // - pat-4 is ignored because not part of cohort due to gender='other'

        var statusUrl = torchClient.executeExtractData(TestUtils.loadCrtdl("src/test/resources/DiagnosticsBlackBoxIT/CRTDL_consent.json")).block();
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
        assertThat(jobSummary.numCohortPatients()).isEqualTo(4);
        assertThat(jobSummary.numFinalPatients()).isEqualTo(2);
        assertThat(jobSummary.cohortQueryDurationMs()).isGreaterThanOrEqualTo(0);
        assertThat(jobSummary.durationSummaries().keySet()).containsExactlyInAnyOrder(PER_BATCH_STAGES);
        assertThat(jobSummary.durationSummaries().values()).allSatisfy(duration -> {
            assertThat(duration.averageMs()).isGreaterThanOrEqualTo(0);
            assertThat(duration.medianMs()).isGreaterThanOrEqualTo(0);
        });
        assertThat(jobSummary.patientSummaries()).containsExactlyInAnyOrderEntriesOf(Map.of(
                PatientExclusionStage.CASCADING_DELETE, 0,
                PatientExclusionStage.DIRECT_LOAD, 0,
                PatientExclusionStage.CONSENT_FETCH, 2));
        assertThat(jobSummary.resourceSummaries().keySet()).containsExactly("med-adm-group");
        assertThat(jobSummary.resourceSummaries().get("med-adm-group")).satisfies(groupSummary -> {
            assertThat(groupSummary.mustHaveExclusions()).isEmpty();
            assertThat(groupSummary.refNotFoundExclusions()).isEqualTo(0);
            assertThat(groupSummary.resOutsideBatchExclusions()).isEqualTo(0);
            assertThat(groupSummary.consentExclusions()).isEqualTo(1);
        });

        assertThat(exclusions.getPatientExclusions()).containsExactlyInAnyOrder(
                new PatientExclusionEvent(PatientExclusionStage.CONSENT_FETCH, "pat-3"),
                new PatientExclusionEvent(PatientExclusionStage.CONSENT_FETCH, "pat-5"));
        assertThat(exclusions.getResourceExclusions()).containsExactly(new ResourceExclusionEvent(ResourceExclusionReason.CONSENT,
                "med-adm-group", "MedicationAdministration/med-adm-2", "pat-2", ""));
    }

    @Test
    @DisplayName("Reference is Invalid. Marks the same medication as excluded twice at the reference handler, but with different patient-IDs.")
    void testMustHave_refHandler_duplicateCore_1() throws IOException {
        // - med-adm->med->orga
        // - pat2 and pat5 reference the same core med-3, so it gets resolved twice
        // - med-3 references orga-3, which does not exist
        // - so there are no valid groups at the reference handler, which marks it once at pat-4 and once at pat-5

        var statusUrl = torchClient.executeExtractData(TestUtils.loadCrtdl(
                "src/test/resources/DiagnosticsBlackBoxIT/CRTDL_must-have-ref-handler-duplicate-core-1.json")).block();
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
        assertThat(jobSummary.numCohortPatients()).isEqualTo(5);
        assertThat(exclusions.getResourceExclusions()).containsExactlyInAnyOrder(
                new ResourceExclusionEvent(ResourceExclusionReason.REFERENCE_NOT_FOUND,
                        "orga-group", "Organization/orga-3", "pat-4", ""),
                new ResourceExclusionEvent(ResourceExclusionReason.MUST_HAVE,
                        "med-group", "Medication/med-3", "pat-4", "Medication.manufacturer"),
                new ResourceExclusionEvent(ResourceExclusionReason.MUST_HAVE,
                        "med-group", "Medication/med-3", "pat-5", "Medication.manufacturer")
        );
    }

    @Test
    @DisplayName("Reference is valid but referenced resource is invalid. Marks the same medication as excluded twice at " +
            "the reference handler, but with different patient-IDs.")
    void testMustHave_refHandler_duplicateCore_2() throws IOException {
        // - med-adm->med->orga (orga being invalid)
        // - marks medication (core) but with pat-id
        // - same as first test, but here orga is invalid instead of the reference to the orga

        var statusUrl = torchClient.executeExtractData(TestUtils.loadCrtdl(
                "src/test/resources/DiagnosticsBlackBoxIT/CRTDL_must-have-ref-handler-duplicate-core-2.json")).block();
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
        assertThat(jobSummary.numCohortPatients()).isEqualTo(5);
        assertThat(exclusions.getResourceExclusions()).containsExactlyInAnyOrder(
                new ResourceExclusionEvent(ResourceExclusionReason.REFERENCE_NOT_FOUND,
                        "orga-group", "Organization/orga-3", "pat-4", ""),
                new ResourceExclusionEvent(ResourceExclusionReason.MUST_HAVE,
                        "med-group", "Medication/med-1", "pat-1", "Medication.manufacturer"),
                new ResourceExclusionEvent(ResourceExclusionReason.MUST_HAVE,
                        "med-group", "Medication/med-2", "pat-2", "Medication.manufacturer"),
                new ResourceExclusionEvent(ResourceExclusionReason.MUST_HAVE,
                        "med-group", "Medication/med-3", "pat-4", "Medication.manufacturer"),
                new ResourceExclusionEvent(ResourceExclusionReason.MUST_HAVE,
                        "med-group", "Medication/med-3", "pat-5", "Medication.manufacturer"));
    }

    @Test
    @DisplayName("Marks only the parent medication administrations because the must-have check in the reference handler " +
            "does not note exclusions currently.")
    void testMustHave_refHandler_duplicateCore_3() throws IOException {
        // - med-adm->med
        // - no medication fulfills the must-have check in referenceHandler.collectValidGroups
        // - this is not noted in the batch exclusions
        // - but since the medication themselves are must-have in the medication administrations, the administrations are being noted


        var statusUrl = torchClient.executeExtractData(TestUtils.loadCrtdl(
                "src/test/resources/DiagnosticsBlackBoxIT/CRTDL_must-have-ref-handler-duplicate-core-3.json")).block();
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
        assertThat(jobSummary.numCohortPatients()).isEqualTo(5);
        assertThat(exclusions.getResourceExclusions()).containsExactlyInAnyOrder(
                new ResourceExclusionEvent(ResourceExclusionReason.MUST_HAVE,
                        "med-adm-group", "MedicationAdministration/med-adm-1", "pat-1", "MedicationAdministration.medication[x]"),
                new ResourceExclusionEvent(ResourceExclusionReason.MUST_HAVE,
                        "med-adm-group", "MedicationAdministration/med-adm-2", "pat-2", "MedicationAdministration.medication[x]"),
                new ResourceExclusionEvent(ResourceExclusionReason.MUST_HAVE,
                        "med-adm-group", "MedicationAdministration/med-adm-3", "pat-4", "MedicationAdministration.medication[x]"),
                new ResourceExclusionEvent(ResourceExclusionReason.MUST_HAVE,
                        "med-adm-group", "MedicationAdministration/med-adm-4", "pat-5", "MedicationAdministration.medication[x]")
        );
    }

    @Test
    @DisplayName("Marks one organization twice but in different groups, and one multi reference organization only once, " +
            "because it is de-duplicated by being put into a set for further reference resolving.")
    void testMustHave_refHandler_duplicateCore_4() throws IOException {
        // - med->orga-level-1->orga-level-2 (orga ref to level 2 invalid, so level-1 is invalid)
        // - does not mark orga-1 twice, although it is referenced twice by medications (by med-1 and med-4)
        // - because: once med-1 and med-4 are resolved, that level is finished and returns the new references as set

        var statusUrl = torchClient.executeExtractData(TestUtils.loadCrtdl(
                "src/test/resources/DiagnosticsBlackBoxIT/CRTDL_must-have-ref-handler-duplicate-core-4.json")).block();
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
        assertThat(jobSummary.numCohortPatients()).isEqualTo(5);
        assertThat(exclusions.getResourceExclusions()).containsExactlyInAnyOrder(
                new ResourceExclusionEvent(ResourceExclusionReason.REFERENCE_NOT_FOUND,
                        "orga-group", "Organization/orga-3", "", ""),
                new ResourceExclusionEvent(ResourceExclusionReason.REFERENCE_NOT_FOUND,
                        "orga-group-2", "Organization/orga-3", "", ""),
                new ResourceExclusionEvent(ResourceExclusionReason.MUST_HAVE,
                        "orga-group", "Organization/orga-1", "", "Organization.partOf")
        );
    }

    @Test
    @DisplayName("Marks the same medication twice in the reference extraction step of the reference resolver, also with " +
            "different patient-IDs.")
    void testMustHave_refExtractor_duplicateCore() throws IOException {
        // - pat-A->coreX, pat-B->coreX, coreX->coreY (non-relative reference)
        // - the non-relative reference passes the ProfileMustHaveChecker in the ReferenceHandler
        // - in the next round, the non-relative reference is marked as must-have by the ref extractor though

        var statusUrl = torchClient.executeExtractData(TestUtils.loadCrtdl("src/test/resources/DiagnosticsBlackBoxIT/CRTDL_must-have-ref-extractor-duplicate-core.json")).block();
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
        assertThat(jobSummary.numCohortPatients()).isEqualTo(5);
        assertThat(exclusions.getResourceExclusions()).containsExactlyInAnyOrder(
                new ResourceExclusionEvent(ResourceExclusionReason.MUST_HAVE,
                        "med-group", "Medication/med-1", "pat-1", "Medication.ingredient.item[x]"),
                new ResourceExclusionEvent(ResourceExclusionReason.MUST_HAVE,
                        "med-group", "Medication/med-2", "pat-2", "Medication.ingredient.item[x]"),
                new ResourceExclusionEvent(ResourceExclusionReason.MUST_HAVE,
                        "med-group", "Medication/med-3", "pat-4", "Medication.ingredient.item[x]"),
                new ResourceExclusionEvent(ResourceExclusionReason.MUST_HAVE,
                        "med-group", "Medication/med-3", "pat-5", "Medication.ingredient.item[x]"));
    }


    @Test
    void testMustHave() throws IOException {
        // - notes pat-1 as patient exclusion at CASCADING_DELETE because it has a 'partOf', but this procedure is invalid
        //   which invalidates the medication-administration and due to this being also must-have, the patient is invalid
        // - notes pat-2 and pat-3 as patient exclusions at DIRECT_LOAD because both have no procedure
        // - notes med-adm-2 as resource exclusion due to MUST_HAVE because it has no 'partOf' at all, and med-adm-1
        //   also as MUST_HAVE (not CASCADING_DELETE): its 'partOf' points to proc-1, but proc-1's own must-have
        //   ('status') is already known to be violated by the time med-adm-1's reference to it is resolved, so the
        //   violation is discovered synchronously during reference resolution, not later during cascading delete
        // - only pat-5 survives

        var statusUrl = torchClient.executeExtractData(TestUtils.loadCrtdl("src/test/resources/DiagnosticsBlackBoxIT/CRTDL_must-have.json")).block();
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
        assertThat(jobSummary.numCohortPatients()).isEqualTo(4);
        assertThat(jobSummary.numFinalPatients()).isEqualTo(1);
        assertThat(jobSummary.cohortQueryDurationMs()).isGreaterThanOrEqualTo(0);
        assertThat(jobSummary.durationSummaries().keySet()).containsExactlyInAnyOrder(PER_BATCH_STAGES);
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
            assertThat(groupSummary.mustHaveExclusions()).containsExactly(Map.entry("MedicationAdministration.partOf", 2));
            assertThat(groupSummary.cascadingDeleteExclusions()).isEqualTo(0);
        });

        assertThat(exclusions.getPatientExclusions()).containsExactlyInAnyOrder(
                new PatientExclusionEvent(PatientExclusionStage.CASCADING_DELETE, "pat-1"),
                new PatientExclusionEvent(PatientExclusionStage.DIRECT_LOAD, "pat-2"),
                new PatientExclusionEvent(PatientExclusionStage.DIRECT_LOAD, "pat-3"));
        assertThat(exclusions.getResourceExclusions()).containsExactlyInAnyOrder(
                new ResourceExclusionEvent(ResourceExclusionReason.MUST_HAVE,
                        "med-adm-group", "MedicationAdministration/med-adm-2", "pat-2", "MedicationAdministration.partOf"),
                new ResourceExclusionEvent(ResourceExclusionReason.MUST_HAVE,
                        "med-adm-group", "MedicationAdministration/med-adm-1", "pat-1", "MedicationAdministration.partOf"));
    }

    @Test
    void testRefNotFound() throws IOException {
        // - notes no patient exclusions because nothing is must-have
        // - notes orga-3 as resource exclusion because it is referenced but does not exist in the bundle (works only
        //   because referential integrity is not fulfilled inside the bundle)

        var statusUrl = torchClient.executeExtractData(TestUtils.loadCrtdl("src/test/resources/DiagnosticsBlackBoxIT/CRTDL_ref-not-found.json")).block();
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
        assertThat(jobSummary.numCohortPatients()).isEqualTo(4);
        assertThat(jobSummary.numFinalPatients()).isEqualTo(4);
        assertThat(jobSummary.cohortQueryDurationMs()).isGreaterThanOrEqualTo(0);
        assertThat(jobSummary.durationSummaries().keySet()).containsExactlyInAnyOrder(PER_BATCH_STAGES);
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

    @Test
    void testCascadingDeleteOrphanedResource() throws IOException {
        // - pat-4 (gender 'other', isolated from the other scenarios' male/female cohort) has med-adm-3, whose
        //   'partOf' resolves to a fully valid proc-2 while its 'reasonReference' points to a Condition that doesn't
        //   exist at all
        // - med-adm-3's own must-have fields are all present (a reference field only checks presence, not whether the
        //   target resolves), so it passes DIRECT_LOAD; reference resolution is what discovers 'reasonReference'
        //   can't be resolved, and notes med-adm-3 as MUST_HAVE right there
        // - CRTDL_cascading-delete.json declares 'partOf' before 'reasonReference': reference resolution processes a
        //   resource's attributes in that order and stops at the first must-have violation, so proc-2 only gets
        //   registered as med-adm-3's valid child if 'partOf' is handled first - once med-adm-3 as a whole is
        //   invalidated, cascading delete finds proc-2 orphaned (its only parent link just died) and reports it as
        //   CASCADING_DELETE, with no attribute reference - cascading-delete exclusions never carry one
        // - notes pat-4 as a patient exclusion at CASCADING_DELETE once none of its med-adm-group resources survive

        var statusUrl = torchClient.executeExtractData(TestUtils.loadCrtdl("src/test/resources/DiagnosticsBlackBoxIT/CRTDL_cascading-delete.json")).block();
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
        assertThat(jobSummary.numCohortPatients()).isEqualTo(1);
        assertThat(jobSummary.numFinalPatients()).isEqualTo(0);
        assertThat(jobSummary.durationSummaries().keySet()).containsExactlyInAnyOrder(PER_BATCH_STAGES);
        assertThat(jobSummary.durationSummaries().values()).allSatisfy(duration -> {
            assertThat(duration.averageMs()).isGreaterThanOrEqualTo(0);
            assertThat(duration.medianMs()).isGreaterThanOrEqualTo(0);
        });
        assertThat(jobSummary.patientSummaries()).containsExactlyInAnyOrderEntriesOf(Map.of(
                PatientExclusionStage.CASCADING_DELETE, 1,
                PatientExclusionStage.DIRECT_LOAD, 0,
                PatientExclusionStage.CONSENT_FETCH, 0));
        // CASCADING_DELETE is recorded under the orphaned group's own id ('proc-group' for proc-2), not the id of
        // the group whose resource caused the invalidation ('med-adm-group')
        assertThat(jobSummary.resourceSummaries().keySet()).containsExactlyInAnyOrder("med-adm-group", "cond-group", "proc-group");
        assertThat(jobSummary.resourceSummaries().get("med-adm-group")).satisfies(groupSummary -> {
            assertThat(groupSummary.refNotFoundExclusions()).isEqualTo(0);
            assertThat(groupSummary.resOutsideBatchExclusions()).isEqualTo(0);
            assertThat(groupSummary.consentExclusions()).isEqualTo(0);
            assertThat(groupSummary.mustHaveExclusions()).containsExactly(Map.entry("MedicationAdministration.reasonReference", 1));
            assertThat(groupSummary.cascadingDeleteExclusions()).isEqualTo(0);
        });
        assertThat(jobSummary.resourceSummaries().get("cond-group")).satisfies(groupSummary -> {
            assertThat(groupSummary.refNotFoundExclusions()).isEqualTo(1);
            assertThat(groupSummary.resOutsideBatchExclusions()).isEqualTo(0);
            assertThat(groupSummary.consentExclusions()).isEqualTo(0);
            assertThat(groupSummary.mustHaveExclusions()).isEmpty();
            assertThat(groupSummary.cascadingDeleteExclusions()).isEqualTo(0);
        });
        assertThat(jobSummary.resourceSummaries().get("proc-group")).satisfies(groupSummary -> {
            assertThat(groupSummary.refNotFoundExclusions()).isEqualTo(0);
            assertThat(groupSummary.resOutsideBatchExclusions()).isEqualTo(0);
            assertThat(groupSummary.consentExclusions()).isEqualTo(0);
            assertThat(groupSummary.mustHaveExclusions()).isEmpty();
            assertThat(groupSummary.cascadingDeleteExclusions()).isEqualTo(1);
        });

        assertThat(exclusions.getPatientExclusions()).containsExactly(
                new PatientExclusionEvent(PatientExclusionStage.CASCADING_DELETE, "pat-4"));
        assertThat(exclusions.getResourceExclusions()).containsExactlyInAnyOrder(
                new ResourceExclusionEvent(ResourceExclusionReason.REFERENCE_NOT_FOUND,
                        "cond-group", "Condition/cond-99", "pat-4", ""),
                new ResourceExclusionEvent(ResourceExclusionReason.MUST_HAVE,
                        "med-adm-group", "MedicationAdministration/med-adm-3", "pat-4", "MedicationAdministration.reasonReference"),
                new ResourceExclusionEvent(ResourceExclusionReason.CASCADING_DELETE,
                        "proc-group", "Procedure/proc-2", "pat-4", ""));
    }
}
