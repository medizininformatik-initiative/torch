package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.consent.ConsentHandler;
import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.jobhandling.BatchState;
import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.JobParameters;
import de.medizininformatikinitiative.torch.jobhandling.JobPriority;
import de.medizininformatikinitiative.torch.jobhandling.JobStatus;
import de.medizininformatikinitiative.torch.jobhandling.failure.Severity;
import de.medizininformatikinitiative.torch.jobhandling.result.BatchSelection;
import de.medizininformatikinitiative.torch.jobhandling.workunit.WorkUnitState;
import de.medizininformatikinitiative.torch.jobhandling.workunit.WorkUnitStatus;
import de.medizininformatikinitiative.torch.management.ProcessedGroupFactory;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedCrtdl;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionPatientBatch;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionResourceBundle;
import de.medizininformatikinitiative.torch.model.management.GroupsToProcess;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.TermCode;
import de.medizininformatikinitiative.torch.util.ResultFileManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static de.medizininformatikinitiative.torch.jobhandling.JobTest.job;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class ExtractDataServiceTest {

    @Mock
    ResultFileManager resultFileManager;
    @Mock
    ProcessedGroupFactory processedGroupFactory;
    @Mock
    DirectResourceLoader directResourceLoader;
    @Mock
    ReferenceResolver referenceResolver;
    @Mock
    BatchCopierRedacter batchCopierRedacter;
    @Mock
    CascadingDelete cascadingDelete;
    @Mock
    ConsentHandler consentHandler;
    @Mock
    PatientBatchToCoreBundleWriter batchToCoreWriter;
    @Mock
    DataStore dataStore;

    ExtractDataService service;
    ExtractDataService spyService;

    private static Job jobWithParameters(UUID jobId, JobStatus status, JobParameters params) {
        Instant now = Instant.now();
        return new Job(
                jobId,
                status,
                WorkUnitState.initNow(),
                0,
                Map.of(),
                now,
                now,
                Optional.empty(),
                List.of(),
                params,
                JobPriority.NORMAL,
                WorkUnitState.initNow()
        );
    }

    @BeforeEach
    void setUp() {
        service = new ExtractDataService(
                resultFileManager,
                processedGroupFactory,
                directResourceLoader,
                referenceResolver,
                batchCopierRedacter,
                cascadingDelete,
                batchToCoreWriter,
                consentHandler,
                dataStore
        );
        spyService = Mockito.spy(service);
    }

    // -------------------------------------------------------------------------
    // processBatch branches
    // -------------------------------------------------------------------------

    @Nested
    class ProcessBatchTests {

        @Test
        void processBatch_whenNoConsentCodes_happyPath_finishesAndEmitsCoreBundle() {
            UUID jobId = UUID.randomUUID();
            UUID batchId = UUID.randomUUID();

            // Reuse your fixture job (consentCodes = Optional.empty inside EMPTY_PARAMETERS)
            Job job = job(jobId, JobStatus.PENDING, WorkUnitState.initNow(), Map.of(), WorkUnitState.initNow());

            GroupsToProcess groups = mock(GroupsToProcess.class);
            when(processedGroupFactory.create(any())).thenReturn(groups);
            when(groups.directPatientCompartmentGroups()).thenReturn(List.of());
            when(groups.allGroups()).thenReturn(Map.of());

            PatientBatch rawBatch = mock(PatientBatch.class);
            when(rawBatch.batchId()).thenReturn(batchId);

            BatchState batchState = mock(BatchState.class);
            BatchState finishedState = mock(BatchState.class);
            when(batchState.finishNow(WorkUnitStatus.FINISHED)).thenReturn(finishedState);

            BatchSelection selection = mock(BatchSelection.class);
            when(selection.job()).thenReturn(job);
            when(selection.batchState()).thenReturn(batchState);
            when(selection.batch()).thenReturn(rawBatch);
            PatientBatchWithConsent bwc = mock(PatientBatchWithConsent.class);

            when(directResourceLoader.directLoadPatientCompartment(anyList(), any()))
                    .thenReturn(Mono.just(bwc));
            when(referenceResolver.resolvePatientBatch(eq(bwc), anyMap()))
                    .thenReturn(Mono.just(bwc));
            when(cascadingDelete.handlePatientBatch(eq(bwc), anyMap()))
                    .thenReturn(bwc);

            ExtractionPatientBatch ofResult = mock(ExtractionPatientBatch.class);
            try (MockedStatic<ExtractionPatientBatch> mocked = mockStatic(ExtractionPatientBatch.class)) {
                mocked.when(() -> ExtractionPatientBatch.of(any()))
                        .thenReturn(ofResult);

                ExtractionPatientBatch extracted = mock(ExtractionPatientBatch.class);
                when(batchCopierRedacter.transformBatch(eq(ofResult), anyMap()))
                        .thenReturn(extracted);

                ExtractionResourceBundle coreBundle = mock(ExtractionResourceBundle.class);
                when(batchToCoreWriter.toCoreBundle(extracted)).thenReturn(coreBundle);

                doReturn(Mono.empty()).when(spyService).writeBatch(eq(jobId.toString()), eq(extracted));

                StepVerifier.create(spyService.processBatch(selection))
                        .assertNext(res -> {
                            assertThat(res.jobId()).isEqualTo(jobId);
                            assertThat(res.batchId()).isEqualTo(batchId);
                            assertThat(res.batchState()).isSameAs(finishedState);
                            assertThat(res.resultCoreBundle()).containsSame(coreBundle);
                            assertThat(res.issues()).isEmpty();
                        })
                        .verifyComplete();

                // optional but nice: prove the static conversion was invoked
                mocked.verify(() -> ExtractionPatientBatch.of(any()));

                verifyNoInteractions(consentHandler); // consentCodes empty branch
                verify(spyService).writeBatch(jobId.toString(), extracted);
            }
        }

        @Test
        void processBatch_whenConsentCodesPresent_callsConsentHandler() {
            UUID jobId = UUID.randomUUID();
            UUID batchId = UUID.randomUUID();

            // Mock crtdl to have consent code
            AnnotatedCrtdl crtdl = mock(AnnotatedCrtdl.class);
            TermCode termcode = new TermCode("sys", "val");
            when(crtdl.consentCodes()).thenReturn(Optional.of(Set.of(termcode)));

            JobParameters params = mock(JobParameters.class);
            when(params.crtdl()).thenReturn(crtdl);

            Job job = jobWithParameters(jobId, JobStatus.PENDING, params);

            GroupsToProcess groups = mock(GroupsToProcess.class);
            when(processedGroupFactory.create(crtdl)).thenReturn(groups);
            when(groups.directPatientCompartmentGroups()).thenReturn(List.of());
            when(groups.allGroups()).thenReturn(Map.of());

            PatientBatch rawBatch = mock(PatientBatch.class);
            when(rawBatch.batchId()).thenReturn(batchId);

            BatchState batchState = mock(BatchState.class);
            BatchState finishedState = mock(BatchState.class);
            when(batchState.finishNow(WorkUnitStatus.FINISHED)).thenReturn(finishedState);

            BatchSelection selection = mock(BatchSelection.class);
            when(selection.job()).thenReturn(job);
            when(selection.batchState()).thenReturn(batchState);
            when(selection.batch()).thenReturn(rawBatch);

            PatientBatchWithConsent bwc = mock(PatientBatchWithConsent.class);
            when(bwc.id()).thenReturn(batchId);
            when(bwc.patientIds()).thenReturn(List.of("p1"));

            when(consentHandler.fetchAndBuildConsentInfo(Set.of(termcode), rawBatch))
                    .thenReturn(Mono.just(bwc));

            when(directResourceLoader.directLoadPatientCompartment(anyList(), eq(bwc)))
                    .thenReturn(Mono.just(bwc));
            when(referenceResolver.resolvePatientBatch(eq(bwc), anyMap()))
                    .thenReturn(Mono.just(bwc));
            when(cascadingDelete.handlePatientBatch(eq(bwc), anyMap()))
                    .thenReturn(bwc);

            ExtractionPatientBatch ofResult = mock(ExtractionPatientBatch.class);
            try (MockedStatic<ExtractionPatientBatch> mocked = mockStatic(ExtractionPatientBatch.class)) {
                mocked.when(() -> ExtractionPatientBatch.of(any()))
                        .thenReturn(ofResult);

                ExtractionPatientBatch extracted = mock(ExtractionPatientBatch.class);
                when(batchCopierRedacter.transformBatch(eq(ofResult), anyMap()))
                        .thenReturn(extracted);

                ExtractionResourceBundle coreBundle = mock(ExtractionResourceBundle.class);
                when(batchToCoreWriter.toCoreBundle(extracted)).thenReturn(coreBundle);

                doReturn(Mono.empty()).when(spyService).writeBatch(eq(jobId.toString()), eq(extracted));

                StepVerifier.create(spyService.processBatch(selection))
                        .assertNext(res -> assertThat(res.batchState()).isSameAs(finishedState))
                        .verifyComplete();

                // Assert we actually hit the static conversion
                mocked.verify(() -> ExtractionPatientBatch.of(any()));

                verify(consentHandler).fetchAndBuildConsentInfo(Set.of(termcode), rawBatch);
            }
        }

        @Test
        void processBatch_whenConsentViolated_switchIfEmptyReturnsSkippedResult() {
            UUID jobId = UUID.randomUUID();
            UUID batchId = UUID.randomUUID();

            AnnotatedCrtdl crtdl = mock(AnnotatedCrtdl.class);
            TermCode termcode = new TermCode("sys", "val");
            when(crtdl.consentCodes()).thenReturn(Optional.of(Set.of(termcode)));

            JobParameters params = mock(JobParameters.class);
            when(params.crtdl()).thenReturn(crtdl);

            Job job = jobWithParameters(jobId, JobStatus.PENDING, params);

            when(processedGroupFactory.create(crtdl)).thenReturn(mock(GroupsToProcess.class));

            PatientBatch rawBatch = mock(PatientBatch.class);
            when(rawBatch.batchId()).thenReturn(batchId);

            BatchState batchState = mock(BatchState.class);
            when(batchState.batchId()).thenReturn(batchId);
            BatchState skippedState = mock(BatchState.class);
            when(batchState.skip()).thenReturn(skippedState);

            BatchSelection selection = mock(BatchSelection.class);
            when(selection.job()).thenReturn(job);
            when(selection.batchState()).thenReturn(batchState);
            when(selection.batch()).thenReturn(rawBatch);

            when(consentHandler.fetchAndBuildConsentInfo(Set.of(termcode), rawBatch))
                    .thenReturn(Mono.error(new ConsentViolatedException("no consent")));

            StepVerifier.create(spyService.processBatch(selection))
                    .assertNext(res -> {
                        assertThat(res.jobId()).isEqualTo(jobId);
                        assertThat(res.batchId()).isEqualTo(batchId);
                        assertThat(res.batchState()).isSameAs(skippedState);
                        assertThat(res.resultCoreBundle()).isEmpty();
                        assertThat(res.issues()).hasSize(1);
                        assertThat(res.issues().get(0).severity()).isEqualTo(Severity.WARNING);
                        assertThat(res.issues().get(0).msg()).contains("skipped");
                    })
                    .verifyComplete();

            verifyNoInteractions(directResourceLoader, referenceResolver, batchCopierRedacter, batchToCoreWriter);
        }
    }

    // -------------------------------------------------------------------------
    // writeBatch / writeBundle branches
    // -------------------------------------------------------------------------

    @Nested
    class WriteMethodsTests {

        @Test
        void writeBatch_success_completes() throws Exception {
            ExtractionPatientBatch batch = mock(ExtractionPatientBatch.class);

            StepVerifier.create(service.writeBatch("job", batch))
                    .verifyComplete();

            verify(resultFileManager).saveBatchToNDJSON("job", batch);
        }

        @Test
        void writeBatch_whenIOException_emitsError() throws Exception {
            ExtractionPatientBatch batch = mock(ExtractionPatientBatch.class);
            doThrow(new IOException("disk full"))
                    .when(resultFileManager).saveBatchToNDJSON("job", batch);

            StepVerifier.create(service.writeBatch("job", batch))
                    .verifyErrorSatisfies(e -> {
                        assertThat(e).isInstanceOf(IOException.class);
                        assertThat(e).hasMessageContaining("disk full");
                    });
        }

        @Test
        void writeBundle_success_completes() throws Exception {
            ExtractionResourceBundle bundle = mock(ExtractionResourceBundle.class);

            StepVerifier.create(service.writeBundle("job", bundle))
                    .verifyComplete();

            verify(resultFileManager).saveCoreBundleToNDJSON("job", bundle);
        }

        @Test
        void writeBundle_whenIOException_emitsError() throws Exception {
            ExtractionResourceBundle bundle = mock(ExtractionResourceBundle.class);
            doThrow(new IOException("no space"))
                    .when(resultFileManager).saveCoreBundleToNDJSON("job", bundle);

            StepVerifier.create(service.writeBundle("job", bundle))
                    .verifyErrorSatisfies(e -> {
                        assertThat(e).isInstanceOf(IOException.class);
                        assertThat(e).hasMessageContaining("no space");
                    });
        }
    }

    // -------------------------------------------------------------------------
    // processCore branches (transformed empty vs not)
    // -------------------------------------------------------------------------

    @Nested
    class ProcessCoreTests {

        @Test
        void processCore_whenTransformedEmpty_returnsSkipped_andDoesNotWrite() {
            UUID jobId = UUID.randomUUID();

            // Reuse fixture job with EMPTY_PARAMETERS (crtdl exists, consent not relevant here)
            Job job = job(jobId, JobStatus.PENDING, WorkUnitState.initNow(), Map.of(), WorkUnitState.initNow());

            AnnotatedCrtdl crtdl = job.parameters().crtdl();

            GroupsToProcess groups = mock(GroupsToProcess.class);
            when(processedGroupFactory.create(crtdl)).thenReturn(groups);
            when(groups.directNoPatientGroups()).thenReturn(List.of());
            when(groups.allGroups()).thenReturn(Map.of());

            ResourceBundle rb = new ResourceBundle();
            when(directResourceLoader.processCoreAttributeGroups(anyList(), any(ResourceBundle.class)))
                    .thenReturn(Mono.just(rb));
            when(referenceResolver.resolveCoreBundle(eq(rb), anyMap()))
                    .thenReturn(Mono.just(rb));

            // keep datastore phase trivial (no missing chunks)
            when(dataStore.groupReferencesByTypeInChunks(any())).thenReturn(List.of());

            ExtractionResourceBundle transformed = mock(ExtractionResourceBundle.class);
            when(transformed.isEmpty()).thenReturn(true);

            when(batchCopierRedacter.transformBundle(any(ExtractionResourceBundle.class), anyMap()))
                    .thenReturn(transformed);


            StepVerifier.create(spyService.processCore(job, new ExtractionResourceBundle()))
                    .assertNext(res -> {
                        assertThat(res.jobId()).isEqualTo(jobId);
                        assertThat(res.status()).isEqualTo(WorkUnitStatus.SKIPPED);
                    })
                    .verifyComplete();

            verify(spyService, never()).writeBundle(anyString(), any());
        }

        @Test
        void processCore_whenTransformedNonEmpty_writesAndReturnsFinished() {
            UUID jobId = UUID.randomUUID();

            Job job = job(jobId, JobStatus.PENDING, WorkUnitState.initNow(), Map.of(), WorkUnitState.initNow());
            AnnotatedCrtdl crtdl = job.parameters().crtdl();

            GroupsToProcess groups = mock(GroupsToProcess.class);
            when(processedGroupFactory.create(crtdl)).thenReturn(groups);
            when(groups.directNoPatientGroups()).thenReturn(List.of());
            when(groups.allGroups()).thenReturn(Map.of());

            ResourceBundle rb = new ResourceBundle();
            when(directResourceLoader.processCoreAttributeGroups(anyList(), any(ResourceBundle.class)))
                    .thenReturn(Mono.just(rb));
            when(referenceResolver.resolveCoreBundle(eq(rb), anyMap()))
                    .thenReturn(Mono.just(rb));

            when(dataStore.groupReferencesByTypeInChunks(any())).thenReturn(List.of());

            ExtractionResourceBundle transformed = mock(ExtractionResourceBundle.class);
            when(transformed.isEmpty()).thenReturn(false);

            when(batchCopierRedacter.transformBundle(any(ExtractionResourceBundle.class), anyMap()))
                    .thenReturn(transformed);

            doReturn(Mono.empty()).when(spyService).writeBundle(eq(jobId.toString()), eq(transformed));

            StepVerifier.create(spyService.processCore(job, new ExtractionResourceBundle()))
                    .assertNext(res -> {
                        assertThat(res.jobId()).isEqualTo(jobId);
                        assertThat(res.status()).isEqualTo(WorkUnitStatus.FINISHED);
                    })
                    .verifyComplete();

            verify(spyService).writeBundle(jobId.toString(), transformed);
        }
    }
}
