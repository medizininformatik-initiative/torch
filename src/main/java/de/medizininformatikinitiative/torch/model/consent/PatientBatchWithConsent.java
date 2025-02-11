package de.medizininformatikinitiative.torch.model.consent;

import de.medizininformatikinitiative.torch.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.PatientBatch;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @param applyConsent Boolean if consent is to be applied
 * @param bundles      Map of bundles keyed with Patient ID
 */
public record PatientBatchWithConsent(
        boolean applyConsent,
        Map<String, PatientResourceBundle> bundles

) {
    public PatientBatchWithConsent {
        bundles = Map.copyOf(bundles);
    }


    public PatientBatch patientBatch() {
        return new PatientBatch(bundles.keySet().stream().toList());
    }

    public static PatientBatchWithConsent fromBatch(PatientBatch batch) {
        return new PatientBatchWithConsent(false, batch.ids().stream().collect(
                Collectors.toMap(Function.identity(), PatientResourceBundle::new)));
    }

    public static PatientBatchWithConsent fromList(List<PatientResourceBundle> batch) {
        return new PatientBatchWithConsent(true, batch.stream()
                .collect(Collectors.toMap(PatientResourceBundle::patientId, Function.identity())));
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

        return new PatientBatchWithConsent(applyConsent, filtered);
    }

}
