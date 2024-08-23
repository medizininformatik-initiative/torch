package de.medizininformatikinitiative.torch.rest;

import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.flare.model.mapping.MappingException;
import de.medizininformatikinitiative.torch.BundleCreator;
import de.medizininformatikinitiative.torch.ResourceTransformer;
import de.medizininformatikinitiative.torch.model.Crtdl;
import de.medizininformatikinitiative.torch.util.ResultFileManager;
import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Library;
import org.hl7.fhir.r4.model.Measure;
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
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.*;


@RestController
public class FhirController {

    private static final MediaType MEDIA_TYPE_FHIR_JSON = MediaType.valueOf("application/fhir+json");
    private static final MediaType MEDIA_TYPE_CRTDL_JSON = MediaType.valueOf("application/crtdl+json");

    private static final Logger logger = LoggerFactory.getLogger(FhirController.class);
    private final ConcurrentHashMap<String, String> jobStatusMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bundle> jobResultMap = new ConcurrentHashMap<>();

    private final WebClient webClient;
    private final ResourceTransformer transformer;
    private final BundleCreator bundleCreator;
    private final ObjectMapper objectMapper;
    private final IParser parser;
    private final ResultFileManager resultFileManager;


    @Autowired
    public FhirController(
            @Qualifier("flareClient") WebClient webClient,
            ResultFileManager resultFileManager,
            ResourceTransformer transformer,
            BundleCreator bundleCreator,
            ObjectMapper objectMapper,
            IParser parser) {
        this.webClient = webClient;
        this.transformer = transformer;
        this.bundleCreator = bundleCreator;
        this.objectMapper = objectMapper;
        this.parser = parser;
        this.resultFileManager=resultFileManager;
    }

    @Bean
    public RouterFunction<ServerResponse> queryRouter() {
        logger.info("Init FhirController Router");
        return route(POST("/fhir/$extract-data").and(accept(MEDIA_TYPE_FHIR_JSON)), this::handleCrtdlBundle)
                .andRoute(GET("/fhir/__status/{jobId}"), this::checkStatus).andRoute(POST("debug/crtdl").and(accept(MEDIA_TYPE_CRTDL_JSON)), this::handleCrtdl);
    }

    public Mono<ServerResponse> handleCrtdlBundle(ServerRequest request) {
        var jobId = UUID.randomUUID().toString();
        jobStatusMap.put(jobId, "Processing");

        logger.info("Create CRTDL with jobId: {}", jobId);

        logger.debug("Handling CRTDL Bundle");
        return request.bodyToMono(String.class)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Empty request body")))
                .publishOn(Schedulers.boundedElastic()) // Use boundedElastic scheduler
                .flatMap(body -> {
                    Bundle bundle = parser.parseResource(Bundle.class,body);
                    if (bundle.isEmpty() && !isValidBundle(bundle)) {
                        logger.debug("Empty Bundle");
                        return Mono.error(new IllegalArgumentException("Empty bundle"));
                    }
                    try {
                        logger.info("Non Empty Bundle");
                        Library library = extractLibraryFromBundle(bundle);
                        //Measure measure = extractMeasureFromBundle(bundle);
                        Crtdl crtdl = parseCrtdlContent(decodeCrtdlContent(library));
                        logger.debug("Processing CRTDL");
                        return processCrtdl(crtdl, jobId);
                    } catch (Exception e) {
                        logger.debug("Exception handling");
                        return Mono.error(e);
                    }
                })
                .then(accepted().header("Content-Location", String.valueOf(URI.create("/fhir/__status/" + jobId))).build())
                .onErrorResume(MappingException.class, e -> {
                    logger.warn("Mapping error: {}", e.getMessage());
                    jobStatusMap.put(jobId, "Failed: " + e.getMessage());
                    return badRequest().contentType(MEDIA_TYPE_FHIR_JSON).bodyValue(new Error(e.getMessage()));
                })
                .onErrorResume(WebClientRequestException.class, e -> {
                    logger.error("Service not available because of downstream web client errors: {}", e.getMessage());
                    jobStatusMap.put(jobId, "Failed: " + e.getMessage());
                    return status(503).contentType(MEDIA_TYPE_FHIR_JSON).bodyValue(new Error(e.getMessage()));
                })
                .onErrorResume(IllegalArgumentException.class, e -> {
                    logger.warn("Bad request: {}", e.getMessage());
                    jobStatusMap.put(jobId, "Failed: " + e.getMessage());
                    return badRequest().contentType(MEDIA_TYPE_FHIR_JSON).bodyValue(new Error(e.getMessage()));
                })
                .onErrorResume(Exception.class, e -> {
                    logger.error("Unexpected error: {}", e.getMessage());
                    jobStatusMap.put(jobId, "Failed: " + e.getMessage());
                    return status(500).contentType(MEDIA_TYPE_FHIR_JSON).bodyValue(new Error(e.getMessage()));
                });
    }


