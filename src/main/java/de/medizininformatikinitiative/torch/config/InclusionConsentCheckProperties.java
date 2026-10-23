package de.medizininformatikinitiative.torch.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings for checking the cohort's inclusion criteria against the patients' consent windows.
 *
 * @param enabled          whether patients are removed whose inclusion criteria are only met by unconsented resources
 * @param fhirMappingsFile Flare's FHIR search mapping of the ontology, used to translate the inclusion criteria
 */
@ConfigurationProperties(prefix = "torch.inclusion-consent-check")
@Validated
public record InclusionConsentCheckProperties(
        boolean enabled,
        @NotBlank(message = "FHIR mappings file path is required") String fhirMappingsFile
) {
}
