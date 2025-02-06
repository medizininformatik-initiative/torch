package de.medizininformatikinitiative.torch;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.cql.CqlClient;
import de.medizininformatikinitiative.torch.management.ConsentHandler;
import de.medizininformatikinitiative.torch.management.StructureDefinitionHandler;
import de.medizininformatikinitiative.torch.model.PatientBatch;
import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.fhir.QueryParams;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;
import de.medizininformatikinitiative.torch.service.DataStore;
import de.medizininformatikinitiative.torch.setup.ContainerManager;
import de.medizininformatikinitiative.torch.util.ResourceReader;
import de.numcodex.sq2cql.Translator;
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
import org.springframework.boot.test.web.server.LocalServerPort;
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

@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.main.allow-bean-definition-overriding=true"}, classes = Torch.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ResourceTransformationIT {


    @Autowired
    ResourceReader resourceReader;


    @Autowired
    @Qualifier("fhirClient")
    protected WebClient webClient;

    ContainerManager manager;


    protected final ResourceTransformer transformer;
    protected final DataStore dataStore;
    protected final StructureDefinitionHandler cds;
    protected BundleCreator bundleCreator;
    protected ObjectMapper objectMapper;
    protected CqlClient cqlClient;
    protected Translator cqlQueryTranslator;
    @Value("${torch.fhir.testPopulation.path}")
    private String testPopulationPath;
    protected FhirContext fhirContext;


    protected final DseMappingTreeBase dseMappingTreeBase;


    private static final String RESOURCE_PATH_PREFIX = "src/test/resources/";
    @LocalServerPort
    private int port;


    @Autowired
    ConsentHandler consentHandler;


    @Autowired
    public ResourceTransformationIT(ResourceTransformer transformer, DataStore dataStore, StructureDefinitionHandler cds, FhirContext fhirContext, BundleCreator bundleCreator, ObjectMapper objectMapper, CqlClient cqlClient, Translator cqlQueryTranslator, DseMappingTreeBase dseMappingTreeBase) {
        this.transformer = transformer;
        this.dseMappingTreeBase = dseMappingTreeBase;
        this.manager = new ContainerManager();
        this.objectMapper = objectMapper;
        this.dataStore = dataStore;
        this.cds = cds;


    }

    @BeforeAll
    void init() throws IOException {

        manager.startContainers();
        webClient.post().bodyValue(Files.readString(Path.of(testPopulationPath))).header("Content-Type", "application/fhir+json").retrieve().toBodilessEntity().block();
    }


    @Test
    public void collectPatientsbyResource() throws IOException {


        FileInputStream fis = new FileInputStream("src/test/resources/CRTDL/CRTDL_observation_all_fields.json");
        Crtdl crtdl = objectMapper.readValue(fis, Crtdl.class);
        fis.close();

        Mono<Map<String, Collection<Resource>>> result = transformer.collectResourcesByPatientReference(crtdl.dataExtraction().attributeGroups(), new PatientBatch(List.of("1", "2", "4", "VHF00006")), crtdl.consentKey());

        StepVerifier.create(result)
                .expectNextMatches(map -> map.containsKey("1")) // Patient1 is in consent info
                .verifyComplete();


    }

    @Test
    void testExecuteQueryWithBatchAllPatients() {
        PatientBatch batch = PatientBatch.of("1", "2");
        Query query = new Query("Patient", EMPTY); // Basic query setup

        Flux<Resource> result = transformer.executeQueryWithBatch(batch, query);

        StepVerifier.create(result)
                .expectNextMatches(resource -> resource instanceof Patient)
                .expectNextMatches(resource -> resource instanceof Patient)
                .verifyComplete();

    }

    @Test
    void testExecuteQueryWithBatch_Success() throws IOException {
        FileInputStream fis = new FileInputStream("src/test/resources/CRTDL/CRTDL_observation_all_fields.json");
        Crtdl crtdl = objectMapper.readValue(fis, Crtdl.class);
        fis.close();


        PatientBatch batch = PatientBatch.of("1", "2");


        List<Query> queries = crtdl.dataExtraction().attributeGroups().getFirst().queries(dseMappingTreeBase, "Observation");
        List<QueryParams> params = crtdl.dataExtraction().attributeGroups().getFirst().queries(dseMappingTreeBase, "Observation").stream().map(Query::params).toList();


        StepVerifier.create(transformer.executeQueryWithBatch(batch, queries.getFirst()))
                .expectNextMatches(resource -> resource instanceof Observation)
                .expectNextMatches(resource -> resource instanceof Observation)
                .verifyComplete();

    }
}
