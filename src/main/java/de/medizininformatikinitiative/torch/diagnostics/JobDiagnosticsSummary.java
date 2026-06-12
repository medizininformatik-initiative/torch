package de.medizininformatikinitiative.torch.diagnostics;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Compact, shareable summary of job diagnostics aggregated by exclusion kind.
 *
 * @param cohortPatientsTotal total patients in the cohort
 * @param finalPatientsTotal  total patients remaining after all exclusions
 * @param exclusionsByKind    per-kind patient and resource exclusion totals derived from the exclusion log
 */
public record JobDiagnosticsSummary(
        @JsonProperty long cohortPatientsTotal,
        @JsonProperty long finalPatientsTotal,
        @JsonProperty List<ExclusionKindCounts> exclusionsByKind
) {

    public static JobDiagnosticsSummary from(JobDiagnostics diag, List<ExclusionRecord> exclusions) {
        Map<ExclusionKind, long[]> byKind = new EnumMap<>(ExclusionKind.class);
        for (ExclusionRecord r : exclusions) {
            long[] c = byKind.computeIfAbsent(r.reason(), k -> new long[2]);
            if (r.resourceId() == null) {
                c[0]++; // patient-level
            } else {
                c[1]++; // resource-level
            }
        }
        List<ExclusionKindCounts> list = byKind.entrySet().stream()
                .map(e -> new ExclusionKindCounts(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .toList();
        return new JobDiagnosticsSummary(diag.cohortPatientsTotal(), diag.finalPatientsTotal(), list);
    }
}
