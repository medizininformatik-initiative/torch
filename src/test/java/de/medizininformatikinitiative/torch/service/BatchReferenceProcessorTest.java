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

    @InjectMocks
    private BatchReferenceProcessor patientBatchProcessor;

    @BeforeEach
    void setUp() {
        patientBatchProcessor = new BatchReferenceProcessor(referenceResolver);
    }

    @Test
    void processPatientBatches_shouldUpdateBatchesAndResolveCoreBundle() {
        String patientId1 = "patient1";
        String patientId2 = "patient2";

        PatientBatchWithConsent batch1 = new PatientBatchWithConsent(
                Map.of(patientId1, patientResourceBundle1, patientId2, patientResourceBundle2),
                true
        );
        List<PatientBatchWithConsent> batches = List.of(batch1);
        when(referenceResolver.resolvePatient(eq(patientResourceBundle1), any(ResourceBundle.class), eq(true)))
                .thenReturn(Mono.just(patientResourceBundle1));
        when(referenceResolver.resolvePatient(eq(patientResourceBundle2), any(ResourceBundle.class), eq(true)))
                .thenReturn(Mono.just(patientResourceBundle2));

        when(referenceResolver.resolveCoreBundle(any(ResourceBundle.class))).thenReturn(Mono.empty());


        Mono<List<PatientBatchWithConsent>> result = patientBatchProcessor.processPatientBatches(
                Mono.just(batches), Mono.just(coreResourceBundle)
        );


        StepVerifier.create(result)
                .expectNextMatches(updatedBatches -> {
                    // Ensure only one batch exists
                    if (updatedBatches.size() != 1) return false;
                    PatientBatchWithConsent updatedBatch = updatedBatches.get(0);

                    // Ensure patient bundles are correctly updated
                    return updatedBatch.bundles().containsKey(patientId1) &&
                            updatedBatch.bundles().containsKey(patientId2) &&
                            updatedBatch.applyConsent();
                })
                .verifyComplete();

        // Verify that patient bundles were resolved first
        verify(referenceResolver).resolvePatient(patientResourceBundle1, coreResourceBundle, true);
        verify(referenceResolver).resolvePatient(patientResourceBundle2, coreResourceBundle, true);

        // Verify that core bundle was resolved last
        verify(referenceResolver).resolveCoreBundle(coreResourceBundle);
    }
}
