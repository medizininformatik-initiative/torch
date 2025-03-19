package de.medizininformatikinitiative.torch.model.consent;

import de.medizininformatikinitiative.torch.model.fhir.QueryParams;
import de.medizininformatikinitiative.torch.model.management.ImmutableResourceBundle;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.multiStringValue;

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

    private static String patientSearchParam(String type) {
        return "Patient".equals(type) ? "_id" : "patient";
    }


    public PatientBatch patientBatch() {
        return new PatientBatch(bundles.keySet().stream().toList());
    }


    public static PatientBatchWithConsent fromBatchWithStaticInfo(PatientBatch batch, ImmutableResourceBundle resourceBundle) {
        return new PatientBatchWithConsent(batch.ids().stream().collect(
                Collectors.toMap(Function.identity(), id -> new PatientResourceBundle(id, resourceBundle))), false);
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

    /**
     * Returns a search param which selects all resources of Compartment of patients defined in this batch.
     *
     * @param resourceType FHIR resource type to be searched for
     * @return Search Param
     */
    public QueryParams compartmentSearchParam(String resourceType) {
        return QueryParams.of(patientSearchParam(resourceType), searchPatientParamValue(resourceType));
    }

    private QueryParams.Value searchPatientParamValue(String type) {
        if ("Patient".equals(type)) {
            return multiStringValue(keySet().stream().toList());
        } else {
            return multiStringValue(keySet().stream().map(id -> "Patient/" + id).toList());
        }
    }


}
