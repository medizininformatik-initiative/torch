package de.medizininformatikinitiative.torch.rest;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.medizininformatikinitiative.torch.config.TorchProperties;
import de.medizininformatikinitiative.torch.diagnostics.JobDiagnosticSummary;
import de.medizininformatikinitiative.torch.exceptions.ConsentFormatException;
import de.medizininformatikinitiative.torch.exceptions.ValidationException;
import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.failure.Severity;
import de.medizininformatikinitiative.torch.jobhandling.workunit.WorkUnitStatus;
import de.medizininformatikinitiative.torch.management.OperationOutcomeCreator;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedCrtdl;
import de.medizininformatikinitiative.torch.rest.schema.JobManifestSchema;
import de.medizininformatikinitiative.torch.rest.schema.OperationOutcomeSchema;
import de.medizininformatikinitiative.torch.service.CohortQueryService;
import de.medizininformatikinitiative.torch.service.CrtdlValidatorService;
import de.medizininformatikinitiative.torch.service.JobPersistenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static de.medizininformatikinitiative.torch.diagnostics.DiagnosticsStore.PATIENT_EXCLUSIONS_FILE;
import static de.medizininformatikinitiative.torch.diagnostics.DiagnosticsStore.REPORTS_DIRECTORY;
import static de.medizininformatikinitiative.torch.diagnostics.DiagnosticsStore.RESOURCE_EXCLUSIONS_FILE;
import static de.medizininformatikinitiative.torch.diagnostics.DiagnosticsStore.SUMMARY_FILE;
import static de.medizininformatikinitiative.torch.management.OperationOutcomeCreator.createOperationOutcome;
import static java.util.Objects.requireNonNull;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.accepted;

/**
 * Reactive endpoints for TORCH's FHIR API.
 *
 * <p>Exposes:
 * <ul>
 *   <li>{@code POST /fhir/$extract-data} to create a job</li>
 *   <li>{@code GET  /fhir/__status/{jobId}} to poll job status/results</li>
 * </ul>
 * <p>
 * The asynchronous behavior of {@code $extract-data} and {@code __status}
 *  * follows the FHIR Asynchronous Request pattern as defined in
 *  * <a href="https://hl7.org/fhir/R5/async-bulk.html">FHIR R5 Async Bulk Data
 */
@RestController

public class FhirController {

    private static final Logger logger = LoggerFactory.getLogger(FhirController.class);

    private static final MediaType MEDIA_TYPE_FHIR_JSON = MediaType.valueOf("application/fhir+json");
    public static final String VALUE_OBJECT = "valueObject";

    private final FhirContext fhirContext;
    private final ExtractDataParametersParser extractDataParametersParser;
    private final JobPersistenceService persistence;
    private final String baseUrl;
    private final String fileServerName;
    private final CrtdlValidatorService validator;
    private final ObjectMapper mapper;
    private final CohortQueryService cohortQueryService;

    @Autowired
    public FhirController(FhirContext fhirContext, ExtractDataParametersParser parser, CrtdlValidatorService validator,
                          JobPersistenceService persistence, TorchProperties properties, ObjectMapper mapper,
                          CohortQueryService cohortQueryService) {
        this.fhirContext = requireNonNull(fhirContext);
        this.extractDataParametersParser = requireNonNull(parser);
        this.validator = requireNonNull(validator);
        this.persistence = requireNonNull(persistence);
        this.baseUrl = properties.base().url();
        this.mapper = mapper;
        this.cohortQueryService = requireNonNull(cohortQueryService);
        this.fileServerName = properties.output().file().server().url();
    }

    @Bean
    @RouterOperations({
            @RouterOperation(
                    path = "/fhir/$extract-data",
                    method = RequestMethod.POST,
                    beanClass = FhirController.class,
                    beanMethod = "handleExtractData"
            ),
            @RouterOperation(
                    path = "/fhir/__status/{jobId}",
                    method = RequestMethod.GET,
                    beanClass = FhirController.class,
                    beanMethod = "checkStatus"
            ),
            @RouterOperation(
                    path = "/fhir/$translate-cql/{jobId}",
                    method = RequestMethod.GET,
                    beanClass = FhirController.class,
                    beanMethod = "handleCqlForJob"
            ),
            @RouterOperation(
                    path = "/fhir/$translate-cql",
                    method = RequestMethod.POST,
                    beanClass = FhirController.class,
                    beanMethod = "handleCqlTranslation"
            )
    })
    public RouterFunction<ServerResponse> queryRouter() {
        logger.info("Init FhirController Router");
        return route(POST("/fhir/$extract-data").and(accept(MEDIA_TYPE_FHIR_JSON)), this::handleExtractData)
                .andRoute(GET("/fhir/__status/{jobId}"), this::checkStatus)
                .andRoute(GET("/fhir/$translate-cql/{jobId}"), this::handleCqlForJob)
                .andRoute(POST("/fhir/$translate-cql"), this::handleCqlTranslation);
    }

