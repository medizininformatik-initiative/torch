package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.consent.ConsentHandler;
import de.medizininformatikinitiative.torch.diagnostics.BatchDiagnostics;
import de.medizininformatikinitiative.torch.diagnostics.BatchDiagnosticsAcc;
import de.medizininformatikinitiative.torch.diagnostics.ExclusionAcc;
import de.medizininformatikinitiative.torch.diagnostics.ExclusionKind;
import de.medizininformatikinitiative.torch.diagnostics.ExclusionRecord;
import de.medizininformatikinitiative.torch.diagnostics.PipelineStage;
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
import de.medizininformatikinitiative.torch.management.ProcessedGroupFactory;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
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
import java.util.stream.Collectors;

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
    private final PostCascadeMustHaveChecker postCascadeMustHaveChecker;

    public ExtractDataService(ResultFileManager resultFileManager,
                              ProcessedGroupFactory processedGroupFactory,
                              DirectResourceLoader directResourceLoader,
                              ReferenceResolver referenceResolver,
                              BatchCopierRedacter batchCopierRedacter,
                              CascadingDelete cascadingDelete,
                              PatientBatchToCoreBundleWriter writer,
                              ConsentHandler consentHandler,
                              DataStore dataStore,
                              PostCascadeMustHaveChecker postCascadeMustHaveChecker) {
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
                max / (1024 * 1024), total / (1024 * 1024), used / (1024 * 1024),
                free / (1024 * 1024), available / (1024 * 1024));
    }

    public Mono<BatchResult> processBatch(BatchSelection selection) {
        AnnotatedCrtdl crtdl = selection.job().parameters().crtdl();
        GroupsToProcess groupsToProcess = processedGroupFactory.create(crtdl);
        BatchState batchState = selection.batchState();
        PatientBatch batch = selection.batch();
        UUID jobId = selection.job().id();

        BatchDiagnosticsAcc acc = new BatchDiagnosticsAcc(jobId, batch.batchId(), batch.ids().size());
        ExclusionAcc writer = new ExclusionAcc();
        long consentStart = System.nanoTime();

        Mono<PatientBatchWithConsent> batchWithConsent =
                crtdl.consentCodes()
                        .map(code -> consentHandler.fetchAndBuildConsentInfo(code, batch))
                        .orElse(Mono.just(PatientBatchWithConsent.fromBatch(batch)))
                        .doOnNext(bwc -> acc.recordStage(PipelineStage.CONSENT_FETCH,
                                System.nanoTime() - consentStart, bwc.patientIds().size()))
                        .onErrorResume(ConsentViolatedException.class, ex -> Mono.empty());

        return batchWithConsent
                .flatMap(bwc -> processBatchAfterConsent(bwc, jobId, groupsToProcess, batchState, acc, writer))
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    batch.ids().forEach(patId ->
                            writer.record(new ExclusionRecord(patId, ExclusionKind.CONSENT, null, null, null)));
                    BatchDiagnostics diag = acc.snapshot(0);
                    return new BatchResult(jobId, batch.batchId(), batchState.skip(),
                            Optional.empty(), Optional.of(diag), writer.snapshot(),
                            List.of(Issue.simple(Severity.WARNING,
                                    "Batch " + selection.batchState().batchId() + " skipped because of no consenting patients")));
                }));
    }

    private Mono<BatchResult> processBatchAfterConsent(PatientBatchWithConsent batch,
                                                       UUID jobId,
                                                       GroupsToProcess groupsToProcess,
                                                       BatchState batchState,
                                                       BatchDiagnosticsAcc acc,
                                                       ExclusionAcc writer) {
        UUID batchId = batch.id();
        logMemory(batchId);

        long directLoadStart = System.nanoTime();
        return directResourceLoader
                .directLoadPatientCompartment(groupsToProcess.directPatientCompartmentGroups(), batch, writer)
                .doOnNext(loaded -> acc.recordStage(PipelineStage.DIRECT_LOAD,
                        System.nanoTime() - directLoadStart, loaded.totalResources()))
                .doOnNext(loadedBatch -> logger.debug("Directly loaded patient compartment for batch {} with {} patients",
                        batchId, loadedBatch.patientIds().size()))
                .flatMap(patientBatch -> {
                    long refStart = System.nanoTime();
                    return referenceResolver.resolvePatientBatch(patientBatch, groupsToProcess.allGroups(), writer)
                            .doOnNext(resolved -> acc.recordStage(PipelineStage.REFERENCE_RESOLVE,
                                    System.nanoTime() - refStart, resolved.totalResources()));
                })
                .map(patientBatch -> {
                    long start = System.nanoTime();
                    PatientBatchWithConsent result = cascadingDelete.handlePatientBatch(patientBatch, groupsToProcess.allGroups());
                    acc.recordStage(PipelineStage.CASCADING_DELETE, System.nanoTime() - start, result.totalResources());
                    return result;
                })
                .map(patientBatch -> filterPostCascadeMustHaveViolations(patientBatch, groupsToProcess.directPatientCompartmentGroups(), writer))
                .doOnNext(loadedBatch -> logger.debug("Batch resolved references {} with {} patients",
                        batchId, loadedBatch.patientIds().size()))
                .map(patientBatch -> {
                    long start = System.nanoTime();
                    ExtractionPatientBatch result = batchCopierRedacter.transformBatch(
                            ExtractionPatientBatch.of(patientBatch), groupsToProcess.allGroups());
                    acc.recordStage(PipelineStage.COPY_REDACT, System.nanoTime() - start, result.totalResources());
                    return result;
                })
                .doOnNext(__ -> logger.debug("Batch finished extraction {}", batchId))
                .flatMap(extractedBatch ->
                        writeBatch(jobId.toString(), extractedBatch)
                                .subscribeOn(Schedulers.boundedElastic())
                                .thenReturn(extractedBatch))
                .map(extractedBatch -> {
                    BatchDiagnostics diag = acc.snapshot(extractedBatch.bundles().size());
                    WorkUnitStatus status = extractedBatch.isEmpty() ? WorkUnitStatus.SKIPPED : WorkUnitStatus.FINISHED;

                    return new BatchResult(
                            jobId,
                            batchId,
                            batchState.finishNow(status),
                            Optional.of(batchToCoreWriter.toCoreBundle(extractedBatch)),
                            Optional.of(diag), writer.snapshot(), List.of());
                });
    }

    public Mono<CoreResult> processCore(Job job, ExtractionResourceBundle preComputedCoreBundle) {
        AnnotatedCrtdl crtdl = job.parameters().crtdl();
        GroupsToProcess groupsToProcess = processedGroupFactory.create(crtdl);

        BatchDiagnosticsAcc acc = new BatchDiagnosticsAcc(job.id(), job.id(), 0);
        ExclusionAcc writer = new ExclusionAcc();

        return directResourceLoader
                .processCoreAttributeGroups(groupsToProcess.directNoPatientGroups(), new ResourceBundle(), writer)
                .flatMap(cb -> {
                    long refStart = System.nanoTime();
                    return referenceResolver.resolveCoreBundle(cb, groupsToProcess.allGroups(), writer)
                            .doOnNext(resolved -> acc.recordStage(PipelineStage.REFERENCE_RESOLVE,
                                    System.nanoTime() - refStart, resolved.getValidResourceGroups().size()));
                })
                .flatMap(cb -> {
                    logger.debug("Running final cascading delete on core bundle");
                    long start = System.nanoTime();
                    cascadingDelete.handleBundle(cb, groupsToProcess.allGroups());
                    acc.recordStage(PipelineStage.CASCADING_DELETE, System.nanoTime() - start, cb.getValidResourceGroups().size());
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
                    ExtractionResourceBundle transformed =
                            batchCopierRedacter.transformBundle(cb, groupsToProcess.allGroups());
                    if (transformed.isEmpty()) {
                        return Mono.just(new CoreResult(job.id(), List.of(),
                                WorkUnitStatus.SKIPPED, Optional.of(acc.snapshot(0)), writer.snapshot()));
                    }
                    return writeBundle(job.id().toString(), transformed)
                            .thenReturn(new CoreResult(job.id(), List.of(),
                                    WorkUnitStatus.FINISHED, Optional.of(acc.snapshot(0)), writer.snapshot()));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

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

    /**
     * Removes patients that violate must-have requirements after cascading delete.
     *
     * <p>Cascading delete can remove resources that were initially present. A patient may
     * therefore pass direct loading and reference resolution, but still become invalid
     * after the final cascade step. Such removals must be recorded explicitly because
     * they do not pass through the earlier direct-load exclusion paths.</p>
     *
     * <p>This method records only one patient-level exclusion event per removed patient.
     * It deliberately does not count excluded resources or attributes, because resources
     * affected by the cascade may already have been loaded, counted, or removed in
     * earlier processing stages.</p>
     *
     * @param batch               patient batch after cascading delete
     * @param directPatientGroups direct patient-compartment groups whose must-have
     *                            requirements must still be satisfied
     * @param writer              exclusion writer used to record patients removed by this check
     * @return batch containing only patients that still satisfy post-cascade must-have checks
     * @throws NullPointerException if {@code batch}, {@code directPatientGroups}, or
     *                              {@code writer} is {@code null}
     */
    private PatientBatchWithConsent filterPostCascadeMustHaveViolations(
            PatientBatchWithConsent batch,
            List<AnnotatedAttributeGroup> directPatientGroups,
            ExclusionAcc writer
    ) {
        Set<String> survivingPatientIds = batch.bundles().entrySet().stream()
                .filter(entry -> {
                    try {
                        postCascadeMustHaveChecker.validate(
                                entry.getValue().bundle(),
                                directPatientGroups
                        );
                        return true;
                    } catch (MustHaveViolatedException e) {
                        writer.record(new ExclusionRecord(
                                entry.getKey(),
                                ExclusionKind.MUST_HAVE_CASCADE,
                                null,
                                null,
                                null
                        ));
                        return false;
                    }
                })
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        return batch.keep(survivingPatientIds);
    }
}
