package de.medizininformatikinitiative.torch;

import de.medizininformatikinitiative.torch.assertions.Assertions;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.ResourceType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

/**
 * In order for the tests to work locally, a torch image must be built:
 * => mvn clean package -DskipTests && docker build -t torch:latest .
 * <p>
 * If running on windows make sure to set line separators to `LF` instead of `CRLF` in the `start-nginx.sh` and the
 * `docker-entrypoint.sh`.
 */
@Testcontainers
class ReferenceResolveBlackBoxIT {

    private static final Logger logger = LoggerFactory.getLogger(ReferenceResolveBlackBoxIT.class);

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

    static void uploadTestData(String bundleFilePath) throws IOException {
        logger.info("Uploading test data...for test {}", bundleFilePath);
        blazeClient.transact(Files.readString(Path.of(bundleFilePath))).block();
    }

    @Nested
    class BasicTests {
        @BeforeAll
        static void init() throws IOException {
            uploadTestData("testdata/shorthand/fsh-generated/resources/Bundle-test-diag-enc-diag.json");
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
        void nestedRefResolve() throws IOException {
            /*
            Diabetes Condition .encounter -> Encounter
            Diabetes Encounter .diagnosis -> Conditions not diabetes
            One diagnosis not referenced by encounter

            CRTDL to extract Diabetes Condition, the Encounter, the diagnoses referenced by the Diabetes encounter
            and all resources linked to Patient via .subject

            Should extract all resources except the not from encounter referenced condition "torch-test-diag-enc-diag-diag-3"
            All resources should link to the patient
             */

            var statusUrl = torchClient.executeExtractData(TestUtils.loadCrtdl("src/test/resources/CrtdlItTests/CRTDL_test_diag-enc-diag-ref-backbone.json")).block();
            assertThat(statusUrl).isNotNull();

            var statusResponse = torchClient.pollStatus(statusUrl).block();
            assertThat(statusResponse).isNotNull();

            var coreBundles = statusResponse.coreBundleUrl().stream().flatMap(fileServerClient::fetchBundles).toList();
            var patientBundles = statusResponse.patientBundleUrls().stream().flatMap(fileServerClient::fetchBundles).toList();

            executeStandardTests(patientBundles);

            assertThat(coreBundles).isEmpty();
            assertThat(patientBundles).hasSize(1);

            assertThat(patientBundles).allSatisfy(b -> assertThat(b).extractResourcesByType(ResourceType.Provenance)
                    .allSatisfy(

                            r -> assertThat(r).extractTopElementNames()
                                    .containsExactlyInAnyOrder("resourceType", "id", "target", "recorded", "occurredPeriod", "agent", "entity")));

            // all Encounter in all patient bundles must have the given top fields and have at least one reference in diagnosis.condition
            assertThat(patientBundles).allSatisfy(b -> assertThat(b).extractResourcesByType(ResourceType.Encounter)
                    .allSatisfy(encounter -> assertThat(encounter).extractTopElementNames().containsExactlyInAnyOrder("resourceType", "id", "meta", "status", "class", "subject", "diagnosis")));

            assertThat(patientBundles).satisfiesOnlyOnce(bundle -> assertThat(bundle).extractResourceById("Encounter", "torch-test-diag-enc-diag-enc-1").isNotNull());

            assertThat(patientBundles).satisfiesOnlyOnce(bundle -> assertThat(bundle).extractResourceById("Encounter", "torch-test-diag-enc-diag-enc-1").extractElementsAt("Encounter.diagnosis.condition.reference")
                    .containsExactly(
                            TestUtils.nodeFromValueString("Condition/torch-test-diag-enc-diag-diag-1"),
                            TestUtils.nodeFromValueString("Condition/torch-test-diag-enc-diag-diag-2")
                    ));
        }

        @AfterAll
        static void cleanup() {
            blazeClient.deleteResources(Set.of("List", "Condition", "Encounter", "Patient"));
        }
    }


    @Nested
    class FilterTest {
        @Nested
        class TestBundle1 {
            /*
            src/test/resources/ReferenceResolveBlackBoxIT/bundle-1.json contains:

            - pat-1
            - pat 2
            - orga-1            // all organizations are "includeReferenceOnly=true" in the CRTDLs
            - orga-2
            - orga-3
            - subst-1
            - subst-2           // this substance does not satisfy the (first) substance filter
            - med-1 -> orga-1
            - med-2 -> orga-2   // this medication does not satisfy the medication filter
            - med-3 -> orga-3   // this medication is unreferenced
            - med-4 -> subst-1; -> orga-3  // this medication is unreferenced
            - med-5 -> subst-2; -> orga-3  // this medication is unreferenced
            - med-adm-1 -> pat-1; -> med-1
            - med-adm-2 -> pat-2; -> med-1

            - enc-1 -> pat-1; -> cond-1
            - cond-1 -> pat-1           // this encounter does not satisfy the (first) encounter filter
             */

