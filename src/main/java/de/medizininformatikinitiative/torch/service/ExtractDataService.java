package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.consent.ConsentHandler;
import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.jobhandling.BatchState;
import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.failure.Issue;
import de.medizininformatikinitiative.torch.jobhandling.failure.Severity;
import de.medizininformatikinitiative.torch.jobhandling.result.BatchResult;
import de.medizininformatikinitiative.torch.jobhandling.result.BatchSelection;
import de.medizininformatikinitiative.torch.jobhandling.result.CoreResult;
import de.medizininformatikinitiative.torch.jobhandling.workunit.WorkUnitStatus;
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
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

/**
 * Extraction pipeline for TORCH.
 *
 * <p>Provides:
 * <ul>
 *   <li>batch processing ({@link #processBatch(BatchSelection)})</li>
 *   <li>core processing ({@link #processCore(Job, ExtractionResourceBundle)})</li>
 * </ul>
 *
 * <p>Persists results as NDJSON via {@link ResultFileManager}.</p>
 */
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

    /**
     * Processes a single batch of a job.
     *
     * <p>Steps:
     * <ol>
     *   <li>build consent info (or skip if violated)</li>
     *   <li>load / resolve / redact resources for the batch</li>
     *   <li>persist batch NDJSON and return {@link BatchResult}</li>
     * </ol>
     *
     * @param selection identifies the job + batch to process
     * @return mono emitting the resulting {@link BatchResult}
     */
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
                .switchIfEmpty(Mono.fromSupplier(() ->
                        new BatchResult(jobID, batch.batchId(), batchState.skip(), Optional.empty(), List.of(Issue.simple(Severity.WARNING, "Batch " + selection.batchState().batchId() + " skipped because of no consenting Patients")))
                ));
    }

    /**
     * Continues batch processing once consent has been resolved.
     *
     * @param batch           batch enriched with consent information
     * @param jobID           owning job id
     * @param groupsToProcess processed group set derived from CRTDL
     * @param batchState      state snapshot to update in the returned {@link BatchResult}
     * @return mono emitting a finished/failed {@link BatchResult}
     */
    private Mono<BatchResult> processBatchAfterConsent(PatientBatchWithConsent batch, UUID jobID, GroupsToProcess groupsToProcess, BatchState batchState) {
        UUID id = batch.id();
        logMemory(id);
        return directResourceLoader.directLoadPatientCompartment(groupsToProcess.directPatientCompartmentGroups(), batch)
                .doOnNext(loadedBatch -> logger.debug("Directly loaded patient compartment for batch {} with {} patients", id, loadedBatch.patientIds().size()))
                .flatMap(patientBatch -> referenceResolver.resolvePatientBatch(patientBatch, groupsToProcess.allGroups()))
                .map(patientBatch -> cascadingDelete.handlePatientBatch(patientBatch, groupsToProcess.allGroups()))
                .doOnNext(loadedBatch -> logger.debug("Batch resolved references {} with {} patients", id, loadedBatch.patientIds().size()))
                .map(patientBatch -> batchCopierRedacter.transformBatch(ExtractionPatientBatch.of(patientBatch), groupsToProcess.allGroups()))
                .doOnNext(loadedBatch -> logger.debug("Batch finished extraction  {} ", id))
                .flatMap(patientBatch -> writeBatch(jobID.toString(), patientBatch).subscribeOn(Schedulers.boundedElastic())
                        .thenReturn(new BatchResult(jobID, batch.id(), batchState.finishNow(WorkUnitStatus.FINISHED), Optional.of(batchToCoreWriter.toCoreBundle(patientBatch)), List.of()))
                );
    }


    /**
     * Processes the job's core (non-batch) resources and persists the resulting core bundle.
     *
     * <p>Loads direct core groups, resolves references, applies cascading delete, fills missing cache entries
     * via the datastore batch endpoint, then redacts/transforms and writes NDJSON.</p>
     *
     * @param job                   job to process
     * @param preComputedCoreBundle already available core bundle content to merge in
     * @return mono emitting the {@link CoreResult} (skipped if empty, finished otherwise)
     */
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
                    cb = cb.merge(preComputedCoreBundle);

                    List<Map<String, Set<String>>> missingChunks =
                            dataStore.groupReferencesByTypeInChunks(cb.missingCacheEntries());
                    ExtractionResourceBundle finalCb = cb;
                    return Flux.fromIterable(missingChunks)
                            .map(DataStoreHelper::createBatchBundleForReferences)
                            .concatMap(dataStore::executeBundle)
                            .flatMapIterable(list -> list)
                            .doOnNext(resource -> {
                                String id = ResourceUtils.getRelativeURL(resource);
                                finalCb.put(id, Optional.of(resource));
                            })
                            .then(Mono.just(finalCb)); // return updated cb
                })
                .flatMap(cb -> {
                    // transform first, then decide status
                    ExtractionResourceBundle transformed = batchCopierRedacter.transformBundle(cb, groupsToProcess.allGroups());


                    if (transformed.isEmpty()) {
                        return Mono.just(new CoreResult(job.id(), List.of(), WorkUnitStatus.SKIPPED));
                    }

                    return writeBundle(job.id().toString(), transformed)
                            .thenReturn(new CoreResult(job.id(), List.of(), WorkUnitStatus.FINISHED));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Persists a single batch as NDJSON.
     *
     * @param jobID owning job id (directory key)
     * @param batch extracted batch content
     * @return completion signal (error if writing fails)
     */
    Mono<Void> writeBatch(String jobID, ExtractionPatientBatch batch) {
        return Mono.defer(() -> {
            try {
                resultFileManager.saveBatchToNDJSON(jobID, batch);
                return Mono.empty();
            } catch (IOException e) {
                return Mono.error(e);
            }
        });
    }

    /**
     * Persists the core bundle as NDJSON.
     *
     * @param jobID  owning job id (directory key)
     * @param bundle extracted/transformed core bundle
     * @return completion signal (error if writing fails)
     */
    Mono<Void> writeBundle(String jobID, ExtractionResourceBundle bundle) {
        return Mono.defer(() -> {
            try {
                resultFileManager.saveCoreBundleToNDJSON(jobID, bundle);
                return Mono.empty();
            } catch (IOException e) {
                return Mono.error(e);
            }
        });

    }

}

