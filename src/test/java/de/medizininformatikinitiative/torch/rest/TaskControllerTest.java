package de.medizininformatikinitiative.torch.rest;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.TestUtils;
import de.medizininformatikinitiative.torch.exceptions.JobNotFoundException;
import de.medizininformatikinitiative.torch.exceptions.StateConflictException;
import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.JobParameters;
import de.medizininformatikinitiative.torch.jobhandling.JobStatus;
import de.medizininformatikinitiative.torch.service.JobPersistenceService;
import de.medizininformatikinitiative.torch.taskhandling.JobTaskMapper;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskControllerTest {

    static final JobParameters EMPTY_PARAMETERS = TestUtils.emptyJobParams();

    @Mock
    JobPersistenceService persistence;

    FhirContext fhirContext;
    JobTaskMapper jobTaskMapper;
    TaskController controller;
    WebTestClient client;

    @BeforeEach
    void setUp() {
        fhirContext = FhirContext.forR4();
        jobTaskMapper = new JobTaskMapper();
        controller = new TaskController(fhirContext, persistence, jobTaskMapper);
        client = WebTestClient.bindToRouterFunction(controller.taskRouter()).build();
    }

    private Task parseTask(String json) {
        return fhirContext.newJsonParser().parseResource(Task.class, json);
    }

    private OperationOutcome parseOutcome(String json) {
        return fhirContext.newJsonParser().parseResource(OperationOutcome.class, json);
    }

    private Job job(UUID jobId, JobStatus status, long version) {
        Job job = Job.init(jobId, EMPTY_PARAMETERS)
                .withStatus(status);

        while (job.version() < version) {
            job = job.incrementVersion();
        }
        return job;
    }

    @Nested
    class PauseOperation {

        @Test
        void ok() throws JobNotFoundException {
            UUID jobId = UUID.randomUUID();
            Job paused = job(jobId, JobStatus.PAUSED, 2);

            when(persistence.pauseJob(jobId))
                    .thenReturn(paused);

            String response = client.post()
                    .uri("/fhir/Task/{id}/$pause", jobId)
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().contentType("application/fhir+json")
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            Task task = parseTask(response);
            assertThat(task.getIdElement().getIdPart()).isEqualTo(jobId.toString());
            assertThat(task.getStatus()).isEqualTo(Task.TaskStatus.ONHOLD);
            assertThat(task.getBusinessStatus().getCodingFirstRep().getCode()).isEqualTo(JobStatus.PAUSED.name());
        }

        @Test
        void conflict() throws JobNotFoundException {
            UUID jobId = UUID.randomUUID();

            when(persistence.pauseJob(jobId))
                    .thenThrow(new StateConflictException("Cannot pause from status COMPLETED"));

            String response = client.post()
                    .uri("/fhir/Task/{id}/$pause", jobId)
                    .exchange()
                    .expectStatus().isEqualTo(409)
                    .expectHeader().contentType("application/fhir+json")
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            OperationOutcome outcome = parseOutcome(response);
            assertThat(outcome.getIssueFirstRep().getCode()).isEqualTo(OperationOutcome.IssueType.CONFLICT);
        }

        @Test
        void notFound() throws JobNotFoundException {
            UUID jobId = UUID.randomUUID();

            when(persistence.pauseJob(jobId))
                    .thenThrow(new JobNotFoundException(jobId));

            String response = client.post()
                    .uri("/fhir/Task/{id}/$pause", jobId)
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectHeader().contentType("application/fhir+json")
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            OperationOutcome outcome = parseOutcome(response);
            assertThat(outcome.getIssueFirstRep().getCode()).isEqualTo(OperationOutcome.IssueType.NOTFOUND);
        }

        @Test
        void malformedId() {
            String response = client.post()
                    .uri("/fhir/Task/{id}/$pause", "not-a-uuid")
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectHeader().contentType("application/fhir+json")
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            OperationOutcome outcome = parseOutcome(response);
            assertThat(outcome.getIssueFirstRep().getCode()).isEqualTo(OperationOutcome.IssueType.NOTFOUND);
        }
    }

    @Nested
    class CancelOperation {

        @Test
        void ok() throws JobNotFoundException {
            UUID jobId = UUID.randomUUID();
            Job cancelled = job(jobId, JobStatus.CANCELLED, 2);

            when(persistence.cancelJob(jobId))
                    .thenReturn(cancelled);

            String response = client.post()
                    .uri("/fhir/Task/{id}/$cancel", jobId)
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().contentType("application/fhir+json")
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            Task task = parseTask(response);
            assertThat(task.getIdElement().getIdPart()).isEqualTo(jobId.toString());
            assertThat(task.getStatus()).isEqualTo(Task.TaskStatus.CANCELLED);
            assertThat(task.getBusinessStatus().getCodingFirstRep().getCode()).isEqualTo(JobStatus.CANCELLED.name());
        }

        @Test
        void conflict() throws JobNotFoundException {
            UUID jobId = UUID.randomUUID();

            when(persistence.cancelJob(jobId))
                    .thenThrow(new StateConflictException("Cannot cancel from status FAILED"));

            String response = client.post()
                    .uri("/fhir/Task/{id}/$cancel", jobId)
                    .exchange()
                    .expectStatus().isEqualTo(409)
                    .expectHeader().contentType("application/fhir+json")
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            OperationOutcome outcome = parseOutcome(response);
            assertThat(outcome.getIssueFirstRep().getCode()).isEqualTo(OperationOutcome.IssueType.CONFLICT);
        }

        @Test
        void notFound() throws JobNotFoundException {
            UUID jobId = UUID.randomUUID();

            when(persistence.cancelJob(jobId))
                    .thenThrow(new JobNotFoundException(jobId));

            String response = client.post()
                    .uri("/fhir/Task/{id}/$cancel", jobId)
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectHeader().contentType("application/fhir+json")
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            OperationOutcome outcome = parseOutcome(response);
            assertThat(outcome.getIssueFirstRep().getCode()).isEqualTo(OperationOutcome.IssueType.NOTFOUND);
        }

        @Test
        void malformedId() {
            String response = client.post()
                    .uri("/fhir/Task/{id}/$cancel", "broken-id")
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectHeader().contentType("application/fhir+json")
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            OperationOutcome outcome = parseOutcome(response);
            assertThat(outcome.getIssueFirstRep().getCode()).isEqualTo(OperationOutcome.IssueType.NOTFOUND);
        }
    }

    @Nested
    class ResumeOperation {

        @Test
        void ok() throws JobNotFoundException {
            UUID jobId = UUID.randomUUID();
            Job resumed = job(jobId, JobStatus.RUNNING_PROCESS_CORE, 3);

            when(persistence.resumeJob(jobId))
                    .thenReturn(resumed);

            String response = client.post()
                    .uri("/fhir/Task/{id}/$resume", jobId)
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().contentType("application/fhir+json")
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            Task task = parseTask(response);
            assertThat(task.getIdElement().getIdPart()).isEqualTo(jobId.toString());
            assertThat(task.getStatus()).isEqualTo(Task.TaskStatus.INPROGRESS);
            assertThat(task.getBusinessStatus().getCodingFirstRep().getCode())
                    .isEqualTo(JobStatus.RUNNING_PROCESS_CORE.name());
        }

        @Test
        void conflict() throws JobNotFoundException {
            UUID jobId = UUID.randomUUID();

            when(persistence.resumeJob(jobId))
                    .thenThrow(new StateConflictException("Cannot resume from status FAILED"));

            String response = client.post()
                    .uri("/fhir/Task/{id}/$resume", jobId)
                    .exchange()
                    .expectStatus().isEqualTo(409)
                    .expectHeader().contentType("application/fhir+json")
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            OperationOutcome outcome = parseOutcome(response);
            assertThat(outcome.getIssueFirstRep().getCode()).isEqualTo(OperationOutcome.IssueType.CONFLICT);
        }

        @Test
        void notFound() throws JobNotFoundException {
            UUID jobId = UUID.randomUUID();

            when(persistence.resumeJob(jobId))
                    .thenThrow(new JobNotFoundException(jobId));

            String response = client.post()
                    .uri("/fhir/Task/{id}/$resume", jobId)
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectHeader().contentType("application/fhir+json")
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            OperationOutcome outcome = parseOutcome(response);
            assertThat(outcome.getIssueFirstRep().getCode()).isEqualTo(OperationOutcome.IssueType.NOTFOUND);
        }

        @Test
        void malformedId() {
            String response = client.post()
                    .uri("/fhir/Task/{id}/$resume", "invalid-id")
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectHeader().contentType("application/fhir+json")
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            OperationOutcome outcome = parseOutcome(response);
            assertThat(outcome.getIssueFirstRep().getCode()).isEqualTo(OperationOutcome.IssueType.NOTFOUND);
        }
    }

    @Nested
    class DeleteOperation {

        @Test
        void ok() throws Exception {
            UUID jobId = UUID.randomUUID();

            doNothing().when(persistence).deleteJob(jobId);

            client.delete()
                    .uri("/fhir/Task/{id}", jobId)
                    .exchange()
                    .expectStatus().isNoContent();
        }

        @Test
        void notFound() throws Exception {
            UUID jobId = UUID.randomUUID();

            doThrow(new JobNotFoundException(jobId)).when(persistence).deleteJob(jobId);

            String response = client.delete()
                    .uri("/fhir/Task/{id}", jobId)
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectHeader().contentType("application/fhir+json")
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            OperationOutcome outcome = parseOutcome(response);
            assertThat(outcome.getIssueFirstRep().getCode()).isEqualTo(OperationOutcome.IssueType.NOTFOUND);
        }

        @Test
        void malformedId() {
            String response = client.delete()
                    .uri("/fhir/Task/{id}", "bad-id")
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectHeader().contentType("application/fhir+json")
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            OperationOutcome outcome = parseOutcome(response);
            assertThat(outcome.getIssueFirstRep().getCode()).isEqualTo(OperationOutcome.IssueType.NOTFOUND);
        }

        @Test
        void internalServerErrorOnIoException() throws Exception {
            UUID jobId = UUID.randomUUID();

            doThrow(new IOException("disk failure")).when(persistence).deleteJob(jobId);

            String response = client.delete()
                    .uri("/fhir/Task/{id}", jobId)
                    .exchange()
                    .expectStatus().isEqualTo(500)
                    .expectHeader().contentType("application/fhir+json")
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            OperationOutcome outcome = parseOutcome(response);
            assertThat(outcome.getIssueFirstRep().getSeverity())
                    .isEqualTo(OperationOutcome.IssueSeverity.FATAL);
        }
    }
}
