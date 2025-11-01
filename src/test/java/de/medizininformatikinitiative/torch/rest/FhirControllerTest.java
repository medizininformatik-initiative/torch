package de.medizininformatikinitiative.torch.rest;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.exceptions.ConsentFormatException;
import de.medizininformatikinitiative.torch.exceptions.ValidationException;
import de.medizininformatikinitiative.torch.model.crtdl.ExtractDataParameters;
import de.medizininformatikinitiative.torch.service.CrtdlValidatorService;
import de.medizininformatikinitiative.torch.service.ExtractDataService;
import de.medizininformatikinitiative.torch.util.CrtdlFactory;
import de.medizininformatikinitiative.torch.util.ResultFileManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FhirControllerTest {

    private static final String BASE_URL = "http://base-url";

    @Mock
    ResultFileManager resultFileManager;

    @Mock
    ExtractDataParametersParser extractDataParametersParser;

    @Mock
    ExtractDataService extractDataService;

    @Mock
    CrtdlValidatorService validator;

    WebTestClient client;

    @BeforeEach
    void setup() {
        FhirContext fhirContext = FhirContext.forR4();
        FhirController fhirController = new FhirController(fhirContext, resultFileManager, extractDataParametersParser, extractDataService, BASE_URL, validator);
        client = WebTestClient.bindToRouterFunction(fhirController.queryRouter()).build();
    }

    @Test
    void checkAcceptedStatus() {
        when(resultFileManager.getStatus("accepted-job")).thenReturn(HttpStatus.ACCEPTED);

        var response = client.get().uri("/fhir/__status/{jobId}", "accepted-job").exchange();

        response.expectStatus().isEqualTo(HttpStatus.ACCEPTED).expectBody().isEmpty();
    }

    @Test
    void extractDataSuccess() {

        ExtractDataParameters params = new ExtractDataParameters(CrtdlFactory.empty(), Collections.emptyList());
        when(extractDataParametersParser.parseParameters(any())).thenReturn(params);
        when(extractDataService.startJob(any(), any(), any())).thenReturn(Mono.empty());

        WebTestClient.ResponseSpec response = client.post().uri("/fhir/$extract-data").contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange();

        response.expectStatus().isAccepted().expectHeader()
                .value("Content-Location",
                        location -> assertThat(location).startsWith(BASE_URL + "/fhir/__status/"));

    }

    @ParameterizedTest
    @CsvSource({
            "ok,       application/json,        200",
            "ok-null,  text/plain,              500",
            "error,    application/fhir+json,   500",
            "read-fail,text/plain,              500",
            "missing,  application/fhir+json,   404"
    })
    void loadStatusBranches(String scenario, String expectedContentType, int expectedStatus) {
        String jobId = "test-job";
        switch (scenario) {

            case "ok" -> {
                when(resultFileManager.getStatus(jobId)).thenReturn(HttpStatus.OK);
                when(resultFileManager.loadBundleFromFileSystem(eq(jobId), anyString())).thenReturn(Map.of("message", "bundle content"));
            }
            case "ok-null" -> {
                when(resultFileManager.getStatus(jobId)).thenReturn(HttpStatus.OK);
                when(resultFileManager.loadBundleFromFileSystem(eq(jobId), anyString())).thenReturn(Map.of());
            }
            case "missing" -> {
                when(resultFileManager.getStatus(jobId)).thenReturn(HttpStatus.NOT_FOUND);
                when(resultFileManager.loadErrorFromFileSystem(jobId)).thenReturn("Error file not found for job: " + jobId);
            }
            case "error" -> {
                when(resultFileManager.getStatus(jobId)).thenReturn(HttpStatus.INTERNAL_SERVER_ERROR);
                when(resultFileManager.loadErrorFromFileSystem(jobId)).thenReturn("{\"resourceType\":\"OperationOutcome\"}");
            }
            case "read-fail" -> {
                when(resultFileManager.getStatus(jobId)).thenReturn(HttpStatus.INTERNAL_SERVER_ERROR);
                // Throw the IOException when loadErrorFromFileSystem is called
                when(resultFileManager.loadErrorFromFileSystem(jobId)).thenAnswer(invocation -> {
                    throw new IOException("disk failure");
                });
            }
            default -> throw new IllegalArgumentException("Unexpected scenario: " + scenario);
        }

        var response = client.get().uri("/fhir/__status/{jobId}", jobId).exchange();

        response.expectStatus().isEqualTo(expectedStatus).expectHeader().contentType(expectedContentType).expectBody(String.class).value(body -> {
            switch (scenario) {
                case "ok" -> assertThat(body).contains("bundle content");
                case "ok-null" -> assertThat(body).contains("Results could not be loaded for job: test-job");
                case "error" -> assertThat(body).contains("\"resourceType\":\"OperationOutcome\"");
                case "missing" -> assertThat(body).contains("Error file not found for job");
                case "read-fail" -> assertThat(body).contains("Error file could not be read: disk failure");
                default -> throw new IllegalArgumentException("Unexpected scenario: " + scenario);
            }
        });
    }


    @Nested
    class StatusEndpointTests {

        @Test
        void emptyStatusMapReturnsEmptyJson() {
            when(resultFileManager.getJobStatusMap()).thenReturn(Collections.emptyMap());

            var response = client.get().uri("/fhir/__status/").exchange();

            response.expectStatus().isOk().expectHeader().contentType(MediaType.APPLICATION_JSON)
                    .expectBody().json("{}");
        }

        @Test
        void statusMapReturnsStatusText() {
            Map<String, HttpStatus> map = new HashMap<>();
            map.put("job1", HttpStatus.ACCEPTED);
            map.put("job2", HttpStatus.OK);
            when(resultFileManager.getJobStatusMap()).thenReturn(map);

            var response = client.get().uri("/fhir/__status/").exchange();

            response.expectStatus().isOk().expectHeader().contentType(MediaType.APPLICATION_JSON)
                    .expectBody()
                    .jsonPath("$.job1").isEqualTo("202 Accepted")
                    .jsonPath("$.job2").isEqualTo("200 OK");
        }
    }

    @Nested
    class ExtractDataErrorBranchTests {

        @Test
        void emptyRequestBodyTriggersBadRequest() {
            var response = client.post().uri("/fhir/$extract-data").exchange();

            response.expectStatus().isBadRequest()
                    .expectHeader().contentType("application/fhir+json")
                    .expectBody()
                    .jsonPath("$.resourceType").isEqualTo("OperationOutcome");
        }

        @Test
        void blankRequestBodyTriggersBadRequest() {
            var response = client.post().uri("/fhir/$extract-data").contentType(MediaType.APPLICATION_JSON).bodyValue("  ").exchange();

            response.expectStatus().isBadRequest().expectHeader().contentType("application/fhir+json")
                    .expectBody()
                    .jsonPath("$.resourceType").isEqualTo("OperationOutcome");
        }
    }

    @Nested
    class Validator {
        @Test
        void invalidCrtdlTriggersBadRequest() throws ValidationException, ConsentFormatException {
            ExtractDataParameters params = new ExtractDataParameters(CrtdlFactory.empty(), Collections.emptyList());
            when(extractDataParametersParser.parseParameters(any())).thenReturn(params);
            when(validator.validateAndAnnotate(any())).thenThrow(new ValidationException("Invalid CRTDL"));

            var response = client.post().uri("/fhir/$extract-data").contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange();

            response.expectStatus().isBadRequest().expectHeader().contentType("application/fhir+json")
                    .expectBody()
                    .jsonPath("$.resourceType").isEqualTo("OperationOutcome")
                    .jsonPath("$.issue[0].diagnostics").isEqualTo("Invalid CRTDL");
        }
    }
}
