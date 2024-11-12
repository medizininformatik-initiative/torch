package de.medizininformatikinitiative.torch.model.consent;

import de.medizininformatikinitiative.torch.model.PatientBatch;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public record ConsentInfo(
        boolean applyConsent,
        Map<String, Provisions> provisions

) {
    public ConsentInfo {
        provisions = Map.copyOf(provisions);
    }

    public PatientBatch patientBatch() {
        return new PatientBatch(provisions.keySet().stream().toList());
    }

    public static ConsentInfo fromBatch(PatientBatch batch) {
        return new ConsentInfo(false, batch.ids().stream().collect(
                Collectors.toMap(Function.identity(), id -> Provisions.of())));
    }

}
