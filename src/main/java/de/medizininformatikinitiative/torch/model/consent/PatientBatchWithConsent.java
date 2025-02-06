package de.medizininformatikinitiative.torch.model.consent;

import de.medizininformatikinitiative.torch.model.PatientBatch;
import de.medizininformatikinitiative.torch.model.PatientResourceBundle;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @param bundles      Map of bundles keyed with Patient ID
 * @param applyConsent
 */
public record PatientBatchWithConsent(
        Map<String, PatientResourceBundle> bundles,
        Boolean applyConsent) {
    public PatientBatchWithConsent {
        bundles = Map.copyOf(bundles);
    }


    public PatientBatch patientBatch() {
        return new PatientBatch(bundles.keySet().stream().toList());
    }

    public static PatientBatchWithConsent fromBatch(PatientBatch batch) {
        return new PatientBatchWithConsent(batch.ids().stream().collect(
                Collectors.toMap(Function.identity(), PatientResourceBundle::new)), false);
    }

    public static PatientBatchWithConsent fromList(List<PatientResourceBundle> batch) {
        return new PatientBatchWithConsent(batch.stream()
                .collect(Collectors.toMap(PatientResourceBundle::patientId, Function.identity())), false);
    }

    public Collection<String> keySet() {
        return bundles.keySet();
    }


    public PatientResourceBundle get(String id) {
        return bundles.get(id);
    }

    public PatientBatchWithConsent keep(Collection<String> safeSet) {
        Map<String, PatientResourceBundle> filtered = bundles.entrySet().stream()
                .filter(entry -> safeSet.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return new PatientBatchWithConsent(filtered, applyConsent);
    }

}
