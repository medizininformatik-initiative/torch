package de.medizininformatikinitiative.torch.model.consent;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.model.management.CachelessResourceBundle;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Encounter;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @param bundles      Map of bundles keyed with Patient ID
 * @param applyConsent
 */
public record PatientBatchWithConsent(Map<String, PatientResourceBundle> bundles, boolean applyConsent) {

    public PatientBatchWithConsent {
        bundles = Map.copyOf(bundles);
    }

    public static PatientBatchWithConsent fromBatch(PatientBatch batch) {
        return new PatientBatchWithConsent(batch.ids().stream().collect(
                Collectors.toMap(Function.identity(), PatientResourceBundle::new)), false);
    }

    public static PatientBatchWithConsent fromList(List<PatientResourceBundle> batch) {
        return new PatientBatchWithConsent(batch.stream()
                .collect(Collectors.toMap(PatientResourceBundle::patientId, Function.identity())), false);
    }

    public void addStaticInfo(CachelessResourceBundle staticInfo) {
        bundles.values().forEach(bundle -> bundle.addStaticInfo(staticInfo));
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

    public void writeFhirBundlesTo(FhirContext fhirContext, Writer out) throws IOException {
        for (Bundle fhirBundle : bundles.values().stream().map(PatientResourceBundle::bundle).map(ResourceBundle::toFhirBundle).toList()) {
            fhirContext.newJsonParser().setPrettyPrint(false).encodeResourceToWriter(fhirBundle, out);
            out.append("\n");
        }
    }

    public PatientBatchWithConsent adjustConsentPeriodsByPatientEncounters(Map<String, Collection<Encounter>> encountersByPatient) {
        return new PatientBatchWithConsent(bundles.entrySet().stream()
                .map(entry ->
                        {
                            String patientId = entry.getKey();
                            PatientResourceBundle bundle = entry.getValue();
                            Collection<Encounter> encounters = encountersByPatient.get(patientId);

                            if (encounters == null || encounters.isEmpty()) {

                                return Map.entry(patientId, bundle);
                            } else {

                                return Map.entry(patientId, bundle.adjustConsentPeriodsByPatientEncounters(encounters));
                            }
                        }
                )
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)), applyConsent);
    }
}
