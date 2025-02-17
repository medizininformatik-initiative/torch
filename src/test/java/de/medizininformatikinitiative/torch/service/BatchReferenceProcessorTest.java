package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.model.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.ResourceBundle;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

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
    private ResourceBundle resolvedCoreBundle;

    @InjectMocks
    private BatchReferenceProcessor patientBatchProcessor;

    @BeforeEach
    void setUp() {
        patientBatchProcessor = new BatchReferenceProcessor(referenceResolver);
    }

    @Test
    void processBatches_shouldUpdateBatchesAndResolveCoreBundle() {
        String patientId1 = "patient1";
        String patientId2 = "patient2";
        String CORE = "CORE";

        // Create a batch with two patient resource bundles
        PatientBatchWithConsent batch1 = new PatientBatchWithConsent(
                Map.of(patientId1, patientResourceBundle1, patientId2, patientResourceBundle2),
                true
        );
        List<PatientBatchWithConsent> batches = List.of(batch1);

        // Mock reference resolution for patient resources
        when(referenceResolver.resolvePatient(eq(patientResourceBundle1), eq(coreResourceBundle), eq(true)))
                .thenReturn(Mono.just(patientResourceBundle1));
        when(referenceResolver.resolvePatient(eq(patientResourceBundle2), eq(coreResourceBundle), eq(true)))
                .thenReturn(Mono.just(patientResourceBundle2));

        // Mock core bundle resolution
        when(referenceResolver.resolveCoreBundle(eq(coreResourceBundle)))
                .thenReturn(Mono.just(resolvedCoreBundle));

        // Run the processBatches method
        Mono<List<PatientBatchWithConsent>> result = patientBatchProcessor.processBatches(
                Mono.just(batches), Mono.just(coreResourceBundle)
        );

        // Validate the output using StepVerifier
        StepVerifier.create(result)
                .assertNext(updatedBatches -> {
                    // Expect two batches: one with resolved patient data, one with the core bundle
                    assertThat(updatedBatches).hasSize(2);

                    // First batch should contain updated patient data
                    PatientBatchWithConsent updatedPatientBatch = updatedBatches.get(0);
                    assertThat(updatedPatientBatch.bundles()).containsKeys(patientId1, patientId2);
                    assertThat(updatedPatientBatch.applyConsent()).isTrue();

                    // Second batch should contain the resolved core bundle
                    PatientBatchWithConsent coreBundleBatch = updatedBatches.get(1);
                    assertThat(coreBundleBatch.bundles()).containsKey(CORE);
                    assertThat(coreBundleBatch.bundles().get(CORE).bundle()).isEqualTo(resolvedCoreBundle);
                    assertThat(coreBundleBatch.applyConsent()).isFalse();
                })
                .verifyComplete();

        // Verify that patient bundles were resolved first
        verify(referenceResolver).resolvePatient(patientResourceBundle1, coreResourceBundle, true);
        verify(referenceResolver).resolvePatient(patientResourceBundle2, coreResourceBundle, true);

        // Verify that core bundle was resolved last
        verify(referenceResolver).resolveCoreBundle(coreResourceBundle);
    }
}
