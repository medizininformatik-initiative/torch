package de.medizininformatikinitiative.torch.model.extraction;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import org.hl7.fhir.r4.model.Bundle;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public record ExtractionPatientBatch(Map<String, ExtractionResourceBundle> bundles,
                                     ExtractionResourceBundle coreBundle, java.util.UUID id) {

    public ExtractionPatientBatch(Map<String, ExtractionResourceBundle> bundles, UUID id) {
        this(bundles, new ExtractionResourceBundle(), id);
    }

    public static ExtractionPatientBatch of(PatientBatchWithConsent patientBatch) {
        Map<String, ExtractionResourceBundle> converted =
                patientBatch.bundles().entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                e -> ExtractionResourceBundle.of(e.getValue())   // <-- CORRECT call
                        ));
        ExtractionResourceBundle core = ExtractionResourceBundle.of(patientBatch.coreBundle());
        return new ExtractionPatientBatch(converted, core, patientBatch.id());
    }

    public Boolean isEmpty() {
        return bundles.values().stream().allMatch(ExtractionResourceBundle::isEmpty);
    }

    public int getNumPatients() {
        return bundles.size();
    }

    /**
     * Counts resources that successfully completed extraction across all patient bundles, grouped by AttributeGroup-ID.
     *
     * @return groupId to count of successfully extracted resources, summed over all patients in this batch
     */
    public Map<String, Integer> resourceInclusionCounts() {
        return resourceInclusionCounts(id -> true);
    }

    /**
     * Counts resources that successfully completed extraction and are accepted by {@code includeFilter}, across all
     * patient bundles, grouped by AttributeGroup-ID.
     *
     * @param includeFilter predicate deciding which resources are counted (e.g. to avoid double-counting resources
     *                      that are handed off to a later processing stage for a final count there)
     * @return groupId to count of successfully extracted resources, summed over all patients in this batch
     */
    public Map<String, Integer> resourceInclusionCounts(Predicate<ExtractionId> includeFilter) {
        Map<String, Integer> counts = new HashMap<>();
        bundles.values().forEach(bundle -> bundle.resourceInclusionCounts(includeFilter)
                .forEach((groupId, count) -> counts.merge(groupId, count, Integer::sum)));
        return counts;
    }

    public void writeToFhirBundles(FhirContext fhirContext, Writer out, String extractionId) throws IOException {
        for (Bundle fhirBundle : bundles.values().stream().map(bundle -> bundle.toFhirBundle(extractionId)).toList()) {
            fhirContext.newJsonParser().setPrettyPrint(false).encodeResourceToWriter(fhirBundle, out);
            out.append("\n");
        }
    }

    public ExtractionResourceBundle get(String id) {
        return bundles.get(id);
    }

    /** Total number of present (non-empty) cached resources across all patient bundles. */
    public long totalResources() {
        return bundles.values().stream()
                .mapToLong(b -> b.cache().values().stream()
                        .filter(java.util.Optional::isPresent)
                        .count())
                .sum();
    }
}
