package de.medizininformatikinitiative.torch;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.exceptions.ValidationException;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedCrtdl;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;
import de.medizininformatikinitiative.torch.service.CrtdlValidatorService;
import de.medizininformatikinitiative.torch.setup.ContainerManager;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.EMPTY;

@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.main.allow-bean-definition-overriding=true"}, classes = Torch.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DirectResourceLoaderIT {

    private final ContainerManager manager = new ContainerManager();
    @Autowired
    CrtdlValidatorService validator;
    @Autowired
    @Qualifier("fhirClient")
    private WebClient webClient;
    @Autowired
    private DirectResourceLoader dLoader;
    @Autowired
    private ObjectMapper objectMapper;
    @Value("${torch.fhir.testPopulation.path}")
    private String testPopulationPath;
    @Autowired
    private DseMappingTreeBase dseMappingTreeBase;

    @BeforeAll
    void init() throws IOException {
        manager.startContainers();
        webClient.post().bodyValue(Files.readString(Path.of(testPopulationPath))).header("Content-Type", "application/fhir+json").retrieve().toBodilessEntity().block();
    }

    @Test
    public void collectPatientsByResource() throws IOException, ValidationException {
        AnnotatedCrtdl crtdl = readCrdtl("src/test/resources/CRTDL/CRTDL_observation_all_fields_withoutReference.json");

        Mono<PatientBatchWithConsent> result = dLoader.directLoadPatientCompartment(
                crtdl.dataExtraction().attributeGroups(),
                PatientBatchWithConsent.fromBatch(new PatientBatch(List.of("1", "2", "4", "VHF00006")))
        );

        StepVerifier.create(result)
                .expectNextMatches(map -> map.bundles().containsKey("VHF00006"))
                .verifyComplete();
    }

    @Test
    void testExecuteQueryWithBatchAllPatients() {
        PatientBatch batch = PatientBatch.of("1", "2");
        Query query = new Query("Patient", EMPTY); // Basic query setup

        Flux<Resource> result = dLoader.executeQueryWithBatch(batch, query);

        StepVerifier.create(result)
                .expectNextMatches(resource -> resource instanceof Patient)
                .expectNextMatches(resource -> resource instanceof Patient)
                .verifyComplete();
    }

    @Test
    void testExecuteQueryWithBatch_Success() throws IOException, ValidationException {
        AnnotatedCrtdl crtdl = readCrdtl("src/test/resources/CRTDL/CRTDL_observation_all_fields_withoutReference.json");

        PatientBatch batch = PatientBatch.of("1", "2");

        List<Query> queries = crtdl.dataExtraction().attributeGroups().getFirst().queries(dseMappingTreeBase, "Observation");

        StepVerifier.create(dLoader.executeQueryWithBatch(batch, queries.getFirst()))
                .expectNextMatches(resource -> resource instanceof Observation)
                .expectNextMatches(resource -> resource instanceof Observation)
                .verifyComplete();
    }

    private AnnotatedCrtdl readCrdtl(String name) throws IOException, ValidationException {
        try (FileInputStream fis = new FileInputStream(name)) {
            return validator.validate(objectMapper.readValue(fis, Crtdl.class));
        }
    }
}
