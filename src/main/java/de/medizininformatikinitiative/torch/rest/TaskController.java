package de.medizininformatikinitiative.torch.rest;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.exceptions.JobNotFoundException;
import de.medizininformatikinitiative.torch.exceptions.StateConflictException;
import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.failure.Severity;
import de.medizininformatikinitiative.torch.management.OperationOutcomeCreator;
import de.medizininformatikinitiative.torch.service.JobPersistenceService;
import de.medizininformatikinitiative.torch.taskhandling.JobTaskMapper;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Task;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;
import static org.springframework.web.reactive.function.server.RequestPredicates.DELETE;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Component
public class TaskController {

    private static final MediaType MEDIA_TYPE_FHIR_JSON = MediaType.valueOf("application/fhir+json");

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );

    private final FhirContext fhirContext;
    private final JobPersistenceService persistence;
    private final JobTaskMapper jobTaskMapper;

    @Autowired
    public TaskController(FhirContext fhirContext,
                          JobPersistenceService persistence,
                          JobTaskMapper jobTaskMapper) {
        this.fhirContext = requireNonNull(fhirContext);
        this.persistence = requireNonNull(persistence);
        this.jobTaskMapper = requireNonNull(jobTaskMapper);
    }

    @Bean
    @RouterOperations({
            @RouterOperation(
                    path = "/fhir/Task/{id}/$pause",
                    method = RequestMethod.POST,
                    beanClass = TaskController.class,
                    beanMethod = "pauseTask"
            ),
            @RouterOperation(
                    path = "/fhir/Task/{id}/$cancel",
                    method = RequestMethod.POST,
                    beanClass = TaskController.class,
                    beanMethod = "cancelTask"
            ),
            @RouterOperation(
                    path = "/fhir/Task/{id}/$resume",
                    method = RequestMethod.POST,
                    beanClass = TaskController.class,
                    beanMethod = "resumeTask"
            ),
            @RouterOperation(
                    path = "/fhir/Task/{id}",
                    method = RequestMethod.DELETE,
                    beanClass = TaskController.class,
                    beanMethod = "deleteTask"
            )
    })
    public RouterFunction<ServerResponse> taskRouter() {
        return route(POST("/fhir/Task/{id}/$pause"), this::pauseTask)
                .andRoute(POST("/fhir/Task/{id}/$cancel"), this::cancelTask)
                .andRoute(POST("/fhir/Task/{id}/$resume"), this::resumeTask)
                .andRoute(DELETE("/fhir/Task/{id}"), this::deleteTask);
    }

    public Mono<ServerResponse> pauseTask(ServerRequest request) {
        return handleTaskOperation(request, "pause", persistence::pauseJob);
    }

    public Mono<ServerResponse> cancelTask(ServerRequest request) {
        return handleTaskOperation(request, "cancel", persistence::cancelJob);
    }

    public Mono<ServerResponse> resumeTask(ServerRequest request) {
        return handleTaskOperation(request, "resume", persistence::resumeJob);
    }

    public Mono<ServerResponse> deleteTask(ServerRequest request) {
        return parseJobId(request)
                .flatMap(jobId -> Mono.fromCallable(() -> {
                            persistence.deleteJob(jobId);
                            return jobId;
                        })
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMap(ignored -> ServerResponse.noContent().build())
                        .onErrorResume(IOException.class, e -> operationOutcome(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                OperationOutcomeCreator.createOperationOutcome(e)
                        )))
                .onErrorResume(JobNotFoundException.class, e -> notFound(e.getMessage()));
    }

    private Mono<ServerResponse> handleTaskOperation(
            ServerRequest request,
            String operationName,
            TaskOperation operation
    ) {
        return parseJobId(request)
                .flatMap(jobId -> Mono.fromCallable(() -> operation.apply(jobId))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMap(job -> {
                            Task task = jobTaskMapper.toFhirTask(job);

                            return ServerResponse.ok()
                                    .contentType(MEDIA_TYPE_FHIR_JSON)
                                    .bodyValue(fhirContext.newJsonParser().encodeResourceToString(task));
                        })
                        .onErrorResume(StateConflictException.class, e ->
                                operationOutcome(
                                        HttpStatus.CONFLICT,
                                        OperationOutcomeCreator.simple(
                                                Severity.ERROR,
                                                OperationOutcome.IssueType.CONFLICT,
                                                "Task/" + jobId + " cannot be $" + operationName + ": " + e.getMessage()
                                        )
                                )
                        ))
                .onErrorResume(JobNotFoundException.class, e -> notFound(e.getMessage()));
    }

    private Mono<UUID> parseJobId(ServerRequest request) {
        String rawId = request.pathVariable("id");
        if (!UUID_PATTERN.matcher(rawId).matches()) {
            return Mono.error(new JobNotFoundException(rawId));
        }
        return Mono.just(UUID.fromString(rawId));
    }

    private Mono<ServerResponse> notFound(String message) {
        return operationOutcome(
                HttpStatus.NOT_FOUND,
                OperationOutcomeCreator.simple(
                        Severity.ERROR,
                        OperationOutcome.IssueType.NOTFOUND,
                        message
                )
        );
    }

    private Mono<ServerResponse> operationOutcome(HttpStatus status, OperationOutcome outcome) {
        return ServerResponse.status(status)
                .contentType(MEDIA_TYPE_FHIR_JSON)
                .bodyValue(fhirContext.newJsonParser().encodeResourceToString(outcome));
    }

    @FunctionalInterface
    interface TaskOperation {
        Job apply(UUID jobId) throws JobNotFoundException;
    }
}
