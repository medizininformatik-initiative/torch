package de.medizininformatikinitiative.torch;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import de.medizininformatikinitiative.torch.consent.ConsentHandler;
import de.medizininformatikinitiative.torch.cql.CqlClient;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.exceptions.ValidationException;
import de.medizininformatikinitiative.torch.management.StructureDefinitionHandler;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedCrtdl;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;
import de.medizininformatikinitiative.torch.service.CrtdlValidatorService;
import de.medizininformatikinitiative.torch.service.DataStore;
import de.medizininformatikinitiative.torch.setup.ContainerManager;
import de.medizininformatikinitiative.torch.testUtil.FhirTestHelper;
import de.medizininformatikinitiative.torch.util.ResourceReader;
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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
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
import java.util.List;
import java.util.Objects;
import java.util.Scanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.main.allow-bean-definition-overriding=true"}, classes = Torch.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class FhirControllerIT {

    protected static final Logger logger = LoggerFactory.getLogger(FhirControllerIT.class);


    @Autowired
    ResourceReader resourceReader;

    @Autowired
    FhirTestHelper fhirTestHelper;

    @Autowired
    FhirContext context;

    @Autowired
    CrtdlValidatorService validatorService;

    @Autowired
    @Qualifier("fhirClient")
    protected WebClient webClient;

    ContainerManager manager;

    @Autowired
    @Qualifier("flareClient")
    protected WebClient flareClient;


    protected final DirectResourceLoader transformer;
    protected final DataStore dataStore;
    protected final StructureDefinitionHandler cds;
    protected ObjectMapper objectMapper;
    protected CqlClient cqlClient;
    protected Translator cqlQueryTranslator;
    @Value("${torch.fhir.testPopulation.path}")
    private String testPopulationPath;
    protected FhirContext fhirContext;


    protected final DseMappingTreeBase dseMappingTreeBase;


    private static final String RESOURCE_PATH_PREFIX = "src/test/resources/";
    @LocalServerPort
    private int port;


    @Autowired
    ConsentHandler consentHandler;

    @Autowired
    public FhirControllerIT(DirectResourceLoader transformer, DataStore dataStore, StructureDefinitionHandler cds, FhirContext fhirContext, ObjectMapper objectMapper, CqlClient cqlClient, Translator cqlQueryTranslator, DseMappingTreeBase dseMappingTreeBase) {
        this.transformer = transformer;
        this.dataStore = dataStore;
        this.cds = cds;
        this.fhirContext = fhirContext;
        this.objectMapper = objectMapper;
        this.cqlClient = cqlClient;
        this.cqlQueryTranslator = cqlQueryTranslator;
        this.dseMappingTreeBase = dseMappingTreeBase;
        this.manager = new ContainerManager();


    }

    @BeforeAll
    void init() throws IOException {

        manager.startContainers();

        webClient.post().bodyValue(Files.readString(Path.of(testPopulationPath))).header("Content-Type", "application/fhir+json").retrieve().toBodilessEntity().block();
        logger.info("Data Import on {}", webClient.options());
    }


    @Test
    public void testCapability() {
        TestRestTemplate restTemplate = new TestRestTemplate();

        HttpHeaders headers = new HttpHeaders();
        HttpEntity<String> entity = new HttpEntity<>(null, headers);

        ResponseEntity<String> response = restTemplate.exchange("http://localhost:" + port + "/fhir/metadata", HttpMethod.GET, entity, String.class);
        assertEquals(200, response.getStatusCode().value(), "Capability statement not working");
    }

    @Nested
    class Endpoint {

        @ParameterizedTest
        @ValueSource(strings = {"src/test/resources/CRTDL_Parameters/Parameters_observation_all_fields_without_refs.json", "src/test/resources/CRTDL_Parameters/Parameters_observation_all_fields_without_refs_patients.json"})
        public void validObservation(String parametersFile) throws PatientIdNotFoundException, IOException {
            HttpHeaders headers = new HttpHeaders();
            headers.add("content-type", "application/fhir+json");
            testExecutor(parametersFile, "http://localhost:" + port + "/fhir/$extract-data", headers, 200);
        }

        @Test
        public void emptyRequestBodyReturnsBadRequest() {
            TestRestTemplate restTemplate = new TestRestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.valueOf("application/fhir+json"));
            HttpEntity<String> entity = new HttpEntity<>("", headers);

            ResponseEntity<String> response = restTemplate.postForEntity("http://localhost:" + port + "/fhir/$extract-data", entity, String.class);

            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(response.getBody()).contains("OperationOutcome");
            assertThat(response.getBody()).contains("Empty request body");
        }

        @ParameterizedTest
        @ValueSource(strings = {"src/test/resources/CRTDL_Parameters/Parameters_invalid_CRTDL.json"})
        public void invalidCRTDLReturnsValidationException(String parametersFile) throws IOException {
            HttpHeaders headers = new HttpHeaders();
            headers.add("content-type", "application/fhir+json");
            testExecutor(parametersFile, "http://localhost:" + port + "/fhir/$extract-data", headers, 400);
        }


    }


    @Test
    public void testFlare() throws IOException {
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
        assertEquals(4, patientCount);
    }

    @Test
    public void testCql() throws IOException {
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

    /*  @Test
      public void testFhirSearchCondition() throws IOException, PatientIdNotFoundException, ValidationException {
          executeTest(List.of(RESOURCE_PATH_PREFIX + "DataStoreIT/expectedOutput/diagnosis_basic_bundle.json"), List.of(RESOURCE_PATH_PREFIX + "CRTDL/CRTDL_diagnosis_basic_date.json", RESOURCE_PATH_PREFIX + "CRTDL/CRTDL_diagnosis_basic_code.json", RESOURCE_PATH_PREFIX + "CRTDL/CRTDL_diagnosis_basic.json"));
      }

    @Test
    public void testFhirSearchObservation() throws IOException, PatientIdNotFoundException, ValidationException {
        executeTest(List.of(RESOURCE_PATH_PREFIX + "DataStoreIT/expectedOutput/observation_basic_bundle_id3.json"), List.of(RESOURCE_PATH_PREFIX + "CRTDL/CRTDL_observation.json"));
    }

    @Test

    /*public void testFhirSearchConditionObservation() throws IOException, PatientIdNotFoundException, ValidationException {
        executeTest(List.of(RESOURCE_PATH_PREFIX + "DataStoreIT/expectedOutput/observation_diagnosis_basic_bundle_id3.json"), List.of(RESOURCE_PATH_PREFIX + "CRTDL/CRTDL_diagnosis_observation.json"));
    }
*/
  /*  @Test
    public void testAllFields() throws IOException, PatientIdNotFoundException {
        executeTest(List.of(RESOURCE_PATH_PREFIX + "DataStoreIT/expectedOutput/all_fields_patient_3.json"), List.of(RESOURCE_PATH_PREFIX + "CRTDL/CRTDL_all_fields.json"));
    }
*/

    @Test
    public void testMustHave() throws IOException, ValidationException {

        FileInputStream fis = new FileInputStream(RESOURCE_PATH_PREFIX + "CRTDL/CRTDL_observation_must_have.json");
        Crtdl crtdl = objectMapper.readValue(fis, Crtdl.class);
        PatientBatchWithConsent patients = PatientBatchWithConsent.fromBatch(PatientBatch.of("3"));
        AnnotatedCrtdl annotatedCrtdl = validatorService.validate(crtdl);
        Mono<PatientBatchWithConsent> collectedResourcesMono = transformer.directLoadPatientCompartment(annotatedCrtdl.dataExtraction().attributeGroups(), patients, crtdl.consentKey());
        PatientBatchWithConsent result = collectedResourcesMono.block(); // Blocking to get the result
        assert result != null;

        assertTrue(result.bundles().isEmpty());
        fis.close();
    }

    @Test
    public void testCoreMustHave() throws IOException, ValidationException {

        FileInputStream fis = new FileInputStream(RESOURCE_PATH_PREFIX + "CRTDL/CRTDL_medication_must_have.json");
        Crtdl crtdl = objectMapper.readValue(fis, Crtdl.class);
        AnnotatedCrtdl annotatedCrtdl = validatorService.validate(crtdl);
        Mono<ResourceBundle> collectedResourcesMono = transformer.proccessCoreAttributeGroups(annotatedCrtdl.dataExtraction().attributeGroups(), new ResourceBundle());

        // Verify that the Mono fails with the expected exception
        StepVerifier.create(collectedResourcesMono)
                .expectError(MustHaveViolatedException.class)
                .verify();

        fis.close();
    }


    public void testExecutor(String filePath, String url, HttpHeaders headers, int expectedFinalCode) {
        TestRestTemplate restTemplate = new TestRestTemplate();
        try {
            String fileContent = Files.readString(Paths.get(filePath), StandardCharsets.UTF_8);
            HttpEntity<String> entity = new HttpEntity<>(fileContent, headers);

            try {
                long startTime = System.nanoTime();
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
                long durationMs = (System.nanoTime() - startTime) / 1_000_000;

                logger.debug("Duration: {} ms", durationMs);
                assertEquals(202, response.getStatusCode().value(), "Expected 202 from initial POST");
                assertTrue(durationMs < 100, "Initial response took too long: " + durationMs + "ms");

                String statusUrl = "http://localhost:" + port + Objects.requireNonNull(response.getHeaders().get("Content-Location")).getFirst();
                pollStatusEndpoint(restTemplate, headers, statusUrl, expectedFinalCode);

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
                    assertEquals(expectedCode, response.getStatusCode().value(), "Status endpoint returned unexpected code");
                    logger.debug(response.getBody());
                    if (response.getStatusCode().is4xxClientError() || response.getStatusCode().is5xxServerError()) {
                        assertThat(context.newJsonParser().parseResource(response.getBody())).isInstanceOf(OperationOutcome.class);
                    }
                } else if (response.getStatusCode().is4xxClientError() || response.getStatusCode().is5xxServerError()) {
                    Assertions.fail("Polling failed with unexpected error status: " + response.getStatusCode());
                    completed = true;
                }

                if (i >= 100) {
                    Assertions.fail("Polling failed: exceeded maximum attempts");
                }

            } catch (HttpStatusCodeException e) {
                logger.error("HTTP Status code error: {}", e.getStatusCode(), e);
                logger.error("Response Body: {}", e.getResponseBodyAsString());
                if (e.getStatusCode().is4xxClientError() || e.getStatusCode().is5xxServerError()) {
                    Assertions.fail("Polling status endpoint failed with status code: " + e.getStatusCode());
                    completed = true;
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


}
