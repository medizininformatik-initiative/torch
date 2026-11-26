package de.medizininformatikinitiative.torch.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Settings for checking the cohort's inclusion criteria against the patients' consent windows.
 *
 * @param enabled          whether patients are removed whose inclusion criteria are only met by unconsented resources
 * @param fhirMappingsFile Flare's FHIR search mapping of the ontology, used to translate the inclusion criteria
 * @param consentSearchParamsFile the FHIR search param of each consent date field of {@code type_to_consent.json},
 *                                used to restrict the translated queries to the consent window
 * @param concurrency      the number of patients of a batch whose count batch bundles run in parallel
 * @param capabilityStatementRefresh how long the FHIR server's CapabilityStatement, which tells which search params
 *                                   the translated queries may use, is reused before it is fetched again
 */
@ConfigurationProperties(prefix = "torch.inclusion-consent-check")
@Validated
public record InclusionConsentCheckProperties(
        boolean enabled,
        @NotBlank(message = "FHIR mappings file path is required") String fhirMappingsFile,
        @NotBlank(message = "Consent search params file path is required") String consentSearchParamsFile,
        @Min(value = 1, message = "Concurrency must be at least 1") int concurrency,
        @NotNull(message = "Capability statement refresh is required")
        @DurationMin(nanos = 1, message = "Capability statement refresh must be positive") Duration capabilityStatementRefresh
) {
}
