package de.medizininformatikinitiative.torch.consent;

import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Meta;

/**
 * Reduces fetched Consent/Encounter resources to the minimum fields needed to recalculate
 * consent time windows, for use in the consent audit trail.
 *
 * @see de.medizininformatikinitiative.torch.diagnostics.ConsentAudit
 */
public class ConsentAuditMinimizer {

    private ConsentAuditMinimizer() {
    }

    /**
     * Reduces a Consent resource to id, meta.profile, patient, provision, dateTime and status.
     */
    public static Consent minimize(Consent source) {
        Consent minimized = new Consent();
        minimized.setId(source.getIdPart());
        minimized.setMeta(minimizedMeta(source.getMeta()));
        minimized.setPatient(source.getPatient());
        minimized.setProvision(source.getProvision());
        minimized.setDateTimeElement(source.getDateTimeElement());
        minimized.setStatus(source.getStatus());
        return minimized;
    }

    /**
     * Reduces an Encounter resource to id, meta.profile, subject and period.
     */
    public static Encounter minimize(Encounter source) {
        Encounter minimized = new Encounter();
        minimized.setId(source.getIdPart());
        minimized.setMeta(minimizedMeta(source.getMeta()));
        minimized.setSubject(source.getSubject());
        minimized.setPeriod(source.getPeriod());
        return minimized;
    }

    private static Meta minimizedMeta(Meta source) {
        Meta meta = new Meta();
        source.getProfile().forEach(profile -> meta.addProfile(profile.getValue()));
        return meta;
    }
}
