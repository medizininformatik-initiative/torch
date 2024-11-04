package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.BundleCreator;
import de.medizininformatikinitiative.torch.Torch;
import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;
import de.medizininformatikinitiative.torch.setup.ContainerManager;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import de.medizininformatikinitiative.torch.util.ResultFileManager;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;


@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.main.allow-bean-definition-overriding=true"}, classes = Torch.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CrtdlProcessingServiceIT {

    private static final Logger logger = LoggerFactory.getLogger(CrtdlProcessingServiceIT.class);

    // Create an instance of BaseTestSetup
    private static final IntegrationTestSetup INTEGRATION_TEST_SETUP = new IntegrationTestSetup();


    private final CrtdlProcessingService service;


    protected WebClient webClient;
    private DomainResource resourcesrc;
    private DomainResource resourceexpected;
    private Crtdl CRTDL_ALL_OBSERVATIONS;
    private Crtdl CRTDL_NO_PATIENTS;

    private String jobId;
    private Path jobDir;
    // Directory where files are saved
    // Directory where files are saved

    @Autowired
    public CrtdlProcessingServiceIT(CrtdlProcessingService service, BundleCreator bundleCreator, ResultFileManager resultFileManager, @Qualifier("fhirClient") WebClient webClient, ContainerManager containerManager) {
        this.service = service;
        this.webClient = webClient;
        this.bundleCreator = bundleCreator;
        this.resultFileManager = resultFileManager;
        this.manager = containerManager;
        jobId = UUID.randomUUID().toString();
        jobDir = resultFileManager.initJobDir(jobId).block();
    }

    @Value("${torch.fhir.testPopulation.path}")
    private String testPopulationPath;


    ContainerManager manager;


    BundleCreator bundleCreator;

    @Autowired
    ResultFileManager resultFileManager;


    @BeforeAll
    void init() throws IOException {


        FileInputStream fis = new FileInputStream("src/test/resources/CRTDL/CRTDL_observation_all_fields.json");
        CRTDL_ALL_OBSERVATIONS = INTEGRATION_TEST_SETUP.getObjectMapper().readValue(fis, Crtdl.class);
        fis.close();
        fis = new FileInputStream("src/test/resources/CRTDL/CRTDL_observation_not_contained.json");
        CRTDL_NO_PATIENTS = INTEGRATION_TEST_SETUP.getObjectMapper().readValue(fis, Crtdl.class);
        fis.close();

        resourcesrc = INTEGRATION_TEST_SETUP.readResource("src/test/resources/InputResources/Observation/Observation_lab.json");
        resourceexpected = INTEGRATION_TEST_SETUP.readResource("src/test/resources/ResourceTransformationTest/ExpectedOutput/Observation_lab.json");


        manager.startContainers();


        webClient.post()
                .bodyValue(Files.readString(Path.of(testPopulationPath)))
                .header("Content-Type", "application/fhir+json")
                .retrieve()
                .toBodilessEntity()
                .block();

        fis.close();
    }

    @AfterEach
    void cleanUp() throws IOException {
        if (Files.exists(jobDir)) {
            Files.walk(jobDir)
                    .map(Path::toFile)
                    .forEach(file -> {
                        if (!file.delete()) {
                            System.err.println("Failed to delete file: " + file.getAbsolutePath());
                        }
                    });
            Files.deleteIfExists(jobDir); // Delete the main job directory
        }
    }

    private boolean isDirectoryEmpty(Path directory) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            return !stream.iterator().hasNext();
        }
    }

    @Test
    void fetchPatientLists() {
        Mono<List<String>> listMono1 = service.fetchPatientListFromFlare(CRTDL_ALL_OBSERVATIONS);

        Mono<List<String>> listMono2 = service.fetchPatientListFromFlare(CRTDL_ALL_OBSERVATIONS);

        List<String> patientList1 = listMono1.block();
        List<String> patientList2 = listMono2.block();
        assertEquals(4, patientList1.size());
        assertEquals(patientList2, patientList1);

    }

    @Test
    void fetchEmptyPatientLists() {
        Mono<List<String>> listMono1 = service.fetchPatientListFromFlare(CRTDL_NO_PATIENTS);

        Mono<List<String>> listMono2 = service.fetchPatientListFromFlare(CRTDL_NO_PATIENTS);

        List<String> patientList1 = listMono1.block();
        List<String> patientList2 = listMono2.block();
        assertEquals(0, patientList1.size());
        assertEquals(patientList2, patientList1);

    }


    @Test
    void testFetchAndProcessBatches_EmptyPatientList() {
        Mono<Void> result = service.processCrtdl(CRTDL_NO_PATIENTS, jobId);

        // Assert
        StepVerifier.create(result)
                .verifyComplete(); // Verify the Mono completes without emitting any items

        // Assert that the status was set correctly in ResultFileManager
        String expectedStatus = "Failed at collectResources for batch: No patients found.";
        String actualStatus = resultFileManager.getStatus(jobId); // Assuming getStatus is available
        assertEquals(expectedStatus, actualStatus, "Status message should indicate failure due to empty patient list.");

    }

    @Test
    void testProcessBatchWritesFiles() throws IOException {

        List<String> batch = List.of("1", "2"); // Sample batch with patient references

        // Act
        Mono<Void> result = service.processBatch(CRTDL_ALL_OBSERVATIONS, batch, jobId);

        // Assert
        StepVerifier.create(result)
                .verifyComplete(); // Verify that the method completes successfully

        // Verify that files were created in the job directory
        assertTrue(Files.exists(jobDir), "Job directory should exist.");
        assertFalse(isDirectoryEmpty(jobDir), "Job directory should not be empty after processing.");
    }


    @Test
    void processingService() {
        Mono<Void> result = service.processCrtdl(CRTDL_ALL_OBSERVATIONS, jobId);


        Assertions.assertDoesNotThrow(() -> result.block());
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(jobDir)) {
            boolean filesExist = stream.iterator().hasNext();
            assertTrue(filesExist, "Job directory should contain files.");
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to read job directory.");
        }

    }

    @Test
    void testSaveResourcesAsBundles() {

        Map<String, Collection<Resource>> resourceMap = Map.of(
                "Patient", List.of(new Patient()), // Replace with actual test FHIR resources
                "Observation", List.of(new Observation())
        );// Populate with test resources
        UUID batchId = UUID.randomUUID();

        // Act
        Mono<Void> result = service.saveResourcesAsBundles(jobId, resourceMap);

        // Assert
        StepVerifier.create(result)
                .verifyComplete(); // Verify that the method completes successfully

        // Check that files were created in the job directory
        assertTrue(Files.exists(jobDir), "Job directory should exist.");

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(jobDir)) {
            boolean filesExist = stream.iterator().hasNext();
            assertTrue(filesExist, "Job directory should contain files.");
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to read job directory.");
        }

    }


    @Test
    void processCrtdl_EmptyPatientList_ShouldUpdateStatus() throws IOException {

        Mono<Void> result = service.processCrtdl(CRTDL_NO_PATIENTS, jobId);

        StepVerifier.create(result)
                .verifyComplete();

        // Assert that the status was set correctly in ResultFileManager
        String expectedStatus = "Failed at collectResources for batch: No patients found.";
        String actualStatus = resultFileManager.getStatus(jobId); // Assuming getStatus is available
        assertEquals(expectedStatus, actualStatus, "Status message should indicate failure due to empty patient list.");
        assertTrue(isDirectoryEmpty(jobDir), "Job directory should be empty after cleanup.");

    }


}