package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.model.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.ResourceBundle;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

public class BatchReferenceProcessor {

    private final ReferenceResolver referenceResolver;

    public BatchReferenceProcessor(ReferenceResolver referenceResolver) {
        this.referenceResolver = referenceResolver;
    }

    /**
     * Takes the patient batches and applies reference resolving onto every single patient.
     * After all patients are processed and written into the updated patientbatches.
     * Core Bundle is collected into its own PatientResourceBundle in a separate Batch
     *
     * @param batches            Patientbatch containing the underlying patients to be processed.
     * @param coreResourceBundle shared across all patients with an underlying concurrent hashmap
     * @param groupMap
     * @return processed Batches
     */
    public Mono<List<PatientBatchWithConsent>> processBatches(
            List<PatientBatchWithConsent> batches, Mono<ResourceBundle> coreResourceBundle, Map<String, AnnotatedAttributeGroup> groupMap) {
        return coreResourceBundle.flatMap(coreBundle ->
                Flux.fromIterable(batches)
                        .flatMap(batch ->
                                Flux.fromIterable(batch.bundles().entrySet())
                                        .flatMap(entry ->
                                                referenceResolver.resolvePatient(entry.getValue(), coreBundle, batch.applyConsent(), groupMap)
                                                        .map(updatedBundle -> Map.entry(entry.getKey(), updatedBundle))
                                        )
                                        .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                                        .map(updatedBundles -> new PatientBatchWithConsent(updatedBundles, batch.applyConsent()))
                        )
                        .collectList()
                        .flatMap(updatedBatches ->
                                referenceResolver.resolveCoreBundle(coreBundle, groupMap)
                                        .map(resourceBundle -> {
                                            PatientResourceBundle corePatientBundle = new PatientResourceBundle("CORE", resourceBundle);
                                            PatientBatchWithConsent coreBundleBatch = new PatientBatchWithConsent(
                                                    Map.of("CORE", corePatientBundle),
                                                    false
                                            );

                                            updatedBatches.add(coreBundleBatch);
                                            return updatedBatches;
                                        })
                        )
        );
    }

}