            @BeforeAll
            static void init() throws IOException {
                uploadTestData("src/test/resources/ReferenceResolveBlackBoxIT/bundle-1.json");
            }

            @Test
            void testWithoutFilter () throws IOException {
            // should contain all resources of the bundle

            var statusUrl = torchClient.executeExtractData(TestUtils.loadCrtdl("src/test/resources/ReferenceResolveBlackBoxIT/crtdl-without-filter.json")).block();
            assertThat(statusUrl).isNotNull();

            var statusResponse = torchClient.pollStatus(statusUrl).block();
            assertThat(statusResponse).isNotNull();

            var coreBundles = statusResponse.coreBundleUrl().stream().flatMap(fileServerClient::fetchBundles).toList();
            var patientBundles = statusResponse.patientBundleUrls().stream().flatMap(fileServerClient::fetchBundles).toList();

            assertThat(coreBundles).hasSize(1);
            var coreBundle = coreBundles.getFirst();
            assertThat(coreBundle).extractResourcesByType(ResourceType.Organization).hasSize(3);
            assertThat(coreBundle).extractResourcesByType(ResourceType.Medication).hasSize(5);
            assertThat(coreBundle).extractResourcesByType(ResourceType.Substance).hasSize(2);


            assertThat(patientBundles).hasSize(2);
            assertThat(patientBundles).allSatisfy(bundle -> assertThat(bundle).extractResourcesByType(ResourceType.MedicationAdministration).hasSize(1));

            assertThat(patientBundles).allSatisfy(bundle -> assertThat(bundle).extractOnlyPatient().satisfies(patient ->
                    assertThat(bundle).extractResourcesByType(ResourceType.MedicationAdministration).allSatisfy(condition ->
                            assertThat(condition).extractChildrenStringsAt("subject.reference").hasSize(1).first().isEqualTo(patient.getId()))));
        }

            @Test
            void testWithMedicationFilter () throws IOException {
                // should not contain med-2, orga-2 and med-4

                var statusUrl = torchClient.executeExtractData(TestUtils.loadCrtdl("src/test/resources/ReferenceResolveBlackBoxIT/crtdl-with-medication-filter.json")).block();
                assertThat(statusUrl).isNotNull();

                var statusResponse = torchClient.pollStatus(statusUrl).block();
                assertThat(statusResponse).isNotNull();

                var coreBundles = statusResponse.coreBundleUrl().stream().flatMap(fileServerClient::fetchBundles).toList();
                var patientBundles = statusResponse.patientBundleUrls().stream().flatMap(fileServerClient::fetchBundles).toList();

                assertThat(coreBundles).hasSize(1);
                var coreBundle = coreBundles.getFirst();
                assertThat(coreBundle).extractResourcesByType(ResourceType.Organization).hasSize(2);
                assertThat(coreBundle).extractResourcesByType(ResourceType.Medication).hasSize(4);
                assertThat(coreBundle).extractResourcesByType(ResourceType.Substance).hasSize(2);

                assertThat(coreBundle).extractResourceById("Medication", "med-1").isNotNull();
                assertThat(coreBundle).extractResourceById("Medication", "med-3").isNotNull();
                assertThat(coreBundle).extractResourceById("Medication", "med-4").isNotNull();
                assertThat(coreBundle).extractResourceById("Medication", "med-5").isNotNull();
                assertThatThrownBy(() -> Assertions.assertThat(coreBundle).extractResourceById("Medication", "med-2"))
                        .hasMessage("Expected bundle to contain resource of type Medication and with id med-2, but it could not be found");

                assertThat(coreBundle).extractResourceById("Organization", "orga-1").isNotNull();
                assertThat(coreBundle).extractResourceById("Organization", "orga-3").isNotNull();
                assertThatThrownBy(() -> Assertions.assertThat(coreBundle).extractResourceById("Organization", "orga-2"))
                        .hasMessage("Expected bundle to contain resource of type Organization and with id orga-2, but it could not be found");


                assertThat(patientBundles).hasSize(2);
                assertThat(patientBundles).allSatisfy(bundle -> assertThat(bundle).extractResourcesByType(ResourceType.MedicationAdministration).hasSize(1));

                assertThat(patientBundles).allSatisfy(bundle -> assertThat(bundle).extractOnlyPatient().satisfies(patient ->
                        assertThat(bundle).extractResourcesByType(ResourceType.MedicationAdministration).allSatisfy(condition ->
                                assertThat(condition).extractChildrenStringsAt("subject.reference").hasSize(1).first().isEqualTo(patient.getId()))));
            }

