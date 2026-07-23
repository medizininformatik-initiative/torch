package de.medizininformatikinitiative.torch.diagnostics;

/**
 * Single exclusion event recorded during data extraction.
 *
 * <p>The record is intentionally event-level rather than aggregated. This preserves
 * the patient, resource, group, and attribute context needed for debugging and for
 * future job-level summaries at {@code attributeRef} granularity.</p>
 *
 * <p>For patient-level exclusions, {@code resourceId} is usually {@code null}. For
 * resource-level exclusions, {@code resourceId} identifies the resource that was
 * removed or could not be resolved. For core resources outside the patient compartment,
 * {@code patientId} may be {@code null}.</p>
 *
 * @param patientId    FHIR patient id affected by the exclusion; {@code null} for core
 *                     resources that are not in the patient compartment
 * @param reason       high-level reason why the patient or resource was excluded
 * @param groupRef     CRTDL group/profile reference associated with the exclusion;
 *                     {@code null} when no group context applies
 * @param resourceId   FHIR resource id, for example {@code Observation/abc};
 *                     {@code null} for patient-level exclusions
 * @param attributeRef FHIR element id of the missing or violated attribute;
 *                     {@code null} when the exclusion is not attribute-specific
 */
public record ExclusionRecord(
        String patientId,
        ExclusionKind reason,
        String groupRef,
        String resourceId,
        String attributeRef
) {
}
