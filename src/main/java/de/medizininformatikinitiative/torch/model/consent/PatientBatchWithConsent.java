package de.medizininformatikinitiative.torch.model.consent;

import de.medizininformatikinitiative.torch.diagnostics.exclusions.BatchExclusions;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.PatientExclusionStage;
import de.medizininformatikinitiative.torch.diagnostics.BatchDiagnostics;
import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @param bundles      Map of bundles keyed with Patient ID
 * @param applyConsent
 * @param coreBundle
 * @param id
 */
public record PatientBatchWithConsent(Map<String, PatientResourceBundle> bundles, boolean applyConsent,
                                      ResourceBundle coreBundle, java.util.UUID id, BatchDiagnostics diagnostics) {

    public PatientBatchWithConsent {
        bundles = Map.copyOf(bundles);
        Objects.requireNonNull(coreBundle);
    }

    public PatientBatchWithConsent(Map<String, PatientResourceBundle> bundles, UUID id, BatchDiagnostics diagnostics) {
        this(bundles, false, new ResourceBundle(), id, diagnostics);
    }

    public Boolean isEmpty() {
        return bundles.values().stream().allMatch(PatientResourceBundle::isEmpty);
    }

    public BatchExclusions batchExclusions() {
        return diagnostics().batchExclusions();
    }

    public static PatientBatchWithConsent fromBatch(PatientBatch batch) {
        return new PatientBatchWithConsent(batch.ids().stream().collect(
                Collectors.toMap(Function.identity(), PatientResourceBundle::new)), false, new ResourceBundle(), batch.batchId(), batch.diagnostics());
    }

    public static PatientBatchWithConsent fromList(List<PatientResourceBundle> batch) {
        return new PatientBatchWithConsent(batch.stream()
                .collect(Collectors.toMap(PatientResourceBundle::patientId, Function.identity())), false, new ResourceBundle(), UUID.randomUUID(), BatchDiagnostics.empty());
    }

    public static PatientBatchWithConsent fromBatchAndConsent(PatientBatch batch, Map<String, NonContinuousPeriod> consentPeriodsMap) throws ConsentViolatedException {
        Map<String, PatientResourceBundle> fulfilledConsent = new HashMap<>();
        batch.ids().forEach(id -> {
            if (consentPeriodsMap.containsKey(id) && !consentPeriodsMap.get(id).isEmpty())
                fulfilledConsent.put(id, new PatientResourceBundle(id, consentPeriodsMap.get(id), new ResourceBundle()));
            else
                batch.batchExclusions().addPatientExclusion(PatientExclusionStage.CONSENT, id);
        });

        if (fulfilledConsent.isEmpty()) {
            throw new ConsentViolatedException("No patients with valid consent periods found in batch");
        }

        return new PatientBatchWithConsent(fulfilledConsent, true, new ResourceBundle(), batch.batchId(), batch.diagnostics());

    }

    public PatientBatch patientBatch() {
        return new PatientBatch(bundles.keySet().stream().toList(), id, diagnostics);
    }

    public Collection<String> patientIds() {
        return bundles.keySet();
    }

    public PatientResourceBundle get(String id) {
        return bundles.get(id);
    }

    /** Total number of present (non-empty) cached resources across all patient bundles. */
    public long totalResources() {
        return bundles.values().stream()
                .mapToLong(prb -> prb.bundle().cache().values().stream()
                        .filter(java.util.Optional::isPresent)
                        .count())
                .sum();
    }

    public PatientBatchWithConsent keep(Collection<String> safeSet) {
        Map<String, PatientResourceBundle> filtered = bundles.entrySet().stream()
                .filter(entry -> safeSet.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return new PatientBatchWithConsent(filtered, applyConsent, coreBundle, id, diagnostics);
    }

}
