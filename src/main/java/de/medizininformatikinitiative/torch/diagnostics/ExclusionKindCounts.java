package de.medizininformatikinitiative.torch.diagnostics;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Aggregated exclusion counts for a single {@link ExclusionKind}.
 *
 * @param kind              the reason patients or resources were excluded
 * @param patientsExcluded  total patients excluded for this reason
 * @param resourcesExcluded total resources excluded for this reason
 */
public record ExclusionKindCounts(
        @JsonProperty ExclusionKind kind,
        @JsonProperty long patientsExcluded,
        @JsonProperty long resourcesExcluded
) {
}
