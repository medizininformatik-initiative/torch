package de.medizininformatikinitiative.torch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import de.medizininformatikinitiative.torch.exceptions.ConsentFormatException;
import de.medizininformatikinitiative.torch.exceptions.ValidationException;
import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedCrtdl;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CohortQueryServiceTest {


    @Autowired
    CohortQueryService service;
    @Autowired
    CrtdlValidatorService validator;
    @Autowired
    @Qualifier("fhirClient")
    WebClient webClient;
    @Value("${torch.fhir.testPopulation.path}")
    private String testPopulationPath;
    private AnnotatedCrtdl crtdlAllObservations;
    private AnnotatedCrtdl crtdlNoPatients;

    @BeforeAll
    void init() throws IOException, ValidationException, ConsentFormatException {
        // Create an instance of BaseTestSetup
        IntegrationTestSetup integrationTestSetup = new IntegrationTestSetup();
        FileInputStream fis = new FileInputStream("src/test/resources/CRTDL/CRTDL_observation_all_fields_withoutReference.json");
        crtdlAllObservations = validator.validateAndAnnotate(integrationTestSetup.objectMapper().readValue(fis, Crtdl.class));
        fis.close();
        fis = new FileInputStream("src/test/resources/CRTDL/CRTDL_observation_not_contained.json");
        crtdlNoPatients = validator.validateAndAnnotate(integrationTestSetup.objectMapper().readValue(fis, Crtdl.class));
        fis.close();

        webClient.post()
                .bodyValue(Files.readString(Path.of(testPopulationPath)))
                .header("Content-Type", "application/fhir+json")
                .retrieve()
                .toBodilessEntity()
                .block();

        fis.close();
    }


    @Test
    void nonEmpty() throws JsonProcessingException {
        List<String> patients1 = service.fetchPatientListFromFlare(crtdlAllObservations).block();
        List<String> patients2 = service.fetchPatientListUsingCql(crtdlAllObservations).block();

        assertThat(patients1).isEqualTo(patients2);
    }

    @Test
    void empty() throws JsonProcessingException {
        Mono<List<String>> batches1 = service.fetchPatientListFromFlare(crtdlNoPatients);
        Mono<List<String>> batches2 = service.fetchPatientListUsingCql(crtdlNoPatients);

        StepVerifier.create(batches1).expectNext(List.of()).verifyComplete();

        StepVerifier.create(batches2).expectNext(List.of()).verifyComplete();
    }
}
