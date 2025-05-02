package de.medizininformatikinitiative.torch.rest;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.exceptions.ValidationException;
import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;
import de.medizininformatikinitiative.torch.service.CrtdlProcessingService;
import de.medizininformatikinitiative.torch.service.CrtdlValidatorService;
import de.medizininformatikinitiative.torch.util.ResultFileManager;
import org.hl7.fhir.r4.model.Base64BinaryType;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
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
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static de.medizininformatikinitiative.torch.management.OperationOutcomeCreator.createOperationOutcome;
import static org.springframework.web.reactive.function.server.RequestPredicates.*;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.accepted;
import static org.springframework.web.reactive.function.server.ServerResponse.notFound;

@RestController
public class FhirController {

    private static final MediaType MEDIA_TYPE_FHIR_JSON = MediaType.valueOf("application/fhir+json");
    private static final Logger logger = LoggerFactory.getLogger(FhirController.class);
    private final ObjectMapper objectMapper;
    private final FhirContext fhirContext;
    private final ResultFileManager resultFileManager;
    private final CrtdlProcessingService crtdlProcessingService;
    private final CrtdlValidatorService validatorService;

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

    private record DecodedContent(byte[] crtdl, List<String> patientIds) {
    }

    @Autowired
    public FhirController(
            ResultFileManager resultFileManager,
            FhirContext fhirContext,
            CrtdlProcessingService crtdlProcessingService, ObjectMapper objectMapper, CrtdlValidatorService validatorService) {

        this.objectMapper = objectMapper;
        this.fhirContext = fhirContext;
        this.resultFileManager = resultFileManager;

        this.crtdlProcessingService = crtdlProcessingService;
        this.validatorService = validatorService;
    }

    private static DecodedContent decodeCrtdlContent(Parameters parameters) {
        byte[] crtdlContent = null;
        List<String> patientIds = new ArrayList<>();

        for (var parameter : parameters.getParameter()) {
            if (parameter.hasValue()) {
                var value = parameter.getValue();
                if ("crtdl".equals(parameter.getName()) && value.hasType("base64Binary")) {
                    logger.debug("Found crtdl content for parameter '{}'", parameter.getName());
                    crtdlContent = ((Base64BinaryType) parameter.getValue()).getValue();
                }
                if ("patient".equals(parameter.getName()) && value.hasType("string")) {
                    logger.debug("Found Patient content for parameter '{}' {}", parameter.getName(), parameter.getValue());
                    patientIds.add(value.primitiveValue());
                }
            }
        }

        if (crtdlContent == null) {
            throw new IllegalArgumentException("No base64 encoded CRDTL content found in Parameters resource");
        }

        return new DecodedContent(crtdlContent, patientIds);
    }

    @Bean
    public RouterFunction<ServerResponse> queryRouter() {
        logger.info("Init FhirController Router");
        return route(POST("/fhir/$extract-data").and(accept(MEDIA_TYPE_FHIR_JSON)), this::handleExtractData)
                .andRoute(GET("/fhir/__status/{jobId}"), this::checkStatus).andRoute(GET("/fhir/__status/"), this::getGlobalStatus);
    }

    private record DecodedCRTDLContent(Crtdl crtdl, List<String> patientIds) {
    }


    private Mono<DecodedCRTDLContent> parseCrtdl(String body) {

        var parameters = fhirContext.newJsonParser().parseResource(Parameters.class, body);
        if (parameters.isEmpty()) {
            logger.debug("Empty Parameters");
            throw new IllegalArgumentException("Empty Parameters");
        }

        try {
            var decodedContent = decodeCrtdlContent(parameters);
            byte[] crtdlBytes = decodedContent.crtdl();
            List<String> patientIds = decodedContent.patientIds();

            // Process crtdl content, potentially using patientIds
            return Mono.just(new DecodedCRTDLContent(parseCrtdlContent(crtdlBytes), patientIds));
        } catch (IOException e) {
            return Mono.error(e);
        }
    }

