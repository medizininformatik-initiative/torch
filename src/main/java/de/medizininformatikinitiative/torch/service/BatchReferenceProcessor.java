package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.model.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.ResourceBundle;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BatchReferenceProcessor {

    private final ReferenceResolver referenceResolver;

    public BatchReferenceProcessor(ReferenceResolver referenceResolver) {
        this.referenceResolver = referenceResolver;
    }

    /**
     * Takes the patient batches and applies reference resolving onto every single patient.
     * After all patients are processed and written into the updated patientbatches
     *
     * @param batches            Patientbatch containing the underlying patients to be processed.
     * @param coreResourceBundle shared across all patients with an underlying concurrent hashmap
     * @return
     */
    public Mono<List<PatientBatchWithConsent>> processPatientBatches(
            Mono<List<PatientBatchWithConsent>> batches, Mono<ResourceBundle> coreResourceBundle) {

        return coreResourceBundle.flatMap(coreBundle ->
                batches.flatMap(patientBatchList ->
                        Flux.fromIterable(patientBatchList)
                                .flatMap(batch -> {
                                    // Create a mutable map to store updated bundles
                                    Map<String, PatientResourceBundle> updatedBundles = new HashMap<>();

                                    return Flux.fromIterable(batch.bundles().entrySet())
                                            .flatMap(entry ->
                                                    referenceResolver.resolvePatient(entry.getValue(), coreBundle, batch.applyConsent())
                                                            .doOnNext(updatedBundle -> updatedBundles.put(entry.getKey(), updatedBundle))
                                            )
                                            .then(Mono.defer(() -> {
                                                PatientBatchWithConsent updatedBatch = new PatientBatchWithConsent(updatedBundles, batch.applyConsent());
                                                return Mono.just(updatedBatch);
                                            }));
                                })
                                .collectList()
                                .flatMap(updatedBatches ->
                                        coreResourceBundle.flatMap(referenceResolver::resolveCoreBundle)
                                                .thenReturn(updatedBatches)
                                )
                )
        );
    }


}
