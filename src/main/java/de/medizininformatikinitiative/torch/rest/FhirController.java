package de.medizininformatikinitiative.torch.rest;

import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonAppend;
import de.medizininformatikinitiative.flare.model.mapping.MappingException;
import de.medizininformatikinitiative.torch.BundleCreator;
import de.medizininformatikinitiative.torch.ResourceTransformer;
import de.medizininformatikinitiative.torch.model.Crtdl;
import de.medizininformatikinitiative.torch.util.ResultFileManager;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4b.model.TypeConvertor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.*;


@RestController
public class FhirController {

    private static final MediaType MEDIA_TYPE_FHIR_JSON = MediaType.valueOf("application/fhir+json");
    private static final MediaType MEDIA_TYPE_CRTDL_JSON = MediaType.valueOf("application/crtdl+json");

    private static final Logger logger = LoggerFactory.getLogger(FhirController.class);


    private final WebClient webClient;
    private final ResourceTransformer transformer;
    private final BundleCreator bundleCreator;
    private final ObjectMapper objectMapper;
    private final IParser parser;
    private final ResultFileManager resultFileManager;
    private final ExecutorService executorService;

    @Autowired
    public FhirController(
            @Qualifier("flareClient") WebClient webClient,
            ResultFileManager resultFileManager,
            ResourceTransformer transformer,
            BundleCreator bundleCreator,
            ObjectMapper objectMapper,
            IParser parser, ExecutorService executorService) {
        this.webClient = webClient;
        this.transformer = transformer;
        this.bundleCreator = bundleCreator;
        this.objectMapper = objectMapper;
        this.parser = parser;
        this.resultFileManager=resultFileManager;
        this.executorService=executorService;

    }

    @Bean
    public RouterFunction<ServerResponse> queryRouter() {
        logger.info("Init FhirController Router");
        return route(POST("/fhir/$extract-data").and(accept(MEDIA_TYPE_FHIR_JSON)), this::handleCrtdlBundle)
                .andRoute(GET("/fhir/__status/{jobId}"), this::checkStatus).andRoute(POST("debug/crtdl").and(accept(MEDIA_TYPE_CRTDL_JSON)), this::handleCrtdl);
    }

    public Mono<ServerResponse> handleCrtdlBundle(ServerRequest request) {
        var jobId = String.valueOf(request.hashCode());
        resultFileManager.setStatus(jobId,"Processing");

        logger.info("Create CRTDL with jobId: {}", jobId);

        return request.bodyToMono(String.class)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Empty request body")))
                .flatMap(body -> Mono.fromCallable(() -> {
                    Parameters parameters = parser.parseResource(Parameters.class, body);
                    if (parameters.isEmpty()) {
                        logger.debug("Empty Parameters");
                        throw new IllegalArgumentException("Empty Parameters");
                    }
                    Crtdl crtdl = parseCrtdlContent(decodeCrtdlContent(parameters));

                    logger.debug("Parsed CRTDL", crtdl.getSqString());
                    return crtdl;
                }).subscribeOn(Schedulers.boundedElastic())) // Ensure parsing and decoding are non-blocking
                .doOnNext(crtdl -> {
                    // Submit the task to the executor service for background processing
                    executorService.submit(() -> {
                        try {
                            logger.debug("Processing CRTDL in ExecutorService for jobId: {}", jobId);
                            processCrtdl(crtdl, jobId).block(); // Blocking call within the background task
                            resultFileManager.setStatus(jobId, "Completed");
                        } catch (Exception e) {
                            logger.error("Error processing CRTDL for jobId: {}", jobId, e);
                            resultFileManager.setStatus(jobId, "Failed: " + e.getMessage());
                        }
                    });
                })
                .then(
                        accepted()
                        .header("Content-Location", String.valueOf(URI.create("/fhir/__status/" + jobId)))
                        .build())
                .onErrorResume(IllegalArgumentException.class, e -> {
                    logger.warn("Bad request: {}", e.getMessage());
                    resultFileManager.setStatus(jobId, "Failed: " + e.getMessage());
                    return badRequest().contentType(MEDIA_TYPE_FHIR_JSON).bodyValue(new Error(e.getMessage()));
                })
                .onErrorResume(Exception.class, e -> {
                    logger.error("Unexpected error: {}", e.getMessage());
                    resultFileManager.setStatus(jobId, "Failed: " + e.getMessage());
                    return status(500).contentType(MEDIA_TYPE_FHIR_JSON).bodyValue(new Error(e.getMessage()));
                });
    }



