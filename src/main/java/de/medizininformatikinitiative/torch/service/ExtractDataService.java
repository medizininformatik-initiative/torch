package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.accumulators.Acc;
import de.medizininformatikinitiative.torch.consent.ConsentHandler;
import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.jobhandling.BatchState;
import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.failure.Issue;
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
 * <p>Uses {@link Acc} to thread {@link Issue}s through the reactive pipeline on the
 * happy path only. Errors are never caught inside the pipeline — they always bubble up
 * as {@link Mono#error} so the work unit's {@code onErrorResume} can route them to the
 * appropriate persistence method, and
 * {@link de.medizininformatikinitiative.torch.jobhandling.failure.RetryabilityUtil}
 * decides whether to retry or fail permanently.</p>
 *
 * <p>The only exception is {@link ConsentViolatedException}, which is not an error but
 * an intentional skip — it is caught and converted to a skipped {@link BatchResult}.</p>
 *
 * <p>Persists results as NDJSON via {@link ResultFileManager}.</p>
 */
@Service
public class ExtractDataService {

    private static final Logger logger = LoggerFactory.getLogger(ExtractDataService.class);
    public static final String BATCH = "batch=";

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
                              ConsentHandler consentHandler,
                              DataStore dataStore) {
        this.resultFileManager = requireNonNull(resultFileManager);
        this.processedGroupFactory = requireNonNull(processedGroupFactory);
        this.directResourceLoader = requireNonNull(directResourceLoader);
        this.referenceResolver = requireNonNull(referenceResolver);
        this.batchCopierRedacter = requireNonNull(batchCopierRedacter);
        this.cascadingDelete = requireNonNull(cascadingDelete);
        this.batchToCoreWriter = requireNonNull(writer);
        this.consentHandler = requireNonNull(consentHandler);
        this.dataStore = requireNonNull(dataStore);
    }

    // -------------------------------------------------------------------------
    // Batch processing
    // -------------------------------------------------------------------------

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
     * <p>The {@link Acc} accumulator is initialised here and carried through the entire
     * pipeline as a happy-path diagnostic thread. Two possible outcomes:
     * <ul>
     *   <li><b>Skipped</b> — {@link ConsentViolatedException} was raised; no consenting
     *       patients. A warning is attached and a skipped {@link BatchResult} is returned
     *       immediately.</li>
     *   <li><b>Finished</b> — all steps completed; {@link BatchResult} carries all
     *       accumulated INFO/WARNING issues.</li>
     * </ul>
     *
     * <p>Any other error bubbles up as {@link Mono#error} to the work unit.
     *
     * @param selection identifies the job and batch to process
     * @return mono emitting the resulting {@link BatchResult}, or error on failure
     */
    public Mono<BatchResult> processBatch(BatchSelection selection) {
        AnnotatedCrtdl crtdl = selection.job().parameters().crtdl();
        GroupsToProcess groupsToProcess = processedGroupFactory.create(crtdl);
        BatchState batchState = selection.batchState();
        PatientBatch batch = selection.batch();
        UUID jobID = selection.job().id();

        return Mono.just(Acc.ok(batch))

                // 1) Consent resolution
                .flatMap(acc -> {
                    Mono<PatientBatchWithConsent> batchWithConsent =
                            crtdl.consentCodes()
                                    .map(code -> consentHandler.fetchAndBuildConsentInfo(code, acc.value()))
                                    .orElse(Mono.just(PatientBatchWithConsent.fromBatch(acc.value())));

                    return batchWithConsent
                            .map(bwc -> Acc.ok(bwc).mergeIssuesFrom(acc))
                            // ConsentViolatedException is not an error — batch is intentionally skipped
                            .onErrorResume(ConsentViolatedException.class, e ->
                                    Mono.just(acc
                                            .addWarning(
                                                    "Batch " + batchState.batchId() + " skipped",
                                                    "No consenting patients")
                                            .map(v -> null))
                            );
                    // all other errors bubble up to the work unit
                })

                // 2) Route: skipped or continue pipeline
                .flatMap(acc -> {
                    if (acc.value() == null) {
                        // consent violated — return skipped BatchResult immediately
                        return Mono.just(new BatchResult(
                                jobID,
                                batch.batchId(),
                                batchState.skip(),
                                Optional.empty(),
                                acc.issues()
                        ));
                    }
                    return processBatchAfterConsent(acc, jobID, groupsToProcess, batchState);
                });
    }

    // -------------------------------------------------------------------------
    // Core processing
    // -------------------------------------------------------------------------

    /**
     * Continues batch processing once consent has been resolved.
     *
     * <p>Receives an {@link Acc} already carrying any issues from consent resolution,
     * and threads it through the remaining extraction steps:
     * <ol>
     *   <li>Load patient compartment</li>
     *   <li>Reference resolution</li>
     *   <li>Cascading delete</li>
     *   <li>Copy + Redact</li>
     *   <li>Write NDJSON</li>
     * </ol>
     *
     * <p>Errors at any step bubble up as {@link Mono#error} — no catching or accumulation.
     * {@link Acc} only collects INFO/WARNING diagnostics on the happy path.
     *
     * @param acc             accumulator carrying the consented batch and any prior issues
     * @param jobID           owning job id
     * @param groupsToProcess processed group set derived from the CRTDL
     * @param batchState      state snapshot to update in the returned {@link BatchResult}
     * @return mono emitting a finished {@link BatchResult}, or error on failure
     */
    private Mono<BatchResult> processBatchAfterConsent(
            Acc<PatientBatchWithConsent> acc,
            UUID jobID,
            GroupsToProcess groupsToProcess,
            BatchState batchState) {

        UUID id = acc.value().id();
        logMemory(id);

        // Steps 3-6 stay as Acc<PatientBatchWithConsent> until the type boundary at Copy+Redact
        Mono<Acc<ExtractionPatientBatch>> afterRedact = Mono.just(acc)

                // 3) Load patient compartment
                .flatMap(a ->
                        directResourceLoader
                                .directLoadPatientCompartment(
                                        groupsToProcess.directPatientCompartmentGroups(), a.value())
                                .map(loaded -> {
                                    logger.debug("Directly loaded patient compartment for batch {} with {} patients",
                                            id, loaded.patientIds().size());
                                    return Acc.ok(loaded)
                                            .mergeIssuesFrom(a)
                                            .addInfo("Loaded patient compartment", BATCH + id);
                                })
                )

                // 4) Reference resolution
                .flatMap(a ->
                        referenceResolver
                                .resolvePatientBatch(a.value(), groupsToProcess.allGroups())
                                .map(resolved -> {
                                    logger.debug("Batch resolved references {} with {} patients",
                                            id, resolved.patientIds().size());
                                    return Acc.ok(resolved)
                                            .mergeIssuesFrom(a)
                                            .addInfo("Resolved references", BATCH + id);
                                })
                )

                // 5) Cascading delete
                .map(a -> a
                        .map(b -> cascadingDelete.handlePatientBatch(b, groupsToProcess.allGroups()))
                        .addInfo("Applied cascading delete", BATCH + id)
                )

                // 6) Copy + Redact — type boundary: Acc<PatientBatchWithConsent> → Acc<ExtractionPatientBatch>
                .map(a -> a
                        .map(b -> batchCopierRedacter.transformBatch(
                                ExtractionPatientBatch.of(b), groupsToProcess.allGroups()))
                        .addInfo("Extraction finished", BATCH + id)
                );

        return afterRedact

                // 7) Write NDJSON
                .flatMap(a ->
                        writeBatch(jobID.toString(), a.value())
                                .subscribeOn(Schedulers.boundedElastic())
                                .thenReturn(a.addInfo("Wrote NDJSON bundle", BATCH + id))
                )

                // 8) Drain Acc → BatchResult
                .map(a -> new BatchResult(
                        jobID,
                        acc.value().id(),
                        batchState.finishNow(WorkUnitStatus.FINISHED),
                        Optional.of(batchToCoreWriter.toCoreBundle(a.value())),
                        a.issues()
                ));
    }

    // -------------------------------------------------------------------------
    // Persistence helpers
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Processes the job's core (non-batch) resources and persists the resulting core bundle.
     *
     * <p>Uses {@link Acc} to thread {@link Issue}s through the pipeline on the happy path.
     * Errors bubble up as {@link Mono#error} to the work unit — no catching inside the pipeline.
     *
     * @param job                   job to process
     * @param preComputedCoreBundle already available core bundle content to merge in
     * @return mono emitting the {@link CoreResult} (skipped if empty, finished otherwise),
     * or error on failure
     */
    public Mono<CoreResult> processCore(Job job, ExtractionResourceBundle preComputedCoreBundle) {

        AnnotatedCrtdl crtdl = job.parameters().crtdl();
        GroupsToProcess groupsToProcess = processedGroupFactory.create(crtdl);

        // Steps 1-2 produce Acc<ResourceBundle>, split at type boundary before step 3
        Mono<Acc<ExtractionResourceBundle>> afterDelete = directResourceLoader
                .processCoreAttributeGroups(groupsToProcess.directNoPatientGroups(), new ResourceBundle())
                .map(Acc::ok)

                // 1) Reference resolution
                .flatMap(acc ->
                        referenceResolver.resolveCoreBundle(acc.value(), groupsToProcess.allGroups())
                                .map(resolved -> Acc.ok(resolved)
                                        .mergeIssuesFrom(acc)
                                        .addInfo("Resolved core references", "job=" + job.id()))
                )

                // 2) Cascading delete — type boundary: ResourceBundle → ExtractionResourceBundle
                .map(acc -> {
                    logger.debug("Running final cascading delete on core bundle");
                    cascadingDelete.handleBundle(acc.value(), groupsToProcess.allGroups());
                    return acc.map(ExtractionResourceBundle::of)
                            .addInfo("Applied cascading delete", "job=" + job.id());
                });

        return afterDelete

                // 3) Merge precomputed + fill missing cache entries
                .flatMap(acc -> {
                    ExtractionResourceBundle merged = acc.value().merge(preComputedCoreBundle);
                    List<Map<String, Set<String>>> missingChunks =
                            dataStore.groupReferencesByTypeInChunks(merged.missingCacheEntries());

                    return Flux.fromIterable(missingChunks)
                            .map(DataStoreHelper::createBatchBundleForReferences)
                            .concatMap(dataStore::executeBundle)
                            .flatMapIterable(list -> list)
                            .doOnNext(merged::put)
                            .then(Mono.just(acc.map(v -> merged)
                                    .addInfo("Filled missing cache entries", "job=" + job.id())));
                })

                // 4) Transform + Write
                .flatMap(acc -> {
                    ExtractionResourceBundle transformed =
                            batchCopierRedacter.transformBundle(acc.value(), groupsToProcess.allGroups());

                    if (transformed.isEmpty()) {
                        return Mono.just(new CoreResult(job.id(), acc.issues(), WorkUnitStatus.SKIPPED));
                    }

                    return writeBundle(job.id().toString(), transformed)
                            .thenReturn(new CoreResult(
                                    job.id(),
                                    acc.addInfo("Wrote core NDJSON", "job=" + job.id()).issues(),
                                    WorkUnitStatus.FINISHED));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }
}
