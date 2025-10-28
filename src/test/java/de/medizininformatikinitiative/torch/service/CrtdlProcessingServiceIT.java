package de.medizininformatikinitiative.torch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import de.medizininformatikinitiative.torch.Torch;
import de.medizininformatikinitiative.torch.exceptions.ConsentFormatException;
import de.medizininformatikinitiative.torch.exceptions.ValidationException;
import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedCrtdl;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import de.medizininformatikinitiative.torch.util.ResultFileManager;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
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
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;


@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.main.allow-bean-definition-overriding=true"}, classes = Torch.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CrtdlProcessingServiceIT {

    private static final Logger logger = LoggerFactory.getLogger(CrtdlProcessingServiceIT.class);


    @Autowired
    CrtdlProcessingService service;
    @Autowired
    ResultFileManager resultFileManager;
    @Autowired
    CrtdlValidatorService validator;
    @Autowired
    @Qualifier("fhirClient")
    WebClient webClient;
    @Value("${torch.fhir.testPopulation.path}")
    private String testPopulationPath;
    private AnnotatedCrtdl crtdlAllObservations;
    private AnnotatedCrtdl crtdlNoPatients;
    private AnnotatedCrtdl crtdlObservationLinked;
    private AnnotatedCrtdl crtdlObservationMedicationLinked;

    @BeforeAll
    void init() throws IOException, ValidationException, ConsentFormatException {
        // Create an instance of BaseTestSetup
        IntegrationTestSetup integrationTestSetup = new IntegrationTestSetup();
        FileInputStream fis = new FileInputStream("src/test/resources/CRTDL/CRTDL_observation_all_fields_withoutReference.json");
        crtdlAllObservations = validator.validateAndAnnotate(integrationTestSetup.objectMapper().readValue(fis, Crtdl.class));
        fis.close();
        fis = new FileInputStream("src/test/resources/CRTDL/CRTDL_observation_not_contained.json");
        crtdlNoPatients = validator.validateAndAnnotate(integrationTestSetup.objectMapper().readValue(fis, Crtdl.class));
        fis.close();
        fis = new FileInputStream("src/test/resources/CRTDL/CRTDL_observation_linked_encounter.json");
        crtdlObservationLinked = validator.validateAndAnnotate(integrationTestSetup.objectMapper().readValue(fis, Crtdl.class));
        fis.close();
        fis = new FileInputStream("src/test/resources/CRTDL/CRTDL_MedicationAdministraion_linked_encounter_linked_medication.json");
        crtdlObservationMedicationLinked = validator.validateAndAnnotate(integrationTestSetup.objectMapper().readValue(fis, Crtdl.class));
        fis.close();

        webClient.post()
                .bodyValue(Files.readString(Path.of(testPopulationPath)))
                .header("Content-Type", "application/fhir+json")
                .retrieve()
                .toBodilessEntity()
                .block();

        fis.close();
    }

    @AfterAll
    void cleanup() throws IOException {
        clearDirectory("processwithrefs");
        clearDirectory("processwithoutrefs");
        clearDirectory("processWithRefsCoreAndPatient");
    }


    /**
     * Recursively deletes all files inside the given directory.
     *
     * @param jobId the name of the job directory to clean.
     */
    private void clearDirectory(String jobId) throws IOException {
        Path jobDir = resultFileManager.getJobDirectory(jobId); // Get the job directory path

        if (jobDir != null && Files.exists(jobDir)) {
            try (Stream<Path> walk = Files.walk(jobDir)) {
                walk.sorted(Comparator.reverseOrder()) // Delete children before parents
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                logger.error("Failed to delete file: {}", path, e);
                            }
                        });
            }
            logger.info("Cleared job directory: {}", jobDir);
        }
    }

    @Test
    void processReferences() {
        String jobId = "processwithrefs";
        Path jobDir = resultFileManager.initJobDir(jobId).block();

        Mono<Void> result = service.process(crtdlObservationLinked, jobId, List.of());


        Assertions.assertDoesNotThrow(() -> result.block());
        try {
            Assertions.assertNotNull(jobDir);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(jobDir)) {
                boolean filesExist = stream.iterator().hasNext();
                assertTrue(filesExist, "Job directory should contain files.");
            }
        } catch (IOException e) {
            logger.trace(e.getMessage());
            throw new RuntimeException("Failed to read job directory.");
        }
    }

    @Test
    void processReferencesOutsidePatientBundle() {
        String jobId = "processWithRefsCoreAndPatient";
        Path jobDir = resultFileManager.initJobDir(jobId).block();

        Mono<Void> result = service.process(crtdlObservationMedicationLinked, jobId, List.of());


        Assertions.assertDoesNotThrow(() -> result.block());
        try {
            Assertions.assertNotNull(jobDir);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(jobDir)) {
                boolean filesExist = stream.iterator().hasNext();
                assertTrue(filesExist, "Job directory should contain files.");
            }
        } catch (IOException e) {
            logger.trace(e.getMessage());
            throw new RuntimeException("Failed to read job directory.");
        }
    }

    @Test
    void processingService() {
        String jobId = "processwithoutrefs";
        Path jobDir = resultFileManager.initJobDir(jobId).block();
        Mono<Void> result = service.process(crtdlAllObservations, jobId, List.of());


        Assertions.assertDoesNotThrow(() -> result.block());
        try {
            Assertions.assertNotNull(jobDir);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(jobDir)) {
                boolean filesExist = stream.iterator().hasNext();
                assertTrue(filesExist, "Job directory should contain files.");
            }
        } catch (IOException e) {
            logger.trace(e.getMessage());
            throw new RuntimeException("Failed to read job directory.");
        }
    }

    @Nested
    class FetchPatientList {

        @Test
        void nonEmpty() throws JsonProcessingException {
            Mono<List<PatientBatch>> batches1 = service.fetchPatientListFromFlare(crtdlAllObservations).collectList();
            Mono<List<PatientBatch>> batches2 = service.fetchPatientListUsingCql(crtdlAllObservations).collectList();

            StepVerifier.create(Mono.zip(batches1, batches2))
                    .expectNextMatches(t -> t.getT1().equals(t.getT2()) && !t.getT1().isEmpty())
                    .verifyComplete();
        }

        @Test
        void empty() throws JsonProcessingException {
            Flux<PatientBatch> batches1 = service.fetchPatientListFromFlare(crtdlNoPatients);
            Flux<PatientBatch> batches2 = service.fetchPatientListUsingCql(crtdlNoPatients);

            StepVerifier.create(batches1).verifyComplete();
            StepVerifier.create(batches2).verifyComplete();
        }
    }


}
