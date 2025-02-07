package de.medizininformatikinitiative.torch.rest;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
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

    private static byte[] decodeCrtdlContent(Parameters parameters) {
        for (var parameter : parameters.getParameter()) {
            if ("crtdl".equals(parameter.getName())) {
                var valueElement = parameter.getChildByName("value[x]");
                if (valueElement.hasValues()) {
                    return ((Base64BinaryType) valueElement.getValues().getFirst()).getValue();
                }
            }
        }
        throw new IllegalArgumentException("No base64 encoded CRDTL content found in Parameters resource");
    }

    @Bean
    public RouterFunction<ServerResponse> queryRouter() {
        logger.info("Init FhirController Router");
        return route(POST("/fhir/$extract-data").and(accept(MEDIA_TYPE_FHIR_JSON)), this::handleExtractData)
                .andRoute(GET("/fhir/__status/{jobId}"), this::checkStatus);
    }

    private Mono<Crtdl> parseCrtdl(String body) {
        // Parse the request body into Parameters object
        var parameters = fhirContext.newJsonParser().parseResource(Parameters.class, body);
        if (parameters.isEmpty()) {
            logger.debug("Empty Parameters");
            throw new IllegalArgumentException("Empty Parameters");
        }

        try {
            return Mono.just(parseCrtdlContent(decodeCrtdlContent(parameters)));
        } catch (IOException e) {
            // TODO: improve error handling
            return Mono.error(e);
        }
    }

    public Mono<ServerResponse> handleExtractData(ServerRequest request) {
        return request.bodyToMono(String.class)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Empty request body")))
                .flatMap(this::parseCrtdl)
                .flatMap(crtdl -> Mono.fromCallable(() -> validatorService.validate(crtdl)))
                .flatMap(crtdl -> {

                    // Generate jobId based on the request body hashCode
                    var jobId = UUID.randomUUID().toString();
                    logger.info("Create data extraction job id: {}", jobId);
                    resultFileManager.setStatus(jobId, "Processing");

                    // Initialize the job directory asynchronously
                    return resultFileManager.initJobDir(jobId)
                            .doOnNext(jobDir -> {
                                // Submit the task to the executor service for background processing
                                executorService.submit(() -> {
                                    try {
                                        logger.debug("Processing CRTDL in ExecutorService for jobId: {}", jobId);
                                        crtdlProcessingService.process(crtdl, jobId).block();
                                        resultFileManager.setStatus(jobId, "Completed");
                                    } catch (Exception e) {
                                        logger.error("Error processing CRTDL for jobId: {}", jobId, e);
                                        resultFileManager.setStatus(jobId, "Failed: " + e.getMessage());
                                    }
                                });
                            })
                            .then(accepted()
                                    .header("Content-Location", "/fhir/__status/" + jobId)
                                    .build());
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
