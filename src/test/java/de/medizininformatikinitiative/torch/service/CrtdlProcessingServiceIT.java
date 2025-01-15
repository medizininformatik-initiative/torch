package de.medizininformatikinitiative.torch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import de.medizininformatikinitiative.torch.BundleCreator;
import de.medizininformatikinitiative.torch.Torch;
import de.medizininformatikinitiative.torch.model.PatientBatch;
import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;
import de.medizininformatikinitiative.torch.setup.ContainerManager;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import de.medizininformatikinitiative.torch.util.ResultFileManager;
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
import reactor.core.publisher.Flux;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.main.allow-bean-definition-overriding=true"}, classes = Torch.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CrtdlProcessingServiceIT {

    private static final Logger logger = LoggerFactory.getLogger(CrtdlProcessingServiceIT.class);

    // Create an instance of BaseTestSetup
    private static final IntegrationTestSetup INTEGRATION_TEST_SETUP = new IntegrationTestSetup();


    private final CrtdlProcessingService service;

    protected WebClient webClient;
    private Crtdl CRTDL_ALL_OBSERVATIONS;
    private Crtdl CRTDL_NO_PATIENTS;

    private final String jobId;
    private final Path jobDir;

    private static final int BATCH_SIZE = 100;

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
        CRTDL_ALL_OBSERVATIONS = INTEGRATION_TEST_SETUP.objectMapper().readValue(fis, Crtdl.class);
        fis.close();
        fis = new FileInputStream("src/test/resources/CRTDL/CRTDL_observation_not_contained.json");
        CRTDL_NO_PATIENTS = INTEGRATION_TEST_SETUP.objectMapper().readValue(fis, Crtdl.class);
        fis.close();
        manager.startContainers();


        webClient.post()
                .bodyValue(Files.readString(Path.of(testPopulationPath)))
                .header("Content-Type", "application/fhir+json")
                .retrieve()
                .toBodilessEntity()
                .block();

        fis.close();
    }


    private boolean isDirectoryEmpty(Path directory) throws IOException {
        // Try-with-resources for DirectoryStream
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            return !stream.iterator().hasNext();
        }
    }

    @Nested
    class FetchPatientList {

        @Test
        void nonEmpty() throws JsonProcessingException {
            Mono<List<PatientBatch>> batches1 = service.fetchPatientListFromFlare(CRTDL_ALL_OBSERVATIONS).collectList();
            Mono<List<PatientBatch>> batches2 = service.fetchPatientListUsingCql(CRTDL_ALL_OBSERVATIONS).collectList();

            StepVerifier.create(Mono.zip(batches1, batches2))
                    .expectNextMatches(t -> t.getT1().equals(t.getT2()) && !t.getT1().isEmpty())
                    .verifyComplete();
        }

        @Test
        void empty() throws JsonProcessingException {
            Flux<PatientBatch> batches1 = service.fetchPatientListFromFlare(CRTDL_NO_PATIENTS);
            Flux<PatientBatch> batches2 = service.fetchPatientListUsingCql(CRTDL_NO_PATIENTS);

            StepVerifier.create(batches1).verifyComplete();
            StepVerifier.create(batches2).verifyComplete();
        }
    }


    @Test
    void testProcessBatchWritesFiles() throws IOException {

        PatientBatch batch = PatientBatch.of("1", "2");// Sample batch with patient references

        // Act
        Mono<Void> result = service.processBatch(CRTDL_ALL_OBSERVATIONS.dataExtraction().attributeGroups(), batch, jobId, CRTDL_ALL_OBSERVATIONS.consentKey());

        // Assert
        StepVerifier.create(result)
                .verifyComplete(); // Verify that the method completes successfully

        // Verify that files were created in the job directory
        assertTrue(Files.exists(jobDir), "Job directory should exist.");
        assertFalse(isDirectoryEmpty(jobDir), "Job directory should not be isEmpty after processing.");
    }


    @Test
    void processingService() {
        Mono<Void> result = service.processCrtdl(CRTDL_ALL_OBSERVATIONS, jobId);


        Assertions.assertDoesNotThrow(() -> result.block());
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(jobDir)) {
            boolean filesExist = stream.iterator().hasNext();
            assertTrue(filesExist, "Job directory should contain files.");
        } catch (IOException e) {
            logger.trace(e.getMessage());
            throw new RuntimeException("Failed to read job directory.");
        }
    }

    @Test
    void testSaveResourcesAsBundles() {

        Map<String, Collection<Resource>> resourceMap = Map.of(
                "Patient", List.of(new Patient()), // Replace with actual test FHIR resources
                "Observation", List.of(new Observation())
        );// Populate with test resources

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
            logger.trace(e.getMessage());
            throw new RuntimeException("Failed to read job directory.");
        }

    }


}