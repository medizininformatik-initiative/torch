package de.medizininformatikinitiative.torch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import de.medizininformatikinitiative.torch.cql.CqlClient;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedCrtdl;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedDataExtraction;
import de.numcodex.sq2cql.Translator;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.hl7.fhir.r4.model.ListResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the {@code torch.useCql=false} (Flare) branches of {@link CohortQueryService}, which the
 * {@code @SpringBootTest}-based {@code CohortQueryServiceTest} cannot reach since it always runs with
 * {@code torch.useCql=true}.
 */
class CohortQueryServiceFlarePathTest {

    private static final JsonNode COHORT_DEFINITION = JsonNodeFactory.instance.objectNode();

    private MockWebServer flare;
    private CohortQueryService service;

    @BeforeEach
    void setUp() throws IOException {
        flare = new MockWebServer();
        flare.start();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://localhost:%d".formatted(flare.getPort()))
                .build();
        service = new CohortQueryService(webClient, Mockito.mock(Translator.class), Mockito.mock(CqlClient.class), false);
    }

    @AfterEach
    void tearDown() throws IOException {
        flare.shutdown();
    }

    private static AnnotatedCrtdl crtdl(JsonNode cohortDefinition) {
        return new AnnotatedCrtdl(cohortDefinition, Mockito.mock(AnnotatedDataExtraction.class), Optional.empty());
    }

    @Test
    void runCohortQuery_useCqlFalse_returnsPatientIdsFromFlare() {
        flare.enqueue(new MockResponse().setResponseCode(200).setBody("[\"1\",\"2\"]"));

        StepVerifier.create(service.runCohortQuery(crtdl(COHORT_DEFINITION)))
                .expectNext(List.of("1", "2"))
                .verifyComplete();
    }

    @Test
    void evaluateCohortAsFhirList_useCqlFalse_synthesizesListFromFlareIds() {
        flare.enqueue(new MockResponse().setResponseCode(200).setBody("[\"1\",\"2\"]"));

        ListResource list = service.evaluateCohortAsFhirList(COHORT_DEFINITION).block();

        assertThat(list).isNotNull();
        assertThat(list.getEntry()).hasSize(2);
        assertThat(list.getEntry().get(0).getItem().getReference()).isEqualTo("Patient/1");
    }

    @Test
    void fetchPatientListFromFlare_clientError_errorsWithWebClientResponseException() {
        flare.enqueue(new MockResponse().setResponseCode(400).setBody("bad request"));

        StepVerifier.create(service.fetchPatientListFromFlare(COHORT_DEFINITION))
                .verifyError(WebClientResponseException.class);
    }

    @Test
    void fetchPatientListFromFlare_malformedResponseBody_errors() {
        flare.enqueue(new MockResponse().setResponseCode(200).setBody("not a json array"));

        StepVerifier.create(service.fetchPatientListFromFlare(COHORT_DEFINITION))
                .verifyErrorMessage("Error parsing response");
    }
}
