package de.medizininformatikinitiative.torch.rest;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import de.medizininformatikinitiative.torch.TestUtils;
import de.medizininformatikinitiative.torch.config.TorchProperties;
import de.medizininformatikinitiative.torch.diagnostics.JobDiagnosticSummary;
import de.medizininformatikinitiative.torch.exceptions.ConsentFormatException;
import de.medizininformatikinitiative.torch.exceptions.ValidationException;
import de.medizininformatikinitiative.torch.jobhandling.BatchState;
import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.JobStatus;
import de.medizininformatikinitiative.torch.jobhandling.failure.Issue;
import de.medizininformatikinitiative.torch.jobhandling.failure.Severity;
import de.medizininformatikinitiative.torch.jobhandling.workunit.WorkUnitState;
import de.medizininformatikinitiative.torch.jobhandling.workunit.WorkUnitStatus;
import de.medizininformatikinitiative.torch.model.crtdl.ExtractDataParameters;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedCrtdl;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedDataExtraction;
import de.medizininformatikinitiative.torch.service.CohortQueryService;
import de.medizininformatikinitiative.torch.service.CrtdlValidatorService;
import de.medizininformatikinitiative.torch.service.ExtractDataService;
import de.medizininformatikinitiative.torch.service.JobPersistenceService;
import de.medizininformatikinitiative.torch.util.CrtdlFactory;
import de.medizininformatikinitiative.torch.jobhandling.JobParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
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

    @Mock
    CohortQueryService cohortQueryService;

    ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .registerModule(new com.fasterxml.jackson.datatype.jdk8.Jdk8Module());

    TorchProperties properties = new TorchProperties(
            new TorchProperties.Base("http://base-url"),
            new TorchProperties.Output(new TorchProperties.Output.File(new TorchProperties.Output.File.Server("http://server-url"))),
            new TorchProperties.Profile("/profile-dir"),
            new TorchProperties.Mapping("typeToConsent"),
            new TorchProperties.Flare(null, null),
            new TorchProperties.Results("BASE_DIR"),
            10, 5, 100,
            "mappingsFile", "conceptTreeFile", "dseMappingTreeFile",
            "search-parameters.json",
            true
    );

    WebTestClient client;

    @BeforeEach
    void setup() {
        FhirContext fhirContext = FhirContext.forR4();
        FhirController fhirController = new FhirController(fhirContext, extractDataParametersParser, validator, jobPersistenceService, properties, objectMapper, cohortQueryService);
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

        @ParameterizedTest
        @EnumSource(value = JobStatus.class, names = {
                "RUNNING_PROCESS_BATCH", "RUNNING_GET_COHORT", "RUNNING_PROCESS_CORE", "PENDING", "PAUSED"
        })
        void inProgressJobReturnsAcceptedWithXProgress(JobStatus status) {
            UUID jobId = UUID.randomUUID();
            Job job = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams()).withStatus(status);

            when(jobPersistenceService.getJob(jobId)).thenReturn(Optional.of(job));

            client.get().uri("/fhir/__status/" + jobId).exchange()
                    .expectStatus().isAccepted()
                    .expectHeader().contentType(MediaType.APPLICATION_JSON)
                    .expectHeader().exists("X-Progress")
                    .expectBody()
                    .jsonPath("$.resourceType").isEqualTo("OperationOutcome");
        }

        @Test
        void completedJobReturnsResultsAndExtensions() {
            UUID jobId = UUID.randomUUID();
            UUID batchId = UUID.randomUUID();

            Job completedJob = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams()).withStatus(JobStatus.COMPLETED)
                    .withBatchState(new BatchState(batchId, WorkUnitState.initNow().finishNow(WorkUnitStatus.FINISHED)))
                    .withCoreState(WorkUnitState.initNow().finishNow(WorkUnitStatus.FINISHED));

            when(jobPersistenceService.getJob(jobId)).thenReturn(Optional.of(completedJob));

            client.get().uri("/fhir/__status/" + jobId).exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.output").isArray()
                    .jsonPath("$.output[?(@.type=='NDJSON Bundle')].url").exists()
                    .jsonPath("$.error").isArray()
                    .jsonPath("$.request").isEqualTo("")
                    .jsonPath("$.extension[0].url").isEqualTo("https://torch.mii.de/fhir/StructureDefinition/torch-job");
        }

        @Test
        void completedJobEmitsKickOffUrlAsRequest() {
            UUID jobId = UUID.randomUUID();
            JobParameters params = new JobParameters(
                    TestUtils.emptyJobParams().crtdl(),
                    java.util.List.of(),
                    "http://base-url/fhir/$extract-data"
            );
            Job completedJob = Job.init(jobId, params).withStatus(JobStatus.COMPLETED)
                    .withCoreState(WorkUnitState.initNow().finishNow(WorkUnitStatus.FINISHED));

            when(jobPersistenceService.getJob(jobId)).thenReturn(Optional.of(completedJob));

            client.get().uri("/fhir/__status/" + jobId).exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.transactionTime").exists()
                    .jsonPath("$.request").isEqualTo("http://base-url/fhir/$extract-data")
                    .jsonPath("$.requiresAccessToken").isEqualTo(false)
                    .jsonPath("$.output").isArray()
                    .jsonPath("$.error").isArray();
        }

        @Test
        void completedJobWithSerializationErrorReturnsInternalServerError() throws Exception {
            UUID jobId = UUID.randomUUID();
            UUID batchId = UUID.randomUUID();
            Job completedJob = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams()).withStatus(JobStatus.COMPLETED)
                    .withBatchState(new BatchState(batchId, WorkUnitState.initNow().finishNow(WorkUnitStatus.FINISHED)))
                    .withCoreState(WorkUnitState.initNow().finishNow(WorkUnitStatus.FINISHED));

            when(jobPersistenceService.getJob(jobId)).thenReturn(Optional.of(completedJob));

            ObjectMapper failingMapper = spy(objectMapper);
            doThrow(new com.fasterxml.jackson.core.JsonProcessingException("simulated") {
            })
                    .when(failingMapper).writeValueAsString(any());

            FhirController controller = new FhirController(FhirContext.forR4(), extractDataParametersParser, validator, jobPersistenceService, properties, failingMapper, cohortQueryService);
            WebTestClient.bindToRouterFunction(controller.queryRouter()).build()
                    .get().uri("/fhir/__status/" + jobId).exchange()
                    .expectStatus().is5xxServerError()
                    .expectHeader().contentType("application/fhir+json")
                    .expectBody()
                    .jsonPath("$.resourceType").isEqualTo("OperationOutcome");
        }

        @Test
        void failedJobReturnsInternalServerError() {
            UUID jobId = UUID.randomUUID();
            Job failedJob = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams()).withStatus(JobStatus.FAILED);

            when(jobPersistenceService.getJob(jobId)).thenReturn(Optional.of(failedJob));

            client.get().uri("/fhir/__status/" + jobId).exchange()
                    .expectStatus().is5xxServerError()
                    .expectBody()
                    .jsonPath("$.resourceType").isEqualTo("OperationOutcome");
        }

        @Test
        void tempFailedJobReturnsServiceUnavailable() {
            UUID jobId = UUID.randomUUID();
            Job job = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams()).withStatus(JobStatus.TEMP_FAILED);
            when(jobPersistenceService.getJob(jobId)).thenReturn(Optional.of(job));

            client.get().uri("/fhir/__status/" + jobId).exchange()
                    .expectStatus().isEqualTo(503)
                    .expectBody()
                    .jsonPath("$.resourceType").isEqualTo("OperationOutcome");
        }

        @Test
        void cancelledJobReturnsGone() {
            UUID jobId = UUID.randomUUID();
            Job job = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams()).withStatus(JobStatus.CANCELLED);
            when(jobPersistenceService.getJob(jobId)).thenReturn(Optional.of(job));

            client.get().uri("/fhir/__status/" + jobId).exchange()
                    .expectStatus().isEqualTo(410)
                    .expectBody()
                    .jsonPath("$.resourceType").isEqualTo("OperationOutcome");
        }

        @Test
        void deletedJobReturnsNotFound() {
            UUID jobId = UUID.randomUUID();
            Job job = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams()).withStatus(JobStatus.DELETED);
            when(jobPersistenceService.getJob(jobId)).thenReturn(Optional.of(job));

            client.get().uri("/fhir/__status/" + jobId).exchange()
                    .expectStatus().isNotFound()
                    .expectBody()
                    .jsonPath("$.resourceType").isEqualTo("OperationOutcome");
        }

        @Test
        void completedJobWithDiagnosticsIncludesDiagnosticsExtensions() throws IOException {
            UUID jobId = UUID.randomUUID();
            Job completedJob = Job.init(jobId, TestUtils.emptyJobParams())
                    .withStatus(JobStatus.COMPLETED)
                    .withCoreState(WorkUnitState.initNow().finishNow(WorkUnitStatus.FINISHED));

            when(jobPersistenceService.getJob(jobId)).thenReturn(Optional.of(completedJob));
            when(jobPersistenceService.jobSummaryExists(any())).thenReturn(true);
            when(jobPersistenceService.loadJobDiagnostics(any())).thenReturn(JobDiagnosticSummary.empty());
            when(jobPersistenceService.resourceExclusionsExists(any())).thenReturn(true);
            when(jobPersistenceService.patientExclusionsExists(any())).thenReturn(true);

            client.get().uri("/fhir/__status/" + jobId).exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.output[?(@.type=='OperationOutcome')]").doesNotExist()
                    .jsonPath("$.extension[?(@.url=='torch-job-diagnostics-summary')]").exists()
                    .jsonPath("$.extension[?(@.url=='torch-resource-exclusions')]").exists()
                    .jsonPath("$.extension[?(@.url=='torch-patient-exclusions')]").exists();
        }

        @Test
        void completedJobWithIssuesIncludesIssuesExtension() {
            UUID jobId = UUID.randomUUID();
            Job completedJob = Job.init(jobId, TestUtils.emptyJobParams())
                    .withStatus(JobStatus.COMPLETED)
                    .withCoreState(WorkUnitState.initNow().finishNow(WorkUnitStatus.FINISHED))
                    .withIssuesAdded(List.of(new Issue(Severity.WARNING, "Batch skipped: no consenting patients", "")));

            when(jobPersistenceService.getJob(jobId)).thenReturn(Optional.of(completedJob));

            client.get().uri("/fhir/__status/" + jobId).exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.extension[?(@.url=='torch-job-issues')]").exists()
                    .jsonPath("$.extension[?(@.url=='torch-job-issues')].valueObject").isArray();
        }

        @Test
        void completedJobWithNoIssuesOmitsIssuesExtension() {
            UUID jobId = UUID.randomUUID();
            Job completedJob = Job.init(jobId, TestUtils.emptyJobParams())
                    .withStatus(JobStatus.COMPLETED)
                    .withCoreState(WorkUnitState.initNow().finishNow(WorkUnitStatus.FINISHED));

            when(jobPersistenceService.getJob(jobId)).thenReturn(Optional.of(completedJob));

            client.get().uri("/fhir/__status/" + jobId).exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.extension[?(@.url=='torch-job-issues')]").doesNotExist();
        }
    }

    @Nested
    class CqlForJobTests {

        @Test
        void existingJobReturnsCql() {
            UUID jobId = UUID.randomUUID();
            Job job = Job.init(jobId, TestUtils.emptyJobParams());
            when(jobPersistenceService.getJob(jobId)).thenReturn(Optional.of(job));
            when(cohortQueryService.translateToCql(any())).thenReturn(Mono.just("define Patient: ..."));

            client.get().uri("/fhir/$translate-cql/" + jobId)
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_PLAIN)
                    .expectBody(String.class).isEqualTo("define Patient: ...");
        }

        @Test
        void invalidJobIdReturnsBadRequest() {
            client.get().uri("/fhir/$translate-cql/not-a-uuid")
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectHeader().contentType("application/fhir+json")
                    .expectBody()
                    .jsonPath("$.resourceType").isEqualTo("OperationOutcome")
                    .jsonPath("$.issue[0].code").isEqualTo("invalid");
        }

        @Test
        void nonExistentJobReturnsNotFound() {
            UUID jobId = UUID.randomUUID();
            when(jobPersistenceService.getJob(jobId)).thenReturn(Optional.empty());

            client.get().uri("/fhir/$translate-cql/" + jobId)
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectHeader().contentType("application/fhir+json")
                    .expectBody()
                    .jsonPath("$.resourceType").isEqualTo("OperationOutcome")
                    .jsonPath("$.issue[0].code").isEqualTo("not-found");
        }

        @Test
        void translationErrorReturnsInternalServerError() {
            UUID jobId = UUID.randomUUID();
            Job job = Job.init(jobId, TestUtils.emptyJobParams());
            when(jobPersistenceService.getJob(jobId)).thenReturn(Optional.of(job));
            when(cohortQueryService.translateToCql(any()))
                    .thenReturn(Mono.error(new RuntimeException("Translation failed")));

            client.get().uri("/fhir/$translate-cql/" + jobId)
                    .exchange()
                    .expectStatus().is5xxServerError()
                    .expectHeader().contentType("application/fhir+json")
                    .expectBody()
                    .jsonPath("$.resourceType").isEqualTo("OperationOutcome");
        }
    }

    @Nested
    class CqlTranslationTests {

        @Test
        void ccdlBodyReturnsCql() {
            when(cohortQueryService.translateToCql(any())).thenReturn(Mono.just("define Patient: ..."));

            client.post().uri("/fhir/$translate-cql")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"version\":\"http://to_be_decided.com/draft-1/schema#\",\"inclusionCriteria\":[[]]}")
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_PLAIN)
                    .expectBody(String.class).isEqualTo("define Patient: ...");
        }

        @Test
        void crtdlBodyExtractsCohortDefinition() {
            when(cohortQueryService.translateToCql(any())).thenReturn(Mono.just("define Patient: ..."));

            client.post().uri("/fhir/$translate-cql")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"cohortDefinition\":{\"inclusionCriteria\":[[]]},\"dataExtraction\":{}}")
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_PLAIN)
                    .expectBody(String.class).isEqualTo("define Patient: ...");
        }

        @Test
        void emptyBodyReturnsBadRequest() {
            client.post().uri("/fhir/$translate-cql")
                    .contentType(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectHeader().contentType("application/fhir+json")
                    .expectBody()
                    .jsonPath("$.resourceType").isEqualTo("OperationOutcome");
        }

        @Test
        void translationErrorReturnsInternalServerError() {
            when(cohortQueryService.translateToCql(any()))
                    .thenReturn(Mono.error(new RuntimeException("Translation failed")));

            client.post().uri("/fhir/$translate-cql")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{}")
                    .exchange()
                    .expectStatus().is5xxServerError()
                    .expectHeader().contentType("application/fhir+json")
                    .expectBody()
                    .jsonPath("$.resourceType").isEqualTo("OperationOutcome");
        }

        @Test
        void invalidJsonBodyReturnsBadRequest() {
            client.post().uri("/fhir/$translate-cql")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("")
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectHeader().contentType("application/fhir+json")
                    .expectBody()
                    .jsonPath("$.resourceType").isEqualTo("OperationOutcome");
        }
    }

    @Nested
    class ExtractDataSuccessTests {

        AnnotatedCrtdl annotated = new AnnotatedCrtdl(
                JsonNodeFactory.instance.objectNode(),
                new AnnotatedDataExtraction(List.of()),
                Optional.empty());
        ExtractDataParameters params = new ExtractDataParameters(CrtdlFactory.empty(), List.of());

        @Test
        void successReturnsAcceptedWithContentLocation() throws Exception {
            UUID createdJobId = UUID.randomUUID();
            when(extractDataParametersParser.parseParameters(any())).thenReturn(params);
            when(validator.validateAndAnnotate(any())).thenReturn(annotated);
            when(jobPersistenceService.createJob(any(), any(), any())).thenReturn(createdJobId);

            client.post().uri("/fhir/$extract-data")
                    .contentType(MediaType.valueOf("application/fhir+json"))
                    .bodyValue("{}")
                    .exchange()
                    .expectStatus().isAccepted()
                    .expectHeader().valueMatches("Content-Location", ".*/fhir/__status/" + createdJobId);
        }

        @Test
        void createJobIOExceptionReturnsInternalServerError() throws Exception {
            when(extractDataParametersParser.parseParameters(any())).thenReturn(params);
            when(validator.validateAndAnnotate(any())).thenReturn(annotated);
            when(jobPersistenceService.createJob(any(), any(), any())).thenThrow(new IOException("disk full"));

            client.post().uri("/fhir/$extract-data")
                    .contentType(MediaType.valueOf("application/fhir+json"))
                    .bodyValue("{}")
                    .exchange()
                    .expectStatus().is5xxServerError()
                    .expectHeader().contentType("application/fhir+json")
                    .expectBody()
                    .jsonPath("$.resourceType").isEqualTo("OperationOutcome");
        }

        @Test
        void consentFormatExceptionReturnsBadRequest() throws Exception {
            when(extractDataParametersParser.parseParameters(any())).thenReturn(params);
            when(validator.validateAndAnnotate(any())).thenThrow(new ConsentFormatException("bad consent"));

            client.post().uri("/fhir/$extract-data")
                    .contentType(MediaType.valueOf("application/fhir+json"))
                    .bodyValue("{}")
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectHeader().contentType("application/fhir+json")
                    .expectBody()
                    .jsonPath("$.resourceType").isEqualTo("OperationOutcome");
        }
    }

    @Test
    void completedJobOmitsSkippedBatchOutputs() {
        UUID jobId = UUID.randomUUID();
        UUID finishedBatchId = UUID.randomUUID();
        UUID skippedBatchId = UUID.randomUUID();

        Job completedJob = Job.init(jobId, TestUtils.emptyJobParams())
                .withStatus(JobStatus.COMPLETED)
                .withBatchState(new BatchState(
                        finishedBatchId,
                        WorkUnitState.initNow().finishNow(WorkUnitStatus.FINISHED)))
                .withBatchState(new BatchState(
                        skippedBatchId,
                        WorkUnitState.initNow().finishNow(WorkUnitStatus.SKIPPED)))
                .withCoreState(WorkUnitState.initNow().finishNow(WorkUnitStatus.FINISHED));

        when(jobPersistenceService.getJob(jobId)).thenReturn(Optional.of(completedJob));

        client.get().uri("/fhir/__status/" + jobId).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.output").isArray()
                .jsonPath("$.output.length()").isEqualTo(2)
                .jsonPath("$.output[?(@.url=='http://server-url/" + jobId + "/" + finishedBatchId + ".ndjson')]").exists()
                .jsonPath("$.output[?(@.url=='http://server-url/" + jobId + "/" + skippedBatchId + ".ndjson')]").doesNotExist()
                .jsonPath("$.output[?(@.url=='http://server-url/" + jobId + "/core.ndjson')]").exists();
    }
}
