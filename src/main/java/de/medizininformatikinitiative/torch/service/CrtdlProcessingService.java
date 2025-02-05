package de.medizininformatikinitiative.torch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.BundleCreator;
import de.medizininformatikinitiative.torch.ResourceTransformer;
import de.medizininformatikinitiative.torch.cql.CqlClient;
import de.medizininformatikinitiative.torch.management.AttributeGroupProcessor;
import de.medizininformatikinitiative.torch.model.PatientBatch;
import de.medizininformatikinitiative.torch.model.ProcessedGroups;
import de.medizininformatikinitiative.torch.model.crtdl.AttributeGroup;
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

import java.util.*;

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
    private final AttributeGroupProcessor attributeGroupProcessor;

    public CrtdlProcessingService(@Qualifier("flareClient") WebClient webClient,
                                  Translator cqlQueryTranslator,
                                  CqlClient cqlClient,
                                  ResultFileManager resultFileManager,
                                  ResourceTransformer transformer,
                                  BundleCreator bundleCreator,
                                  AttributeGroupProcessor attributeGroupProcessor, @Value("${torch.batchsize:10}") int batchsize,
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
        this.attributeGroupProcessor = attributeGroupProcessor;
    }


    public Mono<Void> processCrtdl(Crtdl crtdl, String jobId) {
        //Class with resources mapped to their respective attribute groups

        //structure type+resourcenid -> resource
        //record type id
        //reference map : referenceURL -> resourcenID,ElementID
        //write out if no reference needed?
        //Should a patient specific resource  jump out of that context? Technically yes e.g. same person has multiple ids


        ProcessedGroups processedGroups = attributeGroupProcessor.process(crtdl);

        //First Pass
       /* return fetchPatientBatches(crtdl).flatMap(batch -> processBatch(processedGroups.firstPass(), batch, jobId, crtdl.consentKey()), maxConcurrency)
                //second Pass with attribute Groups
                //reference Handling
                //writeOut
                .then();
    */

        return fetchPatientBatches(crtdl).flatMap(batch -> transformer.collectResourcesByPatientReference(processedGroups.firstPass(), batch, crtdl.consentKey()), maxConcurrency)
                .filter(resourceMap -> !resourceMap.isEmpty()).doOnError(error -> logger.error("Error in collectResources: {}", error.getMessage())).then();
    }


    // Get Patient Cohort
    // Check cohort for Consent
    // Fetch First Pass -> by batch and checking consent? Apply must haves
    // Fetch Second Pass outside patient compartment + apply must haves
    // Resolve References
    // Extract

    public Mono<Void> process(Crtdl crtdl, String jobID) {
        ProcessedGroups processedGroups = attributeGroupProcessor.process(crtdl);
        Flux<PatientBatch> batches = fetchPatientBatches(crtdl);
        Mono<Collection<Resource>> secondPassResources = fetchCoreData(processedGroups.secondPass());

        return collectAndMergeResources(batches, processedGroups.firstPass(), secondPassResources, crtdl.consentKey())
                .flatMap(mergedResources -> saveResourcesAsBundles(jobID, mergedResources))
                .doOnError(error -> logger.error("Error saving resources: {}", error.getMessage()))
                .doOnSuccess(unused -> logger.debug("Successfully saved all resources for jobId: {}", jobID))
                .then();
    }


    public Mono<Map<String, Collection<Resource>>> collectAndMergeResources(
            Flux<PatientBatch> batches,
            List<AttributeGroup> firstPassGroups,
            Mono<Collection<Resource>> secondPassResources,
            Optional<String> consentKey) {

        return Mono.zip(
                batches
                        .flatMap(batch -> transformer.collectResourcesByPatientReference(firstPassGroups, batch, consentKey)
                                .filter(resourceMap -> !resourceMap.isEmpty()), maxConcurrency)
                        .collectList(),
                secondPassResources
        ).map(tuple -> {
            List<Map<String, Collection<Resource>>> processedList = tuple.getT1();
            Collection<Resource> secondPass = tuple.getT2();

            // Merge all first pass maps into one
            Map<String, Collection<Resource>> merged = new HashMap<>();
            processedList.forEach(resourceMap ->
                    resourceMap.forEach((key, value) ->
                            merged.merge(key, value, (existing, newValues) -> {
                                existing.addAll(newValues);
                                return existing;
                            })
                    )
            );
            merged.put("core", secondPass);
            return merged;
        });
    }

    private Mono<Collection<Resource>> fetchCoreData(List<AttributeGroup> attributeGroups) {
        return Flux.fromIterable(attributeGroups)
                .flatMap(group -> transformer.fetchAndTransformResources(Optional.empty(), group), maxConcurrency)
                .collectList()
                .map(ArrayList::new); // Ensures it returns Collection<Resource> instead of List<Resource>
    }


    Mono<Void> processBatch(List<AttributeGroup> firstPass, PatientBatch batch, String jobId, Optional<String> consentKey) {
        logger.trace("Processing batch {}", batch);

        return transformer.collectResourcesByPatientReference(firstPass, batch, consentKey)
                .filter(resourceMap -> !resourceMap.isEmpty())
                .flatMap(resourceMap -> saveResourcesAsBundles(jobId, resourceMap))
                .doOnError(error -> logger.error("Error in saveResourcesAsBundles: {}", error.getMessage()))
                .doOnSuccess(unused -> logger.debug("Successfully saved resources for jobId: {}", jobId));
    }


    //escalate
    Mono<Void> saveResourcesAsBundles(String jobId, Map<String, Collection<Resource>> resourceMap) {
        Map<String, Bundle> bundles = bundleCreator.createBundles(resourceMap);
        UUID batchId = UUID.randomUUID();

        return Flux.fromIterable(bundles.values())
                .flatMap(bundle -> resultFileManager.saveBundleToNDJSON(jobId, batchId.toString(), bundle)
                        .subscribeOn(Schedulers.boundedElastic())
                        .doOnSuccess(unused -> logger.trace("Bundle appended: {}", batchId)), maxConcurrency)
                .then();
    }

    private void handleBatchError(String jobId, Throwable error) {
        resultFileManager.setStatus(jobId, "Failed at collectResources for batch: " + error.getMessage());
        logger.error("Error in collectResourcesByPatientReference for batch: {}", error.getMessage());
    }


    public Flux<PatientBatch> fetchPatientBatches(Crtdl crtdl) {
        try {
            return (useCql) ? fetchPatientListUsingCql(crtdl) : fetchPatientListFromFlare(crtdl);
        } catch (JsonProcessingException e) {
            return Flux.error(e);
        }
    }

    public Flux<PatientBatch> fetchPatientListFromFlare(Crtdl crtdl) {
        return webClient.post()
                .uri("/query/execute-cohort")
                .contentType(MediaType.parseMediaType("application/sq+json"))
                .bodyValue(crtdl.cohortDefinition().toString())
                .retrieve()
                .onStatus(status -> status.value() >= 400, ClientResponse::createException)
                .bodyToFlux(String.class)
                .publishOn(Schedulers.boundedElastic())
                .flatMap(response -> {
                    try {
                        List<String> list = objectMapper.readValue(response, new TypeReference<>() {
                        });
                        logger.debug("Got  {} patient IDs", list.size());
                        return Flux.fromIterable(PatientBatch.of(list).split(batchSize));
                    } catch (JsonProcessingException e) {
                        logger.error("Error parsing response: {}", e.getMessage());
                        return Flux.error(new RuntimeException("Error parsing response", e));
                    }
                })
                .doOnSubscribe(subscription -> logger.debug("Fetching patient list from Flare"))
                .doOnError(e -> logger.error("Error fetching patient list from Flare: {}", e.getMessage()));
    }

    public Flux<PatientBatch> fetchPatientListUsingCql(Crtdl crtdl) throws JsonProcessingException {
        StructuredQuery ccdl = objectMapper.treeToValue(crtdl.cohortDefinition(), StructuredQuery.class);
        return cqlClient.fetchPatientIds(cqlQueryTranslator.toCql(ccdl).print())
                .window(batchSize)
                .flatMap(Flux::collectList)
                .map(PatientBatch::of);
    }


}