    public Mono<ServerResponse> handleCrtdl(ServerRequest request) {
        var jobId = UUID.randomUUID().toString();
        resultFileManager.setStatus(jobId, "Processing");

        logger.info("DEBUG Endpoint: Create CRTDL with jobId: {}", jobId);

        logger.info("DEBUG Endpoint: Handling CRTDL");
        return request.bodyToMono(String.class)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Empty request body")))
                .publishOn(Schedulers.boundedElastic()) // Use boundedElastic scheduler
                .flatMap(crtdlContent -> {
                    try {
                        logger.debug("Non Empty CRTDL");
                        Crtdl crtdl = parseCrtdlContent(crtdlContent.getBytes());
                        logger.debug("DEBUG Endpoint: Processing CRTDL");
                        return processCrtdl(crtdl, jobId);
                    } catch (Exception e) {
                        logger.debug("DEBUG Endpoint: Exception handling");
                        return Mono.error(e);
                    }
                })
                .then(accepted().header("Content-Location", String.valueOf(URI.create("/fhir/__status/" + jobId))).build())
                .onErrorResume(MappingException.class, e -> {
                    logger.error("DEBUG Endpoint: Mapping error: {}", e.getMessage());
                    resultFileManager.setStatus(jobId, "Failed: " + e.getMessage());
                    return badRequest().contentType(MEDIA_TYPE_FHIR_JSON).bodyValue(new Error(e.getMessage()));
                })
                .onErrorResume(WebClientRequestException.class, e -> {
                    logger.error("DEBUG Endpoint: Service not available because of downstream web client errors: {}", e.getMessage());
                    resultFileManager.setStatus(jobId, "Failed: " + e.getMessage());
                    return status(503).contentType(MEDIA_TYPE_FHIR_JSON).bodyValue(new Error(e.getMessage()));
                })
                .onErrorResume(IllegalArgumentException.class, e -> {
                    logger.error("DEBUG Endpoint: Bad request: {}", e.getMessage());
                    resultFileManager.setStatus(jobId, "Failed: " + e.getMessage());
                    return badRequest().contentType(MEDIA_TYPE_FHIR_JSON).bodyValue(new Error(e.getMessage()));
                })
                .onErrorResume(Exception.class, e -> {
                    logger.error("DEBUG Endpoint: Unexpected error: {}", e.getMessage());
                    resultFileManager.setStatus(jobId, "Failed: " + e.getMessage());
                    return status(500).contentType(MEDIA_TYPE_FHIR_JSON).bodyValue(new Error(e.getMessage()));
                });
    }


    private byte[] decodeCrtdlContent(Parameters parameters) {
        for (Parameters.ParametersParameterComponent parameter : parameters.getParameter()) {
            if ("crtdl".equals(parameter.getName())) {

                Property valueElement = parameter.getChildByName("value[x]");

                if (valueElement.hasValues()) {
                    Base64BinaryType value = (Base64BinaryType) valueElement.getValues().getFirst();
                    logger.info(" valueElement has values {}",value);
                    return value.getValue();
                }
            }
        }
        throw new IllegalArgumentException("No base64 encoded CRDTL content found in Library resource");
    }

    private Crtdl parseCrtdlContent(byte[] content) throws IOException {
        // Convert byte array to string for logging and debugging
        String contentString = new String(content, StandardCharsets.UTF_8);

        JsonNode rootNode = objectMapper.readTree(contentString);
        if (rootNode == null || rootNode.isNull()) {
            throw new IOException("Invalid CRTDL");
        }

        // Extract the cohortDefinition object
        JsonNode cohortDefinitionNode = rootNode.path("cohortDefinition");

        // Convert the cohortDefinition object to a JSON string
        String cohortDefinitionJson = objectMapper.writeValueAsString(cohortDefinitionNode);
        Crtdl crtdl = objectMapper.readValue(content, Crtdl.class);
        crtdl.setSqString(cohortDefinitionJson);
        return crtdl;
    }