    public Mono<ServerResponse> handleExtractData(ServerRequest request) {
        String jobId = UUID.randomUUID().toString();
        return request.bodyToMono(String.class)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Empty request body")))
                .flatMap(this::parseCrtdl)
                .flatMap(decoded -> {

                    logger.info("Create data extraction job id: {}", jobId);
                    resultFileManager.setStatus(jobId, HttpStatus.ACCEPTED);

                    // Kick off background processing (fire-and-forget, safely)
                    Mono.defer(() ->
                                    resultFileManager.initJobDir(jobId)
                                            .then(Mono.fromCallable(() -> validatorService.validate(decoded.crtdl))
                                                    .subscribeOn(Schedulers.boundedElastic()))
                                            .flatMap(validated -> crtdlProcessingService.process(validated, jobId, decoded.patientIds))
                                            .doOnSuccess(v -> resultFileManager.setStatus(jobId, HttpStatus.OK))
                                            .doOnError(e -> {
                                                logger.error("Job {} failed: {}", jobId, e.getMessage(), e);

                                                HttpStatus status;
                                                if (e instanceof IllegalArgumentException || e instanceof ValidationException) {
                                                    status = HttpStatus.BAD_REQUEST;
                                                } else {
                                                    status = HttpStatus.INTERNAL_SERVER_ERROR;
                                                }
                                                OperationOutcome outcome = createOperationOutcome(jobId, e);

                                                resultFileManager.saveErrorToJSON(jobId, outcome, status)
                                                        .subscribeOn(Schedulers.boundedElastic())
                                                        .subscribe(); // fine here as it's clearly a background side effect
                                            })
                                            .onErrorResume(e -> Mono.empty())
                            )
                            .subscribeOn(Schedulers.boundedElastic()).subscribe(); // final fire-and-forget

                    return accepted()
                            .header("Content-Location", "/fhir/__status/" + jobId)
                            .build();
                })
                .onErrorResume(IllegalArgumentException.class, e -> {
                    logger.warn("Bad request: {}", e.getMessage());
                    OperationOutcome outcome = createOperationOutcome(jobId, e);

                    // Prepare the response immediately
                    Mono<ServerResponse> response = ServerResponse.badRequest()
                            .contentType(MEDIA_TYPE_FHIR_JSON)
                            .bodyValue(fhirContext.newJsonParser().encodeResourceToString(outcome));

                    // Save the error in the background but don't block the response
                    return response.delayUntil(r ->
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

                    // Prepare the response immediately
                    Mono<ServerResponse> response = ServerResponse.status(500)
                            .contentType(MEDIA_TYPE_FHIR_JSON)
                            .bodyValue(fhirContext.newJsonParser().encodeResourceToString(outcome));

                    // Save the error in the background but don't block the response
                    return response.delayUntil(r ->
                            resultFileManager.saveErrorToJSON(jobId, outcome, HttpStatus.INTERNAL_SERVER_ERROR)
                                    .onErrorResume(writeError -> {
                                        logger.error("Failed to save error file: {}", writeError.getMessage(), writeError);
                                        return Mono.empty();
                                    })
                    );
                });
    }


    private Crtdl parseCrtdlContent(byte[] content) throws IOException {
        return objectMapper.readValue(content, Crtdl.class);
    }


    public Mono<ServerResponse> checkStatus(ServerRequest request) {
        String transactionTime = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        var jobId = request.pathVariable("jobId");
        logger.debug("Job Requested {}", jobId);
        logger.debug("Size of Map {} {}", resultFileManager.getSize(), resultFileManager.jobStatusMap.entrySet());


        HttpStatus status = resultFileManager.getStatus(jobId);
        logger.debug("Status of jobID {} var {}", jobId, resultFileManager.jobStatusMap.get(jobId));

        if (status == null) {
            return notFound().build();
        }
        switch (status) {
            case HttpStatus.OK -> {
                // Capture the full request URL and transaction time
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