    public Mono<ServerResponse> handleCrtdl(ServerRequest request) {
        var jobId = UUID.randomUUID().toString();
        jobStatusMap.put(jobId, "Processing");

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
                    jobStatusMap.put(jobId, "Failed: " + e.getMessage());
                    return badRequest().contentType(MEDIA_TYPE_FHIR_JSON).bodyValue(new Error(e.getMessage()));
                })
                .onErrorResume(WebClientRequestException.class, e -> {
                    logger.error("DEBUG Endpoint: Service not available because of downstream web client errors: {}", e.getMessage());
                    jobStatusMap.put(jobId, "Failed: " + e.getMessage());
                    return status(503).contentType(MEDIA_TYPE_FHIR_JSON).bodyValue(new Error(e.getMessage()));
                })
                .onErrorResume(IllegalArgumentException.class, e -> {
                    logger.error("DEBUG Endpoint: Bad request: {}", e.getMessage());
                    jobStatusMap.put(jobId, "Failed: " + e.getMessage());
                    return badRequest().contentType(MEDIA_TYPE_FHIR_JSON).bodyValue(new Error(e.getMessage()));
                })
                .onErrorResume(Exception.class, e -> {
                    logger.error("DEBUG Endpoint: Unexpected error: {}", e.getMessage());
                    jobStatusMap.put(jobId, "Failed: " + e.getMessage());
                    return status(500).contentType(MEDIA_TYPE_FHIR_JSON).bodyValue(new Error(e.getMessage()));
                });
    }


    private boolean isValidBundle(Bundle bundle) {
        boolean hasLibrary = false;
        boolean hasMeasure = false;

        for (BundleEntryComponent entry : bundle.getEntry()) {
            if (entry.getResource() instanceof Library) {
                hasLibrary = true;

            } else if (entry.getResource() instanceof Measure) {
                hasMeasure = true;

            }


        }
        if (hasLibrary && hasMeasure) {
            return true;
        }
        logger.error("No library or measure");
        return false;
    }

    private Library extractLibraryFromBundle(Bundle bundle) {
        for (BundleEntryComponent entry : bundle.getEntry()) {
            if (entry.getResource() instanceof Library) {
                return (Library) entry.getResource();
            }
        }
        throw new IllegalArgumentException("No Library resource found in the Bundle");
    }

    private Measure extractMeasureFromBundle(Bundle bundle) {
        for (BundleEntryComponent entry : bundle.getEntry()) {
            if (entry.getResource() instanceof Measure) {
                return (Measure) entry.getResource();
            }
        }
        throw new IllegalArgumentException("No Measure resource found in the Bundle");
    }

    private byte[] decodeCrtdlContent(Library library) {
        for (Attachment attachment : library.getContent()) {
            if ("application/crtdl+json".equals(attachment.getContentType())) {
                logger.debug(Arrays.toString(attachment.getData()));
                return attachment.getData();
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
                                    jobStatusMap.put(jobId, "Failed at collectResources: " + e.getMessage());
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
                                    resultFileManager.saveBundleToFileSystem(jobId, finalBundle,jobStatusMap);
                                    logger.debug("Bundle {}", parser.setPrettyPrint(true).encodeResourceToString(finalBundle));
                                    return Mono.empty();
                                })
                ).doOnError(error -> {
                    jobStatusMap.put(jobId, "Failed: " + error.getMessage());
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
        var status = jobStatusMap.getOrDefault(jobId, "Unknown Job ID");

        if ("Unknown Job ID".equals(status)) {
            return notFound().build();
        }

        if (status.contains("Failed")) {
            return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).bodyValue(status);
        }

        if ("Completed".equals(status)) {
            return serveBundleFromFileSystem(jobId);
        } else {
            return accepted().build();
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
