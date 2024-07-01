package de.medizininformatikinitiative;

import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.model.Attribute;
import de.medizininformatikinitiative.model.AttributeGroup;
import de.medizininformatikinitiative.model.Crtdl;
import de.medizininformatikinitiative.util.FhirSearchBuilder;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toCollection;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    private CdsStructureDefinitionHandler cds;
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
            .withLogConsumer(new Slf4jLogConsumer(logger))
            .withReuse(true); // Allow container reuse
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
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                //.defaultHeader("Accept", "application/fhir+json")
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
        logger.error("Setup Complete");
    }

    @Test
    public void testFhirSearch() throws IOException {
        System.out.println("Starting FHITR Search Test");
        try (FileInputStream fis = new FileInputStream("src/test/resources/CRTDL/CRTDL_diagnosis_basic.json")) {

            ObjectMapper objectMapper = new ObjectMapper();
            Crtdl crtdl = objectMapper.readValue(fis, Crtdl.class);
            Attribute attribute1 = crtdl.getCohortDefinition().getDataExtraction().getAttributeGroups().get(0).getAttributes().get(0);
            System.out.println("Attribute found");
            assertEquals("Condition.code", attribute1.getAttributeRef());
            FhirSearchBuilder searchBuilder = new FhirSearchBuilder(cds);
            AttributeGroup group = crtdl.getCohortDefinition().getDataExtraction().getAttributeGroups().get(0);
            List<String> batches = searchBuilder.getSearchBatchesAsUrls(group, Stream.of("7ca6f273-82f1-4fd5-9b0d-e9e4f957fe42").collect(toCollection(ArrayList::new)), 2);
            batches.forEach(System.out::println);
            List<MultiValueMap<String, String>> parameters = searchBuilder.getSearchBatches(group,Stream.of("7ca6f273-82f1-4fd5-9b0d-e9e4f957fe42").collect(toCollection(ArrayList::new)), 2);

            BodyInserters.FormInserter<String> formInserter = BodyInserters.fromFormData(parameters.get(0));

            Flux<Resource> resources = dataStore.getResources(crtdl.getResourceType(), parameters.get(0));
            /*String response = webClient.post()
                    .uri("/"+crtdl.getResourceType()+"/_search")
                    .body(formInserter)
                    .accept(APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
                      System.out.println("FHIR Search Response: {}" + response);
            // Parse the response using the FHIR parser
            Bundle bundle = parser.parseResource(Bundle.class, response);
*/


            // Check that the bundle is not null and contains Condition resources
            assertThat(resources).isNotNull();
            assertNotNull(Mono.from(resources));
            resources.toStream().forEach(x->assertEquals(x.fhirType(),"Condition"));
            // Log the response for debugging


        } catch (Exception e) {
            System.out.println("Error in Handling Client "+e);
            logger.error("Data Store IT Error in CRTDL", e);
        }
        System.out.println("Test finished ");

    }
}