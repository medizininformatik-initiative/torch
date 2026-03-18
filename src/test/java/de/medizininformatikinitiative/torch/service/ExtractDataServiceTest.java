package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.TestUtils;
import de.medizininformatikinitiative.torch.consent.ConsentHandler;
import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.jobhandling.BatchState;
import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.JobParameters;
import de.medizininformatikinitiative.torch.jobhandling.failure.Issue;
import de.medizininformatikinitiative.torch.jobhandling.failure.Severity;
import de.medizininformatikinitiative.torch.jobhandling.result.BatchSelection;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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
    @Mock
    PostCascadeMustHaveChecker postCascadeMustHaveChecker;

    ExtractDataService service;
    ExtractDataService spyService;

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
                dataStore,
                postCascadeMustHaveChecker
        );
        spyService = Mockito.spy(service);
    }

    // -------------------------------------------------------------------------
    // processBatch branches
    // -------------------------------------------------------------------------

    @Nested
    class ProcessBatchTests {

        @Test
        void processBatch_whenPostCascadeMustHaveFailsForOnePatient_filtersPatientAndStillFinishes() throws Exception {
            UUID jobId = UUID.randomUUID();
            UUID batchId = UUID.randomUUID();
            Job job = Job.init(jobId, TestUtils.emptyJobParams());


            GroupsToProcess groups = mock(GroupsToProcess.class);
            when(processedGroupFactory.create(any())).thenReturn(groups);

            List<de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup> directPatientGroups = List.of();
            when(groups.directPatientCompartmentGroups()).thenReturn(directPatientGroups);
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

            de.medizininformatikinitiative.torch.model.management.PatientResourceBundle patient1 =
                    mock(de.medizininformatikinitiative.torch.model.management.PatientResourceBundle.class);
            de.medizininformatikinitiative.torch.model.management.PatientResourceBundle patient2 =
                    mock(de.medizininformatikinitiative.torch.model.management.PatientResourceBundle.class);

            ResourceBundle patientBundle1 = new ResourceBundle();
            ResourceBundle patientBundle2 = new ResourceBundle();

            when(patient1.bundle()).thenReturn(patientBundle1);
            when(patient2.bundle()).thenReturn(patientBundle2);

            Map<String, de.medizininformatikinitiative.torch.model.management.PatientResourceBundle> bundles =
                    new LinkedHashMap<>();
            bundles.put("p1", patient1);
            bundles.put("p2", patient2);

            PatientBatchWithConsent resolvedBatch = new PatientBatchWithConsent(
                    bundles,
                    false,
                    new ResourceBundle(),
                    batchId
            );

            when(directResourceLoader.directLoadPatientCompartment(anyList(), any()))
                    .thenReturn(Mono.just(resolvedBatch));
            when(referenceResolver.resolvePatientBatch(eq(resolvedBatch), anyMap()))
                    .thenReturn(Mono.just(resolvedBatch));
            when(cascadingDelete.handlePatientBatch(eq(resolvedBatch), anyMap()))
                    .thenReturn(resolvedBatch);

            when(postCascadeMustHaveChecker.validate(any(ResourceBundle.class), any()))
                    .thenAnswer(invocation -> {
                        ResourceBundle bundle = invocation.getArgument(0);
                        if (bundle == patientBundle1) {
                            return patientBundle1;
                        }
                        if (bundle == patientBundle2) {
                            throw new MustHaveViolatedException("missing direct group");
                        }
                        throw new AssertionError("Unexpected bundle passed to validate: " + bundle);
                    });

            ExtractionPatientBatch ofResult = mock(ExtractionPatientBatch.class);
            try (MockedStatic<ExtractionPatientBatch> mocked = mockStatic(ExtractionPatientBatch.class)) {
                mocked.when(() -> ExtractionPatientBatch.of(any())).thenAnswer(invocation -> {
                    PatientBatchWithConsent filtered = invocation.getArgument(0);
                    assertThat(filtered.patientIds()).containsExactly("p1");
                    return ofResult;
                });

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
                            assertThat(res.issues())
                                    .extracting(Issue::msg)
                                    .containsExactly(
                                            "Loaded patient compartment",
                                            "Resolved references",
                                            "Applied cascading delete",
                                            "Applied post-cascade direct must-have check",
                                            "Extraction finished",
                                            "Wrote NDJSON bundle"
                                    );
                        })
                        .verifyComplete();

                mocked.verify(() -> ExtractionPatientBatch.of(any()));

                ArgumentCaptor<ResourceBundle> bundleCaptor = ArgumentCaptor.forClass(ResourceBundle.class);
                verify(postCascadeMustHaveChecker, Mockito.times(2))
                        .validate(bundleCaptor.capture(), any());

                assertThat(bundleCaptor.getAllValues())
                        .containsExactly(patientBundle1, patientBundle2);

                verify(spyService).writeBatch(jobId.toString(), extracted);
            }
        }

        @Test
        void processBatch_whenPostCascadeMustHaveFailsForAllPatients_skipsBatchAndDoesNotEmitCoreBundle() throws Exception {
            UUID jobId = UUID.randomUUID();
            UUID batchId = UUID.randomUUID();
            Job job = Job.init(jobId, TestUtils.emptyJobParams());

            GroupsToProcess groups = mock(GroupsToProcess.class);
            when(processedGroupFactory.create(any())).thenReturn(groups);

            List<de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup> directPatientGroups = List.of();
            when(groups.directPatientCompartmentGroups()).thenReturn(directPatientGroups);
            when(groups.allGroups()).thenReturn(Map.of());

            PatientBatch rawBatch = mock(PatientBatch.class);
            when(rawBatch.batchId()).thenReturn(batchId);

            BatchState batchState = mock(BatchState.class);
            BatchState skippedState = mock(BatchState.class);
            when(batchState.skip()).thenReturn(skippedState);

            BatchSelection selection = mock(BatchSelection.class);
            when(selection.job()).thenReturn(job);
            when(selection.batchState()).thenReturn(batchState);
            when(selection.batch()).thenReturn(rawBatch);

            de.medizininformatikinitiative.torch.model.management.PatientResourceBundle patient1 =
                    mock(de.medizininformatikinitiative.torch.model.management.PatientResourceBundle.class);
            de.medizininformatikinitiative.torch.model.management.PatientResourceBundle patient2 =
                    mock(de.medizininformatikinitiative.torch.model.management.PatientResourceBundle.class);

            ResourceBundle patientBundle1 = new ResourceBundle();
            ResourceBundle patientBundle2 = new ResourceBundle();

            when(patient1.bundle()).thenReturn(patientBundle1);
            when(patient2.bundle()).thenReturn(patientBundle2);

            Map<String, de.medizininformatikinitiative.torch.model.management.PatientResourceBundle> bundles =
                    new LinkedHashMap<>();
            bundles.put("p1", patient1);
            bundles.put("p2", patient2);

            PatientBatchWithConsent resolvedBatch = new PatientBatchWithConsent(
                    bundles,
                    false,
                    new ResourceBundle(),
                    batchId
            );

            when(directResourceLoader.directLoadPatientCompartment(anyList(), any()))
                    .thenReturn(Mono.just(resolvedBatch));
            when(referenceResolver.resolvePatientBatch(eq(resolvedBatch), anyMap()))
                    .thenReturn(Mono.just(resolvedBatch));
            when(cascadingDelete.handlePatientBatch(eq(resolvedBatch), anyMap()))
                    .thenReturn(resolvedBatch);

            when(postCascadeMustHaveChecker.validate(any(ResourceBundle.class), any()))
                    .thenThrow(new MustHaveViolatedException("missing direct group"));

            ExtractionPatientBatch ofResult = mock(ExtractionPatientBatch.class);
            try (MockedStatic<ExtractionPatientBatch> mocked = mockStatic(ExtractionPatientBatch.class)) {
                mocked.when(() -> ExtractionPatientBatch.of(any())).thenAnswer(invocation -> {
                    PatientBatchWithConsent filtered = invocation.getArgument(0);
                    assertThat(filtered.patientIds()).isEmpty();
                    return ofResult;
                });

                ExtractionPatientBatch extracted = mock(ExtractionPatientBatch.class);
                when(batchCopierRedacter.transformBatch(eq(ofResult), anyMap()))
                        .thenReturn(extracted);
                when(extracted.isEmpty()).thenReturn(true);

                doReturn(Mono.empty()).when(spyService).writeBatch(eq(jobId.toString()), eq(extracted));

                StepVerifier.create(spyService.processBatch(selection))
                        .assertNext(res -> {
                            assertThat(res.jobId()).isEqualTo(jobId);
                            assertThat(res.batchId()).isEqualTo(batchId);
                            assertThat(res.batchState()).isSameAs(skippedState);
                            assertThat(res.resultCoreBundle()).isEmpty();
                            assertThat(res.issues())
                                    .extracting(Issue::msg)
                                    .containsExactly(
                                            "Loaded patient compartment",
                                            "Resolved references",
                                            "Applied cascading delete",
                                            "Applied post-cascade direct must-have check",
                                            "Extraction finished",
                                            "Wrote NDJSON bundle"
                                    );
                        })
                        .verifyComplete();

                mocked.verify(() -> ExtractionPatientBatch.of(any()));
                verify(postCascadeMustHaveChecker, Mockito.times(2)).validate(any(ResourceBundle.class), any());
                verify(spyService).writeBatch(jobId.toString(), extracted);
                verify(batchToCoreWriter, never()).toCoreBundle(any());
            }
        }

        @Test
        void processBatch_whenNoConsentCodes_happyPath_finishesAndEmitsCoreBundle() {
            UUID jobId = UUID.randomUUID();
            UUID batchId = UUID.randomUUID();
            Job job = Job.init(jobId, TestUtils.emptyJobParams());

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
                            assertThat(res.issues())
                                    .extracting(Issue::msg)
                                    .containsExactly(
                                            "Loaded patient compartment",
                                            "Resolved references",
                                            "Applied cascading delete",
                                            "Applied post-cascade direct must-have check",
                                            "Extraction finished",
                                            "Wrote NDJSON bundle"
                                    );
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

            Job job = Job.init(jobId, params);

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

            Job job = Job.init(jobId, params);


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
            Job job = Job.init(jobId, TestUtils.emptyJobParams());


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
            Job job = Job.init(jobId, TestUtils.emptyJobParams());

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

        @Test
        void processCore_whenPostCascadeMustHaveFails_emitsError() throws Exception {
            Job job = Job.init(UUID.randomUUID(), TestUtils.emptyJobParams());
            AnnotatedCrtdl crtdl = job.parameters().crtdl();

            GroupsToProcess groups = mock(GroupsToProcess.class);
            when(processedGroupFactory.create(crtdl)).thenReturn(groups);

            @SuppressWarnings("unchecked")
            List directNoPatientGroups = List.of();
            when(groups.directNoPatientGroups()).thenReturn(directNoPatientGroups);
            when(groups.allGroups()).thenReturn(Map.of());

            ResourceBundle rb = new ResourceBundle();
            when(directResourceLoader.processCoreAttributeGroups(anyList(), any(ResourceBundle.class)))
                    .thenReturn(Mono.just(rb));
            when(referenceResolver.resolveCoreBundle(eq(rb), anyMap()))
                    .thenReturn(Mono.just(rb));

            doThrow(new MustHaveViolatedException("required direct core group missing"))
                    .when(postCascadeMustHaveChecker).validate(rb, directNoPatientGroups);

            StepVerifier.create(spyService.processCore(job, new ExtractionResourceBundle()))
                    .verifyErrorSatisfies(e -> {
                        assertThat(e).isInstanceOf(MustHaveViolatedException.class);
                        assertThat(e).hasMessageContaining("required direct core group missing");
                    });

            verify(postCascadeMustHaveChecker).validate(rb, directNoPatientGroups);
            verify(spyService, never()).writeBundle(anyString(), any());
            verifyNoInteractions(dataStore);
        }
    }
}
