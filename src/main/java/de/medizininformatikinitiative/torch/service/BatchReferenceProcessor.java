package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

public class BatchReferenceProcessor {
    private static final Logger logger = LoggerFactory.getLogger(BatchReferenceProcessor.class);

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
        //preprocess corebundle references not reference only


        return coreResourceBundle.flatMap(coreBundle ->
                Flux.fromIterable(batches)
                        // resolveReferences + extraction + file creation
                        .flatMap(batch -> referenceResolver.processSinglePatientBatch(batch, coreBundle, groupMap),
                                Runtime.getRuntime().availableProcessors()) // Process batches in parallel
                        .collectList()
                        .flatMap(updatedBatches ->
                                {
                                    return referenceResolver.resolveCoreBundle(coreBundle, groupMap)
                                            .map(resourceBundle -> {
                                                if (!resourceBundle.isEmpty()) {
                                                    PatientResourceBundle corePatientBundle = new PatientResourceBundle("CORE", resourceBundle);
                                                    PatientBatchWithConsent coreBundleBatch = new PatientBatchWithConsent(
                                                            Map.of("CORE", corePatientBundle), false);
                                                    updatedBatches.add(coreBundleBatch);
                                                }
                                                return updatedBatches;
                                            });
                                }
                        )
        );
    }
}
