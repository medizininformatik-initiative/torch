package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionPatientBatch;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionResourceBundle;
import de.medizininformatikinitiative.torch.model.extraction.ResourceExtractionInfo;
import org.springframework.stereotype.Component;

@Component
public class PatientBatchToCoreBundleWriter {

    private final CompartmentManager compartmentManager;

    public PatientBatchToCoreBundleWriter(CompartmentManager compartmentManager) {
        this.compartmentManager = compartmentManager;
    }

    /**
     * Creates a new core bundle from a ExtractionPatientBatch
     *
     * @param batch the patient batch containing patient bundles + its own core bundle
     */
    public ExtractionResourceBundle toCoreBundle(ExtractionPatientBatch batch) {
        ExtractionResourceBundle resourceBundle = new ExtractionResourceBundle();
        handlePatientBundles(resourceBundle, batch);
        handleSourceCoreBundle(resourceBundle, batch.coreBundle());
        return resourceBundle;
    }


    /**
     * Merges all non-compartment extraction data from the patient's ExtractionBundles
     * and merges the batch's core bundle into the global {@code coreBundle}.
     *
     * @param coreBundle the global core bundle to update
     * @param batch      the patient batch containing patient bundles + its own core bundle
     */
    public void updateCore(ExtractionResourceBundle coreBundle, ExtractionPatientBatch batch) {
        handlePatientBundles(coreBundle, batch);
        handleSourceCoreBundle(coreBundle, batch.coreBundle());
    }


    /**
     * Merges all ExtractionBundles inside the provided {@link ExtractionPatientBatch} into the
     * given {@code coreBundle}. Only resources whose keys are *not* part of the compartment
     * (as defined by {@link CompartmentManager}) are merged.
     * <p>
     * Rules:
     * <ul>
     *   <li>Merge {@code extractionInfoMap} keys only if outside the compartment</li>
     *   <li>Deep-merge {@link ResourceExtractionInfo} using {@code ResourceExtractionInfo::merge}</li>
     *   <li>Put cache entries only when Optional is present and key is outside compartment</li>
     * </ul>
     *
     * @param coreBundle global core bundle that is populated
     * @param batch      patient batch containing multiple ExtractionBundles
     */
    private void handlePatientBundles(ExtractionResourceBundle coreBundle, ExtractionPatientBatch batch) {

        batch.bundles().values().forEach(bundle -> {
            bundle.extractionInfoMap().forEach((key, info) -> {
                if (!compartmentManager.isInCompartment(key)) {
                    coreBundle.extractionInfoMap().merge(
                            key,
                            info,
                            ResourceExtractionInfo::merge
                    );
                }
            });
            bundle.cache().forEach((key, value) -> {
                if (!compartmentManager.isInCompartment(key) && value.isPresent()) {
                    coreBundle.cache().put(key, value);
                }
            });
        });
    }


    /**
     * Merges the batch’s own core bundle (which is already compartment-filtered)
     * into the global core bundle.
     * <p>
     * Rules:
     * <ul>
     *   <li>Deep-merge extraction info using {@code ResourceExtractionInfo::merge}</li>
     *   <li>Only propagate cache values where Optional is present</li>
     *   <li>Assumes all keys are safe because the batch-level core bundle already
     *       excludes compartment resources</li>
     * </ul>
     *
     * @param coreBundle       global core bundle to merge into
     * @param sourceCoreBundle the batch’s compartment-filtered core bundle
     */
    private void handleSourceCoreBundle(
            ExtractionResourceBundle coreBundle,
            ExtractionResourceBundle sourceCoreBundle
    ) {

        // extractionInfoMap
        sourceCoreBundle.extractionInfoMap().forEach((key, info) ->
                coreBundle.extractionInfoMap().merge(
                        key,
                        info,
                        ResourceExtractionInfo::merge
                )
        );

        // cache
        sourceCoreBundle.cache().forEach((key, value) -> {
            if (value.isPresent()) {
                coreBundle.cache().put(key, value);
            }
        });
    }
}
