package de.medizininformatikinitiative.torch;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import de.medizininformatikinitiative.torch.model.Crtdl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;


@SpringBootTest(classes = Torch.class,webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class FlareIT extends AbstractIT {

    @Autowired
    public FlareIT(@Qualifier("fhirClient") WebClient webClient,
                   @Qualifier("flareClient") WebClient flareClient, ResourceTransformer transformer, DataStore dataStore, CdsStructureDefinitionHandler cds, FhirContext context, IParser parser, BundleCreator bundleCreator, ObjectMapper objectMapper) {
        super(webClient, flareClient, transformer, dataStore, cds, context, parser, bundleCreator, objectMapper);
    }


    @Test
    public void testFlare() throws IOException {
        FileInputStream fis = new FileInputStream("src/test/resources/CRTDL/CRTDL_diagnosis_female.json");
        String jsonString = new Scanner(fis, StandardCharsets.UTF_8).useDelimiter("\\A").next();

        // Read the JSON file into a JsonNode

        JsonNode rootNode = objectMapper.readTree(jsonString);

        // Extract the cohortDefinition object
        JsonNode cohortDefinitionNode = rootNode.path("cohortDefinition");

        // Convert the cohortDefinition object to a JSON string
        String cohortDefinitionJson = objectMapper.writeValueAsString(cohortDefinitionNode);

        Crtdl crtdl = objectMapper.readValue(jsonString, Crtdl.class);
        crtdl.setSqString(cohortDefinitionJson);
        logger.info(crtdl.getStructuredQuery().toString());
        // Log the URL and headers
        logger.info("Request Headers: Content-Type=application/sq+json, Accept=application/json");
        logger.info("Request Body: {}", crtdl.getSqString());

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
        logger.info("Response Body: {}", responseBody);
        // Parse the response body as a list of patient IDs
        List<String> patientIds = objectMapper.readValue(responseBody, TypeFactory.defaultInstance().constructCollectionType(List.class, String.class));

        // Count the number of patient IDs
        int patientCount = patientIds.size();
        logger.info(String.valueOf(patientIds));
        Assertions.assertEquals(3, patientCount);
    }

}

