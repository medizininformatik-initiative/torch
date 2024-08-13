package de.medizininformatikinitiative.torch;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.config.AppConfig;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;

import de.medizininformatikinitiative.torch.model.Crtdl;
import de.medizininformatikinitiative.torch.rest.FhirController;
import org.hl7.fhir.r4.model.Bundle;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

@Testcontainers
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AppConfig.class)
@SpringBootTest(classes = Torch.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class FhirControllerIT extends AbstractIT {

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
                "src/test/resources/CRTDL_Library/Bundle_Diagnosis_Female.json");
        testExecutor(filePaths, expectedResourceFilePaths, "http://localhost:" + port + "/fhir/$extract-data", headers);
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