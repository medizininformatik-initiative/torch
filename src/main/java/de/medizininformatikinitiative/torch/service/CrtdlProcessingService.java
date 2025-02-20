package de.medizininformatikinitiative.torch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.DirectResourceLoader;
import de.medizininformatikinitiative.torch.cql.CqlClient;
import de.medizininformatikinitiative.torch.management.ProcessedGroupFactory;
import de.medizininformatikinitiative.torch.model.GroupsToProcess;
import de.medizininformatikinitiative.torch.model.PatientBatch;
import de.medizininformatikinitiative.torch.model.ResourceBundle;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedCrtdl;
import de.medizininformatikinitiative.torch.util.ResultFileManager;
import de.numcodex.sq2cql.Translator;
import de.numcodex.sq2cql.model.structured_query.StructuredQuery;
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

import java.util.List;

@Service
public class CrtdlProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(CrtdlProcessingService.class);

    private final DirectResourceLoader directLoader;
    private final ResultFileManager resultFileManager;
    private final int batchSize;
    private final int maxConcurrency;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final CqlClient cqlClient;
    private final Boolean useCql;
    private final Translator cqlQueryTranslator;
    private final ProcessedGroupFactory processedGroupFactory;
    private final BatchReferenceProcessor batchReferenceProcessor;
    private final BatchCopierRedacter batchCopierRedacter;


    public CrtdlProcessingService(@Qualifier("flareClient") WebClient webClient,
                                  Translator cqlQueryTranslator,
                                  CqlClient cqlClient,
                                  ResultFileManager resultFileManager,
                                  DirectResourceLoader directLoader,
                                  ProcessedGroupFactory processedGroupFactory,
                                  BatchReferenceProcessor batchReferenceProcessor,
                                  BatchCopierRedacter batchCopierRedacter,
                                  @Value("${torch.batchsize:10}") int batchsize,
                                  @Value("5") int maxConcurrency,
                                  @Value("${torch.useCql}") boolean useCql) {
        this.webClient = webClient;
        this.directLoader = directLoader;
        this.objectMapper = new ObjectMapper();
        this.resultFileManager = resultFileManager;
        this.batchSize = batchsize;
        this.maxConcurrency = maxConcurrency;
        this.cqlClient = cqlClient;
        this.useCql = useCql;
        this.cqlQueryTranslator = cqlQueryTranslator;
        this.processedGroupFactory = processedGroupFactory;
        this.batchReferenceProcessor = batchReferenceProcessor;
        this.batchCopierRedacter = batchCopierRedacter;
    }


    // Get Patient Cohort
    // Check cohort for Consent
    // Fetch First Pass -> by batch and checking consent? Apply must haves
    // Fetch Second Pass outside patient compartment + apply must haves
    // Resolve References
    // Extract

    public Mono<Void> process(AnnotatedCrtdl crtdl, String jobID) {
        GroupsToProcess groupsToProcess = processedGroupFactory.create(crtdl);
        Mono<ResourceBundle> coreResourceBundle = directLoader.proccessCoreAttributeGroups(groupsToProcess.directNoPatientGroups());

        Flux<PatientBatch> batches = fetchPatientBatches(crtdl);


        return batches
                .flatMap(batch -> directLoader.directLoadPatientCompartment(groupsToProcess.directPatientCompartmentGroups(), batch, crtdl.consentKey()), maxConcurrency)
                .collectList()
                .flatMap(directlyLoadedPatients -> batchReferenceProcessor.processBatches(directlyLoadedPatients, coreResourceBundle, groupsToProcess.allGroups()))
                //TODO: Cascading delete
                .map(referencedBatch -> batchCopierRedacter.transformBatch(referencedBatch, groupsToProcess.allGroups()))
                .flatMap(transformedBatches -> resultFileManager.saveBatchList(jobID, transformedBatches))
                .doOnError(error -> logger.error("Error saving resources: {}", error.getMessage()))
                .doOnSuccess(unused -> logger.debug("Successfully saved all resources for jobId: {}", jobID))
                .then();
    }


    public Flux<PatientBatch> fetchPatientBatches(AnnotatedCrtdl crtdl) {
        try {
            return (useCql) ? fetchPatientListUsingCql(crtdl) : fetchPatientListFromFlare(crtdl);
        } catch (JsonProcessingException e) {
            return Flux.error(e);
        }
    }

    public Flux<PatientBatch> fetchPatientListFromFlare(AnnotatedCrtdl crtdl) {
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

    public Flux<PatientBatch> fetchPatientListUsingCql(AnnotatedCrtdl crtdl) throws JsonProcessingException {
        StructuredQuery ccdl = objectMapper.treeToValue(crtdl.cohortDefinition(), StructuredQuery.class);
        return cqlClient.fetchPatientIds(cqlQueryTranslator.toCql(ccdl).print())
                .window(batchSize)
                .flatMap(Flux::collectList)
                .map(PatientBatch::of);
    }


}