                @Test
                void testWithMedicationFilterAndIncludeRefOnly () throws IOException {
                // should not contain orga-2 and med-2 (not satisiying filter) and orga 3, med-3 and med-4 (not referenced and includeRefOnly)

                var statusUrl = torchClient.executeExtractData(TestUtils.loadCrtdl("src/test/resources/ReferenceResolveBlackBoxIT/crtdl-with-medication-filter-and-includeRefOnly.json")).block();
                assertThat(statusUrl).isNotNull();

                var statusResponse = torchClient.pollStatus(statusUrl).block();
                assertThat(statusResponse).isNotNull();

                var coreBundles = statusResponse.coreBundleUrl().stream().flatMap(fileServerClient::fetchBundles).toList();
                var patientBundles = statusResponse.patientBundleUrls().stream().flatMap(fileServerClient::fetchBundles).toList();

                assertThat(coreBundles).hasSize(1);
                var coreBundle = coreBundles.getFirst();
                assertThat(coreBundle).extractResourcesByType(ResourceType.Organization).hasSize(1);
                assertThat(coreBundle).extractResourcesByType(ResourceType.Medication).hasSize(1);
                assertThat(coreBundle).extractResourcesByType(ResourceType.Medication).hasSize(1);
                assertThat(coreBundle).extractResourcesByType(ResourceType.Substance).hasSize(0);

                assertThat(coreBundle).extractResourceById("Medication", "med-1").isNotNull();
                assertThatThrownBy(() -> Assertions.assertThat(coreBundle).extractResourceById("Medication", "med-2"))
                        .hasMessage("Expected bundle to contain resource of type Medication and with id med-2, but it could not be found");
                assertThatThrownBy(() -> Assertions.assertThat(coreBundle).extractResourceById("Medication", "med-3"))
                        .hasMessage("Expected bundle to contain resource of type Medication and with id med-3, but it could not be found");
                assertThatThrownBy(() -> Assertions.assertThat(coreBundle).extractResourceById("Medication", "med-4"))
                        .hasMessage("Expected bundle to contain resource of type Medication and with id med-4, but it could not be found");

                assertThat(coreBundle).extractResourceById("Organization", "orga-1").isNotNull();
                assertThatThrownBy(() -> Assertions.assertThat(coreBundle).extractResourceById("Organization", "orga-2"))
                        .hasMessage("Expected bundle to contain resource of type Organization and with id orga-2, but it could not be found");
                assertThatThrownBy(() -> Assertions.assertThat(coreBundle).extractResourceById("Organization", "orga-3"))
                        .hasMessage("Expected bundle to contain resource of type Organization and with id orga-3, but it could not be found");


                assertThat(patientBundles).hasSize(2);
                assertThat(patientBundles).allSatisfy(bundle -> assertThat(bundle).extractResourcesByType(ResourceType.MedicationAdministration).hasSize(1));

                assertThat(patientBundles).allSatisfy(bundle -> assertThat(bundle).extractOnlyPatient().satisfies(patient ->
                        assertThat(bundle).extractResourcesByType(ResourceType.MedicationAdministration).allSatisfy(condition ->
                                assertThat(condition).extractChildrenStringsAt("subject.reference").hasSize(1).first().isEqualTo(patient.getId()))));
            }

                @Test
                void testWithSubstanceFilter () throws IOException {
                // should not contain subst-1

                var statusUrl = torchClient.executeExtractData(TestUtils.loadCrtdl("src/test/resources/ReferenceResolveBlackBoxIT/crtdl-with-substance-filter.json")).block();
                assertThat(statusUrl).isNotNull();

                var statusResponse = torchClient.pollStatus(statusUrl).block();
                assertThat(statusResponse).isNotNull();

                var coreBundles = statusResponse.coreBundleUrl().stream().flatMap(fileServerClient::fetchBundles).toList();
                var patientBundles = statusResponse.patientBundleUrls().stream().flatMap(fileServerClient::fetchBundles).toList();

                assertThat(coreBundles).hasSize(1);
                var coreBundle = coreBundles.getFirst();
                assertThat(coreBundle).extractResourcesByType(ResourceType.Organization).hasSize(3);
                assertThat(coreBundle).extractResourcesByType(ResourceType.Medication).hasSize(5);
                assertThat(coreBundle).extractResourcesByType(ResourceType.Substance).hasSize(1);

                assertThat(coreBundle).extractResourceById("Medication", "med-1").isNotNull();
                assertThat(coreBundle).extractResourceById("Medication", "med-2").isNotNull();
                assertThat(coreBundle).extractResourceById("Medication", "med-3").isNotNull();

                assertThat(coreBundle).extractResourceById("Organization", "orga-1").isNotNull();
                assertThat(coreBundle).extractResourceById("Organization", "orga-2").isNotNull();
                assertThat(coreBundle).extractResourceById("Organization", "orga-3").isNotNull();

                assertThat(coreBundle).extractResourceById("Substance", "subst-1").isNotNull();
                assertThatThrownBy(() -> Assertions.assertThat(coreBundle).extractResourceById("Substance", "subst-2"))
                        .hasMessage("Expected bundle to contain resource of type Substance and with id subst-2, but it could not be found");


                assertThat(patientBundles).hasSize(2);
                assertThat(patientBundles).allSatisfy(bundle -> assertThat(bundle).extractResourcesByType(ResourceType.MedicationAdministration).hasSize(1));

                assertThat(patientBundles).allSatisfy(bundle -> assertThat(bundle).extractOnlyPatient().satisfies(patient ->
                        assertThat(bundle).extractResourcesByType(ResourceType.MedicationAdministration).allSatisfy(condition ->
                                assertThat(condition).extractChildrenStringsAt("subject.reference").hasSize(1).first().isEqualTo(patient.getId()))));
            }

