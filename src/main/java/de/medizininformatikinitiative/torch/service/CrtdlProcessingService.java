package de.medizininformatikinitiative.torch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.DirectResourceLoader;
import de.medizininformatikinitiative.torch.cql.CqlClient;
import de.medizininformatikinitiative.torch.management.ProcessedGroupFactory;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedCrtdl;
import de.medizininformatikinitiative.torch.model.management.*;
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
import java.util.Map;

@Service
public class CrtdlProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(CrtdlProcessingService.class);

    private final ResultFileManager resultFileManager;
    private final int batchSize;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final CqlClient cqlClient;
    private final Boolean useCql;
    private final Translator cqlQueryTranslator;
    private final ProcessedGroupFactory processedGroupFactory;
    private final DirectResourceLoader directResourceLoader;
    private final int maxConcurrency;
    private final ReferenceResolver referenceResolver;
    private final BatchCopierRedacter batchCopierRedacter;
    private final CascadingDelete cascadingDelete;
    private final PatientBatchToCoreBundleWriter batchToCoreWriter;


    public CrtdlProcessingService(@Qualifier("flareClient") WebClient webClient,
                                  Translator cqlQueryTranslator,
                                  CqlClient cqlClient,
                                  ResultFileManager resultFileManager,
                                  ProcessedGroupFactory processedGroupFactory,
                                  @Value("${torch.batchsize:10}") int batchsize,
                                  @Value("${torch.useCql}") boolean useCql,
                                  DirectResourceLoader directResourceLoader,
                                  ReferenceResolver referenceResolver,
                                  BatchCopierRedacter batchCopierRedacter,
                                  @Value("5") int maxConcurrency, CascadingDelete cascadingDelete, PatientBatchToCoreBundleWriter writer) {
        this.webClient = webClient;
        this.objectMapper = new ObjectMapper();
        this.resultFileManager = resultFileManager;
        this.batchSize = batchsize;
        this.cqlClient = cqlClient;
        this.useCql = useCql;
        this.cqlQueryTranslator = cqlQueryTranslator;
        this.processedGroupFactory = processedGroupFactory;
        this.directResourceLoader = directResourceLoader;
        this.maxConcurrency = maxConcurrency;
        this.referenceResolver = referenceResolver;
        this.batchCopierRedacter = batchCopierRedacter;
        this.cascadingDelete = cascadingDelete;
        this.batchToCoreWriter = writer;
    }


    /**
     * Fetches the patient batch from cohort, starts the processing pipeline and then starts the output process to write the result to file
     *
     * @param crtdl      CRTDL to be processed
     * @param jobID      JobID of the current job used to identify the extraction
     * @param patientIds Patient cohort passed as parameter
     * @return mono finishes when complete
     */
    public Mono<Void> process(AnnotatedCrtdl crtdl, String jobID, List<String> patientIds) {
        GroupsToProcess groupsToProcess = processedGroupFactory.create(crtdl);

        Flux<PatientBatch> batches;
        if (patientIds.isEmpty()) {
            batches = fetchPatientBatches(crtdl);
        } else {
            batches = Flux.just(new PatientBatch(patientIds));
        }
        ResourceBundle coreBundle = new ResourceBundle();

// Step 1: Preprocess Core Bundle (but don't write it out yet)
        Mono<ImmutableResourceBundle> preprocessedCoreBundle = directResourceLoader
                .proccessCoreAttributeGroups(groupsToProcess.directNoPatientGroups(), coreBundle)
                .flatMap(updatedCoreBundle -> referenceResolver.resolveCoreBundle(updatedCoreBundle, groupsToProcess.allGroups()))
                .flatMap(updatedCoreBundle -> Mono.fromRunnable(() -> cascadingDelete.handleBundle(updatedCoreBundle, groupsToProcess.allGroups()))
                        .thenReturn(updatedCoreBundle)) // Ensure sequential execution
                .map(ImmutableResourceBundle::new) // Create immutable snapshot after processing
                .cache(); // Ensure it runs only once

        // Step 2: Process Patient Batches Using Preprocessed Core Bundle
        return preprocessedCoreBundle.flatMapMany(coreSnapshot ->
                Flux.from(batches)
                        .map(batch -> PatientBatchWithConsent.fromBatchWithStaticInfo(batch, coreSnapshot))
                        .flatMap(batch ->
                                        directResourceLoader.directLoadPatientCompartment(
                                                        groupsToProcess.directPatientCompartmentGroups(), batch, crtdl.consentKey()
                                                )
                                                .flatMap(patientBatch ->
                                                        referenceResolver.processSinglePatientBatch(patientBatch, coreBundle, groupsToProcess.allGroups())
                                                )
                                                .map(patientBatch -> cascadingDelete.handlePatientBatch(patientBatch, groupsToProcess.allGroups()))
                                                .flatMap(patientBatch -> batchCopierRedacter.transformBatch(Mono.just(patientBatch), groupsToProcess.allGroups())
                                                )
                                                .flatMap(transformedBatch -> {
                                                            batchToCoreWriter.updateCore(transformedBatch, coreBundle);
                                                            return resultFileManager.saveBatchToNDJSON(jobID, Mono.just(transformedBatch));
                                                        }
                                                ),
                                maxConcurrency
                        )
        ).then(
                // Step 3: Write the Final Core Resource Bundle to File
                Mono.defer(() -> {
                    PatientResourceBundle corePatientBundle = new PatientResourceBundle("CORE", coreBundle);
                    PatientBatchWithConsent coreBundleBatch = new PatientBatchWithConsent(Map.of("CORE", corePatientBundle), false);

                    return resultFileManager.saveBatchToNDJSON(
                            jobID, batchCopierRedacter.transformBatch(
                                    Mono.just(coreBundleBatch), groupsToProcess.allGroups()
                            )
                    );
                })
        );
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
