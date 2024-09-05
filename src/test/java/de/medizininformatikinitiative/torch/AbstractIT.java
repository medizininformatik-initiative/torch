package de.medizininformatikinitiative.torch;


import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.model.Crtdl;
import de.medizininformatikinitiative.torch.util.ResourceReader;
import de.medizininformatikinitiative.torch.util.ResourceUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@Testcontainers
@SpringBootTest()
@ActiveProfiles("test")
//@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractIT {


    protected final WebClient webClient;
    protected final WebClient flareClient;
    protected final ResourceTransformer transformer;
    protected final DataStore dataStore;
    protected final CdsStructureDefinitionHandler cds;
    protected final FhirContext context;
    protected final IParser parser;
    protected final BundleCreator bundleCreator;
    protected final ObjectMapper objectMapper;

    protected static final Logger logger = LoggerFactory.getLogger(AbstractIT.class);
    protected static boolean dataImported = false;

    @Value("${torch.fhir.testPopulation.path}")
    private String testPopulationPath;


    @Autowired
    public AbstractIT(
            @Qualifier("fhirClient") WebClient webClient,
            @Qualifier("flareClient") WebClient flareClient,
            ResourceTransformer transformer,
            DataStore dataStore,
            CdsStructureDefinitionHandler cds,
            FhirContext context,
            IParser parser,
            BundleCreator bundleCreator,
            ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.flareClient = flareClient;
        this.transformer = transformer;
        this.dataStore = dataStore;
        this.cds = cds;
        this.context = context;
        this.parser = parser;
        this.bundleCreator = bundleCreator;
        this.objectMapper = objectMapper;
    }

    @Container
    public static DockerComposeContainer<?> environment =
            new DockerComposeContainer<>(new File("src/test/resources/docker-compose.yml"))
                    .withExposedService("blaze", 8080, Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(10)))
                    .withLogConsumer("blaze", new Slf4jLogConsumer(logger).withPrefix("blaze"))
                    .withExposedService("flare", 8080, Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(10)))
                    .withLogConsumer("flare", new Slf4jLogConsumer(logger).withPrefix("flare"));




    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry){
        environment.start();
        checkServiceHealth("blaze", "/health");
        checkServiceHealth("flare", "/cache/stats");
        String blazeHost = environment.getServiceHost("blaze", 8080);
        Integer blazePort = environment.getServicePort("blaze", 8080);
        String flareHost = environment.getServiceHost("flare", 8080);
        Integer flarePort = environment.getServicePort("flare", 8080);
        logger.info("Blaze  %s:%d Flare %s %d ".formatted(blazeHost,blazePort,flareHost,flarePort));
        registry.add("torch.fhir.url", () -> "http://%s:%d/fhir".formatted(blazeHost, blazePort));
        registry.add("torch.flare.url", () -> "http://%s:%d".formatted(flareHost,flarePort));
    }


    @BeforeEach
    void init() throws IOException {

        if (!dataImported) {
            webClient.post()
                    .bodyValue(Files.readString(Path.of(testPopulationPath)))
                    .header("Content-Type", "application/fhir+json")
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            dataImported = true;
            logger.info("Data Import on {}", webClient.options());
        }
   logger.info("Setup Complete");

    }


    protected Map<String, Bundle> loadExpectedResources(List<String> filePaths) throws IOException, PatientIdNotFoundException {
        Map<String, Bundle> expectedResources = new HashMap<>();
        for (String filePath : filePaths) {
            Bundle bundle = (Bundle) ResourceReader.readResource(filePath);
            String patientId = ResourceUtils.getPatientIdFromBundle(bundle);
            expectedResources.put(patientId, bundle);
        }
        return expectedResources;
    }

    private static void checkServiceHealth(String service, String healthEndpoint) {
        String host = environment.getServiceHost(service, 8080);
        int servicePort = environment.getServicePort(service, 8080);
        String url = String.format("http://%s:%d%s", host, servicePort, healthEndpoint);

        WebClient webClient = WebClient.create();
        int attempts = 0;
        int maxAttempts = 10;

        while (attempts < maxAttempts) {
            try {
                Mono<String> responseMono = webClient.get()
                        .uri(url)
                        .retrieve()
                        .bodyToMono(String.class);

                String response = responseMono.block();
                assertThat(response).isNotNull();
                logger.info("Health check passed for service: {} at {}", service, url);
                return;
            } catch (WebClientResponseException e) {
                logger.warn("Health check failed for service: {} at {}. Status code: {}. Retrying...", service, url, e.getStatusCode());
            } catch (Exception e) {
                logger.warn("Health check failed for service: {} at {}. Exception: {}. Retrying...", service, url, e.getMessage());
            }
            attempts++;
            try {
                Thread.sleep(5000); // Wait 5 seconds before retrying
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        throw new RuntimeException("Health check failed for service: " + service + " at " + url);
    }



    private void validateBundles(Map<String, Bundle> bundles, Map<String, Bundle> expectedResources) {
        for (Map.Entry<String, Bundle> entry : bundles.entrySet()) {
            String patientId = entry.getKey();
            Bundle bundle = entry.getValue();
            Bundle expectedBundle = expectedResources.get(patientId);
            // Remove the meta.lastUpdated field from both bundles
            // You can calculate milliseconds using a tool or method or directly input the correct value.


            // Remove meta.lastUpdated from all contained resources in the bundle
            removeMetaLastUpdated(bundle);
            removeMetaLastUpdated(expectedBundle);

            //logger.debug(parser.setPrettyPrint(true).encodeResourceToString(bundle));
            Assertions.assertNotNull(expectedBundle, "No expected bundle found for patientId " + patientId);
            Assertions.assertEquals(parser.setPrettyPrint(true).encodeResourceToString(expectedBundle),parser.setPrettyPrint(true).encodeResourceToString(bundle),

                    bundle + " Expected not equal to actual output");
        }
    }

    private void removeMetaLastUpdated(Bundle bundle) {
        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            Resource resource = entry.getResource();
            if (resource != null && resource.hasMeta() && resource.getMeta().hasLastUpdated()) {
                logger.info("Removed lastUpdated ");
                resource.getMeta().setLastUpdated(null);
            }
        }
    }

}