                @Test
                void testWithTwoSubstanceFilters () throws IOException {
                // subst-2 does not fulfill the first substance filter but the second one -> should exist in result

                var statusUrl = torchClient.executeExtractData(TestUtils.loadCrtdl("src/test/resources/ReferenceResolveBlackBoxIT/crtdl-with-two-substance-filters.json")).block();
                assertThat(statusUrl).isNotNull();

                var statusResponse = torchClient.pollStatus(statusUrl).block();
                assertThat(statusResponse).isNotNull();

                var coreBundles = statusResponse.coreBundleUrl().stream().flatMap(fileServerClient::fetchBundles).toList();
                var patientBundles = statusResponse.patientBundleUrls().stream().flatMap(fileServerClient::fetchBundles).toList();

                assertThat(coreBundles).hasSize(1);
                var coreBundle = coreBundles.getFirst();
                assertThat(coreBundle).extractResourcesByType(ResourceType.Organization).hasSize(3);
                assertThat(coreBundle).extractResourcesByType(ResourceType.Medication).hasSize(5);
                assertThat(coreBundle).extractResourcesByType(ResourceType.Substance).hasSize(2);

                assertThat(coreBundle).extractResourceById("Medication", "med-1").isNotNull();
                assertThat(coreBundle).extractResourceById("Medication", "med-2").isNotNull();
                assertThat(coreBundle).extractResourceById("Medication", "med-3").isNotNull();

                assertThat(coreBundle).extractResourceById("Organization", "orga-1").isNotNull();
                assertThat(coreBundle).extractResourceById("Organization", "orga-2").isNotNull();
                assertThat(coreBundle).extractResourceById("Organization", "orga-3").isNotNull();

                assertThat(coreBundle).extractResourceById("Substance", "subst-1").isNotNull();
                assertThat(coreBundle).extractResourceById("Substance", "subst-2").isNotNull();


                assertThat(patientBundles).hasSize(2);
                assertThat(patientBundles).allSatisfy(bundle -> assertThat(bundle).extractResourcesByType(ResourceType.MedicationAdministration).hasSize(1));

                assertThat(patientBundles).allSatisfy(bundle -> assertThat(bundle).extractOnlyPatient().satisfies(patient ->
                        assertThat(bundle).extractResourcesByType(ResourceType.MedicationAdministration).allSatisfy(condition ->
                                assertThat(condition).extractChildrenStringsAt("subject.reference").hasSize(1).first().isEqualTo(patient.getId()))));
            }

            @AfterAll
            static void cleanup() {
                blazeClient.deleteResources(Set.of("List", "MedicationAdministration", "Medication", "Substance", "Organization", "Patient"));
            }
        }

        @Nested
        class TestBundle2 {
            /*
                src/test/resources/ReferenceResolveBlackBoxIT/bundle-2.json contains:

                - enc-1 -> pat-1; -> cond-1
                - cond-1 -> pat-1           // this encounter does not satisfy the (first) encounter filter
             */

            @BeforeAll
            static void init() throws IOException {
                uploadTestData("src/test/resources/ReferenceResolveBlackBoxIT/bundle-2.json");
            }

