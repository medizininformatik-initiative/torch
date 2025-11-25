package de.medizininformatikinitiative.torch.rest;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.medizininformatikinitiative.torch.config.TorchProperties;
import de.medizininformatikinitiative.torch.exceptions.ConsentFormatException;
import de.medizininformatikinitiative.torch.exceptions.ValidationException;
import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.failure.Severity;
import de.medizininformatikinitiative.torch.jobhandling.workunit.WorkUnitStatus;
import de.medizininformatikinitiative.torch.management.OperationOutcomeCreator;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedCrtdl;
import de.medizininformatikinitiative.torch.service.CrtdlValidatorService;
import de.medizininformatikinitiative.torch.service.JobPersistenceService;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

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

    private final FhirContext fhirContext;
    private final ExtractDataParametersParser extractDataParametersParser;
    private final JobPersistenceService persistence;
    private final String baseUrl;
    private final String fileServerName;
    private final CrtdlValidatorService validator;
    private final ObjectMapper mapper;

    @Autowired
    public FhirController(FhirContext fhirContext, ExtractDataParametersParser parser, CrtdlValidatorService validator,
                          JobPersistenceService persistence, TorchProperties properties, ObjectMapper mapper) {
        this.fhirContext = requireNonNull(fhirContext);
        this.extractDataParametersParser = requireNonNull(parser);
        this.validator = requireNonNull(validator);
        this.persistence = requireNonNull(persistence);
        this.baseUrl = properties.base().url();
        this.mapper = mapper;

        this.fileServerName = properties.output().file().server().url();
    }

    @Bean
    public RouterFunction<ServerResponse> queryRouter() {
        logger.info("Init FhirController Router");
        return route(POST("/fhir/$extract-data").and(accept(MEDIA_TYPE_FHIR_JSON)), this::handleExtractData)
                .andRoute(GET("/fhir/__status/{jobId}"), this::checkStatus);
    }

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
                .filter(body -> body != null && !body.isBlank())
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
                                parameters.patientIds()
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
                            Severity.ERROR.ERROR,
                            OperationOutcome.IssueType.NOTFOUND,
                            "Job not found \n" +
                                    "No job with id " + jobId + " exists"
                    )
            );

            return ServerResponse.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(oo);
        }

        String transactionTime = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        String requestUrl = request.uri().toString();
        logger.trace("Status of queried jobID {} is {}", jobId, job.status());
        return switch (job.status()) {
            case COMPLETED -> {
                JsonNode json = loadCompletedJobJSON(job, requestUrl, transactionTime, fileServerName);
                yield ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(json);
            }
            case PAUSED, PENDING, RUNNING_PROCESS_BATCH, RUNNING_PROCESS_CORE, RUNNING_GET_COHORT ->
                    accepted().contentType(MediaType.APPLICATION_JSON).bodyValue(fhirContext.newJsonParser().encodeResourceToString(
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
        };
    }


    /**
     * Builds the completed-job response JSON.
     *
     * <p>Includes NDJSON output URLs and embeds the {@link Job} as a custom extension.</p>
     */
    public JsonNode loadCompletedJobJSON(Job job,
                                         String requestUrl,
                                         String transactionTime, String fileServerName) {
        ObjectNode root = mapper.createObjectNode();
        root.put("transactionTime", transactionTime);
        root.put("request", requestUrl);
        root.put("requiresAccessToken", false);

        ArrayNode outputArr = mapper.createArrayNode();
        // Job already contains the filenames — just generate URLs
        job.batches().keySet().forEach(f ->
                outputArr.add(mapper.createObjectNode()
                        .put("url", fileServerName + "/" + job.id() + "/" + f + ".ndjson")
                        .put("type", "NDJSON Bundle"))
        );

        if (job.coreState().status() == WorkUnitStatus.FINISHED) {
            outputArr.add(mapper.createObjectNode()
                    .put("url", fileServerName + "/" + job.id() + "/core.ndjson")
                    .put("type", "NDJSON Bundle"));
        }

        root.set("output", outputArr);
        JsonNode jobNode = mapper.valueToTree(job);

        ArrayNode extensionArr = mapper.createArrayNode();
        extensionArr.add(mapper.createObjectNode()
                .put("url", "https://torch.mii.de/fhir/StructureDefinition/torch-job")
                .set("valueObject", jobNode));

        root.set("extension", extensionArr);
        return root;
    }
}
