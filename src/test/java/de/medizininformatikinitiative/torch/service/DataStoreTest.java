package de.medizininformatikinitiative.torch.service;


import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Instant;

import static org.hl7.fhir.r4.model.ResourceType.Patient;

class DataStoreTest {

    private static final Instant FIXED_INSTANT = Instant.ofEpochSecond(104152);
    public static final String PATIENT_BUNDLE = """
            {
              "resourceType": "Bundle",
              "type": "searchset",
              "entry": [
                {
                  "resource": {
                    "resourceType": "Patient"
                  }
                }
              ]
            }
            """;
    private MockWebServer mockStore;

    private DataStore dataStore;


    @BeforeEach
    void initialize() throws IOException {
        mockStore = new MockWebServer();
        mockStore.start();
        FhirContext ctx = FhirContext.forR4();
        WebClient client = WebClient.builder()
                .baseUrl("http://localhost:%d/fhir".formatted(mockStore.getPort()))
                .defaultHeader("Accept", "application/fhir+json")
                .build();
        dataStore = new DataStore(client, ctx, 1000);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockStore.shutdown();
    }


    @Nested
    class Search {

        @Test
        @DisplayName("fails after 3 unsuccessful retries")
        void retryFails() {
            mockStore.enqueue(new MockResponse().setResponseCode(500));
            mockStore.enqueue(new MockResponse().setResponseCode(500));
            mockStore.enqueue(new MockResponse().setResponseCode(500));
            mockStore.enqueue(new MockResponse().setResponseCode(500));
            mockStore.enqueue(new MockResponse().setResponseCode(200));

            var result = dataStore.search(Query.ofType("Observation"));

            StepVerifier.create(result).verifyErrorMessage("Retries exhausted: 3/3");
        }

        @Test
        @DisplayName("doesn't retry a 400")
        void errorNoRetry() {
            mockStore.enqueue(new MockResponse().setResponseCode(400));

            var result = dataStore.search(Query.ofType("Observation"));

            StepVerifier.create(result).verifyError(WebClientResponseException.BadRequest.class);
        }

        @Test
        void emptyResult() {
            mockStore.enqueue(new MockResponse().setResponseCode(200));

            var result = dataStore.search(Query.ofType("Observation"));

            StepVerifier.create(result).verifyComplete();
        }

        @Test
        void fullResult() {
            mockStore.enqueue(new MockResponse().setResponseCode(200).setBody(PATIENT_BUNDLE));

            var result = dataStore.search(Query.ofType("Patient"));

            StepVerifier.create(result).expectNextMatches(resource -> resource.getResourceType() == Patient).verifyComplete();
        }


    }


}