            @Test
            void testWithConditionFilter() throws IOException {
                // should not contain enc-1

                var statusUrl = torchClient.executeExtractData(TestUtils.loadCrtdl("src/test/resources/ReferenceResolveBlackBoxIT/crtdl-with-condition-filter.json")).block();
                assertThat(statusUrl).isNotNull();

                var statusResponse = torchClient.pollStatus(statusUrl).block();
                assertThat(statusResponse).isNotNull();

                var coreBundles = statusResponse.coreBundleUrl().stream().flatMap(fileServerClient::fetchBundles).toList();
                var patientBundles = statusResponse.patientBundleUrls().stream().flatMap(fileServerClient::fetchBundles).toList();

                assertThat(coreBundles).isEmpty();
                assertThat(patientBundles).hasSize(1);
                var patientBundle = patientBundles.getFirst();


                assertThat(patientBundle).containsNEntries(4);
                assertThat(patientBundle).extractResourceById("Encounter", "enc-1").isNotNull();
                assertThat(patientBundle).extractResourceById("Patient", "pat-1").isNotNull();
                assertThat(patientBundle).extractResourcesByType(ResourceType.Provenance).hasSize(2);
            }

            @Test
            void testWithTwoConditionFilters() throws IOException {
                // cond-1 does not fulfill the first condition filter but the second one -> should exist in result

                uploadTestData("src/test/resources/ReferenceResolveBlackBoxIT/bundle-2.json");

                var statusUrl = torchClient.executeExtractData(TestUtils.loadCrtdl("src/test/resources/ReferenceResolveBlackBoxIT/crtdl-with-two-condition-filters.json")).block();
                assertThat(statusUrl).isNotNull();

                var statusResponse = torchClient.pollStatus(statusUrl).block();
                assertThat(statusResponse).isNotNull();

                var coreBundles = statusResponse.coreBundleUrl().stream().flatMap(fileServerClient::fetchBundles).toList();
                var patientBundles = statusResponse.patientBundleUrls().stream().flatMap(fileServerClient::fetchBundles).toList();

                assertThat(coreBundles).isEmpty();
                assertThat(patientBundles).hasSize(1);
                var patientBundle = patientBundles.getFirst();

                assertThat(patientBundle).containsNEntries(6);
                assertThat(patientBundle).extractResourceById("Encounter", "enc-1").isNotNull();
                assertThat(patientBundle).extractResourceById("Patient", "pat-1").isNotNull();
                assertThat(patientBundle).extractResourceById("Condition", "cond-1").isNotNull();
                assertThat(patientBundle).extractResourcesByType(ResourceType.Provenance).hasSize(3);
            }

            @AfterAll
            static void cleanup() {
                blazeClient.deleteResources(Set.of("List", "Patient", "Condition", "Encounter"));
            }
        }

        @Nested
        @DisplayName("Test patient resource referencing core resource")
        class TestBundle3 {
            /*
                src/test/resources/ReferenceResolveBlackBoxIT/bundle-3.json contains:

                - med-req-1:
                    -> pat-1
                    -> recorder(l1): prac-1
                    -> requester(l2): prac-1
                - med-req-2:
                    -> pat-2
                    -> recorder(l1): prac-2
                    -> requester(l2): prac-2
                - prac-1
                - prac-2
                - pat-1
                - pat-2

                - l1, l2 = linked group 1, linked group 2 in CRTDL

                - used to handle edge cases where the same reference is fulfilled in one linked group but not the other
             */

            @BeforeAll
            static void init() throws IOException {
                uploadTestData("src/test/resources/ReferenceResolveBlackBoxIT/bundle-3.json");
            }

            private Bundle getPatientBundle(List<Bundle> bundles, String patientID) {
                return bundles.stream().filter(bundle -> bundle.getEntry().stream()
                        .anyMatch(entry -> patientID.equals(entry.getResource().getIdPart()))).toList().getFirst();
            }

            @Test
            void testWithoutPractitionerFilter() throws IOException {
                // should contain both recorder and requester for both patients

                var statusUrl = torchClient.executeExtractData(TestUtils.loadCrtdl("src/test/resources/ReferenceResolveBlackBoxIT/crtdl-without-practitioner-filter.json")).block();
                assertThat(statusUrl).isNotNull();

                var statusResponse = torchClient.pollStatus(statusUrl).block();
                assertThat(statusResponse).isNotNull();

                var coreBundles = statusResponse.coreBundleUrl().stream().flatMap(fileServerClient::fetchBundles).toList();
                var patientBundles = statusResponse.patientBundleUrls().stream().flatMap(fileServerClient::fetchBundles).toList();

                assertThat(coreBundles).hasSize(1);
                assertThat(patientBundles).hasSize(2);

                var bundlePat1 = getPatientBundle(patientBundles, "pat-1");
                assertThat(bundlePat1).extractResourcesByType(ResourceType.MedicationRequest).hasSize(1).first()
                        .satisfies(medReq -> assertThat(medReq)
                                .extractChildrenStringsAt("recorder.reference").hasSize(1).first()
                                .isEqualTo("PractitionerRole/prac-1"));
                assertThat(bundlePat1).extractResourcesByType(ResourceType.MedicationRequest).hasSize(1).first()
                        .satisfies(medReq -> assertThat(medReq)
                                .extractChildrenStringsAt("requester.reference").hasSize(1).first()
                                .isEqualTo("PractitionerRole/prac-1"));

                var bundlePat2 = getPatientBundle(patientBundles, "pat-2");
                assertThat(bundlePat2).extractResourcesByType(ResourceType.MedicationRequest).hasSize(1).first()
                        .satisfies(medReq -> assertThat(medReq)
                                .extractChildrenStringsAt("recorder.reference").hasSize(1).first()
                                .isEqualTo("PractitionerRole/prac-2"));
                assertThat(bundlePat2).extractResourcesByType(ResourceType.MedicationRequest).hasSize(1).first()
                        .satisfies(medReq -> assertThat(medReq)
                                .extractChildrenStringsAt("requester.reference").hasSize(1).first()
                                .isEqualTo("PractitionerRole/prac-2"));
            }

