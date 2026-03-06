package de.medizininformatikinitiative.torch.rest;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.Torch;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Task;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(
        properties = {"spring.main.allow-bean-definition-overriding=true"},
        classes = Torch.class,
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TaskControllerIT {

    private static final MediaType FHIR_JSON = MediaType.valueOf("application/fhir+json");
    private static final Path DEFAULT_PARAMETERS = Path.of(
            "src/test/resources/CRTDL_Parameters/Parameters_observation_all_fields_without_refs.json"
    );

    @Autowired
    FhirContext fhirContext;

    @Value("${server.port}")
    private int port;

    private TestRestTemplate rest;

    @BeforeAll
    void setUp() {
        rest = new TestRestTemplate();
    }

    private static String weakEtag(long version) {
        return "W/\"" + version + "\"";
    }

    private static long parseWeakEtag(String etagHeader) {
        if (etagHeader == null || etagHeader.isBlank()) {
            throw new IllegalArgumentException("Missing ETag header");
        }

        String v = etagHeader.trim();
        if (v.startsWith("W/")) {
            v = v.substring(2).trim();
        }
        if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
            v = v.substring(1, v.length() - 1);
        }

        return Long.parseLong(v);
    }

    @Test
    void getTask_afterRealJobFinished_returnsCompletedTask_andMatchingEtagAndVersion() throws Exception {
        JobRun run = startRealJobAndWaitForCompletion(DEFAULT_PARAMETERS);

        ResponseEntity<String> resp = getTaskResponse(run.jobId);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getHeaders().getContentType()).isEqualTo(FHIR_JSON);

        String etag = resp.getHeaders().getFirst("ETag");
        assertThat(etag).isNotBlank();
        long versionFromEtag = parseWeakEtag(etag);

        Resource parsed = parseResource(resp.getBody());
        assertThat(parsed).isInstanceOf(Task.class);

        Task task = (Task) parsed;
        assertThat(task.getIdElement().getIdPart()).isEqualTo(run.jobId.toString());
        assertThat(task.getStatus()).isEqualTo(Task.TaskStatus.COMPLETED);
        assertThat(task.getMeta().getVersionId()).isNotBlank();
        assertThat(Long.parseLong(task.getMeta().getVersionId())).isEqualTo(versionFromEtag);
    }

    @Test
    void putTask_withIfMatch_returnsUpdatedTask_andMatchingEtagAndMetaVersion() throws Exception {
        JobRun run = startRealJobAndWaitForCompletion(DEFAULT_PARAMETERS);

        Task current = getTask(run.jobId);
        long currentVersion = Long.parseLong(current.getMeta().getVersionId());

        Task cmd = new Task();
        cmd.setId(run.jobId.toString());
        cmd.setStatus(Task.TaskStatus.ONHOLD);

        ResponseEntity<String> putResp = putTask(run.jobId, cmd, weakEtag(currentVersion));

        assertThat(putResp.getStatusCode().value()).isEqualTo(200);
        assertThat(putResp.getHeaders().getContentType()).isEqualTo(FHIR_JSON);

        String newEtag = putResp.getHeaders().getFirst("ETag");
        assertThat(newEtag).isNotBlank();
        long versionFromEtag = parseWeakEtag(newEtag);

        Resource parsed = parseResource(putResp.getBody());
        assertThat(parsed).isInstanceOf(Task.class);

        Task updated = (Task) parsed;
        assertThat(updated.getIdElement().getIdPart()).isEqualTo(run.jobId.toString());
        assertThat(updated.getMeta().getVersionId()).isNotBlank();
        assertThat(Long.parseLong(updated.getMeta().getVersionId())).isEqualTo(versionFromEtag);
        assertThat(versionFromEtag).isGreaterThanOrEqualTo(currentVersion);

        Task fetchedAgain = getTask(run.jobId);
        assertThat(Long.parseLong(fetchedAgain.getMeta().getVersionId())).isEqualTo(versionFromEtag);
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    @Test
    void deleteTask_wrongVersion_returns409_then_correctVersion_returns204() throws Exception {
        JobRun run = startRealJobAndWaitForCompletion(DEFAULT_PARAMETERS);

        Task current = getTask(run.jobId);
        long version = Long.parseLong(current.getMeta().getVersionId());

        ResponseEntity<String> conflict = deleteTask(run.jobId, weakEtag(version - 1));
        assertThat(conflict.getStatusCode().value()).isEqualTo(409);
        assertThat(conflict.getHeaders().getContentType()).isEqualTo(FHIR_JSON);

        Resource conflictBody = parseResource(conflict.getBody());
        assertThat(conflictBody).isInstanceOf(OperationOutcome.class);

        ResponseEntity<String> deleted = deleteTask(run.jobId, weakEtag(version));
        assertThat(deleted.getStatusCode().value()).isEqualTo(204);

        ResponseEntity<String> deletedAgain = deleteTask(run.jobId, weakEtag(version));
        assertThat(deletedAgain.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void pauseThenResume_jobReachesCompleted() throws Exception {
        JobRun run = startRealJob(DEFAULT_PARAMETERS);

        Task beforePause = waitForRunnableTask(run.jobId);

        Task pauseCmd = new Task();
        pauseCmd.setId(run.jobId.toString());
        pauseCmd.setStatus(Task.TaskStatus.ONHOLD);
        pauseCmd.getMeta().setVersionId(beforePause.getMeta().getVersionId());

        ResponseEntity<String> pauseResp = putTask(
                run.jobId,
                pauseCmd,
                weakEtag(Long.parseLong(beforePause.getMeta().getVersionId()))
        );

        assertThat(pauseResp.getStatusCode().value()).isEqualTo(200);
        assertThat(pauseResp.getHeaders().getContentType()).isEqualTo(FHIR_JSON);

        Resource pauseParsed = parseResource(pauseResp.getBody());
        assertThat(pauseParsed).isInstanceOf(Task.class);

        Task paused = (Task) pauseParsed;
        assertThat(paused.getStatus()).isEqualTo(Task.TaskStatus.ONHOLD);

        Task pausedGet = getTask(run.jobId);
        assertThat(pausedGet.getStatus()).isEqualTo(Task.TaskStatus.ONHOLD);

        Task resumeCmd = new Task();
        resumeCmd.setId(run.jobId.toString());
        resumeCmd.setStatus(Task.TaskStatus.INPROGRESS);
        resumeCmd.getMeta().setVersionId(pausedGet.getMeta().getVersionId());

        ResponseEntity<String> resumeResp = putTask(
                run.jobId,
                resumeCmd,
                weakEtag(Long.parseLong(pausedGet.getMeta().getVersionId()))
        );

        assertThat(resumeResp.getStatusCode().value()).isEqualTo(200);
        assertThat(resumeResp.getHeaders().getContentType()).isEqualTo(FHIR_JSON);

        Resource resumeParsed = parseResource(resumeResp.getBody());
        assertThat(resumeParsed).isInstanceOf(Task.class);

        Task resumed = (Task) resumeParsed;
        assertThat(resumed.getStatus()).isNotEqualTo(Task.TaskStatus.ONHOLD);
        assertThat(resumed.getStatus()).isNotIn(Task.TaskStatus.FAILED, Task.TaskStatus.CANCELLED);

        Task completed = waitForTaskCompletion(run.jobId);
        assertThat(completed.getStatus()).isEqualTo(Task.TaskStatus.COMPLETED);
    }

    private JobRun startRealJob(Path parametersFile) throws IOException {
        String payload = Files.readString(parametersFile, StandardCharsets.UTF_8);

        ResponseEntity<String> start = rest.exchange(
                url("/fhir/$extract-data"),
                HttpMethod.POST,
                new HttpEntity<>(payload, fhirHeaders()),
                String.class
        );

        assertThat(start.getStatusCode().value()).isEqualTo(202);

        String statusUrl = start.getHeaders().getFirst("Content-Location");
        assertThat(statusUrl).isNotBlank();

        UUID jobId = UUID.fromString(statusUrl.substring(statusUrl.lastIndexOf('/') + 1));
        return new JobRun(jobId, statusUrl);
    }

    private JobRun startRealJobAndWaitForCompletion(Path parametersFile) throws IOException {
        JobRun run = startRealJob(parametersFile);
        Task completed = waitForTaskCompletion(run.jobId);
        assertThat(completed.getStatus()).isEqualTo(Task.TaskStatus.COMPLETED);
        return run;
    }

    private Task waitForTaskCompletion(UUID jobId) {
        for (int i = 0; i < 200; i++) {
            Task task = getTaskAllowing404(jobId);

            if (task == null) {
                sleep(100);
                continue;
            }

            if (task.getStatus() == Task.TaskStatus.COMPLETED) {
                return task;
            }

            if (task.getStatus() == Task.TaskStatus.FAILED || task.getStatus() == Task.TaskStatus.CANCELLED) {
                Assertions.fail("Task ended in terminal error state: " + task.getStatus());
            }

            sleep(200);
        }

        Assertions.fail("Task did not complete in time");
        return null;
    }

    private Task waitForRunnableTask(UUID jobId) {
        for (int i = 0; i < 150; i++) {
            Task task = getTaskAllowing404(jobId);

            if (task == null) {
                sleep(100);
                continue;
            }

            if (task.getStatus() == Task.TaskStatus.INPROGRESS
                    || task.getStatus() == Task.TaskStatus.READY
                    || task.getStatus() == Task.TaskStatus.REQUESTED) {
                return task;
            }

            if (task.getStatus() == Task.TaskStatus.COMPLETED) {
                Assertions.fail("Task already completed before pause/resume interaction");
            }

            if (task.getStatus() == Task.TaskStatus.FAILED || task.getStatus() == Task.TaskStatus.CANCELLED) {
                Assertions.fail("Task ended in terminal error state before pause/resume interaction: " + task.getStatus());
            }

            sleep(100);
        }

        Assertions.fail("Task never reached a runnable state");
        return null;
    }

    private Task getTask(UUID jobId) {
        ResponseEntity<String> resp = getTaskResponse(jobId);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getHeaders().getContentType()).isEqualTo(FHIR_JSON);

        Resource parsed = parseResource(resp.getBody());
        assertThat(parsed).isInstanceOf(Task.class);

        return (Task) parsed;
    }

    private Task getTaskAllowing404(UUID jobId) {
        ResponseEntity<String> resp = getTaskResponse(jobId);

        if (resp.getStatusCode().value() == 404) {
            return null;
        }

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getHeaders().getContentType()).isEqualTo(FHIR_JSON);

        Resource parsed = parseResource(resp.getBody());
        assertThat(parsed).isInstanceOf(Task.class);

        return (Task) parsed;
    }

    private ResponseEntity<String> getTaskResponse(UUID jobId) {
        return rest.exchange(
                url("/fhir/Task/" + jobId),
                HttpMethod.GET,
                new HttpEntity<>(null, fhirHeaders()),
                String.class
        );
    }

    private ResponseEntity<String> putTask(UUID jobId, Task cmd, String ifMatch) {
        HttpHeaders headers = fhirHeaders();
        if (ifMatch != null) {
            headers.add("If-Match", ifMatch);
        }

        return rest.exchange(
                url("/fhir/Task/" + jobId),
                HttpMethod.PUT,
                new HttpEntity<>(fhirContext.newJsonParser().encodeResourceToString(cmd), headers),
                String.class
        );
    }

    private HttpHeaders fhirHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(FHIR_JSON);
        h.setAccept(List.of(FHIR_JSON));
        return h;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private ResponseEntity<String> deleteTask(UUID jobId, String ifMatch) {
        HttpHeaders headers = fhirHeaders();
        if (ifMatch != null) {
            headers.add("If-Match", ifMatch);
        }

        return rest.exchange(
                url("/fhir/Task/" + jobId),
                HttpMethod.DELETE,
                new HttpEntity<>(null, headers),
                String.class
        );
    }

    private Resource parseResource(String body) {
        return (Resource) fhirContext.newJsonParser().parseResource(body);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private record JobRun(UUID jobId, String statusUrl) {
    }
}
