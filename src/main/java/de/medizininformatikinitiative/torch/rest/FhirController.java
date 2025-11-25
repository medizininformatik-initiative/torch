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
import static org.springframework.web.reactive.function.server.ServerResponse.notFound;

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

    public Mono<ServerResponse> handleExtractData(ServerRequest request) {
        UUID jobId = UUID.randomUUID();

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
                    try {
                        persistence.createJob(
                                annotated,
                                parameters.patientIds(),
                                jobId
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
                        logger.warn("Bad request: {}", e.getMessage(), e);
                    } else {
                        logger.error("Internal server error: {}", e.getMessage(), e);
                    }

                    OperationOutcome outcome = createOperationOutcome(jobId.toString(), e);

                    return ServerResponse.status(status)
                            .contentType(MEDIA_TYPE_FHIR_JSON)
                            .bodyValue(fhirContext.newJsonParser().encodeResourceToString(outcome));
                });
    }

    public Mono<ServerResponse> checkStatus(ServerRequest request) {
        var jobId = request.pathVariable("jobId");
        Job job;
        try {
            job = persistence.loadJob(UUID.fromString(jobId));
        } catch (IOException e) {
            return notFound().build();
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
            //persistence to Result JSON
            case PENDING, RUNNING_PROCESS_BATCH, RUNNING_PROCESS_CORE, RUNNING_CREATE_BATCHES ->
                    accepted().contentType(MEDIA_TYPE_FHIR_JSON).bodyValue(issuesToJson(job));
            //add persistence Operation Outcome
            case FAILED ->
                    ServerResponse.status(500).contentType(MEDIA_TYPE_FHIR_JSON).bodyValue(OperationOutcomeCreator.fromJob(job));
            //persistence to Error Operation Outcome
            case CANCELLED ->
                    ServerResponse.status(410).contentType(MEDIA_TYPE_FHIR_JSON).bodyValue(OperationOutcomeCreator.fromJob(job));
            //persistence to Cancelled Operation Outcome
            default -> notFound().build();
        };
    }

    private String issuesToJson(Job job) {
        return fhirContext.newJsonParser().encodeResourceToString(
                OperationOutcomeCreator.fromJob(job));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Job Status
    // ─────────────────────────────────────────────────────────────────────────────

    public JsonNode loadCompletedJobJSON(Job job,
                                         String requestUrl,
                                         String transactionTime, String fileServerName) {
        ObjectNode root = mapper.createObjectNode();
        root.put("transactionTime", transactionTime);
        root.put("request", requestUrl);
        root.put("requiresAccessToken", false);

        ArrayNode outputArr = mapper.createArrayNode();
        ArrayNode errorArr = mapper.createArrayNode();

        // Job already contains the filenames — just generate URLs
        job.batches().keySet().forEach(f ->
                outputArr.add(mapper.createObjectNode()
                        .put("url", fileServerName + "/" + job.id() + "/" + f + ".ndjson")
                        .put("type", "NDJSON Bundle"))
        );

        errorArr.add(mapper.createObjectNode()
                .put("url", fileServerName + "/" + job.id() + "/outcome.json")
                .put("type", "OperationOutcome"));
        root.set("output", outputArr);
        root.set("error", errorArr);
        return root;
    }
}
