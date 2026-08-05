package de.medizininformatikinitiative.torch.diagnostics;

import org.hl7.fhir.r4.model.Resource;

import static java.util.Objects.requireNonNull;

/**
 * A single minimized Consent or Encounter resource used to calculate a patient's consent
 * time window, kept for traceability.
 *
 * @param patientId the patient the resource was fetched for
 * @param resource  the minimized Consent or Encounter resource
 */
public record ConsentAuditEntry(String patientId, Resource resource) {

    public ConsentAuditEntry {
        requireNonNull(patientId);
        requireNonNull(resource);
    }
}
