package de.medizininformatikinitiative.torch.rest;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.exceptions.VersionConflictException;
import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.failure.Severity;
import de.medizininformatikinitiative.torch.management.OperationOutcomeCreator;
import de.medizininformatikinitiative.torch.service.JobPersistenceService;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.springframework.web.reactive.function.server.RequestPredicates.DELETE;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.PUT;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Component
public class TaskController {

    private static final Logger log = LoggerFactory.getLogger(TaskController.class);
    private static final MediaType FHIR_JSON = MediaType.valueOf("application/fhir+json");

    private final FhirContext fhirContext;
    private final JobPersistenceService persistence;
    private final JobTaskMapper mapper;

    public TaskController(FhirContext fhirContext,
                          JobPersistenceService persistence,
                          JobTaskMapper mapper) {
        this.fhirContext = fhirContext;
        this.persistence = persistence;
        this.mapper = mapper;
    }

    @Bean
    public RouterFunction<ServerResponse> taskRouter() {
        return route(GET("/fhir/Task/{id}").and(accept(FHIR_JSON)), this::getTask)
                .andRoute(PUT("/fhir/Task/{id}")
                        .and(contentType(FHIR_JSON))
                        .and(accept(FHIR_JSON)), this::putTask)
                .andRoute(DELETE("/fhir/Task/{id}"), this::deleteTask);
    }

    public Mono<ServerResponse> getTask(ServerRequest request) {
        UUID id = parseUuidOrNull(request.pathVariable("id"));
        if (id == null) return badRequest("Invalid Task.id (expected UUID)");

        Job job = persistence.getJob(id).orElse(null);
        if (job == null) return notFound("Job not found: " + id);

        Task task = mapper.toFhirTask(job);

        return ServerResponse.ok()
                .contentType(FHIR_JSON)
                .header("ETag", weakEtag(job.version()))
                .bodyValue(fhirContext.newJsonParser().encodeResourceToString(task));
    }

    public Mono<ServerResponse> putTask(ServerRequest request) {
        UUID id = parseUuidOrNull(request.pathVariable("id"));
        if (id == null) return badRequest("Invalid Task.id (expected UUID)");

        if (persistence.getJob(id).isEmpty()) return notFound("Job not found: " + id);

        return request.bodyToMono(String.class).flatMap(body -> {

            Resource parsed;
            try {
                parsed = (Resource) fhirContext.newJsonParser().parseResource(body);
            } catch (Exception e) {
                return badRequest("Invalid FHIR JSON: " + e.getMessage());
            }

            if (!(parsed instanceof Task incoming)) {
                return badRequest("Body must be a Task resource");
            }

            String incomingId = incoming.getIdElement() != null ? incoming.getIdElement().getIdPart() : null;
            if (incomingId != null && !incomingId.isBlank() && !incomingId.equals(id.toString())) {
                return badRequest("Path id != Task.id");
            }

            // Handle If-Match header
            Long ifMatchVersion;
            try {
                ifMatchVersion = parseIfMatchVersionOrNull(request);
            } catch (IllegalArgumentException e) {
                return badRequest(e.getMessage());
            }

            if (ifMatchVersion != null) {

                String metaVersion = incoming.getMeta() != null
                        ? incoming.getMeta().getVersionId()
                        : null;

                if (metaVersion == null || metaVersion.isBlank()) {
                    incoming.getMeta().setVersionId(Long.toString(ifMatchVersion));
                } else {
                    try {
                        long metaV = Long.parseLong(metaVersion);
                        if (metaV != ifMatchVersion) {
                            return badRequest(
                                    "Version mismatch: If-Match=" + ifMatchVersion +
                                            " but Task.meta.versionId=" + metaV);
                        }
                    } catch (NumberFormatException e) {
                        return badRequest("Invalid Task.meta.versionId (expected number)");
                    }
                }
            }

            Job updated;
            try {
                updated = persistence.applyTaskCommand(id, incoming).orElse(null);
            } catch (VersionConflictException e) {
                return outcome(HttpStatus.CONFLICT, e.getMessage(),
                        OperationOutcome.IssueType.CONFLICT, Severity.ERROR);
            } catch (IllegalArgumentException e) {
                return badRequest(e.getMessage());
            } catch (Exception e) {
                log.error("FHIR_TASK_01 Failed to apply Task command for {}: {}", id, e.getMessage(), e);
                return serverError("Failed to apply Task command: " + e.getMessage());
            }

            if (updated == null) return notFound("Job not found: " + id);

            Task out = mapper.toFhirTask(updated);

            return ServerResponse.ok()
                    .contentType(FHIR_JSON)
                    .header("ETag", weakEtag(updated.version()))
                    .bodyValue(fhirContext.newJsonParser().encodeResourceToString(out));
        });
    }

    public Mono<ServerResponse> deleteTask(ServerRequest request) {

        UUID id = parseUuidOrNull(request.pathVariable("id"));
        if (id == null) return badRequest("Invalid Task.id (expected UUID)");

        Long expectedVersion;
        try {
            expectedVersion = parseIfMatchVersionOrNull(request);
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }

        try {
            persistence.deleteJob(id, expectedVersion);
            return ServerResponse.noContent().build();
        } catch (VersionConflictException e) {
            return outcome(HttpStatus.CONFLICT, e.getMessage(),
                    OperationOutcome.IssueType.CONFLICT, Severity.ERROR);
        } catch (Exception e) {
            log.error("FHIR_TASK_02 Failed to delete job {}: {}", id, e.getMessage(), e);
            return serverError("Failed to delete job: " + e.getMessage());
        }
    }

    private String weakEtag(long version) {
        return "W/\"" + version + "\"";
    }

    private UUID parseUuidOrNull(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private Mono<ServerResponse> badRequest(String msg) {
        return outcome(HttpStatus.BAD_REQUEST, msg, OperationOutcome.IssueType.INVALID, Severity.ERROR);
    }

    private Mono<ServerResponse> notFound(String msg) {
        return outcome(HttpStatus.NOT_FOUND, msg, OperationOutcome.IssueType.NOTFOUND, Severity.ERROR);
    }

    private Mono<ServerResponse> serverError(String msg) {
        return outcome(HttpStatus.INTERNAL_SERVER_ERROR, msg, OperationOutcome.IssueType.EXCEPTION, Severity.ERROR);
    }

    private Mono<ServerResponse> outcome(HttpStatus status, String msg,
                                         OperationOutcome.IssueType type,
                                         Severity sev) {

        OperationOutcome oo = OperationOutcomeCreator.simple(sev, type, msg);

        return ServerResponse.status(status)
                .contentType(FHIR_JSON)
                .bodyValue(fhirContext.newJsonParser().encodeResourceToString(oo));
    }

    private Long parseIfMatchVersionOrNull(ServerRequest request) {

        String ifMatch = request.headers().firstHeader("If-Match");
        if (ifMatch == null || ifMatch.isBlank()) return null;

        String v = ifMatch.trim();

        if (v.startsWith("W/")) v = v.substring(2).trim();

        if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
            v = v.substring(1, v.length() - 1);
        }

        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid If-Match value (expected version number)");
        }
    }
}