            @Test
            void testWithOnePractitionerFilter() throws IOException {
                // - when prac-2 is fetched via l1, there is no filter -> field is extracted
                // - when prac-2 is fetched via l2, there is a filter that it doesn't fulfill -> data absent reason
                // => should contain recorder but not requester in med-req-2 (pat-2), because requester linked group has an unfulfilled filter

                var statusUrl = torchClient.executeExtractData(TestUtils.loadCrtdl("src/test/resources/ReferenceResolveBlackBoxIT/crtdl-with-practitioner-filter.json")).block();
                assertThat(statusUrl).isNotNull();

                var statusResponse = torchClient.pollStatus(statusUrl).block();
                assertThat(statusResponse).isNotNull();

                var coreBundles = statusResponse.coreBundleUrl().stream().flatMap(fileServerClient::fetchBundles).toList();
                var patientBundles = statusResponse.patientBundleUrls().stream().flatMap(fileServerClient::fetchBundles).toList();

                assertThat(coreBundles).hasSize(1);
                assertThat(patientBundles).hasSize(2);

                var bundlePat1 = getPatientBundle(patientBundles, "pat-1");
                assertThat(bundlePat1).extractResourcesByType(ResourceType.MedicationRequest).hasSize(1).first()
                        .satisfies(medReq -> assertThat(medReq)
                                .extractChildrenStringsAt("recorder.reference").hasSize(1).first()
                                .isEqualTo("PractitionerRole/prac-1"));
                assertThat(bundlePat1).extractResourcesByType(ResourceType.MedicationRequest).hasSize(1).first()
                        .satisfies(medReq -> assertThat(medReq)
                                .extractChildrenStringsAt("requester.reference").hasSize(1).first()
                                .isEqualTo("PractitionerRole/prac-1"));

                var bundlePat2 = getPatientBundle(patientBundles, "pat-2");
                assertThat(bundlePat2).extractResourcesByType(ResourceType.MedicationRequest).hasSize(1).first()
                        .satisfies(medReq -> assertThat(medReq)
                                .extractChildrenStringsAt("recorder.reference").hasSize(1).first()
                                .isEqualTo("PractitionerRole/prac-2"));
                assertThat(bundlePat2).extractResourcesByType(ResourceType.MedicationRequest).hasSize(1).first()
                        .satisfies(medReq -> assertThat(medReq).hasDataAbsentReasonAt("requester.reference", "masked"));
            }


            @AfterAll
            static void cleanup() {
                blazeClient.deleteResources(Set.of("List", "Patient", "MedicationRequest", "PractitionerRole"));
            }
        }

        @Nested
        @DisplayName("Test core resource referencing core resource")
        class TestBundle4 {
            /*
                src/test/resources/ReferenceResolveBlackBoxIT/bundle-4.json contains:

                - orga-1:
                    -> partOf(l1): prac-1
                    -> endpoint(l2): prac-1
                - orga-2:
                    -> partOf(l1): prac-2
                    -> endpoint(l2): prac-2
                - prac-1
                - prac-2

                - l1, l2 = linked group 1, linked group 2 in CRTDL
                - bundle-4 is similar to bundle-3 but with core resources
                - note that the references of the organizations are exploited to hold practitioners instead of correct resource types
                - this is not FHIR-conform, but we just need a non-patient-compartment resource with two references of the same type
             */

            @BeforeAll
            static void init() throws IOException {
                uploadTestData("src/test/resources/ReferenceResolveBlackBoxIT/bundle-4.json");
            }

            private Bundle getPatientBundle(List<Bundle> bundles, String patientID) {
                return bundles.stream().filter(bundle -> bundle.getEntry().stream()
                        .anyMatch(entry -> patientID.equals(entry.getResource().getIdPart()))).toList().getFirst();
            }

