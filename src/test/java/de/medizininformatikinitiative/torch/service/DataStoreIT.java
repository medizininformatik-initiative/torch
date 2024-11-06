package de.medizininformatikinitiative.torch.service;


import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.testUtil.FhirTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

@Testcontainers
class DataStoreIT {

    @Value("${torch.fhir.testPopulation.path}")
    private String testPopulationPath;

    protected static final Logger logger = LoggerFactory.getLogger(DataStoreIT.class);

    protected static boolean dataImported = false;


    private static final Instant FIXED_INSTANT = Instant.ofEpochSecond(104152);

    private static final FhirContext fhirContext = FhirContext.forR4();
    @Container
    @SuppressWarnings("resource")
    private final GenericContainer<?> blaze = new GenericContainer<>("samply/blaze:0.29")
            .withImagePullPolicy(PullPolicy.alwaysPull())
            .withEnv("LOG_LEVEL", "debug")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/health").forStatusCode(200))
            .withLogConsumer(new Slf4jLogConsumer(logger));

    private DataStore dataStore;

    @SuppressWarnings("HttpUrlsUsage")
    @BeforeEach
    void setUp() throws IOException {
        var host = "%s:%d".formatted(blaze.getHost(), blaze.getFirstMappedPort());
        FhirTestHelper.checkServiceHealth("blaze", "/health", blaze.getHost(), blaze.getFirstMappedPort());
        ConnectionProvider provider = ConnectionProvider.builder("custom")
                .maxConnections(4)
                .build();
        HttpClient httpClient = HttpClient.create(provider);
        WebClient client = WebClient.builder()
                .baseUrl("http://%s/fhir".formatted(host))
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Accept", "application/fhir+json")
                .defaultHeader("X-Forwarded-Host", host)
                .build();
        dataStore = new DataStore(client, fhirContext, Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC), 1000);


        if (!dataImported) {


            client.post()
                    .bodyValue(Files.readString(Path.of("src/test/resources/BlazeBundle.json")))
                    .header("Content-Type", "application/fhir+json")
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            dataImported = true;
            logger.info("Data Import on {}", client.options());

        }

    }

    @Test
    void getRenameEmpty() {
        var result = dataStore.search(Query.ofType("Observation"));

        StepVerifier.create(result.doOnNext(obs -> logger.info("Emitted Observation: {}", obs.getId())))
                .expectNextCount(5)
                .verifyComplete();
    }


}

