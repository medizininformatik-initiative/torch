package de.medizininformatikinitiative.torch.diagnostics;

/**
 * Aggregated exclusion counts for a single {@link ExclusionKind}.
 *
 * @param kind              the reason patients or resources were excluded
 * @param patientsExcluded  total patients excluded for this reason
 * @param resourcesExcluded total resources excluded for this reason
 */
public record ExclusionKindCounts(
        ExclusionKind kind,
        long patientsExcluded,
        long resourcesExcluded
) {
}
