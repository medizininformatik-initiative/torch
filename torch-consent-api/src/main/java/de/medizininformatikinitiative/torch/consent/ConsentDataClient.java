package de.medizininformatikinitiative.torch.consent;

import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.Encounter;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Narrow FHIR search port a {@link ConsentEvaluator} implementation can use to fetch consent-related
 * resources, without depending on the host application's full FHIR client or internal search-query
 * builder types. The host application provides the implementation.
 */
public interface ConsentDataClient {

    /**
     * Searches for {@code Consent} resources for the given patients that conform to (or are a
     * sub-profile of) {@code profileUrl}.
     *
     * @param patientIds the patient IDs to search within
     * @param profileUrl the StructureDefinition profile URL to filter by
     * @return a {@link Flux} of matching consent resources paired with their patient ID
     */
    Flux<PatientResource<Consent>> searchActiveConsentsByProfile(List<String> patientIds, String profileUrl);

    /**
     * Searches for {@code Encounter} resources for the given patients that conform to (or are a
     * sub-profile of) {@code profileUrl}.
     *
     * @param patientIds the patient IDs to search within
     * @param profileUrl the StructureDefinition profile URL to filter by
     * @return a {@link Flux} of matching encounter resources paired with their patient ID
     */
    Flux<PatientResource<Encounter>> searchEncountersByProfile(List<String> patientIds, String profileUrl);
}
