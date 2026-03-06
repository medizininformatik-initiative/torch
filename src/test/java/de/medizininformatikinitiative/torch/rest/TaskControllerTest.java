package de.medizininformatikinitiative.torch.rest;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.exceptions.VersionConflictException;
import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.JobParameters;
import de.medizininformatikinitiative.torch.jobhandling.JobPriority;
import de.medizininformatikinitiative.torch.jobhandling.JobStatus;
import de.medizininformatikinitiative.torch.jobhandling.workunit.WorkUnitState;
import de.medizininformatikinitiative.torch.service.JobPersistenceService;
import org.hl7.fhir.r4.model.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class TaskControllerTest {

    private static final MediaType FHIR_JSON = MediaType.valueOf("application/fhir+json");

    private FhirContext fhirContext;
    private JobPersistenceService persistence;
    private JobTaskMapper mapper;

    private WebTestClient client;

    private static Job minimalJob(UUID id, long version) {
        Instant now = Instant.parse("2025-01-01T00:00:00Z");
        return new Job(
                id,
                JobStatus.PENDING,
                WorkUnitState.initNow(),
                0,
                Map.of(),
                now,
                now,
                Optional.empty(),
                List.of(),
                mock(JobParameters.class), // controller never touches it
                JobPriority.NORMAL,
                WorkUnitState.initNow(),
                version
        );
    }

    // -------------------------------------------------------------------------
    // GET
    // -------------------------------------------------------------------------

    @BeforeEach
    void setUp() {
        fhirContext = FhirContext.forR4();
        persistence = mock(JobPersistenceService.class);
        mapper = mock(JobTaskMapper.class);

        TaskController controller = new TaskController(fhirContext, persistence, mapper);

        client = WebTestClient
                .bindToRouterFunction(controller.taskRouter())
                .build();
    }

    @Test
    void getTask_invalidUuid_returns400() {
        client.get()
                .uri("/fhir/Task/not-a-uuid")
                .accept(FHIR_JSON)
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(persistence, mapper);
    }

    @Test
    void putTask_withoutTaskId_usesPathId_andReturns200() {
        UUID id = UUID.randomUUID();
        Job current = minimalJob(id, 1L);
        Job updated = minimalJob(id, 2L);

        when(persistence.getJob(id)).thenReturn(Optional.of(current));
        when(persistence.applyTaskCommand(eq(id), any(Task.class))).thenReturn(Optional.of(updated));

        Task mappedOut = new Task();
        mappedOut.setId(id.toString());
        when(mapper.toFhirTask(updated)).thenReturn(mappedOut);

        Task t = new Task();
        t.setStatus(Task.TaskStatus.ONHOLD);

        client.put()
                .uri("/fhir/Task/{id}", id)
                .contentType(FHIR_JSON)
                .accept(FHIR_JSON)
                .bodyValue(fhirContext.newJsonParser().encodeResourceToString(t))
                .exchange()
                .expectStatus().isOk();

        verify(persistence).applyTaskCommand(eq(id), any(Task.class));
    }

    @Test
    void putTask_pathIdMismatch_returns400AndDoesNotCallApply() {
        UUID pathId = UUID.randomUUID();
        UUID bodyId = UUID.randomUUID();

        when(persistence.getJob(pathId)).thenReturn(Optional.of(minimalJob(pathId, 1L)));

        Task t = new Task();
        t.setId(bodyId.toString());
        t.setStatus(Task.TaskStatus.ONHOLD);

        client.put()
                .uri("/fhir/Task/{id}", pathId)
                .contentType(FHIR_JSON)
                .accept(FHIR_JSON)
                .bodyValue(fhirContext.newJsonParser().encodeResourceToString(t))
                .exchange()
                .expectStatus().isBadRequest();

        verify(persistence).getJob(pathId);
        verifyNoMoreInteractions(persistence);
        verifyNoInteractions(mapper);
    }

    @Test
    void getTask_jobNotFound_returns404() {
        UUID id = UUID.randomUUID();
        when(persistence.getJob(id)).thenReturn(Optional.empty());

        client.get()
                .uri("/fhir/Task/{id}", id)
                .accept(FHIR_JSON)
                .exchange()
                .expectStatus().isNotFound();

        verify(persistence).getJob(id);
        verifyNoInteractions(mapper);
    }

    @Test
    void putTask_invalidIfMatch_returns400_andDoesNotCallApply() {
        UUID id = UUID.randomUUID();
        when(persistence.getJob(id)).thenReturn(Optional.of(minimalJob(id, 1L)));

        Task t = new Task();
        t.setId(id.toString());
        t.setStatus(Task.TaskStatus.ONHOLD);

        client.put()
                .uri("/fhir/Task/{id}", id)
                .contentType(FHIR_JSON)
                .accept(FHIR_JSON)
                .header("If-Match", "W/\"abc\"")
                .bodyValue(fhirContext.newJsonParser().encodeResourceToString(t))
                .exchange()
                .expectStatus().isBadRequest();

        verify(persistence).getJob(id);
        verifyNoMoreInteractions(persistence);
        verifyNoInteractions(mapper);
    }

    @Test
    void putTask_ifMatchAndMetaVersionMismatch_returns400() {
        UUID id = UUID.randomUUID();
        when(persistence.getJob(id)).thenReturn(Optional.of(minimalJob(id, 7L)));

        Task t = new Task();
        t.setId(id.toString());
        t.setStatus(Task.TaskStatus.ONHOLD);
        t.getMeta().setVersionId("8");

        client.put()
                .uri("/fhir/Task/{id}", id)
                .contentType(FHIR_JSON)
                .accept(FHIR_JSON)
                .header("If-Match", "W/\"7\"")
                .bodyValue(fhirContext.newJsonParser().encodeResourceToString(t))
                .exchange()
                .expectStatus().isBadRequest();

        verify(persistence).getJob(id);
        verifyNoMoreInteractions(persistence);
        verifyNoInteractions(mapper);
    }

    @Test
    void putTask_nonNumericMetaVersion_returns400() {
        UUID id = UUID.randomUUID();
        when(persistence.getJob(id)).thenReturn(Optional.of(minimalJob(id, 7L)));

        Task t = new Task();
        t.setId(id.toString());
        t.setStatus(Task.TaskStatus.ONHOLD);
        t.getMeta().setVersionId("abc");

        client.put()
                .uri("/fhir/Task/{id}", id)
                .contentType(FHIR_JSON)
                .accept(FHIR_JSON)
                .header("If-Match", "W/\"7\"")
                .bodyValue(fhirContext.newJsonParser().encodeResourceToString(t))
                .exchange()
                .expectStatus().isBadRequest();

        verify(persistence).getJob(id);
        verifyNoMoreInteractions(persistence);
        verifyNoInteractions(mapper);
    }

    @Test
    void putTask_unexpectedException_returns500OperationOutcome() {
        UUID id = UUID.randomUUID();
        when(persistence.getJob(id)).thenReturn(Optional.of(minimalJob(id, 1L)));
        when(persistence.applyTaskCommand(eq(id), any(Task.class)))
                .thenThrow(new RuntimeException("boom"));

        Task t = new Task();
        t.setId(id.toString());
        t.setStatus(Task.TaskStatus.ONHOLD);

        client.put()
                .uri("/fhir/Task/{id}", id)
                .contentType(FHIR_JSON)
                .accept(FHIR_JSON)
                .bodyValue(fhirContext.newJsonParser().encodeResourceToString(t))
                .exchange()
                .expectStatus().is5xxServerError()
                .expectHeader().contentType(FHIR_JSON)
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("\"resourceType\":\"OperationOutcome\""));
    }

    @Test
    void deleteTask_unexpectedException_returns500OperationOutcome() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new RuntimeException("boom")).when(persistence).deleteJob(id, 5L);

        client.delete()
                .uri("/fhir/Task/{id}", id)
                .header("If-Match", "\"5\"")
                .exchange()
                .expectStatus().is5xxServerError()
                .expectHeader().contentType(FHIR_JSON)
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("\"resourceType\":\"OperationOutcome\""));

        verify(persistence).deleteJob(id, 5L);
    }

    @Test
    void putTask_ifMatchHeader_withoutMetaVersion_stillWorks() {

        UUID id = UUID.randomUUID();

        Job current = minimalJob(id, 7L);
        Job updated = minimalJob(id, 8L);

        when(persistence.getJob(id)).thenReturn(Optional.of(current));
        when(persistence.applyTaskCommand(eq(id), any(Task.class)))
                .thenReturn(Optional.of(updated));

        Task mapped = new Task();
        mapped.setId(id.toString());

        when(mapper.toFhirTask(updated)).thenReturn(mapped);

        Task t = new Task();
        t.setId(id.toString());
        t.setStatus(Task.TaskStatus.ONHOLD);

        String body = fhirContext.newJsonParser().encodeResourceToString(t);

        client.put()
                .uri("/fhir/Task/{id}", id)
                .contentType(FHIR_JSON)
                .accept(FHIR_JSON)
                .header("If-Match", "W/\"7\"")
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("ETag", "W/\"8\"");
    }

    // -------------------------------------------------------------------------
    // PUT
    // -------------------------------------------------------------------------

    @Test
    void putTask_emptyBody_returns400() {
        UUID id = UUID.randomUUID();
        when(persistence.getJob(id)).thenReturn(Optional.of(minimalJob(id, 1L)));

        client.put()
                .uri("/fhir/Task/{id}", id)
                .contentType(FHIR_JSON)
                .accept(FHIR_JSON)
                .bodyValue("")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void getTask_ok_returns200AndTaskJson() {
        UUID id = UUID.randomUUID();
        Job job = minimalJob(id, 7L);

        Task mapped = new Task();
        mapped.setId(id.toString());

        when(persistence.getJob(id)).thenReturn(Optional.of(job));
        when(mapper.toFhirTask(job)).thenReturn(mapped);

        client.get()
                .uri("/fhir/Task/{id}", id)
                .accept(FHIR_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(FHIR_JSON)
                .expectHeader().valueEquals("ETag", "W/\"7\"")
                .expectBody(String.class)
                .value(body -> {
                    // Controller encodes Task to JSON string
                    assertThat(body).contains("\"resourceType\":\"Task\"");
                    assertThat(body).contains("\"id\":\"" + id + "\"");
                });

        verify(persistence).getJob(id);
        verify(mapper).toFhirTask(job);
    }

    @Test
    void putTask_jobNotFound_returns404() {
        UUID id = UUID.randomUUID();
        when(persistence.getJob(id)).thenReturn(Optional.empty());

        client.put()
                .uri("/fhir/Task/{id}", id)
                .contentType(FHIR_JSON)
                .accept(FHIR_JSON)
                .bodyValue("{\"resourceType\":\"Task\"}")
                .exchange()
                .expectStatus().isNotFound();

        verify(persistence).getJob(id);
        verifyNoMoreInteractions(persistence);
        verifyNoInteractions(mapper);
    }

    @Test
    void putTask_invalidJson_returns400() {
        UUID id = UUID.randomUUID();
        when(persistence.getJob(id)).thenReturn(Optional.of(minimalJob(id, 1L)));

        client.put()
                .uri("/fhir/Task/{id}", id)
                .contentType(FHIR_JSON)
                .accept(FHIR_JSON)
                .bodyValue("{not-valid-json")
                .exchange()
                .expectStatus().isBadRequest();

        verify(persistence).getJob(id);
        verifyNoMoreInteractions(persistence);
        verifyNoInteractions(mapper);
    }

    @Test
    void putTask_bodyNotTask_returns400() {
        UUID id = UUID.randomUUID();
        when(persistence.getJob(id)).thenReturn(Optional.of(minimalJob(id, 1L)));

        String body = "{\"resourceType\":\"Patient\",\"id\":\"" + id + "\"}";

        client.put()
                .uri("/fhir/Task/{id}", id)
                .contentType(FHIR_JSON)
                .accept(FHIR_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isBadRequest();

        verify(persistence).getJob(id);
        verifyNoMoreInteractions(persistence);
        verifyNoInteractions(mapper);
    }

    @Test
    void putTask_versionConflict_returns409() {
        UUID id = UUID.randomUUID();
        when(persistence.getJob(id)).thenReturn(Optional.of(minimalJob(id, 1L)));

        when(persistence.applyTaskCommand(eq(id), any(Task.class)))
                .thenThrow(new VersionConflictException("conflict"));

        Task t = new Task();
        t.setId(id.toString());
        t.setStatus(Task.TaskStatus.ONHOLD);
        t.setPriority(Task.TaskPriority.ASAP);

        String body = fhirContext.newJsonParser().encodeResourceToString(t);

        client.put()
                .uri("/fhir/Task/{id}", id)
                .contentType(FHIR_JSON)
                .accept(FHIR_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isEqualTo(409);

        verify(persistence).applyTaskCommand(eq(id), any(Task.class));
        verifyNoInteractions(mapper);
    }

    @Test
    void putTask_applyReturnsEmpty_returns404() {
        UUID id = UUID.randomUUID();
        when(persistence.getJob(id)).thenReturn(Optional.of(minimalJob(id, 1L)));
        when(persistence.applyTaskCommand(eq(id), any(Task.class)))
                .thenReturn(Optional.empty());
        Task t = new Task();
        t.setId(id.toString());
        t.setStatus(Task.TaskStatus.ONHOLD);   // HAPI will serialize correctly ("on-hold")
        t.setPriority(Task.TaskPriority.ASAP);

        String body = fhirContext.newJsonParser().encodeResourceToString(t);

        client.put()
                .uri("/fhir/Task/{id}", id)
                .contentType(FHIR_JSON)
                .accept(FHIR_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isNotFound();

        verify(persistence).applyTaskCommand(eq(id), any(Task.class));
        verifyNoInteractions(mapper);
    }

    // -------------------------------------------------------------------------
    // DELETE
    // -------------------------------------------------------------------------

    @Test
    void putTask_ok_callsApplyAndReturns200() {
        UUID id = UUID.randomUUID();
        Job current = minimalJob(id, 1L);
        Job updated = minimalJob(id, 2L);

        when(persistence.getJob(id)).thenReturn(Optional.of(current));
        when(persistence.applyTaskCommand(eq(id), any(Task.class))).thenReturn(Optional.of(updated));

        Task mappedOut = new Task();
        mappedOut.setId(id.toString());
        when(mapper.toFhirTask(updated)).thenReturn(mappedOut);

        Task t = new Task();
        t.setId(id.toString());
        t.setStatus(Task.TaskStatus.ONHOLD);   // HAPI will serialize correctly ("on-hold")
        t.setPriority(Task.TaskPriority.ASAP);
        String body = fhirContext.newJsonParser().encodeResourceToString(t);

        client.put()
                .uri("/fhir/Task/{id}", id)
                .contentType(FHIR_JSON)
                .accept(FHIR_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(FHIR_JSON)
                .expectBody(String.class)
                .value(json -> assertThat(json).contains("\"resourceType\":\"Task\""));

        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(persistence).applyTaskCommand(eq(id), taskCaptor.capture());
        assertThat(taskCaptor.getValue().getStatus()).isEqualTo(Task.TaskStatus.ONHOLD);

        verify(mapper).toFhirTask(updated);
    }

    @Test
    void deleteTask_invalidUuid_returns400() {
        client.delete()
                .uri("/fhir/Task/nope")
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(persistence, mapper);
    }

    @Test
    void deleteTask_invalidIfMatch_returns400_andDoesNotCallDelete() {
        UUID id = UUID.randomUUID();

        client.delete()
                .uri("/fhir/Task/{id}", id)
                .header("If-Match", "W/\"abc\"")
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(persistence, mapper);
    }

    @Test
    void deleteTask_withWeakEtag_parsesVersionAndCallsDelete() throws Exception {
        UUID id = UUID.randomUUID();

        client.delete()
                .uri("/fhir/Task/{id}", id)
                .header("If-Match", "W/\"123\"")
                .exchange()
                .expectStatus().isNoContent();

        verify(persistence).deleteJob(id, 123L);
    }

    @Test
    void deleteTask_versionConflict_returns409() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new VersionConflictException("conflict"))
                .when(persistence).deleteJob(id, 5L);

        client.delete()
                .uri("/fhir/Task/{id}", id)
                .header("If-Match", "\"5\"")
                .exchange()
                .expectStatus().isEqualTo(409);

        verify(persistence).deleteJob(id, 5L);
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    @Test
    void deleteTask_noIfMatch_callsDeleteWithNullVersion() throws Exception {
        UUID id = UUID.randomUUID();

        client.delete()
                .uri("/fhir/Task/{id}", id)
                .exchange()
                .expectStatus().isNoContent();

        verify(persistence).deleteJob(id, null);
    }
}
