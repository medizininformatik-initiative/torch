package de.medizininformatikinitiative.torch;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import de.medizininformatikinitiative.torch.cql.CqlClient;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.model.Crtdl;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;
import de.medizininformatikinitiative.torch.setup.ContainerManager;
import de.medizininformatikinitiative.torch.testUtil.FhirTestHelper;
import de.medizininformatikinitiative.torch.util.ResourceReader;
import org.hl7.fhir.r4.model.*;
import de.medizininformatikinitiative.torch.service.DataStore;
import de.numcodex.sq2cql.Translator;
import de.numcodex.sq2cql.model.structured_query.StructuredQuery;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.junit.BeforeClass;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
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
import java.util.*;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.main.allow-bean-definition-overriding=true"}, classes = Torch.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class FhirControllerIT {

    protected static final Logger logger = LoggerFactory.getLogger(FhirControllerIT.class);

    protected static boolean dataImported = false;

    @Autowired
    FhirTestHelper fhirTestHelper;

    @Autowired
    @Qualifier("fhirClient")
    protected WebClient webClient;

    @Autowired
    ContainerManager manager;

    @Autowired
    @Qualifier("flareClient")
    protected WebClient flareClient;


    protected final ResourceTransformer transformer;
    protected final DataStore dataStore;
    protected final CdsStructureDefinitionHandler cds;
    protected BundleCreator bundleCreator;
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
    public FhirControllerIT( ResourceTransformer transformer, DataStore dataStore, CdsStructureDefinitionHandler cds, FhirContext fhirContext, BundleCreator bundleCreator, ObjectMapper objectMapper, CqlClient cqlClient,
                            Translator cqlQueryTranslator, DseMappingTreeBase dseMappingTreeBase) {
        this.transformer = transformer;
        this.dataStore = dataStore;
        this.cds = cds;
        this.fhirContext = fhirContext;
        this.bundleCreator = bundleCreator;
        this.objectMapper = objectMapper;
        this.cqlClient = cqlClient;
        this.cqlQueryTranslator = cqlQueryTranslator;
        this.dseMappingTreeBase = dseMappingTreeBase;


    }

    @BeforeEach
    void init() throws IOException {
        if (!dataImported) {
        manager.startContainers();


            webClient.post()
                    .bodyValue(Files.readString(Path.of(testPopulationPath)))
                    .header("Content-Type", "application/fhir+json")
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            dataImported = true;
            logger.info("Data Import on {}", webClient.options());
        }
        logger.info("Setup Complete");
    }

    @Test
    @DirtiesContext
    public void testCapability() {
        TestRestTemplate restTemplate = new TestRestTemplate();

        HttpHeaders headers = new HttpHeaders();
        HttpEntity<String> entity = new HttpEntity<>(null, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/fhir/metadata",
                HttpMethod.GET, entity, String.class);
        assertEquals(200, response.getStatusCode().value(), "Capability statement not working");
    }

    @Test
    @DirtiesContext
    public void testExtractEndpoint() throws PatientIdNotFoundException, IOException {
        HttpHeaders headers = new HttpHeaders();

        headers.add("content-type", "application/fhir+json");
        List<String> expectedResourceFilePaths = List.of(
                "src/test/resources/DataStoreIT/expectedOutput/diagnosis_basic_bundle.json"
        );

        List<String> filePaths = List.of(
                "src/test/resources/CRTDL_Parameters/Parameters_all_fields.json");
        testExecutor(filePaths, expectedResourceFilePaths, "http://localhost:" + port + "/fhir/$extract-data", headers);
    }

    @Test
    public void testExtractEndpointConsent() throws PatientIdNotFoundException, IOException {
        HttpHeaders headers = new HttpHeaders();

        headers.add("content-type", "application/fhir+json");
        List<String> expectedResourceFilePaths = List.of(
                "src/test/resources/DataStoreIT/expectedOutput/diagnosis_basic_bundle.json"
        );

        List<String> filePaths = List.of(
                "src/test/resources/CRTDL_Parameters/Paremeters_all_fields_consent.json");
        testExecutor(filePaths, expectedResourceFilePaths, "http://localhost:" + port + "/fhir/$extract-data", headers);
    }




    @Test
    public void testFlare() throws IOException {
        FileInputStream fis = new FileInputStream(RESOURCE_PATH_PREFIX + "CRTDL/CRTDL_diagnosis_female.json");
        String jsonString = new Scanner(fis, StandardCharsets.UTF_8).useDelimiter("\\A").next();
        Crtdl crtdl = objectMapper.readValue(jsonString, Crtdl.class);
        fis.close();
        String responseBody = flareClient.post()
                .uri("/query/execute-cohort")
                .contentType(MediaType.parseMediaType("application/sq+json"))
                .bodyValue(crtdl.getCohortDefinition().toString())
                .retrieve()
                .onStatus(status -> status.value() == 404, clientResponse -> {
                    logger.error("Received 404 Not Found");
                    return clientResponse.createException();
                })
                .bodyToMono(String.class)
                .block();  // Blocking to get the response synchronously

        logger.debug("Response Body: {}", responseBody);
        List<String> patientIds = objectMapper.readValue(responseBody, TypeFactory.defaultInstance().constructCollectionType(List.class, String.class));

        int patientCount = patientIds.size();
        logger.info(String.valueOf(patientIds));
        assertEquals(4, patientCount);
    }

    @Test
    public void testCql() throws IOException {
        FileInputStream fis = new FileInputStream(RESOURCE_PATH_PREFIX + "CRTDL/CRTDL_diagnosis_female.json");
        String jsonString = new Scanner(fis, StandardCharsets.UTF_8).useDelimiter("\\A").next();
        Crtdl crtdl = objectMapper.readValue(jsonString, Crtdl.class);

        var ccdl = objectMapper.treeToValue(crtdl.getCohortDefinition(), StructuredQuery.class);

        Mono<List<String>> patientListMono = cqlClient.getPatientListByCql(cqlQueryTranslator.toCql(ccdl).print());
        List<String> patientList = patientListMono.block();

        List<String> expectedPatientList = Arrays.asList("1", "2", "4", "VHF00006");
        assertEquals(expectedPatientList.size(), patientList.size(), "Patient list size mismatch");
        assertIterableEquals(expectedPatientList, patientList, "Patient list contents mismatch");
        fis.close();
    }


    @Test
    public void testFhirSearchCondition() throws IOException, PatientIdNotFoundException {
        executeTest(
                List.of(
                        RESOURCE_PATH_PREFIX + "DataStoreIT/expectedOutput/diagnosis_basic_bundle.json"
                ),
                List.of(
                        RESOURCE_PATH_PREFIX + "CRTDL/CRTDL_diagnosis_basic_date.json",
                        RESOURCE_PATH_PREFIX + "CRTDL/CRTDL_diagnosis_basic_code.json",
                        RESOURCE_PATH_PREFIX + "CRTDL/CRTDL_diagnosis_basic.json"
                )
        );
    }

    @Test
    public void testFhirSearchObservation() throws IOException, PatientIdNotFoundException {
        executeTest(
                List.of(
                        RESOURCE_PATH_PREFIX + "DataStoreIT/expectedOutput/observation_basic_bundle_id3.json"
                ),
                List.of(
                        RESOURCE_PATH_PREFIX + "CRTDL/CRTDL_observation.json"
                )
        );
    }

    @Test

    public void testFhirSearchConditionObservation() throws IOException, PatientIdNotFoundException {
        executeTest(
                List.of(
                        RESOURCE_PATH_PREFIX + "DataStoreIT/expectedOutput/observation_diagnosis_basic_bundle_id3.json"
                ),
                List.of(
                        RESOURCE_PATH_PREFIX + "CRTDL/CRTDL_diagnosis_observation.json"
                )
        );
    }

    @Test
    public void testAllFields() throws IOException, PatientIdNotFoundException {
        executeTest(
                List.of(
                        RESOURCE_PATH_PREFIX + "DataStoreIT/expectedOutput/observation_all_fields.json"
                ),
                List.of(
                        RESOURCE_PATH_PREFIX + "CRTDL/CRTDL_all_fields.json"
                )
        );
    }


    @Test
    public void testMustHave() throws IOException {

        FileInputStream fis = new FileInputStream(RESOURCE_PATH_PREFIX + "CRTDL/CRTDL_observation_must_have.json");
        Crtdl crtdl = objectMapper.readValue(fis, Crtdl.class);
        logger.info("ResourceType {}", crtdl.getResourceType());
        List<String> patients = new ArrayList<>();
        patients.add("3");
        Mono<Map<String, Collection<Resource>>> collectedResourcesMono = transformer.collectResourcesByPatientReference(crtdl, patients);
        Map<String, Collection<Resource>> result = collectedResourcesMono.block(); // Blocking to get the result
        assert result != null;
        Assertions.assertTrue(result.isEmpty());
        fis.close();
    }


    private void executeTest(List<String> expectedResourceFilePaths, List<String> filePaths) throws IOException, PatientIdNotFoundException {
        Map<String, Bundle> expectedResources = fhirTestHelper.loadExpectedResources(expectedResourceFilePaths);
        expectedResources.values().forEach(Assertions::assertNotNull);
        List<String> patients = new ArrayList<>(expectedResources.keySet());

        for (String filePath : filePaths) {
            processFile(filePath, patients, expectedResources);
        }
    }

    private void processFile(String filePath, List<String> patients, Map<String, Bundle> expectedResources) {
        try (FileInputStream fis = new FileInputStream(filePath)) {
            Crtdl crtdl = objectMapper.readValue(fis, Crtdl.class);
            Mono<Map<String, Collection<Resource>>> collectedResourcesMono = transformer.collectResourcesByPatientReference(crtdl, patients);

            StepVerifier.create(collectedResourcesMono)
                    .expectNextMatches(combinedResourcesByPatientId -> {
                        Map<String, Bundle> bundles = bundleCreator.createBundles(combinedResourcesByPatientId);
                        fhirTestHelper.validateBundles(bundles, expectedResources);
                        return true;
                    })
                    .expectComplete()
                    .verify();
        } catch (IOException e) {
            logger.error("CRTDL file not found: {}", filePath, e);
        }
    }

    public void testExecutor(List<String> filePaths, List<String> expectedResourceFilePaths, String url, HttpHeaders headers) throws PatientIdNotFoundException, IOException {
        TestRestTemplate restTemplate = new TestRestTemplate();
        Map<String, Bundle> expectedResources = fhirTestHelper.loadExpectedResources(expectedResourceFilePaths);
        expectedResources.values().forEach(Assertions::assertNotNull);
        filePaths.forEach(filePath -> {
            try {
                String fileContent = Files.readString(Paths.get(filePath), StandardCharsets.UTF_8);
                // Read the JSON file into a JsonNode

                HttpEntity<String> entity = new HttpEntity<>(fileContent, headers);
                try {
                    ResponseEntity<String> response = restTemplate.exchange(
                            url,
                            HttpMethod.POST, entity, String.class);
                    logger.info("Got the following response{}", response.toString());
                    assertEquals(202, response.getStatusCode().value(), "Endpoint not accepting crtdl");

                    // Polling the status endpoint
                    pollStatusEndpoint(restTemplate, headers, "http://localhost:" + port + Objects.requireNonNull(response.getHeaders().get("Content-Location")).getFirst());
                } catch (HttpStatusCodeException e) {
                    logger.error("HTTP Status code error: {}", e.getStatusCode(), e);
                    Assertions.fail("HTTP request failed with status code: " + e.getStatusCode());
                }

            } catch (IOException e) {
                logger.error("CRTDL file not found", e);
            }
        });
    }

    private void pollStatusEndpoint(TestRestTemplate restTemplate, HttpHeaders headers, String statusUrl) {
        boolean completed = false;
        int i = 0;

        while (!completed) {
            try {
                i++;
                HttpEntity<String> entity = new HttpEntity<>(null, headers);
                logger.info("Status URL {}", statusUrl);

                // Perform the exchange and get the response
                ResponseEntity<String> response = restTemplate.exchange(
                        statusUrl,
                        HttpMethod.GET, entity, String.class);

                // Log the full call result and response body
                logger.info("Call result {} {}", i, response);
                logger.info("Response Body: {}", response.getBody());

                // Check the status code
                if (response.getStatusCode().value() == 200) {
                    completed = true;
                    assertEquals(200, response.getStatusCode().value(), "Status endpoint did not return 200");
                    logger.debug("Final Response {}", response.getBody());
                }
                if (response.getStatusCode().is4xxClientError() || response.getStatusCode().is5xxServerError()) {
                    logger.error("Polling status endpoint failed with status code: {}", response.getStatusCode());
                    Assertions.fail("Polling status endpoint failed with status code: " + response.getStatusCode());
                    completed = true;
                }
                if (i == 100) {
                    Assertions.fail("Polling status endpoint failed running out of calls.");
                }
            } catch (HttpStatusCodeException e) {
                // Log the HTTP error and response body (if available)
                logger.error("HTTP Status code error: {}", e.getStatusCode(), e);
                logger.error("Response Body: {}", e.getResponseBodyAsString());

                if (e.getStatusCode().is4xxClientError() || e.getStatusCode().is5xxServerError()) {
                    Assertions.fail("Polling status endpoint failed with status code: " + e.getStatusCode());
                    completed = true;
                }
            }

            try {
                Thread.sleep(200); // Delay between polls (e.g., 200ms)
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Polling was interrupted", e);
            }
        }
    }


    @Test
    public void testHandlerWithUpdate() {
        List<String> strings = new ArrayList<>();
        strings.add("VHF00006");

        // Reading resource
        Resource observation = null;
        try {
            //Observation with Consent outside the consent, but inside with encounter within
            observation = ResourceReader.readResource("src/test/resources/InputResources/Observation/Observation_lab_vhf_00006.json");
            DateTimeType time= new DateTimeType("2020-01-01T00:00:00+01:00");
            ((Observation)observation).setEffective(time);
            // Build consent information as a Flux
            Flux<Map<String, Map<String, List<Period>>>> consentInfoFlux = consentHandler.buildingConsentInfo("yes-yes-yes-yes", strings);

            consentInfoFlux = consentHandler.updateConsentPeriodsByPatientEncounters(consentInfoFlux, strings);

            // Collect the Flux into a List of Maps, without altering its structure
            List<Map<String, Map<String, List<Period>>>> consentInfoList = consentInfoFlux.collectList().block();

            // Assuming you need the first element from the list
            Map<String, Map<String, List<Period>>> consentInfo = consentInfoList.get(0);

            // Now pass the Map (instead of the Flux) to checkConsent
            Boolean consentInfoResult = consentHandler.checkConsent((DomainResource) observation, consentInfo);

            assertTrue(consentInfoResult);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }



    @Test
    public void testHandlerWithoutUpdate() {
        List<String> strings = new ArrayList<>();
        strings.add("VHF00006");

        // Reading resource
        Resource observation = null;
        try {
            //Observation with Consent outside the consent, but inside with encounter within
            observation = ResourceReader.readResource("src/test/resources/InputResources/Observation/Observation_lab_vhf_00006.json");
            DateTimeType time= new DateTimeType("2022-01-01T00:00:00+01:00");
            ((Observation)observation).setEffective(time);
            // Build consent information as a Flux
            Flux<Map<String, Map<String, List<Period>>>> consentInfoFlux = consentHandler.buildingConsentInfo("yes-yes-yes-yes", strings);

            consentInfoFlux = consentHandler.updateConsentPeriodsByPatientEncounters(consentInfoFlux, strings);

            // Collect the Flux into a List of Maps, without altering its structure
            List<Map<String, Map<String, List<Period>>>> consentInfoList = consentInfoFlux.collectList().block();

            // Assuming you need the first element from the list
            Map<String, Map<String, List<Period>>> consentInfo = consentInfoList.get(0);

            // Now pass the Map (instead of the Flux) to checkConsent
            Boolean consentInfoResult = consentHandler.checkConsent((DomainResource) observation, consentInfo);

            assertTrue(consentInfoResult);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }

    @Test
    public void testHandlerWithUpdatingFail() {
        List<String> strings = new ArrayList<>();
        strings.add("VHF00006");

        // Reading resource
        Resource observation = null;
        try {
            observation = ResourceReader.readResource("src/test/resources/InputResources/Observation/Observation_lab_vhf_00006.json");
            DateTimeType time= new DateTimeType("2026-01-01T00:00:00+01:00");
            ((Observation)observation).setEffective(time);
            // Build consent information as a Flux
            Flux<Map<String, Map<String, List<Period>>>> consentInfoFlux = consentHandler.buildingConsentInfo("yes-yes-yes-yes", strings);

            // Collect the Flux into a List of Maps, without altering its structure
            List<Map<String, Map<String, List<Period>>>> consentInfoList = consentInfoFlux.collectList().block();

            // Assuming you need the first element from the list
            Map<String, Map<String, List<Period>>> consentInfo = consentInfoList.get(0);

            // Now pass the Map (instead of the Flux) to checkConsent
            Boolean consentInfoResult = consentHandler.checkConsent((DomainResource) observation, consentInfo);

            assertFalse(consentInfoResult);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }



    @Test
    public void testHandlerWithoutUpdatingFail() {
        List<String> strings = new ArrayList<>();
        strings.add("VHF00006");

        // Reading resource
        Resource observation = null;
        try {
            observation = ResourceReader.readResource("src/test/resources/InputResources/Observation/Observation_lab_vhf_00006.json");
            DateTimeType time= new DateTimeType("2020-01-01T00:00:00+01:00");
            ((Observation)observation).setEffective(time);
            // Build consent information as a Flux
            Flux<Map<String, Map<String, List<Period>>>> consentInfoFlux = consentHandler.buildingConsentInfo("yes-yes-yes-yes", strings);

            // Collect the Flux into a List of Maps, without altering its structure
            List<Map<String, Map<String, List<Period>>>> consentInfoList = consentInfoFlux.collectList().block();

            // Assuming you need the first element from the list
            Map<String, Map<String, List<Period>>> consentInfo = consentInfoList.get(0);

            // Now pass the Map (instead of the Flux) to checkConsent
            Boolean consentInfoResult = consentHandler.checkConsent((DomainResource) observation, consentInfo);

            assertFalse(consentInfoResult);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }

}
