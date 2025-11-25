package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.consent.ConsentHandler;
import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.jobhandling.BatchState;
import de.medizininformatikinitiative.torch.jobhandling.Issue;
import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.Severity;
import de.medizininformatikinitiative.torch.jobhandling.WorkUnitStatus;
import de.medizininformatikinitiative.torch.jobhandling.result.BatchResult;
import de.medizininformatikinitiative.torch.jobhandling.result.BatchSelection;
import de.medizininformatikinitiative.torch.jobhandling.result.CoreResult;
import de.medizininformatikinitiative.torch.management.ProcessedGroupFactory;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedCrtdl;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionPatientBatch;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionResourceBundle;
import de.medizininformatikinitiative.torch.model.management.GroupsToProcess;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.util.ResourceUtils;
import de.medizininformatikinitiative.torch.util.ResultFileManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

@Service
public class ExtractDataService {
    private static final Logger logger = LoggerFactory.getLogger(ExtractDataService.class);

    private final ResultFileManager resultFileManager;
    private final ProcessedGroupFactory processedGroupFactory;
    private final DirectResourceLoader directResourceLoader;
    private final ReferenceResolver referenceResolver;
    private final BatchCopierRedacter batchCopierRedacter;
    private final CascadingDelete cascadingDelete;
    private final ConsentHandler consentHandler;
    private final PatientBatchToCoreBundleWriter batchToCoreWriter;
    private final DataStore dataStore;

    public ExtractDataService(ResultFileManager resultFileManager,
                              ProcessedGroupFactory processedGroupFactory,
                              DirectResourceLoader directResourceLoader,
                              ReferenceResolver referenceResolver,
                              BatchCopierRedacter batchCopierRedacter,
                              CascadingDelete cascadingDelete,
                              PatientBatchToCoreBundleWriter writer,
                              ConsentHandler consentHandler, DataStore dataStore) {
        this.resultFileManager = requireNonNull(resultFileManager);
        this.processedGroupFactory = requireNonNull(processedGroupFactory);
        this.directResourceLoader = requireNonNull(directResourceLoader);
        this.referenceResolver = requireNonNull(referenceResolver);
        this.batchCopierRedacter = requireNonNull(batchCopierRedacter);
        this.cascadingDelete = requireNonNull(cascadingDelete);
        this.batchToCoreWriter = writer;
        this.consentHandler = requireNonNull(consentHandler);
        this.dataStore = dataStore;
    }

    private static void logMemory(UUID id) {
        Runtime runtime = Runtime.getRuntime();
        long max = runtime.maxMemory();
        long total = runtime.totalMemory();
        long free = runtime.freeMemory();
        long used = total - free;
        long available = max - used;
        logger.debug("JVM Memory at batch {} start [max: {} MB, total: {} MB, used: {} MB, free: {} MB, available: {} MB]",
                id,
                max / (1024 * 1024),
                total / (1024 * 1024),
                used / (1024 * 1024),
                free / (1024 * 1024),
                available / (1024 * 1024));
    }

    public Mono<BatchResult> processBatch(BatchSelection selection) {
        AnnotatedCrtdl crtdl = selection.job().parameters().crtdl();
        GroupsToProcess groupsToProcess = processedGroupFactory.create(crtdl);
        BatchState batchState = selection.batchState();

        PatientBatch batch = selection.batch();

        // Build consent Mono<PatientBatchWithConsent>
        Mono<PatientBatchWithConsent> batchWithConsent =
                crtdl.consentCodes()
                        .map(code -> consentHandler.fetchAndBuildConsentInfo(code, batch))
                        .orElse(Mono.just(PatientBatchWithConsent.fromBatch(batch)))
                        .onErrorResume(ConsentViolatedException.class, ex -> Mono.empty());
        UUID jobID = selection.job().id();
        return batchWithConsent
                // Skip batch if consent violated → empty
                .flatMap(bwc -> processBatchAfterConsent(bwc, jobID, groupsToProcess, batchState))
                // If batch was skipped because of empty Mono → return default result. Should be marked as failed or something
                .switchIfEmpty(Mono.fromSupplier(() ->
                        new BatchResult(jobID, batch.batchId(), batchState.updateStatus(WorkUnitStatus.SKIPPED), Optional.empty(), List.of(new Issue(Severity.WARNING, "Batch " + selection.batchState().batchId() + " skipped because of no consenting Patients")))
                ));
    }

