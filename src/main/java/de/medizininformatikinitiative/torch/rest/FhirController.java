package de.medizininformatikinitiative.torch.rest;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.exceptions.ValidationException;
import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;
import de.medizininformatikinitiative.torch.service.CrtdlProcessingService;
import de.medizininformatikinitiative.torch.service.CrtdlValidatorService;
import de.medizininformatikinitiative.torch.util.ResultFileManager;
import org.hl7.fhir.r4.model.Base64BinaryType;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.*;

@RestController
public class FhirController {

    private static final MediaType MEDIA_TYPE_FHIR_JSON = MediaType.valueOf("application/fhir+json");
    private static final Logger logger = LoggerFactory.getLogger(FhirController.class);
    private final ObjectMapper objectMapper;
    private final FhirContext fhirContext;
    private final ResultFileManager resultFileManager;
    private final ExecutorService executorService;
    private final CrtdlProcessingService crtdlProcessingService;
    private final CrtdlValidatorService validatorService;

    private static record DecodedContent(byte[] crtdl, List<String> patientIds) {
    }

    private static record DecodedCRTDLContent(Crtdl crtdl, List<String> patientIds) {
    }

    @Autowired
    public FhirController(
            ResultFileManager resultFileManager,
            FhirContext fhirContext,
            ExecutorService executorService, CrtdlProcessingService crtdlProcessingService, ObjectMapper objectMapper, CrtdlValidatorService validatorService) {

        this.objectMapper = objectMapper;
        this.fhirContext = fhirContext;
        this.resultFileManager = resultFileManager;
        this.executorService = executorService;
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
            }
            if (parameter.hasPart()) {
                if ("patients".equals(parameter.getName())) {
                    parameter.getPart().forEach(
                            part -> {
                                patientIds.add(part.getName());
                            }
                    );
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
                .andRoute(GET("/fhir/__status/{jobId}"), this::checkStatus);
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
        return request.bodyToMono(String.class)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Empty request body")))
                .flatMap(this::parseCrtdl)
                .flatMap(decodedCrtdlcontent ->
                        Mono.fromCallable(() -> validatorService.validate(decodedCrtdlcontent.crtdl))
                                .flatMap(crtdl -> {
                                    List<String> patientList = decodedCrtdlcontent.patientIds;

                                    // Generate jobId based on the request body hashCode
                                    var jobId = UUID.randomUUID().toString();
                                    logger.info("Create data extraction job id: {}", jobId);
                                    resultFileManager.setStatus(jobId, "Processing");

                                    return resultFileManager.initJobDir(jobId)
                                            .flatMap(jobDir ->
                                                    crtdlProcessingService.process(crtdl, jobId, patientList)
                                                            .doOnSuccess(v -> resultFileManager.setStatus(jobId, "Completed"))
                                                            .doOnError(e -> {
                                                                logger.error("Error processing CRTDL for jobId: {}", jobId, e);
                                                                resultFileManager.setStatus(jobId, "Failed: " + e.getMessage());
                                                            })
                                                            .subscribeOn(Schedulers.boundedElastic()) // Run async, but remain reactive
                                                            .thenReturn(jobId)
                                            );
                                })
                )
                .flatMap(jobId -> accepted()
                        .header("Content-Location", "/fhir/__status/" + jobId)
                        .build()
                )
                .onErrorResume(ValidationException.class, e -> {
                    logger.warn("Invalid CRTDL: {}", e.getMessage());
                    return badRequest()
                            .contentType(MEDIA_TYPE_FHIR_JSON)
                            .bodyValue(new Error(e.getMessage()));
                })
                .onErrorResume(IllegalArgumentException.class, e -> {
                    logger.warn("Bad request: {}", e.getMessage());
                    return badRequest()
                            .contentType(MEDIA_TYPE_FHIR_JSON)
                            .bodyValue(new Error(e.getMessage()));
                })
                .onErrorResume(Exception.class, e -> {
                    logger.error("Unexpected error: {}", e.getMessage());
                    return status(500)
                            .contentType(MEDIA_TYPE_FHIR_JSON)
                            .bodyValue(new Error(e.getMessage()));
                });
    }


    private Crtdl parseCrtdlContent(byte[] content) throws IOException {
        return objectMapper.readValue(content, Crtdl.class);
    }


    public Mono<ServerResponse> checkStatus(ServerRequest request) {

        var jobId = request.pathVariable("jobId");
        logger.debug("Job Requested {}", jobId);
        logger.debug("Size of Map {} {}", resultFileManager.getSize(), resultFileManager.jobStatusMap.entrySet());


        String status = resultFileManager.getStatus(jobId);
        logger.debug("Status of jobID {} var {}", jobId, resultFileManager.jobStatusMap.get(jobId));

        if (status == null) {
            return notFound().build();
        }
        if ("Completed".equals(status)) {
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
        if (status.contains("Failed")) {
            return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).bodyValue(status);
        }
        if (status.contains("Processing")) {
            return accepted().build();
        } else {
            return notFound().build();
        }
    }

}
