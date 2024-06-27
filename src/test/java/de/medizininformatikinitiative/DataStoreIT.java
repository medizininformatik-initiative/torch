package de.medizininformatikinitiative;

import ca.uhn.fhir.parser.IParser;
import org.assertj.core.api.Condition;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Measure;
import org.hl7.fhir.r4.model.MeasureReport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@Testcontainers
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@ActiveProfiles("test")
public class DataStoreIT {

    @TestConfiguration
    static class Config {
        @Bean
        WebClient webClient() {
            String host = "%s:%d".formatted(blaze.getHost(), blaze.getFirstMappedPort());
            WebClient.Builder builder = WebClient.builder()
                    .baseUrl("http://%s/fhir".formatted(host))
                    .defaultHeader("Accept", "application/fhir+json")
                    .defaultHeader("X-Forwarded-Host", host);

            return builder.build();
        }
    }

    @Autowired
    private WebClient webClient;

    @Autowired
    private DataStore dataStore;

    @Autowired
    private IParser parser;

    private static final Logger logger = LoggerFactory.getLogger(DataStoreIT.class);
    private static boolean dataImported = false;

    @Container
    private static final GenericContainer<?> blaze = new GenericContainer<>("samply/blaze:0.25")
            .withImagePullPolicy(PullPolicy.alwaysPull())
            .withEnv("LOG_LEVEL", "debug")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/health").forStatusCode(200))
            .withLogConsumer(new Slf4jLogConsumer(logger));

    @BeforeAll
    static void startContainer() {
        blaze.start();
    }

    @BeforeEach
    void setUp() throws IOException {
        String host = "%s:%d".formatted(blaze.getHost(), blaze.getFirstMappedPort());
        ConnectionProvider provider = ConnectionProvider.builder("custom")
                .maxConnections(4)
                .build();
        HttpClient httpClient = HttpClient.create(provider);
        webClient = WebClient.builder()
                .baseUrl("http://%s/fhir".formatted(host))
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Accept", "application/fhir+json")
                .defaultHeader("X-Forwarded-Host", host)
                .build();
        if (!dataImported) {
            webClient.post()
                    .contentType(APPLICATION_JSON)
                    .bodyValue(Files.readString(Path.of("src/test/resources/Bundle.json")))
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            dataImported = true;
        }
    }

    @Test
    public void testFhirSearch() throws IOException {
        String response = webClient.get()
                .uri("/Condition")
                .accept(APPLICATION_JSON)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        // Parse the response using the FHIR parser
        Bundle bundle = parser.parseResource(Bundle.class, response);

        // Check that the bundle is not null and contains Condition resources
        assertThat(bundle).isNotNull();
        assertThat(bundle.getEntry()).isNotEmpty();
        assertThat(bundle.getEntry().get(0).getResource()).isInstanceOf(Condition.class);

        // Log the response for debugging
        logger.info("FHIR Search Response: {}", response);
    }
}