    private Mono<BatchResult> processBatchAfterConsent(PatientBatchWithConsent batch, UUID jobID, GroupsToProcess groupsToProcess, BatchState batchState) {
        UUID id = batch.id();
        logMemory(id);
        return directResourceLoader.directLoadPatientCompartment(groupsToProcess.directPatientCompartmentGroups(), batch)
                .doOnNext(loadedBatch -> logger.debug("Directly loaded patient compartment for batch {} with {} patients", id, loadedBatch.patientIds().size()))
                .flatMap(patientBatch -> referenceResolver.processSinglePatientBatch(patientBatch, groupsToProcess.allGroups()))
                .map(patientBatch -> cascadingDelete.handlePatientBatch(patientBatch, groupsToProcess.allGroups()))
                .doOnNext(loadedBatch -> logger.debug("Batch resolved references {} with {} patients", id, loadedBatch.patientIds().size()))
                .map(patientBatch -> batchCopierRedacter.transformBatch(ExtractionPatientBatch.of(patientBatch), groupsToProcess.allGroups()))
                .doOnNext(loadedBatch -> logger.debug("Batch finished extraction  {} ", id))
                .flatMap(patientBatch -> writeBatch(jobID.toString(), patientBatch)
                        .thenReturn(new BatchResult(jobID, batch.id(), batchState.updateStatus(WorkUnitStatus.FINISHED), Optional.of(batchToCoreWriter.toCoreBundle(patientBatch)), List.of()))
                ).onErrorResume(RuntimeException.class, e ->
                        Mono.just(new BatchResult(jobID, batch.id()
                                ,
                                batchState.updateStatus(WorkUnitStatus.FAILED), Optional.empty(), List.of(new Issue(Severity.ERROR, "Fatal error processing batch: " + e.getMessage(), e))))
                );
    }


    public Mono<CoreResult> processCore(Job job, ExtractionResourceBundle preComputedCoreBundle) {

        AnnotatedCrtdl crtdl = job.parameters().crtdl();
        GroupsToProcess groupsToProcess = processedGroupFactory.create(crtdl);

        return directResourceLoader
                .processCoreAttributeGroups(groupsToProcess.directNoPatientGroups(), new ResourceBundle())

                // Resolve references once all data is in the bundle
                .flatMap(cb -> referenceResolver.resolveCoreBundle(cb, groupsToProcess.allGroups()))

                // Cascading delete ONCE on the final graph
                .doOnNext(cb -> {
                    logger.debug("Running final cascading delete on core bundle");
                    cascadingDelete.handleBundle(cb, groupsToProcess.allGroups());
                })
                .map(ExtractionResourceBundle::of)
                .flatMap(cb -> {
                    cb.merge(preComputedCoreBundle);

                    List<Map<String, Set<String>>> missingChunks =
                            dataStore.groupReferencesByTypeInChunks(cb.missingCacheEntries());

                    return Flux.fromIterable(missingChunks)
                            .flatMap(dataStore::executeSearchBatch)
                            .flatMapIterable(list -> list)
                            .doOnNext(resource -> {
                                String id = ResourceUtils.getRelativeURL(resource);
                                cb.put(id, Optional.of(resource));
                            })
                            .then(Mono.just(cb)); // return updated cb
                })
                .flatMap(cb -> {

                    // 4) Write the final core batch
                    ExtractionPatientBatch finalCoreBatch =
                            new ExtractionPatientBatch(Map.of("CORE", cb));

                    return writeBatch(
                            "core",
                            batchCopierRedacter.transformBatch(finalCoreBatch, groupsToProcess.allGroups())
                    );
                })
                .thenReturn(new CoreResult(job.id(), List.of()));
    }

    Mono<Void> writeBatch(String jobID, ExtractionPatientBatch batch) {
        try {
            resultFileManager.saveBatchToNDJSON(jobID, batch);
            return Mono.empty();
        } catch (IOException e) {
            return Mono.error(e);
        }
    }

}

