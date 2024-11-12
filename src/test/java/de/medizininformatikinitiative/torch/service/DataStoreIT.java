package de.medizininformatikinitiative.torch.service;


import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.fhir.QueryParams;
import org.hl7.fhir.r4.model.Observation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.test.StepVerifier;

import java.util.stream.IntStream;

import static org.springframework.http.MediaType.APPLICATION_JSON;

@Testcontainers
class DataStoreIT {

    private static final Logger logger = LoggerFactory.getLogger(DataStoreIT.class);

    @Container
    @SuppressWarnings("resource")
    private final GenericContainer<?> blaze = new GenericContainer<>("samply/blaze:0.30")
            .withImagePullPolicy(PullPolicy.alwaysPull())
            .withEnv("LOG_LEVEL", "debug")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/health").forStatusCode(200))
            .withLogConsumer(new Slf4jLogConsumer(logger));

    private WebClient client;
    private DataStore dataStore;

    @SuppressWarnings("HttpUrlsUsage")
    @BeforeEach
    void setUp() {
        var host = "%s:%d".formatted(blaze.getHost(), blaze.getFirstMappedPort());
        ConnectionProvider provider = ConnectionProvider.builder("custom")
                .maxConnections(4)
                .build();
        HttpClient httpClient = HttpClient.create(provider);
        client = WebClient.builder()
                .baseUrl("http://%s/fhir".formatted(host))
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Accept", "application/fhir+json")
                .defaultHeader("X-Forwarded-Host", host)
                .build();
        dataStore = new DataStore(client, FhirContext.forR4(), 1000);
    }

    @Test
    void search_empty() {
        var result = dataStore.search(Query.ofType("Observation"));

        StepVerifier.create(result).verifyComplete();
    }

    @Test
    void search_oneObservation() {
        createPatient("0");
        createObservation("0");

        var result = dataStore.search(Query.ofType("Observation"));

        StepVerifier.create(result).expectNextMatches(observation -> ((Observation) observation).getSubject().getReference().equals("Patient/0")).verifyComplete();
    }

    @Test
    void search_twoObservationsFromOnePatient() {
        createPatient("0");
        createObservation("0");
        createObservation("0");

        var result = dataStore.search(Query.ofType("Observation"));

        StepVerifier.create(result)
                .expectNextMatches(observation -> ((Observation) observation).getSubject().getReference().equals("Patient/0"))
                .expectNextMatches(observation -> ((Observation) observation).getSubject().getReference().equals("Patient/0"))
                .verifyComplete();
    }

    @Test
    @DisplayName("1000 concurrent requests will fill up the pending acquire queue because of constraint max connections")
    void pendingAcquireQueueReachedMaximum() {
        createPatient("0");

        var result = Flux.range(1, 1000).flatMap(i -> dataStore.search(Query.ofType("Patient"))).collectList();

        StepVerifier.create(result).verifyErrorMessage("Pending acquire queue has reached its maximum size of 8");
    }

    @Test
    void pagingTest() {
        createPatient("0");
        IntStream.range(0, 100).forEach(i -> createObservation("0"));

        var result = dataStore.search(Query.of("Observation", QueryParams.of("_count", QueryParams.stringValue("50"))));

        StepVerifier.create(result)
                .expectNextCount(100)
                .verifyComplete();

    }

    private void createPatient(String id) {
        client.put()
                .uri("/Patient/{id}", id)
                .contentType(APPLICATION_JSON)
                .bodyValue("""
                        { "resourceType": "Patient",
                          "id": "%s"
                        }
                        """.formatted(id))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    private void createObservation(String patientId) {
        client.post()
                .uri("/Observation")
                .contentType(APPLICATION_JSON)
                .bodyValue("""
                        { "resourceType": "Observation",
                          "subject": { "reference": "Patient/%s" }
                        }
                        """.formatted(patientId))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    private void createObservation_withoutReference() {
        client.post()
                .uri("/Observation")
                .contentType(APPLICATION_JSON)
                .bodyValue("""
                        { "resourceType": "Observation",
                          "subject": {}
                        }
                        """)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}
