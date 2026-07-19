package de.medizininformatikinitiative.torch.rest;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.exceptions.JobNotFoundException;
import de.medizininformatikinitiative.torch.exceptions.StateConflictException;
import de.medizininformatikinitiative.torch.exceptions.VersionConflictException;
import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.JobPriority;
import de.medizininformatikinitiative.torch.jobhandling.JobStatus;
import de.medizininformatikinitiative.torch.jobhandling.failure.Severity;
import de.medizininformatikinitiative.torch.management.OperationOutcomeCreator;
import de.medizininformatikinitiative.torch.service.JobPersistenceService;
import de.medizininformatikinitiative.torch.taskhandling.JobTaskMapper;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Task;
import de.medizininformatikinitiative.torch.rest.schema.OperationOutcomeSchema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static org.springframework.web.reactive.function.server.RequestPredicates.DELETE;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RequestPredicates.PUT;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Component
public class TaskController {

    private static final MediaType MEDIA_TYPE_FHIR_JSON = MediaType.valueOf("application/fhir+json");

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );

    private static final Set<String> SUPPORTED_SEARCH_PARAMS = Set.of("_id", "status");

    private final FhirContext fhirContext;
    private final JobPersistenceService persistence;
    private final JobTaskMapper jobTaskMapper;

    /**
     * @param fhirContext    FHIR context used for parsing and serialising resources
     * @param persistence    service for querying and mutating job state
     * @param jobTaskMapper  maps between internal {@link Job} and FHIR {@link Task} representations
     */
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
                    path = "/fhir/Task",
                    method = RequestMethod.GET,
                    beanClass = TaskController.class,
                    beanMethod = "searchTasks"
            ),
            @RouterOperation(
                    path = "/fhir/Task/{id}",
                    method = RequestMethod.GET,
                    beanClass = TaskController.class,
                    beanMethod = "getTask"
            ),
            @RouterOperation(
                    path = "/fhir/Task/{id}",
                    method = RequestMethod.PUT,
                    beanClass = TaskController.class,
                    beanMethod = "updateTask"
            ),
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
    /**
     * Registers all {@code /fhir/Task} routes.
     *
     * @return the composed router function
     */
    public RouterFunction<ServerResponse> taskRouter() {
        return route(GET("/fhir/Task"), this::searchTasks)
                .andRoute(GET("/fhir/Task/{id}"), this::getTask)
                .andRoute(PUT("/fhir/Task/{id}"), this::updateTask)
                .andRoute(POST("/fhir/Task/{id}/$pause"), this::pauseTask)
                .andRoute(POST("/fhir/Task/{id}/$cancel"), this::cancelTask)
                .andRoute(POST("/fhir/Task/{id}/$resume"), this::resumeTask)
                .andRoute(DELETE("/fhir/Task/{id}"), this::deleteTask);
    }

    @Operation(
            summary = "GET /fhir/Task — Search Tasks",
            description = """
                    Returns a FHIR Bundle (searchset) of Tasks matching the given parameters.
                    Supports filtering by job ID and/or status. Use the `Prefer: handling=strict`
                    header to reject unsupported or invalid parameters with a 400 instead of
                    silently ignoring them.
                    """,
            parameters = {
                    @Parameter(
                            name = "_id",
                            in = ParameterIn.QUERY,
                            required = false,
                            description = "Comma-separated list of job UUIDs to filter by",
                            schema = @Schema(type = "string", example = "550e8400-e29b-41d4-a716-446655440000")
                    ),
                    @Parameter(
                            name = "status",
                            in = ParameterIn.QUERY,
                            required = false,
                            description = "Comma-separated list of job statuses to filter by",
                            schema = @Schema(type = "string",
                                    allowableValues = {"PENDING", "RUNNING_GET_COHORT", "RUNNING_PROCESS_BATCH",
                                            "RUNNING_PROCESS_CORE", "PAUSED", "TEMP_FAILED",
                                            "COMPLETED", "FAILED", "CANCELLED"})
                    ),
                    @Parameter(
                            name = "Prefer",
                            in = ParameterIn.HEADER,
                            required = false,
                            description = "Set to `handling=strict` to reject unsupported parameters",
                            schema = @Schema(type = "string", example = "handling=strict")
                    )
            },
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "FHIR Bundle (searchset) of matching Tasks",
                            content = @Content(
                                    mediaType = "application/fhir+json",
                                    schema = @Schema(type = "object"),
                                    examples = @ExampleObject(name = "Running job", value = """
                                            {
                                              "resourceType": "Bundle",
                                              "type": "searchset",
                                              "total": 1,
                                              "entry": [
                                                {
                                                  "resource": {
                                                    "resourceType": "Task",
                                                    "id": "550e8400-e29b-41d4-a716-446655440000",
                                                    "meta": { "versionId": "3", "lastUpdated": "2024-01-15T10:30:00.000+00:00" },
                                                    "status": "in-progress",
                                                    "intent": "order",
                                                    "businessStatus": {
                                                      "coding": [{ "system": "https://medizininformatik-initiative.de/torch/job-status", "code": "RUNNING_PROCESS_BATCH", "display": "Processing batches" }]
                                                    },
                                                    "priority": "routine",
                                                    "description": "TORCH Job 550e8400-e29b-41d4-a716-446655440000",
                                                    "authoredOn": "2024-01-15T10:29:55.000+00:00",
                                                    "executionPeriod": { "start": "2024-01-15T10:29:55.000+00:00" }
                                                  }
                                                }
                                              ]
                                            }
                                            """)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Bad Request — unsupported or invalid parameters (strict mode only)",
                            content = @Content(mediaType = "application/fhir+json",
                                    schema = @Schema(implementation = OperationOutcomeSchema.class),
                                    examples = @ExampleObject(name = "Invalid parameter", value = """
                                            {
                                              "resourceType": "OperationOutcome",
                                              "issue": [{ "severity": "error", "code": "invalid", "diagnostics": "Unsupported search parameters: [foo]; Invalid _id values: [not-a-uuid]" }]
                                            }
                                            """))
                    )
            }
    )
    /**
     * Handles {@code GET /fhir/Task} — searches for tasks by {@code _id} and/or {@code status}.
     *
     * @param request the incoming server request
     * @return a {@link Mono} emitting a FHIR {@link Bundle} of matching tasks
     */
    public Mono<ServerResponse> searchTasks(ServerRequest request) {
        SearchHandlingMode handlingMode = searchHandlingMode(request);

        Set<String> unsupported = request.queryParams().keySet().stream()
                .filter(param -> !SUPPORTED_SEARCH_PARAMS.contains(param))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        ParseResult<UUID> idsResult = parseUuidList(request.queryParam("_id"));
        ParseResult<JobStatus> statusesResult = parseStatusList(request.queryParam("status"));

        Mono<ServerResponse> performSearch = Mono.fromCallable(() -> persistence.findJobs(idsResult.values(), statusesResult.values()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(jobs -> {
                    Bundle bundle = new Bundle();
                    bundle.setType(Bundle.BundleType.SEARCHSET);
                    bundle.setTotal(jobs.size());
                    for (Job job : jobs) {
                        bundle.addEntry().setResource(jobTaskMapper.toFhirTask(job));
                    }
                    return ServerResponse.ok()
                            .contentType(MEDIA_TYPE_FHIR_JSON)
                            .bodyValue(fhirContext.newJsonParser().encodeResourceToString(bundle));
                });

        if (handlingMode == SearchHandlingMode.STRICT) {
            return validateStrictSearch(unsupported, idsResult, statusesResult)
                    .switchIfEmpty(performSearch);
        }
        return performSearch;
    }

    @Operation(
            summary = "GET /fhir/Task/{id} — Read Task",
            description = "Returns the FHIR Task for the given job UUID.",
            parameters = @Parameter(
                    name = "id",
                    in = ParameterIn.PATH,
                    required = true,
                    description = "Job identifier (UUID)",
                    schema = @Schema(type = "string", format = "uuid")
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "FHIR Task resource",
                            content = @Content(
                                    mediaType = "application/fhir+json",
                                    schema = @Schema(type = "object"),
                                    examples = @ExampleObject(name = "Running job", value = """
                                            {
                                              "resourceType": "Task",
                                              "id": "550e8400-e29b-41d4-a716-446655440000",
                                              "meta": { "versionId": "3", "lastUpdated": "2024-01-15T10:30:00.000+00:00" },
                                              "status": "in-progress",
                                              "intent": "order",
                                              "businessStatus": {
                                                "coding": [{ "system": "https://medizininformatik-initiative.de/torch/job-status", "code": "RUNNING_PROCESS_BATCH", "display": "Processing batches" }]
                                              },
                                              "priority": "routine",
                                              "description": "TORCH Job 550e8400-e29b-41d4-a716-446655440000",
                                              "authoredOn": "2024-01-15T10:29:55.000+00:00",
                                              "executionPeriod": { "start": "2024-01-15T10:29:55.000+00:00" }
                                            }
                                            """)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Task not found",
                            content = @Content(mediaType = "application/fhir+json",
                                    schema = @Schema(implementation = OperationOutcomeSchema.class),
                                    examples = @ExampleObject(name = "Not found", value = """
                                            {
                                              "resourceType": "OperationOutcome",
                                              "issue": [{ "severity": "error", "code": "not-found", "diagnostics": "Task/550e8400-e29b-41d4-a716-446655440000 not found" }]
                                            }
                                            """))
                    )
            }
    )
    /**
     * Handles {@code GET /fhir/Task/{id}} — retrieves a single task by its job ID.
     *
     * @param request the incoming server request; must contain a valid UUID path variable {@code id}
     * @return a {@link Mono} emitting the matching FHIR {@link Task}, or {@code 404} if not found
     */
    public Mono<ServerResponse> getTask(ServerRequest request) {
        return parseJobId(request)
                .flatMap(jobId -> Mono.fromCallable(() -> persistence.getJob(jobId))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMap(optJob -> optJob
                                .map(job -> ServerResponse.ok()
                                        .contentType(MEDIA_TYPE_FHIR_JSON)
                                        .bodyValue(fhirContext.newJsonParser().encodeResourceToString(jobTaskMapper.toFhirTask(job))))
                                .orElseGet(() -> notFound("Task/" + jobId + " not found"))))
                .onErrorResume(JobNotFoundException.class, e -> notFound(e.getMessage()));
    }

    @Operation(
            summary = "PUT /fhir/Task/{id} — Update Task Priority",
            description = """
                    Updates the priority of an existing task. The request body must be a FHIR Task resource.
                    Optimistic locking is enforced via the `If-Match: W/"<version>"` header.
                    """,
            parameters = {
                    @Parameter(
                            name = "id",
                            in = ParameterIn.PATH,
                            required = true,
                            description = "Job identifier (UUID)",
                            schema = @Schema(type = "string", format = "uuid")
                    ),
                    @Parameter(
                            name = "If-Match",
                            in = ParameterIn.HEADER,
                            required = true,
                            description = "ETag of the resource version being updated, e.g. `W/\"3\"`",
                            schema = @Schema(type = "string", example = "W/\"3\"")
                    )
            },
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/fhir+json",
                            schema = @Schema(type = "object"),
                            examples = @ExampleObject(name = "Raise priority to ASAP", value = """
                                    {
                                      "resourceType": "Task",
                                      "id": "550e8400-e29b-41d4-a716-446655440000",
                                      "status": "in-progress",
                                      "intent": "order",
                                      "priority": "asap"
                                    }
                                    """)
                    )
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Updated FHIR Task",
                            content = @Content(
                                    mediaType = "application/fhir+json",
                                    schema = @Schema(type = "object"),
                                    examples = @ExampleObject(name = "Updated to ASAP priority", value = """
                                            {
                                              "resourceType": "Task",
                                              "id": "550e8400-e29b-41d4-a716-446655440000",
                                              "meta": { "versionId": "4", "lastUpdated": "2024-01-15T10:31:00.000+00:00" },
                                              "status": "in-progress",
                                              "intent": "order",
                                              "businessStatus": {
                                                "coding": [{ "system": "https://medizininformatik-initiative.de/torch/job-status", "code": "RUNNING_PROCESS_BATCH", "display": "Processing batches" }]
                                              },
                                              "priority": "asap",
                                              "description": "TORCH Job 550e8400-e29b-41d4-a716-446655440000",
                                              "authoredOn": "2024-01-15T10:29:55.000+00:00",
                                              "executionPeriod": { "start": "2024-01-15T10:29:55.000+00:00" }
                                            }
                                            """)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Bad Request — invalid Task body",
                            content = @Content(mediaType = "application/fhir+json",
                                    schema = @Schema(implementation = OperationOutcomeSchema.class),
                                    examples = @ExampleObject(name = "Parse error", value = """
                                            {
                                              "resourceType": "OperationOutcome",
                                              "issue": [{ "severity": "error", "code": "invalid", "diagnostics": "Could not parse Task resource: unexpected token" }]
                                            }
                                            """))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Task not found",
                            content = @Content(mediaType = "application/fhir+json",
                                    schema = @Schema(implementation = OperationOutcomeSchema.class),
                                    examples = @ExampleObject(name = "Not found", value = """
                                            {
                                              "resourceType": "OperationOutcome",
                                              "issue": [{ "severity": "error", "code": "not-found", "diagnostics": "Task/550e8400-e29b-41d4-a716-446655440000 not found" }]
                                            }
                                            """))
                    ),
                    @ApiResponse(
                            responseCode = "409",
                            description = "Conflict — version mismatch",
                            content = @Content(mediaType = "application/fhir+json",
                                    schema = @Schema(implementation = OperationOutcomeSchema.class),
                                    examples = @ExampleObject(name = "Version conflict", value = """
                                            {
                                              "resourceType": "OperationOutcome",
                                              "issue": [{ "severity": "error", "code": "conflict", "diagnostics": "Version mismatch: expected 3, got 2" }]
                                            }
                                            """))
                    ),
                    @ApiResponse(
                            responseCode = "428",
                            description = "Precondition Required — If-Match header missing",
                            content = @Content(mediaType = "application/fhir+json",
                                    schema = @Schema(implementation = OperationOutcomeSchema.class),
                                    examples = @ExampleObject(name = "Missing If-Match", value = """
                                            {
                                              "resourceType": "OperationOutcome",
                                              "issue": [{ "severity": "error", "code": "required", "diagnostics": "If-Match header is required for PUT /fhir/Task/{id}" }]
                                            }
                                            """))
                    )
            }
    )
    /**
     * Handles {@code PUT /fhir/Task/{id}} — updates the priority of an existing task.
     *
     * <p>Requires an {@code If-Match: W/"<version>"} header for optimistic locking.
     *
     * @param request the incoming server request; body must be a valid FHIR {@link Task} JSON
     * @return a {@link Mono} emitting the updated task, or an error response on invalid input or conflict
     */
    public Mono<ServerResponse> updateTask(ServerRequest request) {
        return parseJobId(request)
                .flatMap(jobId -> {
                    Optional<Long> expectedVersion = parseIfMatch(request);
                    if (expectedVersion.isEmpty()) {
                        return operationOutcome(
                                HttpStatus.PRECONDITION_REQUIRED,
                                OperationOutcomeCreator.simple(
                                        Severity.ERROR,
                                        OperationOutcome.IssueType.REQUIRED,
                                        "If-Match header is required for PUT /fhir/Task/{id}"
                                )
                        );
                    }
                    return request.bodyToMono(String.class)
                            .flatMap(this::extractPriority)
                            .flatMap(priority -> Mono.fromCallable(() -> persistence.changePriority(jobId, priority, expectedVersion.get()))
                                    .subscribeOn(Schedulers.boundedElastic()))
                            .flatMap(updatedJob -> ServerResponse.ok()
                                    .contentType(MEDIA_TYPE_FHIR_JSON)
                                    .bodyValue(fhirContext.newJsonParser().encodeResourceToString(jobTaskMapper.toFhirTask(updatedJob))))
                            .onErrorResume(IllegalArgumentException.class, e -> operationOutcome(
                                    HttpStatus.BAD_REQUEST,
                                    OperationOutcomeCreator.simple(
                                            Severity.ERROR,
                                            OperationOutcome.IssueType.INVALID,
                                            e.getMessage()
                                    )
                            ))
                            .onErrorResume(VersionConflictException.class, e -> operationOutcome(
                                    HttpStatus.PRECONDITION_FAILED,
                                    OperationOutcomeCreator.simple(
                                            Severity.ERROR,
                                            OperationOutcome.IssueType.CONFLICT,
                                            e.getMessage()
                                    )
                            ));
                })
                .onErrorResume(JobNotFoundException.class, e -> notFound(e.getMessage()));
    }

    @Operation(
            summary = "POST /fhir/Task/{id}/$pause — Pause Task",
            description = "Pauses a running task. Only valid from RUNNING_* states.",
            parameters = @Parameter(
                    name = "id",
                    in = ParameterIn.PATH,
                    required = true,
                    description = "Job identifier (UUID)",
                    schema = @Schema(type = "string", format = "uuid")
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Updated FHIR Task (now PAUSED)",
                            content = @Content(
                                    mediaType = "application/fhir+json",
                                    schema = @Schema(type = "object"),
                                    examples = @ExampleObject(name = "Task paused", value = """
                                            {
                                              "resourceType": "Task",
                                              "id": "550e8400-e29b-41d4-a716-446655440000",
                                              "meta": { "versionId": "4", "lastUpdated": "2024-01-15T10:31:00.000+00:00" },
                                              "status": "on-hold",
                                              "intent": "order",
                                              "businessStatus": {
                                                "coding": [{ "system": "https://medizininformatik-initiative.de/torch/job-status", "code": "PAUSED", "display": "Paused" }]
                                              },
                                              "priority": "routine",
                                              "description": "TORCH Job 550e8400-e29b-41d4-a716-446655440000",
                                              "authoredOn": "2024-01-15T10:29:55.000+00:00",
                                              "executionPeriod": { "start": "2024-01-15T10:29:55.000+00:00" }
                                            }
                                            """)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Task not found",
                            content = @Content(mediaType = "application/fhir+json",
                                    schema = @Schema(implementation = OperationOutcomeSchema.class),
                                    examples = @ExampleObject(name = "Not found", value = """
                                            {
                                              "resourceType": "OperationOutcome",
                                              "issue": [{ "severity": "error", "code": "not-found", "diagnostics": "Task/550e8400-e29b-41d4-a716-446655440000 not found" }]
                                            }
                                            """))
                    ),
                    @ApiResponse(
                            responseCode = "409",
                            description = "Conflict — task is not in a pausable state",
                            content = @Content(mediaType = "application/fhir+json",
                                    schema = @Schema(implementation = OperationOutcomeSchema.class),
                                    examples = @ExampleObject(name = "Invalid state", value = """
                                            {
                                              "resourceType": "OperationOutcome",
                                              "issue": [{ "severity": "error", "code": "conflict", "diagnostics": "Task/550e8400-e29b-41d4-a716-446655440000 cannot be $pause: already completed" }]
                                            }
                                            """))
                    )
            }
    )
    /**
     * Handles {@code POST /fhir/Task/{id}/$pause} — pauses a running task.
     *
     * @param request the incoming server request
     * @return a {@link Mono} emitting the updated task, or an error response if the transition is invalid
     */
    public Mono<ServerResponse> pauseTask(ServerRequest request) {
        return handleTaskOperation(request, "pause", persistence::pauseJob);
    }

    @Operation(
            summary = "POST /fhir/Task/{id}/$cancel — Cancel Task",
            description = "Cancels a task. Valid from any non-terminal state.",
            parameters = @Parameter(
                    name = "id",
                    in = ParameterIn.PATH,
                    required = true,
                    description = "Job identifier (UUID)",
                    schema = @Schema(type = "string", format = "uuid")
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Updated FHIR Task (now CANCELLED)",
                            content = @Content(
                                    mediaType = "application/fhir+json",
                                    schema = @Schema(type = "object"),
                                    examples = @ExampleObject(name = "Task cancelled", value = """
                                            {
                                              "resourceType": "Task",
                                              "id": "550e8400-e29b-41d4-a716-446655440000",
                                              "meta": { "versionId": "5", "lastUpdated": "2024-01-15T10:31:30.000+00:00" },
                                              "status": "cancelled",
                                              "intent": "order",
                                              "businessStatus": {
                                                "coding": [{ "system": "https://medizininformatik-initiative.de/torch/job-status", "code": "CANCELLED", "display": "Cancelled" }]
                                              },
                                              "priority": "routine",
                                              "description": "TORCH Job 550e8400-e29b-41d4-a716-446655440000",
                                              "authoredOn": "2024-01-15T10:29:55.000+00:00",
                                              "executionPeriod": { "start": "2024-01-15T10:29:55.000+00:00", "end": "2024-01-15T10:31:30.000+00:00" }
                                            }
                                            """)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Task not found",
                            content = @Content(mediaType = "application/fhir+json",
                                    schema = @Schema(implementation = OperationOutcomeSchema.class),
                                    examples = @ExampleObject(name = "Not found", value = """
                                            {
                                              "resourceType": "OperationOutcome",
                                              "issue": [{ "severity": "error", "code": "not-found", "diagnostics": "Task/550e8400-e29b-41d4-a716-446655440000 not found" }]
                                            }
                                            """))
                    ),
                    @ApiResponse(
                            responseCode = "409",
                            description = "Conflict — task cannot be cancelled from its current state",
                            content = @Content(mediaType = "application/fhir+json",
                                    schema = @Schema(implementation = OperationOutcomeSchema.class),
                                    examples = @ExampleObject(name = "Invalid state", value = """
                                            {
                                              "resourceType": "OperationOutcome",
                                              "issue": [{ "severity": "error", "code": "conflict", "diagnostics": "Task/550e8400-e29b-41d4-a716-446655440000 cannot be $cancel: already completed" }]
                                            }
                                            """))
                    )
            }
    )
    /**
     * Handles {@code POST /fhir/Task/{id}/$cancel} — cancels a task.
     *
     * @param request the incoming server request
     * @return a {@link Mono} emitting the updated task, or an error response if the transition is invalid
     */
    public Mono<ServerResponse> cancelTask(ServerRequest request) {
        return handleTaskOperation(request, "cancel", persistence::cancelJob);
    }

    @Operation(
            summary = "POST /fhir/Task/{id}/$resume — Resume Task",
            description = "Resumes a paused task. Only valid from PAUSED state.",
            parameters = @Parameter(
                    name = "id",
                    in = ParameterIn.PATH,
                    required = true,
                    description = "Job identifier (UUID)",
                    schema = @Schema(type = "string", format = "uuid")
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Updated FHIR Task (now running)",
                            content = @Content(
                                    mediaType = "application/fhir+json",
                                    schema = @Schema(type = "object"),
                                    examples = @ExampleObject(name = "Task resumed", value = """
                                            {
                                              "resourceType": "Task",
                                              "id": "550e8400-e29b-41d4-a716-446655440000",
                                              "meta": { "versionId": "5", "lastUpdated": "2024-01-15T10:32:00.000+00:00" },
                                              "status": "in-progress",
                                              "intent": "order",
                                              "businessStatus": {
                                                "coding": [{ "system": "https://medizininformatik-initiative.de/torch/job-status", "code": "RUNNING_PROCESS_BATCH", "display": "Processing batches" }]
                                              },
                                              "priority": "routine",
                                              "description": "TORCH Job 550e8400-e29b-41d4-a716-446655440000",
                                              "authoredOn": "2024-01-15T10:29:55.000+00:00",
                                              "executionPeriod": { "start": "2024-01-15T10:29:55.000+00:00" }
                                            }
                                            """)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Task not found",
                            content = @Content(mediaType = "application/fhir+json",
                                    schema = @Schema(implementation = OperationOutcomeSchema.class),
                                    examples = @ExampleObject(name = "Not found", value = """
                                            {
                                              "resourceType": "OperationOutcome",
                                              "issue": [{ "severity": "error", "code": "not-found", "diagnostics": "Task/550e8400-e29b-41d4-a716-446655440000 not found" }]
                                            }
                                            """))
                    ),
                    @ApiResponse(
                            responseCode = "409",
                            description = "Conflict — task is not in a resumable state",
                            content = @Content(mediaType = "application/fhir+json",
                                    schema = @Schema(implementation = OperationOutcomeSchema.class),
                                    examples = @ExampleObject(name = "Invalid state", value = """
                                            {
                                              "resourceType": "OperationOutcome",
                                              "issue": [{ "severity": "error", "code": "conflict", "diagnostics": "Task/550e8400-e29b-41d4-a716-446655440000 cannot be $resume: not paused" }]
                                            }
                                            """))
                    )
            }
    )
    /**
     * Handles {@code POST /fhir/Task/{id}/$resume} — resumes a paused task.
     *
     * @param request the incoming server request
     * @return a {@link Mono} emitting the updated task, or an error response if the transition is invalid
     */
    public Mono<ServerResponse> resumeTask(ServerRequest request) {
        return handleTaskOperation(request, "resume", persistence::resumeJob);
    }

    @Operation(
            summary = "DELETE /fhir/Task/{id} — Delete Task",
            description = "Deletes a task and all its associated result data.",
            parameters = @Parameter(
                    name = "id",
                    in = ParameterIn.PATH,
                    required = true,
                    description = "Job identifier (UUID)",
                    schema = @Schema(type = "string", format = "uuid")
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "204",
                            description = "No Content — task deleted successfully",
                            content = @Content(schema = @Schema(hidden = true))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Task not found",
                            content = @Content(mediaType = "application/fhir+json",
                                    schema = @Schema(implementation = OperationOutcomeSchema.class),
                                    examples = @ExampleObject(name = "Not found", value = """
                                            {
                                              "resourceType": "OperationOutcome",
                                              "issue": [{ "severity": "error", "code": "not-found", "diagnostics": "Task/550e8400-e29b-41d4-a716-446655440000 not found" }]
                                            }
                                            """))
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Internal Server Error — could not delete result files",
                            content = @Content(mediaType = "application/fhir+json",
                                    schema = @Schema(implementation = OperationOutcomeSchema.class),
                                    examples = @ExampleObject(name = "IO error", value = """
                                            {
                                              "resourceType": "OperationOutcome",
                                              "issue": [{ "severity": "error", "code": "exception", "diagnostics": "Failed to delete result files for job 550e8400-e29b-41d4-a716-446655440000" }]
                                            }
                                            """))
                    )
            }
    )
    /**
     * Handles {@code DELETE /fhir/Task/{id}} — deletes a task and its associated data.
     *
     * @param request the incoming server request
     * @return a {@link Mono} emitting {@code 204 No Content} on success, or an error response on failure
     */
    public Mono<ServerResponse> deleteTask(ServerRequest request) {
        return parseJobId(request)
                .flatMap(jobId -> Mono.fromCallable(() -> {
                            persistence.deleteJob(jobId);
                            return jobId;
                        })
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMap(ignored -> ServerResponse.noContent().build()))
                .onErrorResume(JobNotFoundException.class, e -> notFound(e.getMessage()));
    }

    /**
     * Shared implementation for pause, cancel, and resume operations.
     *
     * @param request       the incoming server request
     * @param operationName human-readable operation name used in error messages (e.g. {@code "pause"})
     * @param operation     the persistence action to apply to the resolved job ID
     * @return a {@link Mono} emitting the updated task, or an error response on failure
     */
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

    /**
     * Parses a comma-separated list of UUID strings.
     *
     * @param param the raw query parameter value, or empty if absent
     * @return valid UUIDs in {@code values} and unrecognised strings in {@code invalidValues}
     */
    private ParseResult<UUID> parseUuidList(Optional<String> param) {
        if (param.isEmpty() || param.get().isBlank()) {
            return new ParseResult<>(Set.of(), Set.of());
        }

        Set<UUID> values = new LinkedHashSet<>();
        Set<String> invalidValues = new LinkedHashSet<>();

        for (String rawValue : param.get().split(",")) {
            String value = rawValue.trim();
            if (value.isEmpty()) {
                continue;
            }

            if (UUID_PATTERN.matcher(value).matches()) {
                values.add(UUID.fromString(value));
            } else {
                invalidValues.add(value);
            }
        }

        return new ParseResult<>(Set.copyOf(values), Set.copyOf(invalidValues));
    }

    /**
     * Parses a comma-separated list of {@link JobStatus} strings.
     *
     * @param param the raw query parameter value, or empty if absent
     * @return valid statuses in {@code values} and unrecognised strings in {@code invalidValues}
     */
    private ParseResult<JobStatus> parseStatusList(Optional<String> param) {
        if (param.isEmpty() || param.get().isBlank()) {
            return new ParseResult<>(Set.of(), Set.of());
        }

        Set<JobStatus> values = new LinkedHashSet<>();
        Set<String> invalidValues = new LinkedHashSet<>();

        for (String rawValue : param.get().split(",")) {
            String value = rawValue.trim();
            if (value.isEmpty()) {
                continue;
            }

            try {
                values.add(JobStatus.valueOf(value.toUpperCase()));
            } catch (IllegalArgumentException e) {
                invalidValues.add(value);
            }
        }

        return new ParseResult<>(Set.copyOf(values), Set.copyOf(invalidValues));
    }

    /**
     * Parses the {@code If-Match} header expecting the form {@code W/"<version>"}.
     *
     * @param request the incoming server request
     * @return the version number, or empty if the header is absent or malformed
     */
    private Optional<Long> parseIfMatch(ServerRequest request) {
        return request.headers().firstHeader("If-Match") == null
                ? Optional.empty()
                : Optional.ofNullable(request.headers().firstHeader("If-Match"))
                  .map(v -> v.replaceAll("^W/\"(.*)\"$", "$1"))
                  .flatMap(v -> {
                      try {
                          return Optional.of(Long.parseLong(v));
                      } catch (NumberFormatException e) {
                          return Optional.empty();
                      }
                  });
    }

    /**
     * Reads the {@code Prefer} header to determine the search handling mode.
     *
     * @param request the incoming server request
     * @return {@link SearchHandlingMode#STRICT} if {@code handling=strict} is present,
     *         {@link SearchHandlingMode#LENIENT} otherwise
     */
    private SearchHandlingMode searchHandlingMode(ServerRequest request) {
        boolean strict = request.headers().header("Prefer").stream()
                .flatMap(header -> Arrays.stream(header.split(",")))
                .map(String::trim)
                .anyMatch(value -> value.matches("(?i)handling\\s*=\\s*strict"));

        return strict ? SearchHandlingMode.STRICT : SearchHandlingMode.LENIENT;
    }

    /**
     * Parses the request body as a FHIR {@link Task} and extracts its priority.
     *
     * @param body the raw JSON request body
     * @return a {@link Mono} emitting the extracted {@link JobPriority}, or an error mono
     *         carrying an {@link IllegalArgumentException} if the body cannot be parsed
     */
    private Mono<JobPriority> extractPriority(String body) {
        try {
            Task task = fhirContext.newJsonParser().parseResource(Task.class, body);
            return Mono.just(parseFhirPriority(task.getPriority()));
        } catch (Exception e) {
            return Mono.error(new IllegalArgumentException("Could not parse Task resource: " + e.getMessage()));
        }
    }

    /**
     * Validates search parameters under strict handling mode.
     *
     * @param unsupported    search parameter names not recognised by this endpoint
     * @param idsResult      parse result for the {@code _id} parameter
     * @param statusesResult parse result for the {@code status} parameter
     * @return an empty {@link Mono} when all parameters are valid, or a {@link Mono} emitting
     *         a {@code 400 Bad Request} response listing all issues
     */
    private Mono<ServerResponse> validateStrictSearch(
            Set<String> unsupported,
            ParseResult<UUID> idsResult,
            ParseResult<JobStatus> statusesResult
    ) {
        Set<String> issues = new LinkedHashSet<>();
        if (!unsupported.isEmpty()) {
            issues.add("Unsupported search parameters: " + unsupported);
        }
        if (!idsResult.invalidValues().isEmpty()) {
            issues.add("Invalid _id values: " + idsResult.invalidValues());
        }
        if (!statusesResult.invalidValues().isEmpty()) {
            issues.add("Invalid status values: " + statusesResult.invalidValues());
        }
        if (issues.isEmpty()) {
            return Mono.empty();
        }
        return operationOutcome(
                HttpStatus.BAD_REQUEST,
                OperationOutcomeCreator.simple(
                        Severity.ERROR,
                        OperationOutcome.IssueType.INVALID,
                        String.join("; ", issues)
                )
        );
    }

    /**
     * Maps a FHIR {@link Task.TaskPriority} to the internal {@link JobPriority}.
     *
     * @param fhirPriority the FHIR priority value, or {@code null}
     * @return {@link JobPriority#HIGH} for {@code ASAP}, {@code STAT}, or {@code URGENT};
     *         {@link JobPriority#NORMAL} for all other values including {@code null}
     */
    private JobPriority parseFhirPriority(Task.TaskPriority fhirPriority) {
        if (fhirPriority == null) {
            return JobPriority.NORMAL;
        }
        return switch (fhirPriority) {
            case ASAP, STAT, URGENT -> JobPriority.HIGH;
            default -> JobPriority.NORMAL;
        };
    }

    /**
     * Resolves the {@code id} path variable as a {@link UUID}.
     *
     * @param request the incoming server request
     * @return a {@link Mono} emitting the parsed UUID, or an error mono with
     *         {@link JobNotFoundException} if the value is not a valid UUID
     */
    private Mono<UUID> parseJobId(ServerRequest request) {
        String rawId = request.pathVariable("id");
        if (!UUID_PATTERN.matcher(rawId).matches()) {
            return Mono.error(new JobNotFoundException(rawId));
        }
        return Mono.just(UUID.fromString(rawId));
    }

    /**
     * Builds a {@code 404 Not Found} operation outcome response.
     *
     * @param message human-readable detail to include in the outcome
     * @return a {@link Mono} emitting the error response
     */
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

    /**
     * Serialises an {@link OperationOutcome} into a FHIR JSON response with the given status.
     *
     * @param status  the HTTP status code
     * @param outcome the outcome resource to serialise
     * @return a {@link Mono} emitting the response
     */
    private Mono<ServerResponse> operationOutcome(HttpStatus status, OperationOutcome outcome) {
        return ServerResponse.status(status)
                .contentType(MEDIA_TYPE_FHIR_JSON)
                .bodyValue(fhirContext.newJsonParser().encodeResourceToString(outcome));
    }

    private enum SearchHandlingMode {
        LENIENT,
        STRICT
    }

    private record ParseResult<T>(Set<T> values, Set<String> invalidValues) {
    }

    @FunctionalInterface
    interface TaskOperation {
        Job apply(UUID jobId) throws JobNotFoundException;
    }
}
