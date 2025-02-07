package de.medizininformatikinitiative.torch.model.consent;

import de.medizininformatikinitiative.torch.model.PatientBatch;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public record PatientBatchWithConsent(
        boolean applyConsent,
        Map<String, Provisions> provisions

) {
    /*
    PatientPackage(ResourceGroupsWrappers,Provisions,ReferenceStack,CoreResourcesWrapper)
     */
    public PatientBatchWithConsent {
        provisions = Map.copyOf(provisions);
    }

    public PatientBatch patientBatch() {
        return new PatientBatch(provisions.keySet().stream().toList());
    }

    public static PatientBatchWithConsent fromBatch(PatientBatch batch) {
        return new PatientBatchWithConsent(false, batch.ids().stream().collect(
                Collectors.toMap(Function.identity(), id -> Provisions.of())));
    }

}
