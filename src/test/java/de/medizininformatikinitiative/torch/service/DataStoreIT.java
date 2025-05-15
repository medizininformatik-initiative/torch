package de.medizininformatikinitiative.torch.service;

import ca.uhn.fhir.parser.IParser;
import de.medizininformatikinitiative.torch.Torch;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.main.allow-bean-definition-overriding=true"}, classes = Torch.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DataStoreIT {

    @Autowired
    DataStore dataStore;


    @Autowired
    @Qualifier("fhirClient")
    WebClient webClient;
    @Autowired
    @Value("${torch.fhir.testPopulation.path}")
    String testPopulationPath;
    IntegrationTestSetup integrationTestSetup = new IntegrationTestSetup();
    IParser parser = integrationTestSetup.fhirContext().newJsonParser();
    String BATCH = """
            {
                "resourceType": "Bundle",
                "type": "batch",
                "meta": {
                  "lastUpdated": "2024-01-01T00:00:00Z"
                },
                "entry": [
                  {
                    "request": {
                      "method": "GET",
                      "url": "Patient?_id=1,2"
                    }
                  },
                  {
                    "request": {
                      "method": "GET",
                      "url": "Observation?_id=123"
                    }
                  }
                ]
              }
            """;

    String BATCH_EMPTY = """
            {
                "resourceType": "Bundle",
                "type": "batch",
                "meta": {
                  "lastUpdated": "2024-01-01T00:00:00Z"
                },
                "entry": [
                  {
                    "request": {
                      "method": "GET",
                      "url": "Observation?_id=123"
                    }
                  }
                ]
              }
            """;

    @BeforeAll
    void init() throws IOException {
        webClient.post().bodyValue(Files.readString(Path.of(testPopulationPath))).header("Content-Type", "application/fhir+json").retrieve().toBodilessEntity().block();
    }


    @Test
    void successSearch() {
        var result = dataStore.search(Query.ofType("Observation"), Observation.class);
        StepVerifier.create(result)
                .recordWith(ArrayList::new)
                .expectNextCount(5)
                .consumeRecordedWith(resources -> resources.forEach(resource ->
                        assertThat(resource.getResourceType()).hasToString("Observation")
                ))
                .verifyComplete();

    }


    @Test
    void successReferenceFetch() {
        Bundle batch = parser.parseResource(Bundle.class, BATCH);
        batch.getMeta().setLastUpdated(new Date());
        Flux<Resource> result = dataStore.fetchResourcesByReferences(batch);
        StepVerifier.create(result)
                .expectNextMatches(resource -> resource instanceof Patient)
                .expectNextMatches(resource -> resource instanceof Patient)
                .verifyComplete();
    }

    @Test
    void emptyReferenceFetch() {
        Bundle batch = parser.parseResource(Bundle.class, BATCH_EMPTY);
        batch.getMeta().setLastUpdated(new Date());
        Flux<Resource> result = dataStore.fetchResourcesByReferences(batch);
        StepVerifier.create(result)
                .verifyComplete();
    }

}
