package de.medizininformatikinitiative.torch.rest;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.config.TorchProperties;
import de.medizininformatikinitiative.torch.service.ExtractDataService;
import de.medizininformatikinitiative.torch.util.CrtdlParser;
import de.medizininformatikinitiative.torch.util.ResultFileManager;
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

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static de.medizininformatikinitiative.torch.management.OperationOutcomeCreator.createOperationOutcome;
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
    private final CrtdlParser crtdlParser;
    private final ExtractDataService extractDataService;
    private final String baseUrl;

    private Mono<ServerResponse> getGlobalStatus(ServerRequest serverRequest) {
        return Mono.fromCallable(() -> resultFileManager.jobStatusMap)
                .flatMap(statusMap -> {
                    // Return empty JSON if map is null or empty
                    if (statusMap == null || statusMap.isEmpty()) {
                        return ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Collections.emptyMap());
                    }

                    // Convert map values to "200 OK" style strings
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


    @Autowired
    public FhirController(FhirContext fhirContext, ResultFileManager resultFileManager,
                          CrtdlParser parser, ExtractDataService extractDataService, TorchProperties torchProperties) {
        this.fhirContext = fhirContext;
        this.resultFileManager = resultFileManager;
        this.crtdlParser = parser;
        this.extractDataService = extractDataService;
        this.baseUrl = torchProperties.base().url();
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
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Empty request body")))
                .flatMap(crtdlParser::parseCrtdl) // returns your decoded object
                .flatMap(decoded -> extractDataService.startJob(decoded.crtdl(), decoded.patientIds(), jobId)
                        .then(ServerResponse.accepted()
                                .header("Content-Location", baseUrl + "/fhir/__status/" + jobId)
                                .build()
                        ))
                .onErrorResume(IllegalArgumentException.class, e -> {
                    logger.warn("Bad request: {}", e.getMessage());
                    OperationOutcome outcome = createOperationOutcome(jobId, e);

                    return ServerResponse.badRequest()
                            .contentType(MEDIA_TYPE_FHIR_JSON)
                            .bodyValue(fhirContext.newJsonParser().encodeResourceToString(outcome))
                            .delayUntil(r ->
                                    resultFileManager.saveErrorToJSON(jobId, outcome, HttpStatus.BAD_REQUEST)
                                            .onErrorResume(writeError -> {
                                                logger.error("Failed to save error file: {}", writeError.getMessage(), writeError);
                                                return Mono.empty();
                                            })
                            );
                })
                .onErrorResume(Exception.class, e -> {
                    logger.error("Unexpected error: {}", e.getMessage(), e);
                    OperationOutcome outcome = createOperationOutcome(jobId, e);

                    return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .contentType(MEDIA_TYPE_FHIR_JSON)
                            .bodyValue(fhirContext.newJsonParser().encodeResourceToString(outcome))
                            .delayUntil(r ->
                                    resultFileManager.saveErrorToJSON(jobId, outcome, HttpStatus.INTERNAL_SERVER_ERROR)
                                            .onErrorResume(writeError -> {
                                                logger.error("Failed to save error file: {}", writeError.getMessage(), writeError);
                                                return Mono.empty();
                                            })
                            );
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
                // Capture the full request URL and transaction time
                String transactionTime = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
                return Mono.fromCallable(() -> resultFileManager.loadBundleFromFileSystem(jobId, transactionTime))
                        .flatMap(bundleMap -> {
                            if (bundleMap == null) {
                                return ServerResponse.notFound().build();
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
                        .flatMap(error -> {
                            if (error != null) {
                                return ServerResponse.status(status).bodyValue(error);
                            } else {
                                return ServerResponse.status(HttpStatus.NOT_FOUND)
                                        .bodyValue("Error file not found for job: " + jobId);
                            }
                        })
                        .onErrorResume(e -> {
                            logger.error("Failed to load error for job {}: {}", jobId, e.getMessage(), e);
                            return ServerResponse.status(HttpStatus.NOT_FOUND)
                                    .bodyValue("Error file could not be read: " + e.getMessage());
                        });
            }
        }
    }
}
