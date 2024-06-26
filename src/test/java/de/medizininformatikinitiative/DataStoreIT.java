package de.medizininformatikinitiative;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.util.BundleBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.model.Crtdl;
import de.medizininformatikinitiative.util.FhirSearchBuilder;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.test.StepVerifier;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import de.medizininformatikinitiative.util.ResourceUtils;

import static de.medizininformatikinitiative.util.ResourceReader.readResource;
import static java.util.stream.Collectors.toCollection;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@Testcontainers
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@ActiveProfiles("test")
public class DataStoreIT {


    @Autowired
    private CdsStructureDefinitionHandler cds;

    private WebClient webClient;

    ResourceTransformer transformer;

    @Autowired
    private BundleBuilder bundleBuilder;

    @Autowired
    private FhirContext context;

    private DataStore dataStore;

    @Autowired
    private IParser parser;
    @Autowired
    BundleCreator bundleCreator;

    private static final Logger logger = LoggerFactory.getLogger(DataStoreIT.class);
    private static boolean dataImported = false;

    @Container
    private static final GenericContainer<?> blaze = new GenericContainer<>("samply/blaze:0.25")
            .withImagePullPolicy(PullPolicy.alwaysPull())
            .withEnv("LOG_LEVEL", "debug")
            .withExposedPorts(8080)
            .waitingFor(new WaitAllStrategy()
                    .withStrategy(Wait.forHttp("/health").forStatusCode(200).forPort(8080))
                    .withStartupTimeout(Duration.ofMinutes(5)))
            .withLogConsumer(new Slf4jLogConsumer(logger));

    @BeforeAll
    static void startContainer() {
        blaze.start();

    }

    @BeforeEach
    void setUp() throws IOException {
        String host = "%s:%d".formatted(blaze.getHost(), blaze.getFirstMappedPort());
        logger.info("Host {}", host);
        ConnectionProvider provider = ConnectionProvider.builder("custom")
                .maxConnections(4)
                .build();
        HttpClient httpClient = HttpClient.create(provider);
        webClient = WebClient.builder()
                .baseUrl("http://%s/fhir".formatted(host))
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .defaultHeader("Accept", "application/fhir+json")
                .build();
        if (!dataImported) {
            webClient.post()
                    .contentType(APPLICATION_JSON)
                    .bodyValue(Files.readString(Path.of("src/test/resources/BlazeBundle.json")))
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            dataImported = true;
            logger.info("Data Import on {}", webClient.options());
        }
        dataStore = new DataStore(webClient, context);
        transformer = new ResourceTransformer(dataStore, cds);
        logger.info("Setup Complete");
    }

    //TODO Read Tests from ConfigFile
    @Test
    public void testFhirSearchCondition() throws IOException, PatientIdNotFoundException {
        List<String> expectedResourceFilePaths = List.of(
                "src/test/resources/DataStoreIT/expectedOutput/diagnosis_basic_bundle.json"
        );

        Map<String, Bundle> expectedResources = loadExpectedResources(expectedResourceFilePaths);
        expectedResources.values().forEach(Assertions::assertNotNull);

        List<String> filePaths = List.of(
                "src/test/resources/CRTDL/CRTDL_diagnosis_basic_date.json",
                "src/test/resources/CRTDL/CRTDL_diagnosis_basic_code.json",
                "src/test/resources/CRTDL/CRTDL_diagnosis_basic.json"
        );

        List<String> patients = new ArrayList<>(expectedResources.keySet());
        testExecutor(filePaths, expectedResources, patients, 1);
    }

    @Test
    public void testFhirSearchObservation() throws IOException, PatientIdNotFoundException {
        List<String> expectedResourceFilePaths = List.of(
                "src/test/resources/DataStoreIT/expectedOutput/observation_basic_bundle_id3.json"
        );

        Map<String, Bundle> expectedResources = loadExpectedResources(expectedResourceFilePaths);
        expectedResources.values().forEach(Assertions::assertNotNull);

        List<String> filePaths = List.of(
                "src/test/resources/CRTDL/CRTDL_observation.json"
        );

        List<String> patients = new ArrayList<>(expectedResources.keySet());
        testExecutor(filePaths, expectedResources, patients, 1);
    }

    @Test
    public void testFhirSearchConditionObservation() throws IOException, PatientIdNotFoundException {
        List<String> expectedResourceFilePaths = List.of(
                "src/test/resources/DataStoreIT/expectedOutput/observation_diagnosis_basic_bundle_id3.json"
        );

        Map<String, Bundle> expectedResources = loadExpectedResources(expectedResourceFilePaths);
        expectedResources.values().forEach(Assertions::assertNotNull);

        List<String> filePaths = List.of(
                "src/test/resources/CRTDL/CRTDL_diagnosis_observation.json"
        );

        List<String> patients = new ArrayList<>(expectedResources.keySet());
        testExecutor(filePaths, expectedResources, patients, 1);
    }

    @Test
    public void testFhirSearchBatch() throws IOException, PatientIdNotFoundException {
    List<String> expectedResourceFilePaths = List.of(
            "src/test/resources/DataStoreIT/expectedOutput/observation_basic_bundle_id3.json",
            "src/test/resources/DataStoreIT/expectedOutput/observation_basic_bundle_id4.json"
        );

        Map<String, Bundle> expectedResources = loadExpectedResources(expectedResourceFilePaths);
        expectedResources.values().forEach(Assertions::assertNotNull);
        List<String> filePaths = List.of(
                "src/test/resources/CRTDL/CRTDL_observation.json"
        );
        ArrayList<String> patients = new ArrayList<>(expectedResources.keySet());
        testExecutor(filePaths, expectedResources,patients,2);
    }

    private Map<String, Bundle> loadExpectedResources(List<String> filePaths) throws IOException, PatientIdNotFoundException {
        Map<String, Bundle> expectedResources = new HashMap<>();
        for (String filePath : filePaths) {
            Bundle bundle = (Bundle) readResource(filePath);
            String patientId = ResourceUtils.getPatientIdFromBundle(bundle);
            expectedResources.put(patientId, bundle);
        }
        return expectedResources;
    }

    public void testExecutor(List<String> filePaths, Map<String, Bundle> expectedResources, List<String> patients, int batchSize) {
        filePaths.forEach(filePath -> {
            try (FileInputStream fis = new FileInputStream(filePath)) {
                ObjectMapper objectMapper = new ObjectMapper();
                Crtdl crtdl = objectMapper.readValue(fis, Crtdl.class);
                Mono<Map<String, Collection<Resource>>> collectedResourcesMono = transformer.collectResourcesByPatientReference(crtdl, patients, batchSize);

                StepVerifier.create(collectedResourcesMono)
                        .expectNextMatches(combinedResourcesByPatientId -> {
                            Map<String, Bundle> bundles = bundleCreator.createBundles(combinedResourcesByPatientId);

                            bundles.forEach((patientId, bundle) -> {
                                Bundle expectedBundle = expectedResources.get(patientId);
                                Assertions.assertNotNull(expectedBundle, "No expected bundle found for patientId " + patientId);
                                Assertions.assertEquals(parser.setPrettyPrint(true).encodeResourceToString(bundle),
                                        parser.setPrettyPrint(true).encodeResourceToString(expectedBundle),
                                        bundle + " Expected not equal to actual output");
                            });
                            return true;
                        })
                        .expectComplete()
                        .verify();
            } catch (IOException e) {
                logger.error("CRTDL file not found", e);
            }
        });
    }
}