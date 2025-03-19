package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchReferenceProcessorTest {

    @Mock
    private ReferenceResolver referenceResolver;

    @Mock
    private ResourceBundle coreResourceBundle;

    @Mock
    private PatientResourceBundle patientResourceBundle1;

    @Mock
    private PatientResourceBundle patientResourceBundle2;

    @Mock
    private PatientResourceBundle patientResourceBundle3;

    @Mock
    private ResourceBundle resolvedCoreBundle;

    @Mock
    private Map<String, AnnotatedAttributeGroup> groupMap;

    @InjectMocks
    private BatchReferenceProcessor patientBatchProcessor;

    @BeforeEach
    void setUp() {
        patientBatchProcessor = new BatchReferenceProcessor(referenceResolver);
    }

    @Test
    void processBatches_shouldHandleMultipleBatchesAndResolveCoreBundleAtEnd() {
        // Given
        String patientId1 = "patient1";
        String patientId2 = "patient2";
        String patientId3 = "patient3";
        String CORE = "CORE";

        // Create multiple patient batches
        PatientBatchWithConsent batch1 = new PatientBatchWithConsent(
                Map.of(patientId1, patientResourceBundle1, patientId2, patientResourceBundle2),
                true
        );
        PatientBatchWithConsent batch2 = new PatientBatchWithConsent(
                Map.of(patientId3, patientResourceBundle3),
                true
        );

        List<PatientBatchWithConsent> batches = List.of(batch1, batch2);


        when(referenceResolver.processSinglePatientBatch(eq(batch1), eq(coreResourceBundle), eq(groupMap)))
                .thenReturn(Mono.just(batch1));


        when(referenceResolver.processSinglePatientBatch(eq(batch2), eq(coreResourceBundle), eq(groupMap)))
                .thenReturn(Mono.just(batch2));


        when(referenceResolver.resolveCoreBundle(eq(coreResourceBundle), eq(groupMap)))
                .thenReturn(Mono.just(resolvedCoreBundle));


        Mono<List<PatientBatchWithConsent>> result = patientBatchProcessor.processBatches(
                batches, Mono.just(coreResourceBundle), groupMap
        );


        StepVerifier.create(result)
                .assertNext(updatedBatches -> {

                    // We should have 3 batches in total: 2 for patients and 1 for the core bundle
                    assertThat(updatedBatches).hasSize(3);

                    // Verify that each patient batch contains its expected patients
                    PatientBatchWithConsent updatedPatientBatch1 = updatedBatches.get(0);
                    assertThat(updatedPatientBatch1.bundles()).containsKeys(patientId1, patientId2);
                    assertThat(updatedPatientBatch1.applyConsent()).isTrue();

                    PatientBatchWithConsent updatedPatientBatch2 = updatedBatches.get(1);
                    assertThat(updatedPatientBatch2.bundles()).containsKeys(patientId3);
                    assertThat(updatedPatientBatch2.applyConsent()).isTrue();

                    // Verify that the last batch contains the core bundle
                    PatientBatchWithConsent coreBundleBatch = updatedBatches.get(2);
                    assertThat(coreBundleBatch.bundles()).containsKey(CORE);
                    assertThat(coreBundleBatch.bundles().get(CORE).bundle()).isEqualTo(resolvedCoreBundle);
                    assertThat(coreBundleBatch.applyConsent()).isFalse();
                })
                .verifyComplete();

        InOrder inOrder = inOrder(referenceResolver);
        inOrder.verify(referenceResolver).processSinglePatientBatch(batch1, coreResourceBundle, groupMap);
        inOrder.verify(referenceResolver).processSinglePatientBatch(batch2, coreResourceBundle, groupMap);
        inOrder.verify(referenceResolver).resolveCoreBundle(coreResourceBundle, groupMap);
    }

}
