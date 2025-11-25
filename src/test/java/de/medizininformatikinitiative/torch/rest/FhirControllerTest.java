package de.medizininformatikinitiative.torch.rest;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.config.TorchProperties;
import de.medizininformatikinitiative.torch.exceptions.ConsentFormatException;
import de.medizininformatikinitiative.torch.exceptions.ValidationException;
import de.medizininformatikinitiative.torch.jobhandling.BatchState;
import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.JobStatus;
import de.medizininformatikinitiative.torch.jobhandling.JobTest;
import de.medizininformatikinitiative.torch.jobhandling.workunit.WorkUnitState;
import de.medizininformatikinitiative.torch.jobhandling.workunit.WorkUnitStatus;
import de.medizininformatikinitiative.torch.model.crtdl.ExtractDataParameters;
import de.medizininformatikinitiative.torch.service.CrtdlValidatorService;
import de.medizininformatikinitiative.torch.service.ExtractDataService;
import de.medizininformatikinitiative.torch.service.JobPersistenceService;
import de.medizininformatikinitiative.torch.util.CrtdlFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FhirControllerTest {
    @Mock
    ExtractDataParametersParser extractDataParametersParser;

    @Mock
    ExtractDataService extractDataService;

    @Mock
    CrtdlValidatorService validator;

    @Mock
    JobPersistenceService jobPersistenceService;

    ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .registerModule(new com.fasterxml.jackson.datatype.jdk8.Jdk8Module());

    TorchProperties properties = new TorchProperties(
            new TorchProperties.Base("http://base-url"),
            new TorchProperties.Output(new TorchProperties.Output.File(new TorchProperties.Output.File.Server("http://server-url"))),
            new TorchProperties.Profile("/profile-dir"),
            new TorchProperties.Mapping("consent", "typeToConsent"),
            new TorchProperties.Flare(null),
            new TorchProperties.Results("BASE_DIR", "persistence"),
            10, 5, 100,
            "mappingsFile", "conceptTreeFile", "dseMappingTreeFile",
            "search-parameters.json",
            true
    );

    WebTestClient client;

    @BeforeEach
    void setup() {
        FhirContext fhirContext = FhirContext.forR4();
        FhirController fhirController = new FhirController(fhirContext, extractDataParametersParser, validator, jobPersistenceService, properties, objectMapper);
        client = WebTestClient.bindToRouterFunction(fhirController.queryRouter()).build();
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

    @Nested
    class CheckStatusTests {


        @Test
        void invalidUuidReturnsBadRequest() {
            var response = client.get().uri("/fhir/__status/not-a-uuid").exchange();

            response.expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.resourceType").isEqualTo("OperationOutcome")
                    .jsonPath("$.issue[0].code").isEqualTo("invalid");
        }

        @Test
        void nonExistentJobReturnsNotFound() {
            UUID jobId = UUID.randomUUID();
            when(jobPersistenceService.getJob(jobId)).thenReturn(Optional.empty());

            var response = client.get().uri("/fhir/__status/" + jobId).exchange();

            response.expectStatus().isNotFound()
                    .expectBody()
                    .jsonPath("$.resourceType").isEqualTo("OperationOutcome")
                    .jsonPath("$.issue[0].code").isEqualTo("not-found");
        }

        @Test
        void runningJobReturnsAcceptedWithOperationOutcome() {
            UUID jobId = UUID.randomUUID();
            // Create a job in RUNNING state using your factory method
            Job runningJob = JobTest.job(jobId, JobStatus.RUNNING_PROCESS_BATCH,
                    WorkUnitState.initNow(), Map.of(), WorkUnitState.initNow());

            when(jobPersistenceService.getJob(jobId)).thenReturn(Optional.of(runningJob));

            client.get().uri("/fhir/__status/" + jobId).exchange()
                    .expectStatus().isAccepted()
                    .expectHeader().contentType(MediaType.APPLICATION_JSON)
                    .expectBody()
                    .jsonPath("$.resourceType").isEqualTo("OperationOutcome");
        }

        @Test
        void completedJobReturnsResultsAndExtensions() {
            UUID jobId = UUID.randomUUID();
            UUID batchId = UUID.randomUUID();

            // Setup a completed job with one batch
            Map<UUID, BatchState> batches = Map.of(
                    batchId, new BatchState(batchId, WorkUnitState.initNow().finishNow(WorkUnitStatus.FINISHED))
            );
            Job completedJob = JobTest.job(jobId, JobStatus.COMPLETED,
                    WorkUnitState.initNow(), batches,
                    WorkUnitState.initNow().finishNow(WorkUnitStatus.FINISHED));

            when(jobPersistenceService.getJob(jobId)).thenReturn(Optional.of(completedJob));

            client.get().uri("/fhir/__status/" + jobId).exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.output").isArray()
                    // Verify the URL construction logic from your controller
                    .jsonPath("$.output[?(@.type=='NDJSON Bundle')].url").exists()
                    .jsonPath("$.extension[0].url").isEqualTo("https://torch.mii.de/fhir/StructureDefinition/torch-job");
        }

        @Test
        void failedJobReturnsInternalServerError() {
            UUID jobId = UUID.randomUUID();
            Job failedJob = JobTest.job(jobId, JobStatus.FAILED,
                    WorkUnitState.initNow(), Map.of(), WorkUnitState.initNow());

            when(jobPersistenceService.getJob(jobId)).thenReturn(Optional.of(failedJob));

            client.get().uri("/fhir/__status/" + jobId).exchange()
                    .expectStatus().is5xxServerError()
                    .expectBody()
                    .jsonPath("$.resourceType").isEqualTo("OperationOutcome");
        }
    }
}
