package de.medizininformatikinitiative.torch;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import de.medizininformatikinitiative.torch.cql.CqlClient;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.exceptions.ValidationException;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedCrtdl;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.service.CrtdlValidatorService;
import de.medizininformatikinitiative.torch.service.DirectResourceLoader;
import de.medizininformatikinitiative.torch.util.ResultFileManager;
import de.numcodex.sq2cql.Translator;
import de.numcodex.sq2cql.model.structured_query.StructuredQuery;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Stream;

import static de.medizininformatikinitiative.torch.util.TimeUtils.durationSecondsSince;
import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.main.allow-bean-definition-overriding=true"}, classes = Torch.class, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FhirControllerIT {

    private static final Logger logger = LoggerFactory.getLogger(FhirControllerIT.class);
    private static final String RESOURCE_PATH_PREFIX = "src/test/resources/";

    @Autowired
    DirectResourceLoader transformer;
    @Autowired
    @Qualifier("fhirClient")
    WebClient webClient;
    @Autowired
    @Qualifier("flareClient")
    WebClient flareClient;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    CqlClient cqlClient;
    @Autowired
    Translator cqlQueryTranslator;
    @Autowired
    FhirContext context;
    @Autowired
    CrtdlValidatorService validatorService;
    @Autowired
    ResultFileManager resultFileManager;
    @Value("${torch.fhir.testPopulation.path}")
    private String testPopulationPath;

    @Value("${server.port}")
    private int port;

    @BeforeAll
    void init() throws IOException {
        webClient.post().bodyValue(Files.readString(Path.of(testPopulationPath))).header("Content-Type", "application/fhir+json").retrieve().toBodilessEntity().block();
    }

    @Test
    void testCapability() {
        TestRestTemplate restTemplate = new TestRestTemplate();
        HttpEntity<String> entity = new HttpEntity<>(null, null);

        ResponseEntity<String> response = restTemplate.exchange("http://localhost:" + port + "/fhir/metadata", HttpMethod.GET, entity, String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void testGlobalStatus() {
        TestRestTemplate restTemplate = new TestRestTemplate();
        HttpEntity<String> entity = new HttpEntity<>(null, null);

        ResponseEntity<String> response = restTemplate.exchange("http://localhost:" + port + "/fhir/__status/", HttpMethod.GET, entity, String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void testFlare() throws IOException {
        FileInputStream fis = new FileInputStream(RESOURCE_PATH_PREFIX + "CRTDL/CRTDL_diagnosis_female.json");
        String jsonString = new Scanner(fis, StandardCharsets.UTF_8).useDelimiter("\\A").next();
        Crtdl crtdl = objectMapper.readValue(jsonString, Crtdl.class);
        fis.close();
        String responseBody = flareClient.post().uri("/query/execute-cohort").contentType(MediaType.parseMediaType("application/sq+json")).bodyValue(crtdl.cohortDefinition().toString()).retrieve().onStatus(status -> status.value() == 404, clientResponse -> {
            logger.error("Received 404 Not Found");
            return clientResponse.createException();
        }).bodyToMono(String.class).block();  // Blocking to get the response synchronously

        logger.debug("Response Body: {}", responseBody);
        List<String> patientIds = objectMapper.readValue(responseBody, TypeFactory.defaultInstance().constructCollectionType(List.class, String.class));

        int patientCount = patientIds.size();
        assertThat(patientCount).isEqualTo(4);
    }

    @Test
    void testCql() throws IOException {
        try (FileInputStream fis = new FileInputStream(RESOURCE_PATH_PREFIX + "CRTDL/CRTDL_diagnosis_female.json")) {
            String jsonString = new Scanner(fis, StandardCharsets.UTF_8).useDelimiter("\\A").next();
            Crtdl crtdl = objectMapper.readValue(jsonString, Crtdl.class);

            var ccdl = objectMapper.treeToValue(crtdl.cohortDefinition(), StructuredQuery.class);

            Flux<String> patientIds = cqlClient.fetchPatientIds(cqlQueryTranslator.toCql(ccdl).print());

            StepVerifier.create(patientIds).
                    expectNext("1", "2", "4", "VHF00006")
                    .verifyComplete();
        }
    }

    @Test
    void testMustHave() throws IOException, ValidationException {
        FileInputStream fis = new FileInputStream(RESOURCE_PATH_PREFIX + "CRTDL/CRTDL_observation_must_have.json");
        Crtdl crtdl = objectMapper.readValue(fis, Crtdl.class);
        PatientBatchWithConsent patients = PatientBatchWithConsent.fromBatch(PatientBatch.of("3"));
        AnnotatedCrtdl annotatedCrtdl = validatorService.validateAndAnnotate(crtdl);
        Mono<PatientBatchWithConsent> collectedResourcesMono = transformer.directLoadPatientCompartment(annotatedCrtdl.dataExtraction().attributeGroups(), patients);
        PatientBatchWithConsent result = collectedResourcesMono.block(); // Blocking to get the result
        assertThat(result).isNotNull();

        assertThat(result.bundles()).isEmpty();
        fis.close();
    }

    @Test
    void testCoreMustHave() throws IOException, ValidationException {

        FileInputStream fis = new FileInputStream(RESOURCE_PATH_PREFIX + "CRTDL/CRTDL_medication_must_have.json");
        Crtdl crtdl = objectMapper.readValue(fis, Crtdl.class);
        AnnotatedCrtdl annotatedCrtdl = validatorService.validateAndAnnotate(crtdl);
        Mono<ResourceBundle> collectedResourcesMono = transformer.processCoreAttributeGroups(annotatedCrtdl.dataExtraction().attributeGroups(), new ResourceBundle());

        // Verify that the Mono fails with the expected exception
        StepVerifier.create(collectedResourcesMono)
                .expectError(MustHaveViolatedException.class)
                .verify();

        fis.close();
    }

    void testExecutor(String filePath, String url, HttpHeaders headers) {
        TestRestTemplate restTemplate = new TestRestTemplate();
        try {
            String fileContent = Files.readString(Paths.get(filePath), StandardCharsets.UTF_8);
            HttpEntity<String> entity = new HttpEntity<>(fileContent, headers);

            try {
                long start = System.nanoTime();
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
                assertThat(response.getStatusCode().value()).isEqualTo(202);
                assertThat(durationSecondsSince(start)).isLessThan(1);
                List<String> locations = response.getHeaders().get("Content-Location");
                assertThat(locations).hasSize(1);
                pollStatusEndpoint(restTemplate, headers, locations.getFirst(), 200);
                clearDirectory(locations.getFirst().substring(locations.getFirst().lastIndexOf('/')));
            } catch (HttpStatusCodeException e) {
                logger.error("HTTP Status code error: {}", e.getStatusCode(), e);
                Assertions.fail("HTTP request failed with status code: " + e.getStatusCode());
            }

        } catch (IOException e) {
            logger.error("CRTDL file not found", e);
        }
    }

    private void pollStatusEndpoint(TestRestTemplate restTemplate, HttpHeaders headers, String statusUrl, int expectedCode) {
        boolean completed = false;
        int i = 0;

        while (!completed) {
            try {
                i++;
                HttpEntity<String> entity = new HttpEntity<>(null, headers);
                ResponseEntity<String> response = restTemplate.exchange(statusUrl, HttpMethod.GET, entity, String.class);

                logger.trace("Poll {}: status={}, body={}", i, response.getStatusCode(), response.getBody());

                if (response.getStatusCode().value() == expectedCode) {
                    completed = true;
                    logger.info("Final status code {} received after {} polls", expectedCode, i);
                    assertThat(expectedCode).isEqualTo(response.getStatusCode().value());
                    logger.debug(response.getBody());
                    if (response.getStatusCode().is4xxClientError() || response.getStatusCode().is5xxServerError()) {
                        assertThat(context.newJsonParser().parseResource(response.getBody())).isInstanceOf(OperationOutcome.class);
                    }
                } else if (response.getStatusCode().is4xxClientError() || response.getStatusCode().is5xxServerError()) {
                    Assertions.fail("Polling failed with unexpected error status: " + response.getStatusCode());
                }

                if (i >= 100) {
                    Assertions.fail("Polling failed: exceeded maximum attempts");
                }

            } catch (HttpStatusCodeException e) {
                logger.error("HTTP Status code error: {}", e.getStatusCode(), e);
                logger.error("Response Body: {}", e.getResponseBodyAsString());
                if (e.getStatusCode().is4xxClientError() || e.getStatusCode().is5xxServerError()) {
                    Assertions.fail("Polling status endpoint failed with status code: " + e.getStatusCode());
                }
            }

            try {
                Thread.sleep(200); // Delay between polls
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Polling was interrupted", e);
            }
        }
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

    @Nested
    class Endpoint {

        @ParameterizedTest
        @ValueSource(strings = {"src/test/resources/CRTDL_Parameters/Parameters_observation_all_fields_without_refs.json", "src/test/resources/CRTDL_Parameters/Parameters_observation_all_fields_without_refs_patients.json"})
        void validObservation(String parametersFile) {
            HttpHeaders headers = new HttpHeaders();
            headers.add("content-type", "application/fhir+json");
            testExecutor(parametersFile, "http://localhost:" + port + "/fhir/$extract-data", headers);
        }

        @Test
        void emptyRequestBodyReturnsBadRequest() {
            TestRestTemplate restTemplate = new TestRestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.valueOf("application/fhir+json"));
            HttpEntity<String> entity = new HttpEntity<>("", headers);

            ResponseEntity<String> response = restTemplate.postForEntity("http://localhost:" + port + "/fhir/$extract-data", entity, String.class);

            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(response.getBody()).contains("OperationOutcome");
            assertThat(response.getBody()).contains("Empty request body");
        }

        @Test
        void invalidCRTDLReturnsBadRequest() throws IOException {
            TestRestTemplate restTemplate = new TestRestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.add("content-type", "application/fhir+json");
            String fileContent = Files.readString(Paths.get("src/test/resources/CRTDL_Parameters/Parameters_invalid_CRTDL.json"), StandardCharsets.UTF_8);
            HttpEntity<String> entity = new HttpEntity<>(fileContent, headers);
            long start = System.nanoTime();
            ResponseEntity<String> response = restTemplate.exchange("http://localhost:" + port + "/fhir/$extract-data", HttpMethod.POST, entity, String.class);
            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(durationSecondsSince(start)).isLessThan(1);
        }

        @Test
        void testPayloadJustBelow2Mb() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.valueOf("application/fhir+json"));

            // Generate JSON payload just below 2 MB
            String payload = buildJsonPayload(2 * 1024 * 1024 - 50); // 2MB - 50 bytes

            TestRestTemplate restTemplate = new TestRestTemplate();
            HttpEntity<String> entity = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    "http://localhost:" + port + "/fhir/$extract-data",
                    entity,
                    String.class
            );

            assertThat(response.getStatusCode().value()).isEqualTo(400);
        }

        @Test
        void testPayloadJustAbove2Mb() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.valueOf("application/fhir+json"));

            // Generate JSON payload just above 2 MB
            String payload = buildJsonPayload(2 * 1024 * 1024 + 50); // 2MB + 50 bytes

            TestRestTemplate restTemplate = new TestRestTemplate();
            HttpEntity<String> entity = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    "http://localhost:" + port + "/fhir/$extract-data",
                    entity,
                    String.class
            );

            // Expect failure due to exceeding maxInMemorySize
            assertThat(response.getStatusCode().is5xxServerError()).isTrue();
        }

        private String buildJsonPayload(int sizeInBytes) {
            // Simple JSON with a single string field
            return "{\"parameter\":[{\"name\":\"data\",\"valueString\":\"" +
                    "x".repeat(Math.max(0, sizeInBytes)) +
                    "\"}]}";
        }
    }
}
