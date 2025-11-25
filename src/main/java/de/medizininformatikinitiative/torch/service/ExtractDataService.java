package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.consent.ConsentHandler;
import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.exceptions.ValidationException;
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
import de.medizininformatikinitiative.torch.model.management.CachelessResourceBundle;
import de.medizininformatikinitiative.torch.model.management.GroupsToProcess;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.util.ResultFileManager;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static de.medizininformatikinitiative.torch.management.OperationOutcomeCreator.createOperationOutcome;
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

    public ExtractDataService(ResultFileManager resultFileManager,
                              ProcessedGroupFactory processedGroupFactory,
                              DirectResourceLoader directResourceLoader,
                              ReferenceResolver referenceResolver,
                              BatchCopierRedacter batchCopierRedacter,
                              CascadingDelete cascadingDelete,
                              PatientBatchToCoreBundleWriter writer,
                              ConsentHandler consentHandler) {
        this.resultFileManager = requireNonNull(resultFileManager);
        this.processedGroupFactory = requireNonNull(processedGroupFactory);
        this.directResourceLoader = requireNonNull(directResourceLoader);
        this.referenceResolver = requireNonNull(referenceResolver);
        this.batchCopierRedacter = requireNonNull(batchCopierRedacter);
        this.cascadingDelete = requireNonNull(cascadingDelete);
        requireNonNull(writer);
        this.consentHandler = requireNonNull(consentHandler);
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

    public static OperationOutcome.OperationOutcomeIssueComponent toError(String msg, Throwable e) {
        return new OperationOutcome.OperationOutcomeIssueComponent()
                .setSeverity(OperationOutcome.IssueSeverity.ERROR)
                .setDiagnostics(msg + ": " + e.getMessage());
    }


    public static OperationOutcome.OperationOutcomeIssueComponent toWarning(String msg) {
        return new OperationOutcome.OperationOutcomeIssueComponent()
                .setSeverity(OperationOutcome.IssueSeverity.WARNING)
                .setDiagnostics(msg);
    }


    private void handleJobError(String jobId, Throwable e) {
        HttpStatus status = (e instanceof IllegalArgumentException || e instanceof ValidationException)
                ? HttpStatus.BAD_REQUEST
                : HttpStatus.INTERNAL_SERVER_ERROR;
        OperationOutcome outcome = createOperationOutcome(jobId, e);
        resultFileManager.saveErrorToJson(jobId, outcome, status)
                .doOnError(err -> resultFileManager.setStatus(jobId, HttpStatus.INTERNAL_SERVER_ERROR))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
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
        return batchWithConsent
                // Skip batch if consent violated → empty
                .flatMap(bwc -> processBatch(bwc, selection.job().id(), groupsToProcess, batchState))
                // If batch was skipped because of empty Mono → return default result. Should be marked as failed or something
                .switchIfEmpty(Mono.fromSupplier(() ->
                        new BatchResult(
                                batchState.updateStatus(WorkUnitStatus.SKIPPED),
                                Optional.empty(),
                                List.of(new Issue(Severity.WARNING, "Batch " + selection.batchState().batchId() + " skipped because of no consenting Patients")))
                ));
    }

    private Mono<BatchResult> processBatch(PatientBatchWithConsent batch, UUID jobID, GroupsToProcess groupsToProcess, BatchState batchState) {
        UUID id = batch.id();
        logMemory(id);
        return directResourceLoader.directLoadPatientCompartment(groupsToProcess.directPatientCompartmentGroups(), batch)
                .doOnNext(loadedBatch -> logger.debug("Directly loaded patient compartment for batch {} with {} patients", id, loadedBatch.patientIds().size()))
                .flatMap(patientBatch -> referenceResolver.processSinglePatientBatch(patientBatch, groupsToProcess.allGroups()))
                .map(patientBatch -> cascadingDelete.handlePatientBatch(patientBatch, groupsToProcess.allGroups()))
                .doOnNext(loadedBatch -> logger.debug("Batch resolved references {} with {} patients", id, loadedBatch.patientIds().size()))
                .map(patientBatch -> batchCopierRedacter.transformBatch(patientBatch, groupsToProcess.allGroups()))
                .doOnNext(loadedBatch -> logger.debug("Batch finished extraction  {} ", id))
                .flatMap(patientBatch -> {
                            //batchToCoreWriter.updateCore(patientBatch, coreBundle);
                            return writeBatch(jobID.toString(), patientBatch)
                                    .thenReturn(new BatchResult(batchState.updateStatus(WorkUnitStatus.FINISHED), Optional.of(new CachelessResourceBundle(patientBatch.coreBundle())), List.of()));
                        }
                ).onErrorResume(RuntimeException.class, e ->
                        Mono.just(new BatchResult(
                                batchState.updateStatus(WorkUnitStatus.FAILED),
                                Optional.empty(),
                                List.of(new Issue(Severity.ERROR, "Fatal error processing batch: " + e.getMessage(), e))))
                );
    }


    Mono<Void> writeBatch(String jobID, PatientBatchWithConsent batch) {
        try {
            resultFileManager.saveBatchToNDJSON(jobID, batch);
            return Mono.empty();
        } catch (IOException e) {
            return Mono.error(e);
        }
    }

    public Mono<CoreResult> processCore(Job job, ResourceBundle preComputedCoreBundle) {

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

                // Redaction and writing final core
                .flatMap(cb -> {
                    PatientResourceBundle corePRB = new PatientResourceBundle("CORE", cb);
                    PatientBatchWithConsent finalCoreBatch =
                            new PatientBatchWithConsent(Map.of("CORE", corePRB), UUID.randomUUID());

                    //TODO add preComputedInfo
                    //load cache if needed
                    return writeBatch(
                            "core",
                            batchCopierRedacter.transformBatch(finalCoreBatch, groupsToProcess.allGroups())
                    );
                })
                .thenReturn(
                        new CoreResult(List.of())
                );
    }
}

