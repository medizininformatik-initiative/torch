package de.medizininformatikinitiative.torch.rest;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.TestUtils;
import de.medizininformatikinitiative.torch.exceptions.JobNotFoundException;
import de.medizininformatikinitiative.torch.exceptions.StateConflictException;
import de.medizininformatikinitiative.torch.exceptions.VersionConflictException;
import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.JobParameters;
import de.medizininformatikinitiative.torch.jobhandling.JobPriority;
import de.medizininformatikinitiative.torch.jobhandling.JobStatus;
import de.medizininformatikinitiative.torch.service.JobPersistenceService;
import de.medizininformatikinitiative.torch.taskhandling.JobTaskMapper;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    @Nested
    class GetTask {

        @Test
        void ok() {
            UUID jobId = UUID.randomUUID();
            Job running = job(jobId, JobStatus.RUNNING_PROCESS_BATCH, 1);

            when(persistence.getJob(jobId)).thenReturn(Optional.of(running));

            String response = client.get()
                    .uri("/fhir/Task/{id}", jobId)
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().contentType("application/fhir+json")
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            Task task = parseTask(response);
            assertThat(task.getIdElement().getIdPart()).isEqualTo(jobId.toString());
            assertThat(task.getStatus()).isEqualTo(Task.TaskStatus.INPROGRESS);
            assertThat(task.getMeta().getVersionId()).isEqualTo("1");
        }

        @Test
        void notFound() {
            UUID jobId = UUID.randomUUID();

            when(persistence.getJob(jobId)).thenReturn(Optional.empty());

            String response = client.get()
                    .uri("/fhir/Task/{id}", jobId)
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectHeader().contentType("application/fhir+json")
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            OperationOutcome outcome = parseOutcome(response);
            assertThat(outcome.getIssueFirstRep().getCode()).isEqualTo(OperationOutcome.IssueType.NOTFOUND);
            assertThat(outcome.getIssueFirstRep().getDiagnostics())
                    .contains("Task/" + jobId + " not found");
        }

        @Test
        void malformedId() {
            String response = client.get()
                    .uri("/fhir/Task/{id}", "not-a-uuid")
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
    class UpdateTask {

        private String taskBody(Task.TaskPriority priority) {
            Task task = new Task();
            task.setPriority(priority);
            return fhirContext.newJsonParser().encodeResourceToString(task);
        }

        @Test
        void okChangesToHigh() throws JobNotFoundException, VersionConflictException {
            UUID jobId = UUID.randomUUID();
            Job updated = job(jobId, JobStatus.PENDING, 2).withPriority(JobPriority.HIGH);

            when(persistence.changePriority(jobId, JobPriority.HIGH, 1L)).thenReturn(updated);

            String response = client.put()
                    .uri("/fhir/Task/{id}", jobId)
                    .header("If-Match", "W/\"1\"")
                    .contentType(MediaType.valueOf("application/fhir+json"))
                    .bodyValue(taskBody(Task.TaskPriority.ASAP))
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().contentType("application/fhir+json")
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            Task task = parseTask(response);
            assertThat(task.getPriority()).isEqualTo(Task.TaskPriority.ASAP);
        }

        @Test
        void okDefaultsToNormalWhenPriorityMissing() throws JobNotFoundException, VersionConflictException {
            UUID jobId = UUID.randomUUID();
            Job updated = job(jobId, JobStatus.PENDING, 2).withPriority(JobPriority.NORMAL);

            when(persistence.changePriority(jobId, JobPriority.NORMAL, 1L)).thenReturn(updated);

            String response = client.put()
                    .uri("/fhir/Task/{id}", jobId)
                    .header("If-Match", "W/\"1\"")
                    .contentType(MediaType.valueOf("application/fhir+json"))
                    .bodyValue("""
                            {
                              "resourceType": "Task"
                            }
                            """)
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().contentType("application/fhir+json")
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            Task task = parseTask(response);
            assertThat(task.getPriority()).isEqualTo(Task.TaskPriority.ROUTINE);
        }

        @Test
        void versionConflict() throws JobNotFoundException, VersionConflictException {
            UUID jobId = UUID.randomUUID();

            when(persistence.changePriority(jobId, JobPriority.NORMAL, 0L))
                    .thenThrow(new VersionConflictException(jobId, 0L, 3L));

            String response = client.put()
                    .uri("/fhir/Task/{id}", jobId)
                    .header("If-Match", "W/\"0\"")
                    .contentType(MediaType.valueOf("application/fhir+json"))
                    .bodyValue(taskBody(Task.TaskPriority.ROUTINE))
                    .exchange()
                    .expectStatus().isEqualTo(412)
                    .expectHeader().contentType("application/fhir+json")
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            OperationOutcome outcome = parseOutcome(response);
            assertThat(outcome.getIssueFirstRep().getCode()).isEqualTo(OperationOutcome.IssueType.CONFLICT);
        }

        @Test
        void missingIfMatchHeader() {
            UUID jobId = UUID.randomUUID();

            String response = client.put()
                    .uri("/fhir/Task/{id}", jobId)
                    .contentType(MediaType.valueOf("application/fhir+json"))
                    .bodyValue(taskBody(Task.TaskPriority.ROUTINE))
                    .exchange()
                    .expectStatus().isEqualTo(428)
                    .expectHeader().contentType("application/fhir+json")
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            OperationOutcome outcome = parseOutcome(response);
            assertThat(outcome.getIssueFirstRep().getCode()).isEqualTo(OperationOutcome.IssueType.REQUIRED);
        }

        @Test
        void malformedIfMatchHeader() {
            UUID jobId = UUID.randomUUID();

            String response = client.put()
                    .uri("/fhir/Task/{id}", jobId)
                    .header("If-Match", "invalid")
                    .contentType(MediaType.valueOf("application/fhir+json"))
                    .bodyValue(taskBody(Task.TaskPriority.ROUTINE))
                    .exchange()
                    .expectStatus().isEqualTo(428)
                    .expectHeader().contentType("application/fhir+json")
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            OperationOutcome outcome = parseOutcome(response);
            assertThat(outcome.getIssueFirstRep().getCode()).isEqualTo(OperationOutcome.IssueType.REQUIRED);
        }

        @Test
        void invalidBody() {
            UUID jobId = UUID.randomUUID();

            String response = client.put()
                    .uri("/fhir/Task/{id}", jobId)
                    .header("If-Match", "W/\"1\"")
                    .contentType(MediaType.valueOf("application/fhir+json"))
                    .bodyValue("{not-json")
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectHeader().contentType("application/fhir+json")
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            OperationOutcome outcome = parseOutcome(response);
            assertThat(outcome.getIssueFirstRep().getCode()).isEqualTo(OperationOutcome.IssueType.INVALID);
        }

        @Test
        void notFound() throws JobNotFoundException, VersionConflictException {
            UUID jobId = UUID.randomUUID();

            when(persistence.changePriority(jobId, JobPriority.NORMAL, 1L))
                    .thenThrow(new JobNotFoundException(jobId));

            String response = client.put()
                    .uri("/fhir/Task/{id}", jobId)
                    .header("If-Match", "W/\"1\"")
                    .contentType(MediaType.valueOf("application/fhir+json"))
                    .bodyValue(taskBody(Task.TaskPriority.ROUTINE))
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
            String response = client.put()
                    .uri("/fhir/Task/{id}", "not-a-uuid")
                    .header("If-Match", "W/\"0\"")
                    .contentType(MediaType.valueOf("application/fhir+json"))
                    .bodyValue(taskBody(Task.TaskPriority.ROUTINE))
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
    class SearchTasks {

        @Test
        void noFiltersReturnsAll() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            List<Job> jobs = List.of(
                    job(id1, JobStatus.PENDING, 0),
                    job(id2, JobStatus.COMPLETED, 1)
            );

            when(persistence.findJobs(Set.of(), Set.of())).thenReturn(jobs);

            String response = client.get()
                    .uri("/fhir/Task")
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().contentType("application/fhir+json")
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            Bundle bundle = fhirContext.newJsonParser().parseResource(Bundle.class, response);
            assertThat(bundle.getType()).isEqualTo(Bundle.BundleType.SEARCHSET);
            assertThat(bundle.getTotal()).isEqualTo(2);
        }

        @Test
        void filterByStatus() {
            UUID id1 = UUID.randomUUID();

            when(persistence.findJobs(Set.of(), Set.of(JobStatus.COMPLETED)))
                    .thenReturn(List.of(job(id1, JobStatus.COMPLETED, 0)));

            String response = client.get()
                    .uri("/fhir/Task?status=COMPLETED")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            Bundle bundle = fhirContext.newJsonParser().parseResource(Bundle.class, response);
            assertThat(bundle.getTotal()).isEqualTo(1);
        }

        @Test
        void filterByIds() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();

            when(persistence.findJobs(Set.of(id1, id2), Set.of()))
                    .thenReturn(List.of(
                            job(id1, JobStatus.PENDING, 0),
                            job(id2, JobStatus.RUNNING_PROCESS_BATCH, 0)
                    ));

            String response = client.get()
                    .uri("/fhir/Task?_id=" + id1 + "," + id2)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            Bundle bundle = fhirContext.newJsonParser().parseResource(Bundle.class, response);
            assertThat(bundle.getTotal()).isEqualTo(2);
        }

        @Test
        void filterByIdsAndStatus() {
            UUID id1 = UUID.randomUUID();

            when(persistence.findJobs(Set.of(id1), Set.of(JobStatus.PENDING)))
                    .thenReturn(List.of(job(id1, JobStatus.PENDING, 0)));

            String response = client.get()
                    .uri("/fhir/Task?_id=" + id1 + "&status=PENDING")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            Bundle bundle = fhirContext.newJsonParser().parseResource(Bundle.class, response);
            assertThat(bundle.getTotal()).isEqualTo(1);
        }

        @Test
        void unknownStatusValuesAreIgnored() {
            when(persistence.findJobs(Set.of(), Set.of())).thenReturn(List.of());

            client.get()
                    .uri("/fhir/Task?status=BOGUS")
                    .exchange()
                    .expectStatus().isOk();
        }

        @Test
        void malformedIdsAreIgnored() {
            when(persistence.findJobs(Set.of(), Set.of())).thenReturn(List.of());

            client.get()
                    .uri("/fhir/Task?_id=not-a-uuid")
                    .exchange()
                    .expectStatus().isOk();
        }

        @Test
        void unsupportedSearchParamIsIgnoredInLenientMode() {
            when(persistence.findJobs(Set.of(), Set.of())).thenReturn(List.of());

            client.get()
                    .uri("/fhir/Task?subject=Patient/123")
                    .exchange()
                    .expectStatus().isOk();

            // optional:
            // verify(persistence).findJobs(Set.of(), Set.of());
        }

        @Test
        void unsupportedParamAlongsideSupportedIsIgnoredInLenientMode() {
            when(persistence.findJobs(Set.of(), Set.of(JobStatus.PENDING)))
                    .thenReturn(List.of());

            client.get()
                    .uri("/fhir/Task?status=PENDING&foo=bar")
                    .exchange()
                    .expectStatus().isOk();

            // optional:
            // verify(persistence).findJobs(Set.of(), Set.of(JobStatus.PENDING));
        }

        @Test
        void blankFiltersAreTreatedAsEmptySets() {
            when(persistence.findJobs(Set.of(), Set.of())).thenReturn(List.of());

            client.get()
                    .uri("/fhir/Task?_id=&status=")
                    .exchange()
                    .expectStatus().isOk();

            // optional verify if you want:
            // verify(persistence).findJobs(Set.of(), Set.of());
        }

        @Test
        void mixedValidAndInvalidStatusAndIdsKeepOnlyValidValues() {
            UUID id1 = UUID.randomUUID();

            when(persistence.findJobs(Set.of(id1), Set.of(JobStatus.PENDING)))
                    .thenReturn(List.of(job(id1, JobStatus.PENDING, 0)));

            String response = client.get()
                    .uri("/fhir/Task?_id=" + id1 + ",bad-id&status=PENDING,BOGUS")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            Bundle bundle = fhirContext.newJsonParser().parseResource(Bundle.class, response);
            assertThat(bundle.getTotal()).isEqualTo(1);
        }

        @Test
        void unsupportedSearchParamReturnsBadRequestInStrictMode() {
            String response = client.get()
                    .uri("/fhir/Task?subject=Patient/123")
                    .header("Prefer", "handling=strict")
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectHeader().contentType("application/fhir+json")
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            OperationOutcome outcome = parseOutcome(response);
            assertThat(outcome.getIssueFirstRep().getCode()).isEqualTo(OperationOutcome.IssueType.INVALID);
            assertThat(outcome.getIssueFirstRep().getDiagnostics())
                    .contains("Unsupported search parameters");
        }

        @Test
        void invalidStatusReturnsBadRequestInStrictMode() {
            String response = client.get()
                    .uri("/fhir/Task?status=BOGUS")
                    .header("Prefer", "handling=strict")
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectHeader().contentType("application/fhir+json")
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            OperationOutcome outcome = parseOutcome(response);
            assertThat(outcome.getIssueFirstRep().getCode()).isEqualTo(OperationOutcome.IssueType.INVALID);
            assertThat(outcome.getIssueFirstRep().getDiagnostics())
                    .contains("Invalid status values");
        }

        @Test
        void invalidIdReturnsBadRequestInStrictMode() {
            String response = client.get()
                    .uri("/fhir/Task?_id=not-a-uuid")
                    .header("Prefer", "handling=strict")
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectHeader().contentType("application/fhir+json")
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            OperationOutcome outcome = parseOutcome(response);
            assertThat(outcome.getIssueFirstRep().getCode()).isEqualTo(OperationOutcome.IssueType.INVALID);
            assertThat(outcome.getIssueFirstRep().getDiagnostics())
                    .contains("Invalid _id values");
        }

        @Test
        void multipleSearchProblemsAreCombinedInStrictMode() {
            String response = client.get()
                    .uri("/fhir/Task?_id=bad-id&status=BOGUS&subject=Patient/123")
                    .header("Prefer", "handling=strict")
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectHeader().contentType("application/fhir+json")
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            OperationOutcome outcome = parseOutcome(response);
            assertThat(outcome.getIssueFirstRep().getCode()).isEqualTo(OperationOutcome.IssueType.INVALID);
            assertThat(outcome.getIssueFirstRep().getDiagnostics())
                    .contains("Unsupported search parameters")
                    .contains("Invalid _id values")
                    .contains("Invalid status values");
        }
    }
}
