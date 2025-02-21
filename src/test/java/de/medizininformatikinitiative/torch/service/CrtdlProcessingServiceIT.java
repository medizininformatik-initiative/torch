package de.medizininformatikinitiative.torch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import de.medizininformatikinitiative.torch.Torch;
import de.medizininformatikinitiative.torch.exceptions.ValidationException;
import de.medizininformatikinitiative.torch.model.PatientBatch;
import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedCrtdl;
import de.medizininformatikinitiative.torch.setup.ContainerManager;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import de.medizininformatikinitiative.torch.util.ResultFileManager;
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
import java.util.List;

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
    private AnnotatedCrtdl CRTDL_ALL_OBSERVATIONS;
    private AnnotatedCrtdl CRTDL_NO_PATIENTS;
    private AnnotatedCrtdl CRTDL_DIAGNOSIS_LINKED;

    String jobId;
    Path jobDir;

    private static final int BATCH_SIZE = 100;

    @Autowired
    public CrtdlProcessingServiceIT(CrtdlProcessingService service, ResultFileManager resultFileManager, @Qualifier("fhirClient") WebClient webClient, ContainerManager containerManager) {
        this.service = service;
        this.webClient = webClient;
        this.resultFileManager = resultFileManager;
        this.manager = containerManager;
    }

    @Value("${torch.fhir.testPopulation.path}")
    private String testPopulationPath;
    ContainerManager manager;

    @Autowired
    ResultFileManager resultFileManager;

    @Autowired
    CrtdlValidatorService validator;


    @BeforeAll
    void init() throws IOException, ValidationException {

        FileInputStream fis = new FileInputStream("src/test/resources/CRTDL/CRTDL_observation_all_fields_withoutReference.json");
        CRTDL_ALL_OBSERVATIONS = validator.validate(INTEGRATION_TEST_SETUP.objectMapper().readValue(fis, Crtdl.class));
        fis.close();
        fis = new FileInputStream("src/test/resources/CRTDL/CRTDL_observation_not_contained.json");
        CRTDL_NO_PATIENTS = validator.validate(INTEGRATION_TEST_SETUP.objectMapper().readValue(fis, Crtdl.class));
        fis.close();
        fis = new FileInputStream("src/test/resources/CRTDL/CRTDL_observation_linked_encounter.json");
        CRTDL_DIAGNOSIS_LINKED = validator.validate(INTEGRATION_TEST_SETUP.objectMapper().readValue(fis, Crtdl.class));
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

    @AfterAll
    void cleanup() {
        clearDirectory("processwithrefs");
        clearDirectory("processwithoutrefs");
    }

    /**
     * Recursively deletes all files inside the given directory.
     *
     * @param jobId the name of the job directory to clean.
     */
    private void clearDirectory(String jobId) {
        Path jobDir = resultFileManager.getJobDirectory(jobId);// Get the job directory path

        if (jobDir != null && Files.exists(jobDir)) {
            try {
                Files.walk(jobDir)
                        .sorted((p1, p2) -> p2.compareTo(p1)) // Delete children before parents
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                logger.error("Failed to delete file: {}", path, e);
                            }
                        });
                logger.info("Cleared job directory: {}", jobDir);
            } catch (IOException e) {
                logger.error("Failed to clean job directory: {}", jobDir, e);
            }
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
    void processReferences() {
        String jobId = "processwithrefs";
        jobDir = resultFileManager.initJobDir(jobId).block();

        Mono<Void> result = service.process(CRTDL_DIAGNOSIS_LINKED, jobId);


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
    void processingService() {
        jobId = "processwithoutrefs";
        jobDir = resultFileManager.initJobDir(jobId).block();
        Mono<Void> result = service.process(CRTDL_ALL_OBSERVATIONS, jobId);


        Assertions.assertDoesNotThrow(() -> result.block());
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(jobDir)) {
            boolean filesExist = stream.iterator().hasNext();
            assertTrue(filesExist, "Job directory should contain files.");
        } catch (IOException e) {
            logger.trace(e.getMessage());
            throw new RuntimeException("Failed to read job directory.");
        }
    }


}