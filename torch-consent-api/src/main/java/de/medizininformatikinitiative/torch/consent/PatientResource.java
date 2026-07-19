package de.medizininformatikinitiative.torch.consent;

import org.hl7.fhir.r4.model.Resource;

/**
 * A FHIR resource paired with the ID of the patient it was resolved for, as returned by
 * {@link ConsentDataClient}. Patient-ID resolution (following {@code subject}/{@code patient}
 * references, handling resource-specific shapes) is the host application's responsibility, not the
 * consent implementation's — see {@link ConsentDataClient}.
 *
 * @param patientId the resolved patient ID
 * @param resource  the FHIR resource
 */
public record PatientResource<T extends Resource>(String patientId, T resource) {
}
