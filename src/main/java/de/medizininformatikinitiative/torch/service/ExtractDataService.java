package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.config.TorchProperties;
import de.medizininformatikinitiative.torch.consent.ConsentHandler;
import de.medizininformatikinitiative.torch.diagnostics.BatchDiagnostics;
import de.medizininformatikinitiative.torch.diagnostics.ConsentAudit;
import de.medizininformatikinitiative.torch.diagnostics.PipelineStage;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.BatchExclusions;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.PatientExclusionStage;
import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.jobhandling.BatchState;
import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.failure.Issue;
import de.medizininformatikinitiative.torch.jobhandling.failure.Severity;
import de.medizininformatikinitiative.torch.jobhandling.result.BatchResult;
import de.medizininformatikinitiative.torch.jobhandling.result.BatchSelection;
import de.medizininformatikinitiative.torch.jobhandling.result.CoreResult;
import de.medizininformatikinitiative.torch.jobhandling.workunit.WorkUnitStatus;
import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.management.ProcessedGroupFactory;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedCrtdl;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionPatientBatch;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionResourceBundle;
import de.medizininformatikinitiative.torch.model.management.GroupsToProcess;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
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
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Extraction pipeline for TORCH.
 *
 * <p>Provides batch processing via {@link #processBatch(BatchSelection)} and core processing
 * via {@link #processCore(Job, ExtractionResourceBundle)}.
 *
 * <p>Diagnostics are collected through {@link BatchExclusions} and the resource-inclusion counts
 * in {@link de.medizininformatikinitiative.torch.diagnostics.BatchDetails} on the happy path.
 * Unexpected failures are not swallowed and bubble up as {@link Mono#error} so callers can
 * route them through the job failure handling logic.
 *
 * <p>{@link ConsentViolatedException} is treated specially for batch processing: it is not
 * considered a hard error, but converted into a skipped {@link BatchResult}.
 *
 * <p>Results are persisted as NDJSON via {@link ResultFileManager}.
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
    private final PostCascadeMustHaveChecker postCascadeMustHaveChecker;
    private final TorchProperties torchProperties;
    private final CompartmentManager compartmentManager;

    public ExtractDataService(ResultFileManager resultFileManager,
                              ProcessedGroupFactory processedGroupFactory,
                              DirectResourceLoader directResourceLoader,
                              ReferenceResolver referenceResolver,
                              BatchCopierRedacter batchCopierRedacter,
                              CascadingDelete cascadingDelete,
                              PatientBatchToCoreBundleWriter writer,
                              ConsentHandler consentHandler,
                              DataStore dataStore,
                              PostCascadeMustHaveChecker postCascadeMustHaveChecker,
                              TorchProperties torchProperties,
                              CompartmentManager compartmentManager) {
        this.resultFileManager = requireNonNull(resultFileManager);
        this.processedGroupFactory = requireNonNull(processedGroupFactory);
        this.directResourceLoader = requireNonNull(directResourceLoader);
        this.referenceResolver = requireNonNull(referenceResolver);
        this.batchCopierRedacter = requireNonNull(batchCopierRedacter);
        this.cascadingDelete = requireNonNull(cascadingDelete);
        this.batchToCoreWriter = requireNonNull(writer);
        this.consentHandler = requireNonNull(consentHandler);
        this.dataStore = requireNonNull(dataStore);
        this.postCascadeMustHaveChecker = requireNonNull(postCascadeMustHaveChecker);
        this.torchProperties = requireNonNull(torchProperties);
        this.compartmentManager = requireNonNull(compartmentManager);
    }

    private static void logMemory(UUID id) {
        Runtime runtime = Runtime.getRuntime();
        long max = runtime.maxMemory();
        long total = runtime.totalMemory();
        long free = runtime.freeMemory();
        long used = total - free;
        long available = max - used;
        logger.debug(
                "JVM Memory at batch {} start [max: {} MB, total: {} MB, used: {} MB, free: {} MB, available: {} MB]",
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
     * <p>If consent resolution raises {@link ConsentViolatedException}, the batch is converted
     * into a skipped result with diagnostics and a warning issue. Any other error bubbles up.
     *
     * <p>If {@code torch.disableConsentCalculation} is set, consent resolution is skipped entirely
     * and every patient in the batch is treated as fully consented.
     *
     * @param selection identifies the job and batch to process
     * @return mono emitting the resulting {@link BatchResult}, or an error
     */
    public Mono<BatchResult> processBatch(BatchSelection selection) {
        AnnotatedCrtdl crtdl = selection.job().parameters().crtdl();
        GroupsToProcess groupsToProcess = processedGroupFactory.create(crtdl);
        BatchState batchState = selection.batchState();
        PatientBatch batch = selection.batch();
        UUID jobId = selection.job().id();

        Mono<PatientBatchWithConsent> unconsented = Mono.just(PatientBatchWithConsent.fromBatch(batch));
        Mono<PatientBatchWithConsent> batchWithConsent = torchProperties.disableConsentCalculation() ? unconsented :
                executeAndMeasureAsync(PipelineStage.CONSENT_FETCH, batch.diagnostics(), () ->
                        crtdl.consentCodes()
                                .map(code -> consentHandler.fetchAndBuildConsentInfo(code, batch))
                                .orElse(unconsented)
                                .onErrorResume(ConsentViolatedException.class, ex -> {
                                    logger.warn("Batch {} skipped: no consenting patients", batch.batchId());
                                    return Mono.empty();
                                }));

        return batchWithConsent
                .flatMap(bwc -> processBatchAfterConsent(bwc, jobId, groupsToProcess, batchState))
                .switchIfEmpty(writeConsentAudit(jobId.toString(), batch.batchId(), batch.diagnostics().consentAudit())
                        .subscribeOn(Schedulers.boundedElastic())
                        .then(Mono.fromSupplier(() ->
                            new BatchResult(
                                    jobId,
                                    batch.batchId(),
                                    batchState.skip(),
                                    Optional.empty(),
                                    Optional.of(batch.diagnostics()),
                                    List.of(Issue.simple(
                                            Severity.WARNING,
                                            "Batch " + selection.batchState().batchId() + " skipped because of no consenting patients"
                                    ))
                            ))));
    }

    private <T> Mono<T> executeAndMeasureAsync(PipelineStage stage, BatchDiagnostics diagnostics, Supplier<Mono<T>> f) {
        long start = System.nanoTime();
        return f.get().doOnNext(batch -> {
            long elapsed = System.nanoTime() - start;
            diagnostics.batchDetails().nanosElapsed().put(stage, elapsed);
        });
    }

    private <T> T executeAndMeasure(PipelineStage stage, BatchDiagnostics diagnostics, Supplier<T> f) {
        long start = System.nanoTime();
        T batch = f.get();

        long elapsed = System.nanoTime() - start;
        diagnostics.batchDetails().nanosElapsed().put(stage, elapsed);

        return batch;
    }

    private void executeAndMeasure(PipelineStage stage, BatchDiagnostics diagnostics, Runnable f) {
        long start = System.nanoTime();
        f.run();
        long elapsed = System.nanoTime() - start;
        diagnostics.batchDetails().nanosElapsed().put(stage, elapsed);
    }

    private static void recordResourceInclusions(BatchDiagnostics diagnostics, Map<String, Integer> counts) {
        counts.forEach((groupId, count) -> diagnostics.batchDetails().resourceInclusions().merge(groupId, count, Integer::sum));
    }

    /**
     * Continues batch processing once consent has been resolved.
     *
     * @param batch           consent-filtered batch
     * @param jobId           owning job id
     * @param groupsToProcess processed group set derived from the CRTDL
     * @param batchState      state snapshot to update in the returned {@link BatchResult}
     * @return mono emitting a finished {@link BatchResult}, or error on failure
     */
    private Mono<BatchResult> processBatchAfterConsent(PatientBatchWithConsent batch,
                                                       UUID jobId,
                                                       GroupsToProcess groupsToProcess,
                                                       BatchState batchState) {
        UUID batchId = batch.id();
        logMemory(batchId);

        return executeAndMeasureAsync(PipelineStage.DIRECT_LOAD, batch.diagnostics(), () -> directResourceLoader
                .directLoadPatientCompartment(
                        groupsToProcess.directPatientCompartmentGroups(),
                        batch
                ))
                .doOnNext(loadedBatch ->
                        logger.debug("Directly loaded patient compartment for batch {} with {} patients",
                                batchId, loadedBatch.patientIds().size()))
                .flatMap(patientBatch ->
                        executeAndMeasureAsync(PipelineStage.REFERENCE_RESOLVE, patientBatch.diagnostics(), () ->
                                referenceResolver.resolvePatientBatch(patientBatch, groupsToProcess.allGroups())))
                .doOnNext(patientBatch ->
                        logger.debug("Batch {} resolved references ({} patients)", batchId, patientBatch.patientIds().size()))
                .map(patientBatch ->
                        executeAndMeasure(PipelineStage.CASCADING_DELETE, patientBatch.diagnostics(), () ->
                                cascadingDelete.handlePatientBatch(patientBatch, groupsToProcess.allGroups())))
                .doOnNext(patientBatch ->
                        logger.debug("Batch {} completed cascading delete ({} patients)", batchId, patientBatch.patientIds().size()))
                .map(patientBatch ->
                        filterPostCascadeMustHaveViolations(patientBatch, groupsToProcess.directPatientCompartmentGroups()))
                .doOnNext(loadedBatch ->
                        logger.debug("Batch {} completed must-have filtering ({} patients)", batchId, loadedBatch.patientIds().size()))
                .map(patientBatch -> {
                    ExtractionPatientBatch transformed = executeAndMeasure(PipelineStage.COPY_REDACT, patientBatch.diagnostics(), () ->
                            batchCopierRedacter.transformBatch(ExtractionPatientBatch.of(patientBatch), groupsToProcess.allGroups()));
                    // Non-compartment resources are handed off to processCore() via toCoreBundle() and counted there instead,
                    // to avoid counting them twice.
                    recordResourceInclusions(patientBatch.diagnostics(),
                            transformed.resourceInclusionCounts(compartmentManager::isInCompartment));
                    return transformed;
                })
                .doOnNext(__ -> logger.debug("Batch finished extraction {}", batchId))
                .flatMap(extractedBatch ->
                        writeBatch(jobId.toString(), extractedBatch)
                                .then(writeConsentAudit(jobId.toString(), batchId, batch.diagnostics().consentAudit()))
                                .subscribeOn(Schedulers.boundedElastic())
                                .thenReturn(extractedBatch)
                )
                .map(extractedBatch -> {
                    WorkUnitStatus status = extractedBatch.isEmpty() ? WorkUnitStatus.SKIPPED : WorkUnitStatus.FINISHED;

                    return new BatchResult(
                            jobId,
                            batchId,
                            batchState.finishNow(status),
                            Optional.of(batchToCoreWriter.toCoreBundle(extractedBatch)),
                            Optional.of(batch.diagnostics().setFinalPatientCount(extractedBatch.getNumPatients())),
                            List.of()
                    );
                });
    }

    /**
     * Processes the job's core (non-batch) resources and persists the resulting core bundle.
     *
     * @param job                   job to process
     * @param preComputedCoreBundle already available core bundle content to merge in
     * @return mono emitting the {@link CoreResult} (skipped if empty, finished otherwise),
     * or error on failure
     */
    public Mono<CoreResult> processCore(Job job, ExtractionResourceBundle preComputedCoreBundle) {
        AnnotatedCrtdl crtdl = job.parameters().crtdl();
        GroupsToProcess groupsToProcess = processedGroupFactory.create(crtdl);

        BatchDiagnostics diagnostics = BatchDiagnostics.empty();

        return executeAndMeasureAsync(PipelineStage.DIRECT_LOAD, diagnostics, () ->
                directResourceLoader.processCoreAttributeGroups(
                        groupsToProcess.directNoPatientGroups(),
                        new ResourceBundle(),
                        diagnostics.batchExclusions()
                ))
                .flatMap(cb -> executeAndMeasureAsync(PipelineStage.REFERENCE_RESOLVE, diagnostics, () ->
                        referenceResolver.resolveCoreBundle(cb, groupsToProcess.allGroups(), diagnostics.batchExclusions())))
                .flatMap(cb -> {
                    logger.debug("Running final cascading delete on core bundle");
                    Set<ResourceGroup> newlyInvalidatedGroups = executeAndMeasure(PipelineStage.CASCADING_DELETE, diagnostics,
                            () -> cascadingDelete.handleBundle(cb, groupsToProcess.allGroups()));
                    newlyInvalidatedGroups.forEach(group -> diagnostics.batchExclusions()
                            .addCascadingDeleteExclusionCore(group.groupId(), group.resourceId().toRelativeUrl()));
                    try {
                        postCascadeMustHaveChecker.validate(cb, groupsToProcess.directNoPatientGroups());
                        return Mono.just(cb);
                    } catch (MustHaveViolatedException e) {
                        return Mono.error(e);
                    }
                })
                .map(ExtractionResourceBundle::of)
                .flatMap(cb -> {
                    ExtractionResourceBundle merged = cb.merge(preComputedCoreBundle);

                    List<Map<String, Set<String>>> missingChunks =
                            dataStore.groupReferencesByTypeInChunks(merged.missingCacheEntries());

                    return Flux.fromIterable(missingChunks)
                            .map(DataStoreHelper::createBatchBundleForReferences)
                            .concatMap(dataStore::executeBundle)
                            .flatMapIterable(list -> list)
                            .doOnNext(merged::put)
                            .then(Mono.just(merged));
                })
                .flatMap(cb -> {
                    ExtractionResourceBundle transformed = executeAndMeasure(PipelineStage.COPY_REDACT, diagnostics, () ->
                                    batchCopierRedacter.transformBundle(cb, groupsToProcess.allGroups()));
                    recordResourceInclusions(diagnostics, transformed.resourceInclusionCounts());

                    if (transformed.isEmpty()) {
                        return Mono.just(new CoreResult(job.id(), List.of(), WorkUnitStatus.SKIPPED,
                                Optional.of(diagnostics)));
                    }

                    return writeBundle(job.id().toString(), transformed)
                            .thenReturn(new CoreResult(job.id(), List.of(), WorkUnitStatus.FINISHED,
                                    Optional.of(diagnostics)));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Persists a single batch as NDJSON.
     *
     * @param jobID owning job id (directory key)
     * @param batch extracted batch content
     * @return completion signal; emits error if writing fails
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
     * Persists a batch's consent audit trail as NDJSON, if not empty.
     * <p>
     * Called for both finished and skipped batches, so consent decisions stay traceable even
     * when a batch has no consenting patients.
     *
     * @param jobID        owning job id (directory key)
     * @param batchId      batch id (also used as filename key)
     * @param consentAudit collected consent audit entries for the batch
     * @return completion signal; emits error if writing fails
     */
    Mono<Void> writeConsentAudit(String jobID, UUID batchId, ConsentAudit consentAudit) {
        return Mono.defer(() -> {
            try {
                resultFileManager.saveConsentBatchToNDJSON(jobID, batchId, consentAudit);
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
     * @param bundle extracted and transformed core bundle
     * @return completion signal; emits error if writing fails
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

    private PatientBatchWithConsent filterPostCascadeMustHaveViolations(
            PatientBatchWithConsent batch,
            List<AnnotatedAttributeGroup> directPatientGroups
    ) {
        Set<String> survivingPatientIds = batch.bundles().entrySet().stream()
                .filter(entry -> {
                    try {
                        postCascadeMustHaveChecker.validate(entry.getValue().bundle(), directPatientGroups);
                        return true;
                    } catch (MustHaveViolatedException e) {
                        batch.batchExclusions().addPatientExclusion(PatientExclusionStage.CASCADING_DELETE, entry.getKey());
                        return false;
                    }
                })
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        return batch.keep(survivingPatientIds);
    }
}
