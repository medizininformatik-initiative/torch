package de.medizininformatikinitiative.torch.model.consent;

import org.hl7.fhir.r4.model.Encounter;

import java.util.Collection;

import static java.util.Objects.requireNonNull;

/**
 * @param patientId  Patient ID
 * @param provisions Map of required Provision Codes with their valid Periods
 *                   <p>
 *                   ConsentInfo
 *                   applyConsent
 *                   Map<String,
 */
public record PatientConsentInfo(
        String patientId,
        Provisions provisions
) {
    public PatientConsentInfo {
        requireNonNull(patientId);
        requireNonNull(provisions);
    }

    public PatientConsentInfo updateConsentPeriodsByPatientEncounters(Collection<Encounter> encounters) {
        return new PatientConsentInfo(patientId, provisions.updateConsentPeriodsByPatientEncounters(encounters));
    }
}