            @Test
            void testWithoutPractitionerFilter() throws IOException {
                // should contain both partOf and endpoint for both organizations

                var statusUrl = torchClient.executeExtractData(TestUtils.loadCrtdl("src/test/resources/ReferenceResolveBlackBoxIT/crtdl-orga-without-prac-filter.json")).block();
                assertThat(statusUrl).isNotNull();

                var statusResponse = torchClient.pollStatus(statusUrl).block();
                assertThat(statusResponse).isNotNull();

                var coreBundles = statusResponse.coreBundleUrl().stream().flatMap(fileServerClient::fetchBundles).toList();
                var patientBundles = statusResponse.patientBundleUrls().stream().flatMap(fileServerClient::fetchBundles).toList();

                assertThat(coreBundles).hasSize(1);
                assertThat(patientBundles).hasSize(0);

                var coreBundle = coreBundles.getFirst();

                assertThat(coreBundle).extractResourceById("Organization", "orga-1")
                        .extractChildrenStringsAt("partOf.reference").hasSize(1).first()
                        .isEqualTo("PractitionerRole/prac-1");
                assertThat(coreBundle).extractResourceById("Organization", "orga-1")
                        .extractChildrenStringsAt("endpoint.reference").hasSize(1).first()
                        .isEqualTo("PractitionerRole/prac-1");

                assertThat(coreBundle).extractResourceById("Organization", "orga-2")
                        .extractChildrenStringsAt("partOf.reference").hasSize(1).first()
                        .isEqualTo("PractitionerRole/prac-2");
                assertThat(coreBundle).extractResourceById("Organization", "orga-2")
                        .extractChildrenStringsAt("endpoint.reference").hasSize(1).first()
                        .isEqualTo("PractitionerRole/prac-2");
            }

            @Test
            void testWithOnePractitionerFilter() throws IOException {
                // - when prac-2 is fetched via l1, there is no filter -> field is extracted
                // - when prac-2 is fetched via l2, there is a filter that it doesn't fulfill -> data absent reason
                // => should contain "partOf" but not "endPoint" in orga-2, because "endPoint" linked group has an unfulfilled filter

                var statusUrl = torchClient.executeExtractData(TestUtils.loadCrtdl("src/test/resources/ReferenceResolveBlackBoxIT/crtdl-orga-with-practitioner-filter.json")).block();
                assertThat(statusUrl).isNotNull();

                var statusResponse = torchClient.pollStatus(statusUrl).block();
                assertThat(statusResponse).isNotNull();

                var coreBundles = statusResponse.coreBundleUrl().stream().flatMap(fileServerClient::fetchBundles).toList();
                var patientBundles = statusResponse.patientBundleUrls().stream().flatMap(fileServerClient::fetchBundles).toList();

                assertThat(coreBundles).hasSize(1);
                assertThat(patientBundles).hasSize(0);

                var coreBundle = coreBundles.getFirst();

                assertThat(coreBundle).extractResourceById("Organization", "orga-1")
                        .extractChildrenStringsAt("partOf.reference").hasSize(1).first()
                        .isEqualTo("PractitionerRole/prac-1");
                assertThat(coreBundle).extractResourceById("Organization", "orga-1")
                        .extractChildrenStringsAt("endpoint.reference").hasSize(1).first()
                        .isEqualTo("PractitionerRole/prac-1");

                assertThat(coreBundle).extractResourceById("Organization", "orga-2")
                        .extractChildrenStringsAt("partOf.reference").hasSize(1).first()
                        .isEqualTo("PractitionerRole/prac-2");
                assertThat(coreBundle).extractResourceById("Organization", "orga-2")
                        .hasDataAbsentReasonAt("endpoint.reference", "masked");
            }


            @AfterAll
            static void cleanup() {
                blazeClient.deleteResources(Set.of("List", "Patient", "Organization", "PractitionerRole"));
            }
        }

        @Nested
        @DisplayName("Test patient resource referencing patient resource")
        class TestBundle5 {
            /*
                src/test/resources/ReferenceResolveBlackBoxIT/bundle-5.json contains:

                - obs-1:
                    -> pat-1
                    -> focus(l1): enc-1
                    -> encounter(l2): enc-1
                - obs-2:
                    -> pat-2
                    -> focus(l1): enc-2
                    -> encounter(l2): enc-2
                - enc-1
                    -> pat-1
                - enc-2
                    -> pat 2
                - pat-1
                - pat-2

                - l1, l2 = linked group 1, linked group 2 in CRTDL
             */

            @BeforeAll
            static void init() throws IOException {
                uploadTestData("src/test/resources/ReferenceResolveBlackBoxIT/bundle-5.json");
            }

            private Bundle getPatientBundle(List<Bundle> bundles, String patientID) {
                return bundles.stream().filter(bundle -> bundle.getEntry().stream()
                        .anyMatch(entry -> patientID.equals(entry.getResource().getIdPart()))).toList().getFirst();
            }

