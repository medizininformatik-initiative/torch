package de.medizininformatikinitiative.torch;

import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.fhir.QueryParams;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;
import de.medizininformatikinitiative.torch.setup.ContainerManager;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.EMPTY;
import static org.assertj.core.api.Fail.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.main.allow-bean-definition-overriding=true"}, classes = Torch.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ResourceTransformationTest {

    private static final Logger logger = LoggerFactory.getLogger(ResourceTransformationTest.class);

    // Create an instance of BaseTestSetup
    private static final IntegrationTestSetup INTEGRATION_TEST_SETUP = new IntegrationTestSetup();

    @Autowired
    @Qualifier("fhirClient")
    protected WebClient webClient;

    @Autowired
    ResourceTransformer transformer;

    @Autowired
    public ResourceTransformationTest(ResourceTransformer transformer) {
        this.transformer = transformer;
    }

    @Value("${torch.fhir.testPopulation.path}")
    private String testPopulationPath;

    ContainerManager manager;

    @Autowired
    DseMappingTreeBase base;

    @BeforeAll
    void init() throws IOException {
        manager = new ContainerManager();
        manager.startContainers();


        webClient.post()
                .bodyValue(Files.readString(Path.of(testPopulationPath)))
                .header("Content-Type", "application/fhir+json")
                .retrieve()
                .toBodilessEntity()
                .block();

        logger.info("Data Import on {}", webClient.options());

    }

    @Test
    public void testObservation() {

        try {
            FileInputStream fis = new FileInputStream("src/test/resources/CRTDL/CRTDL_observation.json");
            Crtdl crtdl = INTEGRATION_TEST_SETUP.getObjectMapper().readValue(fis, Crtdl.class);

            DomainResource resourcesrc = INTEGRATION_TEST_SETUP.readResource("src/test/resources/InputResources/Observation/Observation_lab.json");
            DomainResource resourceexpected = INTEGRATION_TEST_SETUP.readResource("src/test/resources/ResourceTransformationTest/ExpectedOutput/Observation_lab.json");

            DomainResource tgt = (DomainResource) transformer.transform(resourcesrc, crtdl.dataExtraction().attributeGroups().getFirst());

            logger.info("Result: {}", INTEGRATION_TEST_SETUP.fhirContext().newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt));
            assertNotNull(tgt);
            assertEquals(
                    INTEGRATION_TEST_SETUP.fhirContext().newJsonParser().setPrettyPrint(true).encodeResourceToString(resourceexpected),
                    INTEGRATION_TEST_SETUP.fhirContext().newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt),
                    "Expected not equal to actual output"
            );
            fis.close();
        } catch (Exception e) {
            logger.error("", e);
            fail("Deserialization failed: " + e.getMessage(), e);
        }
    }


    @Test
    public void collectPatientsbyResource() {

        try {
            FileInputStream fis = new FileInputStream("src/test/resources/CRTDL/CRTDL_observation_all_fields.json");
            Crtdl crtdl = INTEGRATION_TEST_SETUP.getObjectMapper().readValue(fis, Crtdl.class);
            fis.close();

            Mono<Map<String, Collection<Resource>>> result = transformer.collectResourcesByPatientReference(crtdl, List.of("1", "2", "4", "VHF00006"));

            StepVerifier.create(result)
                    .expectNextMatches(map -> map.containsKey("1")) // Patient1 is in consent info
                    .verifyComplete();

        } catch (Exception e) {
            logger.error("", e);
            fail("Deserialization failed: " + e.getMessage(), e);
        }
    }


    @Test
    void testExecuteQueryWithBatchAllPatients() {
        String batch = "1,2";
        Query query = new Query("Patient", EMPTY); // Basic query setup


        // Act
        Flux<Resource> result = transformer.executeQueryWithBatch(batch, query);

        // Assert
        StepVerifier.create(result)
                .expectNextMatches(resource -> resource instanceof Patient)
                .expectNextMatches(resource -> resource instanceof Patient)
                .verifyComplete();

    }

    @Test
    void testExecuteQueryWithBatch_Success() throws IOException {
        FileInputStream fis = new FileInputStream("src/test/resources/CRTDL/CRTDL_observation_all_fields.json");
        Crtdl crtdl = INTEGRATION_TEST_SETUP.getObjectMapper().readValue(fis, Crtdl.class);
        fis.close();


        String batch = "1,2";


        logger.info("Attribute Groups {}", crtdl.dataExtraction().attributeGroups().size());
        logger.info("Attribute Groups {}", crtdl.dataExtraction().attributeGroups().getFirst().attributes().size());
        logger.info("Attribute Group Resource Type {}", crtdl.dataExtraction().attributeGroups().getFirst().resourceType());
        List<Query> queries = crtdl.dataExtraction().attributeGroups().get(0).queries(base);
        logger.info("Queries size {}", queries.size());
        List<QueryParams> params = crtdl.dataExtraction().attributeGroups().get(0).queryParams(base);
        queries.forEach(x -> logger.info("Query: {}", x.toString())
        );
        logger.info("Queries size {}", params.size());
        params.forEach(x -> logger.info("params: {}", x.toString())
        );


        StepVerifier.create(transformer.executeQueryWithBatch(batch, queries.getFirst()))
                .expectNextMatches(resource -> resource instanceof Observation)
                .expectNextMatches(resource -> resource instanceof Observation)
                .verifyComplete();

    }


}
