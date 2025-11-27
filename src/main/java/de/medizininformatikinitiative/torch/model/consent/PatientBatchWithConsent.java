package de.medizininformatikinitiative.torch.model.consent;

import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @param bundles      Map of bundles keyed with Patient ID
 * @param applyConsent
 * @param coreBundle
 */
public record PatientBatchWithConsent(Map<String, PatientResourceBundle> bundles, boolean applyConsent,
                                      ResourceBundle coreBundle) {

    public PatientBatchWithConsent {
        bundles = Map.copyOf(bundles);
        Objects.requireNonNull(coreBundle);
    }

    public PatientBatchWithConsent(Map<String, PatientResourceBundle> bundles) {
        this(bundles, false, new ResourceBundle());
    }

    public Boolean isEmpty() {
        return bundles.values().stream().allMatch(PatientResourceBundle::isEmpty);
    }

    public static PatientBatchWithConsent fromBatch(PatientBatch batch) {
        return new PatientBatchWithConsent(batch.ids().stream().collect(
                Collectors.toMap(Function.identity(), PatientResourceBundle::new)), false, new ResourceBundle());
    }

    public static PatientBatchWithConsent fromList(List<PatientResourceBundle> batch) {
        return new PatientBatchWithConsent(batch.stream()
                .collect(Collectors.toMap(PatientResourceBundle::patientId, Function.identity())), false, new ResourceBundle());
    }

    public static PatientBatchWithConsent fromBatchAndConsent(PatientBatch batch, Map<String, NonContinuousPeriod> consentPeriodsMap) throws ConsentViolatedException {
        // Filter only patients that have a consent period (non-empty)
        Map<String, PatientResourceBundle> filtered = batch.ids().stream()
                .filter(consentPeriodsMap::containsKey)  // patient has a consent period
                .filter(id -> !consentPeriodsMap.get(id).isEmpty()) // consent period not empty
                .collect(Collectors.toMap(
                        id -> id,
                        id -> new PatientResourceBundle(id, consentPeriodsMap.get(id), new ResourceBundle())
                ));

        if (filtered.isEmpty()) {
            throw new ConsentViolatedException("No patients with valid consent periods found in batch");
        }

        return new PatientBatchWithConsent(filtered, true, new ResourceBundle());

    }

    public PatientBatch patientBatch() {
        return new PatientBatch(bundles.keySet().stream().toList());
    }

    public Collection<String> patientIds() {
        return bundles.keySet();
    }

    public PatientResourceBundle get(String id) {
        return bundles.get(id);
    }

    public PatientBatchWithConsent keep(Collection<String> safeSet) {
        Map<String, PatientResourceBundle> filtered = bundles.entrySet().stream()
                .filter(entry -> safeSet.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return new PatientBatchWithConsent(filtered, applyConsent, coreBundle);
    }

}
