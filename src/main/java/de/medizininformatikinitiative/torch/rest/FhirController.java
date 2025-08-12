package de.medizininformatikinitiative.torch.rest;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.service.ExtractDataService;
import de.medizininformatikinitiative.torch.util.ResultFileManager;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final ResultFileManager resultFileManager;
    private final ExtractDataParametersParser extractDataParametersParser;
    private final ExtractDataService extractDataService;
    private final String baseUrl;

    @Autowired
    public FhirController(FhirContext fhirContext, ResultFileManager resultFileManager,
                          ExtractDataParametersParser parser, ExtractDataService extractDataService, @Value("${torch.base.url}") String baseUrl) {
        this.fhirContext = requireNonNull(fhirContext);
        this.resultFileManager = requireNonNull(resultFileManager);
        this.extractDataParametersParser = requireNonNull(parser);
        this.extractDataService = requireNonNull(extractDataService);
        this.baseUrl = baseUrl;
    }

    private Mono<ServerResponse> getGlobalStatus(ServerRequest serverRequest) {
        return Mono.fromCallable(resultFileManager::getJobStatusMap)
                .flatMap(statusMap -> {
                    if (statusMap.isEmpty()) {
                        return ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Collections.emptyMap());
                    }
                    Map<String, String> statusTextMap = statusMap.entrySet().stream()
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    entry -> entry.getValue().value() + " " + entry.getValue().getReasonPhrase()
                            ));
                    return ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(statusTextMap);
                });
    }

    @Bean
    public RouterFunction<ServerResponse> queryRouter() {
        logger.info("Init FhirController Router");
        return route(POST("/fhir/$extract-data").and(accept(MEDIA_TYPE_FHIR_JSON)), this::handleExtractData)
                .andRoute(GET("/fhir/__status/{jobId}"), this::checkStatus).andRoute(GET("/fhir/__status/"), this::getGlobalStatus);
    }

    public Mono<ServerResponse> handleExtractData(ServerRequest request) {
        String jobId = UUID.randomUUID().toString();

        return request.bodyToMono(String.class)
                .filter(body -> body != null && !body.isBlank())
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Empty request body")))
                .map(extractDataParametersParser::parseParameters)
                .flatMap(parameters -> {
                    Mono<Void> jobMono = extractDataService
                            .startJob(parameters.crtdl(), parameters.patientIds(), jobId);

                    // Launch it asynchronously
                    jobMono.subscribe();
                    return ServerResponse.accepted()
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

                    OperationOutcome outcome = createOperationOutcome(jobId, e);

                    return ServerResponse.status(status)
                            .contentType(MEDIA_TYPE_FHIR_JSON)
                            .bodyValue(fhirContext.newJsonParser().encodeResourceToString(outcome));
                });
    }

    public Mono<ServerResponse> checkStatus(ServerRequest request) {
        var jobId = request.pathVariable("jobId");
        HttpStatus status = resultFileManager.getStatus(jobId);

        if (status == null) {
            return notFound().build();
        }

        logger.debug("Status of jobID {} is {}", jobId, status);

        switch (status) {
            case HttpStatus.OK -> {
                String transactionTime = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
                return Mono.fromCallable(() -> resultFileManager.loadBundleFromFileSystem(jobId, transactionTime))
                        .flatMap(bundleMap -> {
                            if (bundleMap.isEmpty()) {
                                return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .contentType(MediaType.TEXT_PLAIN)
                                        .bodyValue("Results could not be loaded for job: " + jobId);
                            }
                            return ServerResponse.ok()
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .bodyValue(bundleMap);
                        });
            }
            case HttpStatus.ACCEPTED -> {
                return accepted().build();
            }
            default -> {
                return Mono.fromCallable(() -> resultFileManager.loadErrorFromFileSystem(jobId))
                        .flatMap(error -> ServerResponse.status(status).contentType(MEDIA_TYPE_FHIR_JSON).bodyValue(error))
                        .onErrorResume(e -> {
                            logger.error("Failed to load error for job {}: {}", jobId, e.getMessage(), e);
                            return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                    .contentType(MediaType.TEXT_PLAIN)
                                    .bodyValue("Error file could not be read: " + e.getMessage());
                        });
            }
        }
    }
}
