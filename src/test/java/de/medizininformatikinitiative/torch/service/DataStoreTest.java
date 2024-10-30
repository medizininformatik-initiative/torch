package de.medizininformatikinitiative.torch.service;


import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

class DataStoreTest {

    private static final Instant FIXED_INSTANT = Instant.ofEpochSecond(104152);
    private static MockWebServer mockStore;

    private DataStore dataStore;

    @BeforeAll
    static void setUp() throws IOException {
        mockStore = new MockWebServer();
        mockStore.start();
    }

    @AfterAll
    static void tearDown() throws IOException {
        mockStore.shutdown();
    }

    @BeforeEach
    void initialize() {
        FhirContext ctx = FhirContext.forR4();
        WebClient client = WebClient.builder()
                .baseUrl("http://localhost:%d/fhir".formatted(mockStore.getPort()))
                .defaultHeader("Accept", "application/fhir+json")
                .build();
        dataStore = new DataStore(client, ctx, Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC), 1000);
    }


    @Test
    @DisplayName("fails after 3 unsuccessful retries")
    void execute_retry_fails() {
        mockStore.enqueue(new MockResponse().setResponseCode(500));
        mockStore.enqueue(new MockResponse().setResponseCode(500));
        mockStore.enqueue(new MockResponse().setResponseCode(500));
        mockStore.enqueue(new MockResponse().setResponseCode(500));
        mockStore.enqueue(new MockResponse().setResponseCode(200));

        var result = dataStore.getResources(Query.ofType("Observation"));

        StepVerifier.create(result).expectErrorMessage("Retries exhausted: 3/3").verify();
    }

    @Test
    @DisplayName("doesn't retry a 400")
    void execute_retry_400() {
        mockStore.enqueue(new MockResponse().setResponseCode(400));

        var result = dataStore.getResources(Query.ofType("Observation"));

        StepVerifier.create(result).expectError(WebClientResponseException.BadRequest.class).verify();
    }
}
