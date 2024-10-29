package de.medizininformatikinitiative.torch.rest;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.BundleCreator;
import de.medizininformatikinitiative.torch.ResourceTransformer;
import de.medizininformatikinitiative.torch.cql.CqlClient;
import de.medizininformatikinitiative.torch.model.Crtdl;
import de.medizininformatikinitiative.torch.util.ResultFileManager;
import de.numcodex.sq2cql.Translator;
import de.numcodex.sq2cql.model.structured_query.StructuredQuery;
import org.hl7.fhir.r4.model.Base64BinaryType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import static de.medizininformatikinitiative.torch.util.BatchUtils.splitListIntoBatches;
import static org.springframework.web.reactive.function.server.RequestPredicates.*;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.*;

@RestController
public class FhirController {

    private static final MediaType MEDIA_TYPE_FHIR_JSON = MediaType.valueOf("application/fhir+json");
    private static final Logger logger = LoggerFactory.getLogger(FhirController.class);
    private final WebClient webClient;
    private final ResourceTransformer transformer;
    private final BundleCreator bundleCreator;
    private final ObjectMapper objectMapper;
    private final FhirContext fhirContext;
    private final ResultFileManager resultFileManager;
    private final ExecutorService executorService;
    private final int batchSize;
    private final int maxConcurrency;
    private final CqlClient cqlClient;
    private final Translator cqlQueryTranslator;
    private final boolean useCql;


    @Autowired
    public FhirController(
            @Qualifier("flareClient") WebClient webClient,
            Translator cqlQueryTranslator,
            CqlClient cqlClient,
            ResultFileManager resultFileManager,
            ResourceTransformer transformer,
            BundleCreator bundleCreator,
            FhirContext fhirContext,
            ExecutorService executorService,
            @Value("${torch.batchsize:10}") int batchsize,
            @Value("${torch.maxConcurrency:5}") int maxConcurrency,
            @Value("${torch.useCql}") boolean useCql) {
        this.webClient = webClient;
        this.transformer = transformer;
        this.bundleCreator = bundleCreator;
        this.objectMapper = new ObjectMapper();
        this.fhirContext = fhirContext;
        this.resultFileManager = resultFileManager;
        this.executorService = executorService;
        this.batchSize = batchsize;
        this.maxConcurrency = maxConcurrency;
        this.cqlClient = cqlClient;
        this.useCql = useCql;
        this.cqlQueryTranslator = cqlQueryTranslator;
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

        // Decode and parse CRTDL content
        try {
            return Mono.just(parseCrtdlContent(decodeCrtdlContent(parameters)));
        } catch (IOException e) {
            // TODO: improve error handling
            return Mono.error(e);
        }
    }

    public Mono<ServerResponse> handleExtractData(ServerRequest request) {
        // Read the body asynchronously and process it
        return request.bodyToMono(String.class)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Empty request body")))
                .flatMap(this::parseCrtdl)
                .flatMap(crtdl -> {

                    // Generate jobId based on the request body hashCode
                    var jobId = UUID.randomUUID().toString();
                    logger.info("Create data extraction job id: {}", jobId);
                    resultFileManager.setStatus(jobId, "Processing");

                    // Initialize the job directory asynchronously
                    return resultFileManager.initJobDir(jobId)
                            .doOnNext((jobDir) -> {
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

    private Mono<Void> processCrtdl(Crtdl crtdl, String jobId) {
        return fetchPatientList(crtdl)
                .flatMapMany(patientList -> {
                    if (patientList.isEmpty()) {
                        resultFileManager.setStatus(jobId, "Failed at collectResources for batch: ");
                    }
                    // Split the patient list into batches
                    List<List<String>> batches = splitListIntoBatches(patientList, batchSize);
                    return Flux.fromIterable(batches);
                })
                .flatMap(batch -> {
                    // Log the batch being processed
                    return transformer.collectResourcesByPatientReference(crtdl, batch)
                            .onErrorResume(e -> {
                                resultFileManager.setStatus(jobId, "Failed at collectResources for batch: " + e.getMessage());
                                logger.error("Error in collectResourcesByPatientReference for batch: {}", e.getMessage());
                                return Mono.empty();
                            })
                            .filter(resourceMap -> {
                                logger.debug("Resource Map empty {}", resourceMap.isEmpty());
                                return !resourceMap.isEmpty();
                            }) // Filter out empty maps
                            .flatMap(resourceMap -> {
                                Map<String, Bundle> bundles = bundleCreator.createBundles(resourceMap);
                                logger.debug("Bundles Size {}", bundles.size());
                                UUID uuid = UUID.randomUUID();

                                // Save each serialized bundle (as an individual line in NDJSON) to the file system
                                return Flux.fromIterable(bundles.values())
                                        .flatMap(bundle -> resultFileManager.saveBundleToNDJSON(jobId, uuid.toString(), bundle)
                                                        .subscribeOn(Schedulers.boundedElastic())  // Offload the I/O operation
                                                        .doOnSuccess(unused -> logger.debug("Bundle appended: {}", uuid)),
                                                maxConcurrency  // Control the number of concurrent save operations
                                        )
                                        .then(); // Ensure Mono completion for each batch
                            });
                }, maxConcurrency)  // Control the number of concurrent batches processed
                .doOnError(error -> {
                    resultFileManager.setStatus(jobId, "Failed: " + error.getMessage());
                    logger.error("Error processing CRTDL for jobId: {}: {}", jobId, error.getMessage());
                })
                .then();  // This returns Mono<Void> indicating completion
    }

    public Mono<List<String>> fetchPatientList(Crtdl crtdl) {

        try {
            return (useCql) ? fetchPatientListUsingCql(crtdl) : fetchPatientListFromFlare(crtdl);
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
    }

    public Mono<List<String>> fetchPatientListFromFlare(Crtdl crtdl) {
        return webClient.post()
                .uri("/query/execute-cohort")
                .contentType(MediaType.parseMediaType("application/sq+json"))
                .bodyValue(crtdl.cohortDefinition().toString())
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

    public Mono<List<String>> fetchPatientListUsingCql(Crtdl crtdl) throws JsonProcessingException {
        StructuredQuery ccdl = objectMapper.treeToValue(crtdl.cohortDefinition(), StructuredQuery.class);
        return this.cqlClient.getPatientListByCql(cqlQueryTranslator.toCql(ccdl).print());
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
            String requestUrl = request.uri().toString();
            String transactionTime = DateTimeFormatter.ISO_INSTANT.format(Instant.now());

            return Mono.fromCallable(() -> resultFileManager.loadBundleFromFileSystem(jobId, requestUrl, transactionTime))
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