    @Operation(
            summary = "POST /fhir/$extract-data — Bulk Data Kick-off",
            description = """
                    Starts a TORCH extraction job.
                    
                    Expects a FHIR Parameters resource containing:
                    - parameter[name='crtdl'].valueBase64Binary = base64 encoded CRTDL JSON
                    - optional repeated parameter[name='patient'].valueString to provide explicit patient IDs
                    """,
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/fhir+json",
                            examples = {
                                    @ExampleObject(
                                            name = "CRTDL only",
                                            value = """
                                                    {
                                                      "resourceType": "Parameters",
                                                      "parameter": [
                                                        { "name": "crtdl", "valueBase64Binary": "<Base64 encoded CRTDL>" }
                                                      ]
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "CRTDL + patients",
                                            value = """
                                                    {
                                                      "resourceType": "Parameters",
                                                      "parameter": [
                                                        { "name": "crtdl", "valueBase64Binary": "<Base64 encoded CRTDL>" },
                                                        { "name": "patient", "valueString": "Patient/123" },
                                                        { "name": "patient", "valueString": "Patient/456" }
                                                      ]
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "202",
                            description = "Accepted - job created",
                            content = @Content(schema = @Schema(hidden = true)),
                            headers = @Header(
                                    name = "Content-Location",
                                    description = "URL to poll job status",
                                    schema = @Schema(type = "string")
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Bad Request",
                            content = @Content(
                                    mediaType = "application/fhir+json",
                                    schema = @Schema(implementation = OperationOutcomeSchema.class)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Internal Server Error",
                            content = @Content(
                                    mediaType = "application/fhir+json",
                                    schema = @Schema(implementation = OperationOutcomeSchema.class)
                            )
                    )
            }
    )
    /**
     * Handles {@code POST /fhir/$extract-data}.
     *
     * <p>Validates the CRTDL, creates a job, and returns {@code 202 Accepted} with a {@code Content-Location}
     * header pointing to {@code /fhir/__status/{jobId}}.</p>
     *
     * <p>On errors, returns an {@link OperationOutcome} as FHIR JSON.</p>
     */
    public Mono<ServerResponse> handleExtractData(ServerRequest request) {

        return request.bodyToMono(String.class)
                .filter(body -> !body.isBlank())
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Empty request body")))
                .map(extractDataParametersParser::parseParameters)
                .flatMap(parameters -> {
                    AnnotatedCrtdl annotated;
                    try {
                        annotated = validator.validateAndAnnotate(parameters.crtdl());
                    } catch (ValidationException | ConsentFormatException e) {
                        return Mono.error(new IllegalArgumentException(e.getMessage()));
                    }
                    UUID jobId;
                    try {
                        jobId = persistence.createJob(
                                annotated,
                                parameters.patientIds(),
                                baseUrl + "/fhir/$extract-data"
                        );
                    } catch (IOException ioe) {
                        return Mono.error(ioe);
                    }
                    return accepted()
                            .header("Content-Location",
                                    baseUrl + "/fhir/__status/" + jobId)
                            .build();
                }).onErrorResume(Exception.class, e -> {
                    HttpStatus status = (e instanceof IllegalArgumentException)
                            ? HttpStatus.BAD_REQUEST
                            : HttpStatus.INTERNAL_SERVER_ERROR;

                    if (status == HttpStatus.BAD_REQUEST) {
                        logger.warn("FHIR_CONTROLLER_01 Bad request: {}", e.getMessage(), e);
                    } else {
                        logger.error("FHIR_CONTROLLER_02  Internal server error: {}", e.getMessage(), e);
                    }

                    OperationOutcome outcome = createOperationOutcome(e);

                    return ServerResponse.status(status)
                            .contentType(MEDIA_TYPE_FHIR_JSON)
                            .bodyValue(fhirContext.newJsonParser().encodeResourceToString(outcome));
                });
    }


    @Operation(
            summary = "GET /fhir/__status/{jobId} — Bulk Data Status Request",
            parameters = @Parameter(
                    name = "jobId",
                    in = ParameterIn.PATH,
                    required = true,
                    description = "Job identifier returned by the kick-off endpoint",
                    schema = @Schema(type = "string", format = "uuid")
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Job Completed - Manifest is ready",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = JobManifestSchema.class)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "202",
                            description = "Job In-Progress",
                            content = @Content(
                                    mediaType = "application/fhir+json",
                                    schema = @Schema(implementation = OperationOutcomeSchema.class)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Job Not Found Or Deleted",
                            content = @Content(
                                    mediaType = "application/fhir+json",
                                    schema = @Schema(implementation = OperationOutcomeSchema.class)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "410",
                            description = "Job Cancelled or Expired",
                            content = @Content(
                                    mediaType = "application/fhir+json",
                                    schema = @Schema(implementation = OperationOutcomeSchema.class)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Job Failed",
                            content = @Content(
                                    mediaType = "application/fhir+json",
                                    schema = @Schema(implementation = OperationOutcomeSchema.class)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "503",
                            description = "Service Unavailable (Transient Failure)",
                            content = @Content(
                                    mediaType = "application/fhir+json",
                                    schema = @Schema(implementation = OperationOutcomeSchema.class)
                            )
                    )
            }
    )
    /**
     * Handles {@code GET /fhir/__status/{jobId}}.
     *
     * <p>Returns either:
     * <ul>
     *   <li>job output JSON when completed</li>
     *   <li>{@code 202} with {@link OperationOutcome} while running</li>
     *   <li>an {@link OperationOutcome} for failed/cancelled jobs</li>
     * </ul>
     */
    public Mono<ServerResponse> checkStatus(ServerRequest request) {
        var jobIdRaw = request.pathVariable("jobId");
        UUID jobId;
        try {
            jobId = UUID.fromString(jobIdRaw);
        } catch (IllegalArgumentException e) {
            String oo = fhirContext.newJsonParser().encodeResourceToString(
                    OperationOutcomeCreator.simple(
                            Severity.ERROR,
                            OperationOutcome.IssueType.INVALID,
                            "Invalid jobId\n" +
                                    "The provided jobId is not a valid UUID: " + jobIdRaw
                    )
            );

            return ServerResponse.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(oo);
        }
        Job job = persistence.getJob(jobId).orElse(null);
        if (job == null) {
            String oo = fhirContext.newJsonParser().encodeResourceToString(
                    OperationOutcomeCreator.simple(
                            Severity.ERROR,
                            OperationOutcome.IssueType.NOTFOUND,
                            "Job not found \n" +
                                    "No job with id " + jobId + " exists"
                    )
            );

            return ServerResponse.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(oo);
        }

        logger.trace("Status of queried jobID {} is {}", jobId, job.status());
        return switch (job.status()) {
            case COMPLETED -> {
                try {
                    String json = mapper.writeValueAsString(loadCompletedJobJSON(job, fileServerName));
                    yield ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(json);
                } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                    yield ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .contentType(MEDIA_TYPE_FHIR_JSON)
                            .bodyValue(fhirContext.newJsonParser().encodeResourceToString(createOperationOutcome(e)));
                }
            }
            case PAUSED, PENDING, RUNNING_PROCESS_BATCH, RUNNING_PROCESS_CORE, RUNNING_GET_COHORT ->
                    accepted()
                            .header("X-Progress", xProgress(job))
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(fhirContext.newJsonParser().encodeResourceToString(
                                    OperationOutcomeCreator.fromJob(job)));
            case TEMP_FAILED ->
                    ServerResponse.status(503).contentType(MediaType.APPLICATION_JSON).bodyValue(fhirContext.newJsonParser().encodeResourceToString(
                            OperationOutcomeCreator.fromJob(job)));
            case FAILED ->
                    ServerResponse.status(500).contentType(MediaType.APPLICATION_JSON).bodyValue(fhirContext.newJsonParser().encodeResourceToString(
                            OperationOutcomeCreator.fromJob(job)));
            case CANCELLED ->
                    ServerResponse.status(410).contentType(MediaType.APPLICATION_JSON).bodyValue(fhirContext.newJsonParser().encodeResourceToString(
                            OperationOutcomeCreator.fromJob(job)));
            case DELETED ->
                    ServerResponse.status(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON).bodyValue(fhirContext.newJsonParser().encodeResourceToString(
                            OperationOutcomeCreator.fromJob(job)));
        };
    }


    @Operation(
            summary = "GET /fhir/$translate-cql/{jobId} — CQL for a saved job",
            description = "Returns the CQL of the cohort definition stored in an existing job. Useful for debugging.",
            parameters = @Parameter(
                    name = "jobId",
                    in = ParameterIn.PATH,
                    required = true,
                    description = "Job identifier returned by the kick-off endpoint",
                    schema = @Schema(type = "string", format = "uuid")
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "CQL", content = @Content(mediaType = "text/plain")),
                    @ApiResponse(responseCode = "400", description = "Invalid job ID",
                            content = @Content(mediaType = "application/fhir+json", schema = @Schema(implementation = OperationOutcomeSchema.class))),
                    @ApiResponse(responseCode = "404", description = "Job not found",
                            content = @Content(mediaType = "application/fhir+json", schema = @Schema(implementation = OperationOutcomeSchema.class))),
                    @ApiResponse(responseCode = "500", description = "Internal Server Error",
                            content = @Content(mediaType = "application/fhir+json", schema = @Schema(implementation = OperationOutcomeSchema.class)))
            }
    )
    /** Handles {@code GET /fhir/$translate-cql/{jobId}}. */
    public Mono<ServerResponse> handleCqlForJob(ServerRequest request) {
        var jobIdRaw = request.pathVariable("jobId");
        UUID jobId;
        try {
            jobId = UUID.fromString(jobIdRaw);
        } catch (IllegalArgumentException e) {
            return ServerResponse.badRequest()
                    .contentType(MEDIA_TYPE_FHIR_JSON)
                    .bodyValue(fhirContext.newJsonParser().encodeResourceToString(
                            OperationOutcomeCreator.simple(Severity.ERROR, OperationOutcome.IssueType.INVALID,
                                    "Invalid jobId: " + jobIdRaw)));
        }

        Job job = persistence.getJob(jobId).orElse(null);
        if (job == null) {
            return ServerResponse.status(HttpStatus.NOT_FOUND)
                    .contentType(MEDIA_TYPE_FHIR_JSON)
                    .bodyValue(fhirContext.newJsonParser().encodeResourceToString(
                            OperationOutcomeCreator.simple(Severity.ERROR, OperationOutcome.IssueType.NOTFOUND,
                                    "No job with id " + jobId + " exists")));
        }

        return cohortQueryService.translateToCql(job.parameters().crtdl().cohortDefinition())
                .flatMap(cql -> ServerResponse.ok().contentType(MediaType.TEXT_PLAIN).bodyValue(cql))
                .onErrorResume(Exception.class, e -> {
                    logger.error("FHIR_CONTROLLER_03 Failed to translate CQL for job {}: {}", jobId, e.getMessage(), e);
                    return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .contentType(MEDIA_TYPE_FHIR_JSON)
                            .bodyValue(fhirContext.newJsonParser().encodeResourceToString(createOperationOutcome(e)));
                });
    }

    @Operation(
            summary = "POST /fhir/$translate-cql — Translate a CRTDL or CCDL to CQL",
            description = """
                    Accepts either a full CRTDL (with a {@code cohortDefinition} field) or a bare CCDL
                    (structured query) and returns the CQL representation without executing it.
                    """,
            requestBody = @RequestBody(required = true, content = @Content(mediaType = "application/json")),
            responses = {
                    @ApiResponse(responseCode = "200", description = "CQL", content = @Content(mediaType = "text/plain")),
                    @ApiResponse(responseCode = "400", description = "Bad Request",
                            content = @Content(mediaType = "application/fhir+json", schema = @Schema(implementation = OperationOutcomeSchema.class))),
                    @ApiResponse(responseCode = "500", description = "Internal Server Error",
                            content = @Content(mediaType = "application/fhir+json", schema = @Schema(implementation = OperationOutcomeSchema.class)))
            }
    )
    /**
     * Handles {@code POST /fhir/$translate-cql}.
     *
     * <p>Accepts a CRTDL (extracts the {@code cohortDefinition} field) or a bare CCDL
     * (structured query used directly). Returns the CQL as plain text.</p>
     */
    public Mono<ServerResponse> handleCqlTranslation(ServerRequest request) {
        return request.bodyToMono(String.class)
                .filter(body -> !body.isBlank())
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Empty request body")))
                .flatMap(body -> {
                    JsonNode root;
                    try {
                        root = mapper.readTree(body);
                    } catch (IOException e) {
                        return Mono.error(new IllegalArgumentException("Invalid JSON: " + e.getMessage(), e));
                    }
                    JsonNode cohortDefinition = root.has("cohortDefinition") ? root.get("cohortDefinition") : root;
                    return cohortQueryService.translateToCql(cohortDefinition);
                })
                .flatMap(cql -> ServerResponse.ok().contentType(MediaType.TEXT_PLAIN).bodyValue(cql))
                .onErrorResume(Exception.class, e -> {
                    HttpStatus status = e instanceof IllegalArgumentException
                            ? HttpStatus.BAD_REQUEST : HttpStatus.INTERNAL_SERVER_ERROR;
                    if (status == HttpStatus.BAD_REQUEST) {
                        logger.warn("FHIR_CONTROLLER_04 Bad request in $translate-cql: {}", e.getMessage(), e);
                    } else {
                        logger.error("FHIR_CONTROLLER_05 Internal server error in $translate-cql: {}", e.getMessage(), e);
                    }
                    return ServerResponse.status(status)
                            .contentType(MEDIA_TYPE_FHIR_JSON)
                            .bodyValue(fhirContext.newJsonParser().encodeResourceToString(createOperationOutcome(e)));
                });
    }


    /**
     * Returns a human-readable progress string for an in-progress {@link Job}.
     */
    private static String xProgress(Job job) {
        return switch (job.status()) {
            case RUNNING_GET_COHORT -> "Getting cohort";
            case RUNNING_PROCESS_BATCH -> "Processing batches (%.0f%%)".formatted(job.calculateBatchProgress());
            case RUNNING_PROCESS_CORE -> "Processing core resources";
            default -> job.status().display();
        };
    }

    /**
     * Builds the completed-job response JSON.
     *
     * <p>Includes NDJSON output URLs and embeds the {@link Job} as a custom extension.</p>
     */
    public JsonNode loadCompletedJobJSON(Job job, String fileServerName) {
        ObjectNode root = mapper.createObjectNode();
        root.put("transactionTime", DateTimeFormatter.ISO_INSTANT.format(job.startedAt()));
        root.put("request", job.parameters().kickOffUrl() != null ? job.parameters().kickOffUrl() : "");
        root.put("requiresAccessToken", false);

        ArrayNode outputArr = mapper.createArrayNode();
        job.batches().forEach((batchId, batchState) -> {
            if (batchState.status() == WorkUnitStatus.FINISHED) {
                outputArr.add(mapper.createObjectNode()
                        .put("url", fileServerName + "/" + job.id() + "/" + batchId + ".ndjson")
                        .put("type", "NDJSON Bundle"));
            }
        });

        if (job.coreState().status() == WorkUnitStatus.FINISHED) {
            outputArr.add(mapper.createObjectNode()
                    .put("url", fileServerName + "/" + job.id() + "/core.ndjson")
                    .put("type", "NDJSON Bundle"));
        }

        root.set("output", outputArr);
        root.set("error", mapper.createArrayNode());

        JsonNode jobNode = mapper.valueToTree(job);

        ArrayNode extensionArr = mapper.createArrayNode();
        extensionArr.add(mapper.createObjectNode()
                .put("url", "https://torch.mii.de/fhir/StructureDefinition/torch-job")
                .set(VALUE_OBJECT, jobNode));

        if (persistence.jobSummaryExists(job.id())) {
            try {
                JobDiagnosticSummary diag = persistence.loadJobSummary(job.id());
                extensionArr.add(mapper.createObjectNode()
                        .put("url", "torch-job-diagnostics-summary")
                        .put("valueUrl", fileServerName + "/" + job.id() + "/" + REPORTS_DIRECTORY + "/" + SUMMARY_FILE)
                        .set(VALUE_OBJECT, mapper.valueToTree(diag)));
            } catch (Exception e) {
                logger.warn("Failed to load diagnostics summary for job {}: {}", job.id(), e.getMessage());
            }
        }

        if (persistence.resourceExclusionsExists(job.id())) {
            extensionArr.add(mapper.createObjectNode()
                    .put("url", "torch-resource-exclusions")
                    .put("valueUrl", fileServerName + "/" + job.id() + "/" + REPORTS_DIRECTORY + "/" + RESOURCE_EXCLUSIONS_FILE));
        }

        if (persistence.patientExclusionsExists(job.id())) {
            extensionArr.add(mapper.createObjectNode()
                    .put("url", "torch-patient-exclusions")
                    .put("valueUrl", fileServerName + "/" + job.id() + "/" + REPORTS_DIRECTORY + "/" + PATIENT_EXCLUSIONS_FILE));
        }

        if (!job.issues().isEmpty()) {
            extensionArr.add(mapper.createObjectNode()
                    .put("url", "torch-job-issues")
                    .set(VALUE_OBJECT, mapper.valueToTree(job.issues())));
        }

        root.set("extension", extensionArr);
        return root;
    }
}
