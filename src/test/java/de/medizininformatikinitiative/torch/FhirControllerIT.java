package de.medizininformatikinitiative.torch;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import de.medizininformatikinitiative.torch.config.AppConfig;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;

import de.medizininformatikinitiative.torch.model.Crtdl;
import de.medizininformatikinitiative.torch.rest.FhirController;
import org.hl7.fhir.r4.model.Bundle;

import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import reactor.core.publisher.Mono;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/*@Testcontainers
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AppConfig.class)
@SpringBootTest(classes = Torch.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
*/

@SpringBootTest(classes = Torch.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class FhirControllerIT extends AbstractIT {

    private static final String RESOURCE_PATH_PREFIX = "src/test/resources/";

    @LocalServerPort
    private int port;

    @Autowired
    FhirController fhirController;

    @Autowired
    public FhirControllerIT(   @Qualifier("fhirClient") WebClient webClient,
                               @Qualifier("flareClient") WebClient flareClient, ResourceTransformer transformer, DataStore dataStore, CdsStructureDefinitionHandler cds, FhirContext context, IParser parser, BundleCreator bundleCreator, ObjectMapper objectMapper) {
        super(webClient, flareClient, transformer, dataStore, cds, context, parser, bundleCreator, objectMapper);
    }


    @Test
    public void testCapability() {
        TestRestTemplate restTemplate = new TestRestTemplate();

        HttpHeaders headers = new HttpHeaders();
        HttpEntity<String> entity = new HttpEntity<>(null, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/fhir/metadata",
                HttpMethod.GET, entity, String.class);
        Assertions.assertEquals(200, response.getStatusCode().value(), "Capability statement not working");
    }


    @Test
    public void testExtractEndpoint() throws PatientIdNotFoundException, IOException {
        HttpHeaders headers = new HttpHeaders();

        headers.add("content-type", "application/fhir+json");
        List<String> expectedResourceFilePaths = List.of(
                "src/test/resources/DataStoreIT/expectedOutput/diagnosis_basic_bundle.json"
        );

        List<String> filePaths = List.of(
                "src/test/resources/CRTDL_Library/Parameters_all_fields.json");
        testExecutor(filePaths, expectedResourceFilePaths, "http://localhost:" + port + "/fhir/$extract-data", headers);
    }

    @Test
    public void testFlare() throws IOException {
        FileInputStream fis = new FileInputStream(RESOURCE_PATH_PREFIX+"CRTDL/CRTDL_diagnosis_female.json");
        String jsonString = new Scanner(fis, StandardCharsets.UTF_8).useDelimiter("\\A").next();

        // Read the JSON file into a JsonNode

        JsonNode rootNode = objectMapper.readTree(jsonString);

        // Extract the cohortDefinition object
        JsonNode cohortDefinitionNode = rootNode.path("cohortDefinition");

        // Convert the cohortDefinition object to a JSON string
        String cohortDefinitionJson = objectMapper.writeValueAsString(cohortDefinitionNode);

        Crtdl crtdl = objectMapper.readValue(jsonString, Crtdl.class);
        crtdl.setSqString(cohortDefinitionJson);

        // Use the serialized JSON string in the bodyValue method and capture the response
        String responseBody = flareClient.post()
                .uri("/query/execute-cohort")
                .contentType(MediaType.parseMediaType("application/sq+json"))
                .bodyValue(crtdl.getSqString())
                .retrieve()
                .onStatus(status -> status.value() == 404, clientResponse -> {
                    logger.error("Received 404 Not Found");
                    return clientResponse.createException();
                })
                .bodyToMono(String.class)
                .block();  // Blocking to get the response synchronously

        // Log the response body
        logger.debug("Response Body: {}", responseBody);
        // Parse the response body as a list of patient IDs
        List<String> patientIds = objectMapper.readValue(responseBody, TypeFactory.defaultInstance().constructCollectionType(List.class, String.class));

        // Count the number of patient IDs
        int patientCount = patientIds.size();
        logger.info(String.valueOf(patientIds));
        Assertions.assertEquals(3, patientCount);
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
                ),
                1
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
                ),
                1
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
                ),
                1
        );
    }

    @Test
    public void testAllFields() throws IOException, PatientIdNotFoundException {
        executeTest(
                List.of(
                        RESOURCE_PATH_PREFIX + "DataStoreIT/expectedOutput/observation_all_fields.json"
                ),
                List.of(
                        RESOURCE_PATH_PREFIX + "CRTDL/CRTDL_observation_all_fields.json"
                ),
                1
        );
    }



    @Test
    public void testFhirSearchBatch() throws IOException, PatientIdNotFoundException {
        executeTest(
                List.of(
                        RESOURCE_PATH_PREFIX + "DataStoreIT/expectedOutput/observation_basic_bundle_id3.json",
                        RESOURCE_PATH_PREFIX + "DataStoreIT/expectedOutput/observation_basic_bundle_id4.json"
                ),
                List.of(
                        RESOURCE_PATH_PREFIX + "CRTDL/CRTDL_observation.json"
                ),
                2
        );
    }

    @Test
    public void testMustHave() throws IOException {

        FileInputStream fis = new FileInputStream(RESOURCE_PATH_PREFIX+"CRTDL/CRTDL_observation_must_have.json");
        Crtdl crtdl = objectMapper.readValue(fis, Crtdl.class);
        logger.info("ResourceType {}", crtdl.getResourceType());
        List<String> patients = new ArrayList<>();
        patients.add("3");
        Mono<Map<String, Collection<Resource>>> collectedResourcesMono = transformer.collectResourcesByPatientReference(crtdl, patients, 1);
        Map<String, Collection<Resource>> result = collectedResourcesMono.block(); // Blocking to get the result
        assert result != null;
        Assertions.assertTrue(result.isEmpty());

    }


    private void executeTest(List<String> expectedResourceFilePaths, List<String> filePaths, int batchSize) throws IOException, PatientIdNotFoundException {
        Map<String, Bundle> expectedResources = loadExpectedResources(expectedResourceFilePaths);
        expectedResources.values().forEach(Assertions::assertNotNull);
        List<String> patients = new ArrayList<>(expectedResources.keySet());

        for (String filePath : filePaths) {
            processFile(filePath, patients, expectedResources, batchSize);
        }
    }



    public void testExecutor(List<String> filePaths, List<String> expectedResourceFilePaths, String url, HttpHeaders headers) throws PatientIdNotFoundException, IOException {
        TestRestTemplate restTemplate = new TestRestTemplate();
        Map<String, Bundle> expectedResources = loadExpectedResources(expectedResourceFilePaths);
        expectedResources.values().forEach(Assertions::assertNotNull);
        filePaths.forEach(filePath -> {
            try {
                String fileContent = Files.readString(Paths.get(filePath), StandardCharsets.UTF_8);
                // Read the JSON file into a JsonNode

                JsonNode rootNode = objectMapper.readTree(fileContent);

                // Extract the cohortDefinition object
                JsonNode cohortDefinitionNode = rootNode.path("cohortDefinition");

                // Convert the cohortDefinition object to a JSON string
                String cohortDefinitionJson = objectMapper.writeValueAsString(cohortDefinitionNode);

                Crtdl crtdl = objectMapper.readValue(fileContent, Crtdl.class);

                crtdl.setSqString(cohortDefinitionJson);

                List<String>result =fhirController.fetchPatientListFromFlare(crtdl).block();
                logger.info("Flare Call direct {}", result);

                HttpEntity<String> entity = new HttpEntity<>(fileContent, headers);
                try {
                    ResponseEntity<String> response = restTemplate.exchange(
                            url,
                            HttpMethod.POST, entity, String.class);
                    logger.info("Got the following response{}", response.toString());
                    Assertions.assertEquals(202, response.getStatusCode().value(), "Endpoint not accepting crtdl");

                    // Polling the status endpoint
                    pollStatusEndpoint(restTemplate, headers, "http://localhost:" + port+ Objects.requireNonNull(response.getHeaders().get("Content-Location")).getFirst());
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
                ResponseEntity<String> response = restTemplate.exchange(
                        statusUrl,
                        HttpMethod.GET, entity, String.class);
                logger.info(" Call result {} {}", i, response);
                if (response.getStatusCode().value() == 200) {
                    completed = true;
                    Assertions.assertEquals(200, response.getStatusCode().value(), "Status endpoint did not return 200");
                }
                if(response.getStatusCode().is4xxClientError() || response.getStatusCode().is5xxServerError()) {
                    Assertions.fail("Polling status endpoint failed with status code: " + response.getStatusCode());
                    completed = true;
                }
                if(i==100){
                    Assertions.fail("Polling status endpoint failed running out of calls ");
                }
            } catch (HttpStatusCodeException e) {
                logger.error("HTTP Status code error: {}", e.getStatusCode(), e);
                if (e.getStatusCode().is4xxClientError() || e.getStatusCode().is5xxServerError()) {
                    Assertions.fail("Polling status endpoint failed with status code: " + e.getStatusCode());
                    completed = true;
                }
            }
            try {
                Thread.sleep(200); // Delay between polls (e.g., 2 seconds)
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Polling was interrupted", e);
            }
        }
    }

}