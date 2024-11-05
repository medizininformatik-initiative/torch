package de.medizininformatikinitiative.torch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.BundleCreator;
import de.medizininformatikinitiative.torch.ResourceTransformer;
import de.medizininformatikinitiative.torch.cql.CqlClient;
import de.medizininformatikinitiative.torch.model.PatientBatch;
import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;
import de.medizininformatikinitiative.torch.util.ResultFileManager;
import de.numcodex.sq2cql.Translator;
import de.numcodex.sq2cql.model.structured_query.StructuredQuery;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CrtdlProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(CrtdlProcessingService.class);

    private final ResourceTransformer transformer;
    private final BundleCreator bundleCreator;
    private final ResultFileManager resultFileManager;
    private final int batchSize;
    private final int maxConcurrency;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final CqlClient cqlClient;
    private final Boolean useCql;
    private final Translator cqlQueryTranslator;

    public CrtdlProcessingService(@Qualifier("flareClient") WebClient webClient,
                                  Translator cqlQueryTranslator,
                                  CqlClient cqlClient,
                                  ResultFileManager resultFileManager,
                                  ResourceTransformer transformer,
                                  BundleCreator bundleCreator,
                                  @Value("${torch.batchsize:10}") int batchsize,
                                  @Value("5") int maxConcurrency,
                                  @Value("${torch.useCql}") boolean useCql) {
        this.webClient = webClient;
        this.transformer = transformer;
        this.bundleCreator = bundleCreator;
        this.objectMapper = new ObjectMapper();
        this.resultFileManager = resultFileManager;
        this.batchSize = batchsize;
        this.maxConcurrency = maxConcurrency;
        this.cqlClient = cqlClient;
        this.useCql = useCql;
        this.cqlQueryTranslator = cqlQueryTranslator;
    }


    public Mono<Void> processCrtdl(Crtdl crtdl, String jobId) {
        return fetchPatientList(crtdl)
                .flatMap(patientList -> {
                    if (patientList.isEmpty()) {
                        resultFileManager.setStatus(jobId, "Failed at collectResources for batch: No patients found.");
                        return Mono.empty();
                    }
                    return Flux.fromIterable(patientList.split(batchSize))
                            .flatMap(batch -> processBatch(crtdl, batch, jobId), maxConcurrency)
                            .then();
                });
    }

    Mono<Void> processBatch(Crtdl crtdl, PatientBatch batch, String jobId) {
        logger.info("Processing batch {}", batch);
        return transformer.collectResourcesByPatientReference(crtdl, batch)
                .doOnNext(resourceMap -> logger.debug("Collected resources: {}", resourceMap))
                .onErrorResume(error -> {
                    handleBatchError(jobId, error);
                    logger.error("Error in collectResourcesByPatientReference: {}", error.getMessage());
                    return Mono.empty();
                })
                .filter(resourceMap -> {
                    boolean isNotEmpty = !resourceMap.isEmpty();
                    logger.debug("Resource map is empty: {}", !isNotEmpty);
                    return isNotEmpty;
                })
                .flatMap(resourceMap -> saveResourcesAsBundles(jobId, resourceMap)
                        .doOnSuccess(unused -> logger.info("Successfully saved resources for jobId: {}", jobId))
                        .doOnError(error -> logger.error("Error in saveResourcesAsBundles: {}", error.getMessage()))
                );
    }


    Mono<Void> saveResourcesAsBundles(String jobId, Map<String, Collection<Resource>> resourceMap) {
        Map<String, Bundle> bundles = bundleCreator.createBundles(resourceMap);
        UUID batchId = UUID.randomUUID();

        return Flux.fromIterable(bundles.values())
                .flatMap(bundle -> resultFileManager.saveBundleToNDJSON(jobId, batchId.toString(), bundle)
                        .subscribeOn(Schedulers.boundedElastic())
                        .doOnSuccess(unused -> logger.debug("Bundle appended: {}", batchId)), maxConcurrency)
                .then();
    }

    private void handleBatchError(String jobId, Throwable error) {
        resultFileManager.setStatus(jobId, "Failed at collectResources for batch: " + error.getMessage());
        logger.error("Error in collectResourcesByPatientReference for batch: {}", error.getMessage());
    }


    public Mono<PatientBatch> fetchPatientList(Crtdl crtdl) {
        try {
            return (useCql) ? fetchPatientListUsingCql(crtdl) : fetchPatientListFromFlare(crtdl);
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
    }

    public Mono<PatientBatch> fetchPatientListFromFlare(Crtdl crtdl) {
        return webClient.post()
                .uri("/query/execute-cohort")
                .contentType(MediaType.parseMediaType("application/sq+json"))
                .bodyValue(crtdl.cohortDefinition().toString())
                .retrieve()
                .onStatus(status -> status.value() >= 400, ClientResponse::createException)
                .bodyToMono(String.class)
                .publishOn(Schedulers.boundedElastic())
                .flatMap(response -> {
                    try {
                        List<String> list = objectMapper.readValue(response, new TypeReference<>() {
                        });
                        logger.debug("Got  {} patient IDs", list.size());
                        return Mono.just(PatientBatch.of(list));
                    } catch (JsonProcessingException e) {
                        logger.error("Error parsing response: {}", e.getMessage());
                        return Mono.error(new RuntimeException("Error parsing response", e));
                    }
                })
                .doOnSubscribe(subscription -> logger.debug("Fetching patient list from Flare"))
                .doOnError(e -> logger.error("Error fetching patient list from Flare: {}", e.getMessage()));
    }

    public Mono<PatientBatch> fetchPatientListUsingCql(Crtdl crtdl) throws JsonProcessingException {
        StructuredQuery ccdl = objectMapper.treeToValue(crtdl.cohortDefinition(), StructuredQuery.class);
        return this.cqlClient.getPatientListByCql(cqlQueryTranslator.toCql(ccdl).print()).map(PatientBatch::of);
    }


}
