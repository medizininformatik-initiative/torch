package de.medizininformatikinitiative.torch.model.consent;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.model.fhir.QueryParams;
import de.medizininformatikinitiative.torch.model.management.ImmutableResourceBundle;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import org.hl7.fhir.r4.model.Bundle;

import java.io.IOException;
import java.io.Writer;
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
public record PatientBatchWithConsent(Map<String, PatientResourceBundle> bundles, boolean applyConsent) {

    public PatientBatchWithConsent {
        bundles = Map.copyOf(bundles);
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

        return new PatientBatchWithConsent(filtered, applyConsent);
    }

    /**
     * Returns a search param which selects all resources of Compartment of patients defined in this batch.
     *
     * @param resourceType FHIR resource type to be searched for
     * @return Search Param
     */
    public QueryParams compartmentSearchParam(String resourceType) {
        if ("Patient".equals(resourceType)) {
            return QueryParams.of("_id", multiStringValue(patientIds().stream().toList()));
        } else {
            return QueryParams.of("patient", multiStringValue(patientIds().stream().map(id -> "Patient/" + id).toList()));
        }
    }

    public void writeFhirBundlesTo(FhirContext fhirContext, Writer out) throws IOException {
        for (Bundle fhirBundle : bundles.values().stream().map(PatientResourceBundle::bundle).map(ResourceBundle::toFhirBundle).toList()) {
            fhirContext.newJsonParser().setPrettyPrint(false).encodeResourceToWriter(fhirBundle, out);
            out.append("\n");
        }
    }
}