    private Mono<Void> processCrtdl(Crtdl crtdl, String jobId) {
        return fetchPatientListFromFlare(crtdl)
                .flatMap(patientList ->
                        transformer.collectResourcesByPatientReference(crtdl, patientList, 1)
                                .onErrorResume(e -> {
                                    resultFileManager.setStatus(jobId, "Failed at collectResources: " + e.getMessage());
                                    logger.error("Error in collectResourcesByPatientReference: {}", e.getMessage());
                                    return Mono.empty();
                                })
                                .filter(resourceMap -> resourceMap != null && !resourceMap.isEmpty()) // Filter out null or empty maps
                                .flatMap(resourceMap -> {
                                    logger.debug("Map {}", resourceMap.keySet());
                                    Map<String, Bundle> bundles = bundleCreator.createBundles(resourceMap);
                                    logger.debug("Bundles Size {}", bundles.size());
                                    Bundle finalBundle = new Bundle();
                                    finalBundle.setType(Bundle.BundleType.BATCHRESPONSE);
                                    for (Bundle bundle : bundles.values()) {
                                        Bundle.BundleEntryComponent entryComponent = new Bundle.BundleEntryComponent();
                                        entryComponent.setResource(bundle);
                                        finalBundle.addEntry(entryComponent);
                                    }

                                    // Save the bundle to the file system and ensure the Mono completes properly
                                    return resultFileManager.saveBundleToFileSystem(jobId, finalBundle)
                                            .doOnSuccess(unused -> {
                                                logger.debug("Bundle saved: {}", parser.setPrettyPrint(true).encodeResourceToString(finalBundle));
                                            });
                                })
                )
                .doOnError(error -> {
                    resultFileManager.setStatus(jobId, "Failed: " + error.getMessage());
                    logger.error("Error processing CRTDL for jobId: {}: {}", jobId, error.getMessage());
                })
                .then();  // This will return Mono<Void> indicating completion
    }




    public Mono<List<String>> fetchPatientListFromFlare(Crtdl crtdl) {
        logger.debug("Flare called for the following input {}",crtdl.getSqString());
        return webClient.post()
                .uri("/query/execute-cohort")
                .contentType(MediaType.parseMediaType("application/sq+json"))
                .bodyValue(crtdl.getSqString())
                .retrieve()
                .onStatus(status -> status.value() == 404, clientResponse -> {
                    logger.error("Received 404 Not Found");
                    return clientResponse.createException();
                })
                .bodyToMono(String.class)
                .publishOn(Schedulers.boundedElastic())
                .flatMap(response -> {
                    logger.debug("Response Received: {}", response);
                    try {
                        List<String> list = objectMapper.readValue(response, new TypeReference<>() {
                        });
                        logger.debug("Parsed List: {}", list);
                        return Mono.just(list);
                    } catch (JsonProcessingException e) {
                        logger.error("Error parsing response: {}", e.getMessage());
                        return Mono.error(new RuntimeException("Error parsing response", e));
                    }
                })
                .doOnSubscribe(subscription -> logger.debug("Fetching patient list from Flare"))
                .doOnError(e -> logger.error("Error fetching patient list from Flare: {}", e.getMessage()));
    }


    public Mono<ServerResponse> checkStatus(ServerRequest request) {

        var jobId = request.pathVariable("jobId");
        logger.debug("Job Requested {}",jobId);
        logger.debug("Size of Map {} {}",resultFileManager.getSize(),resultFileManager.jobStatusMap.entrySet());


        String status = resultFileManager.getStatus(jobId);
        logger.debug("Status of jobID {} var {}",jobId,resultFileManager.jobStatusMap.get(jobId));

        if(status==null){
            return notFound().build();
        }
        if ("Completed".equals(status)) {
            return serveBundleFromFileSystem(jobId);
        }if (status.contains("Failed")) {
            return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).bodyValue(status);
        }if(status.contains("Processing")){
            return accepted().build();
        }
        else {
            return notFound().build();
        }
    }

    private Mono<ServerResponse> serveBundleFromFileSystem(String jobId) {
        return Mono.fromCallable(() -> resultFileManager.loadBundleFromFileSystem(jobId))
                .flatMap(bundleJson -> {
                    if (bundleJson == null) {
                        return notFound().build();
                    }
                    return ok().contentType(MEDIA_TYPE_FHIR_JSON).bodyValue(bundleJson);
                });
    }



}