            @Test
            void testWithoutPractitionerFilter() throws IOException {
                // should contain both focus and encounter for both patients

                var statusUrl = torchClient.executeExtractData(TestUtils.loadCrtdl("src/test/resources/ReferenceResolveBlackBoxIT/crtdl-without-encounter-filter.json")).block();
                assertThat(statusUrl).isNotNull();

                var statusResponse = torchClient.pollStatus(statusUrl).block();
                assertThat(statusResponse).isNotNull();

                var coreBundles = statusResponse.coreBundleUrl().stream().flatMap(fileServerClient::fetchBundles).toList();
                var patientBundles = statusResponse.patientBundleUrls().stream().flatMap(fileServerClient::fetchBundles).toList();

                assertThat(coreBundles).hasSize(0);
                assertThat(patientBundles).hasSize(2);

                var bundlePat1 = getPatientBundle(patientBundles, "pat-1");
                assertThat(bundlePat1).extractResourcesByType(ResourceType.Observation).hasSize(1).first()
                        .satisfies(medReq -> assertThat(medReq)
                                .extractChildrenStringsAt("focus.reference").hasSize(1).first()
                                .isEqualTo("Encounter/enc-1"));
                assertThat(bundlePat1).extractResourcesByType(ResourceType.Observation).hasSize(1).first()
                        .satisfies(medReq -> assertThat(medReq)
                                .extractChildrenStringsAt("encounter.reference").hasSize(1).first()
                                .isEqualTo("Encounter/enc-1"));

                var bundlePat2 = getPatientBundle(patientBundles, "pat-2");
                assertThat(bundlePat2).extractResourcesByType(ResourceType.Observation).hasSize(1).first()
                        .satisfies(medReq -> assertThat(medReq)
                                .extractChildrenStringsAt("focus.reference").hasSize(1).first()
                                .isEqualTo("Encounter/enc-2"));
                assertThat(bundlePat2).extractResourcesByType(ResourceType.Observation).hasSize(1).first()
                        .satisfies(medReq -> assertThat(medReq)
                                .extractChildrenStringsAt("encounter.reference").hasSize(1).first()
                                .isEqualTo("Encounter/enc-2"));
            }

            @Test
            void testWithOnePractitionerFilter() throws IOException {
                // - when enc-2 is fetched via l1, there is no filter -> field is extracted
                // - when enc-2 is fetched via l2, there is a filter that it doesn't fulfill -> data absent reason
                // => should contain focus but not encounter in obs-2 (pat-2), because encounter linked group has an unfulfilled filter

                var statusUrl = torchClient.executeExtractData(TestUtils.loadCrtdl("src/test/resources/ReferenceResolveBlackBoxIT/crtdl-with-encounter-filter.json")).block();
                assertThat(statusUrl).isNotNull();

                var statusResponse = torchClient.pollStatus(statusUrl).block();
                assertThat(statusResponse).isNotNull();

                var coreBundles = statusResponse.coreBundleUrl().stream().flatMap(fileServerClient::fetchBundles).toList();
                var patientBundles = statusResponse.patientBundleUrls().stream().flatMap(fileServerClient::fetchBundles).toList();

                assertThat(coreBundles).hasSize(0);
                assertThat(patientBundles).hasSize(2);

                var bundlePat1 = getPatientBundle(patientBundles, "pat-1");
                assertThat(bundlePat1).extractResourcesByType(ResourceType.Observation).hasSize(1).first()
                        .satisfies(medReq -> assertThat(medReq)
                                .extractChildrenStringsAt("focus.reference").hasSize(1).first()
                                .isEqualTo("Encounter/enc-1"));
                assertThat(bundlePat1).extractResourcesByType(ResourceType.Observation).hasSize(1).first()
                        .satisfies(medReq -> assertThat(medReq)
                                .extractChildrenStringsAt("encounter.reference").hasSize(1).first()
                                .isEqualTo("Encounter/enc-1"));

                var bundlePat2 = getPatientBundle(patientBundles, "pat-2");
                assertThat(bundlePat2).extractResourcesByType(ResourceType.Observation).hasSize(1).first()
                        .satisfies(medReq -> assertThat(medReq)
                                .extractChildrenStringsAt("focus.reference").hasSize(1).first()
                                .isEqualTo("Encounter/enc-2"));
                assertThat(bundlePat2).extractResourcesByType(ResourceType.Observation).hasSize(1).first()
                        .satisfies(medReq -> assertThat(medReq).hasDataAbsentReasonAt("encounter.reference", "masked"));
            }


            @AfterAll
            static void cleanup() {
                blazeClient.deleteResources(Set.of("List", "Patient", "Observation", "Encounter"));
            }
        }

    }
}
