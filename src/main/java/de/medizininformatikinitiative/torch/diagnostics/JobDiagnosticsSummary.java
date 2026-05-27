package de.medizininformatikinitiative.torch.diagnostics;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Compact, shareable summary of job diagnostics aggregated by exclusion kind.
 *
 * @param cohortPatientsTotal total patients in the cohort
 * @param finalPatientsTotal  total patients remaining after all exclusions
 * @param exclusionsByKind    per-kind patient and resource exclusion totals
 */
public record JobDiagnosticsSummary(
        long cohortPatientsTotal,
        long finalPatientsTotal,
        List<ExclusionKindCounts> exclusionsByKind
) {

    public static JobDiagnosticsSummary from(JobDiagnostics diag) {
        Map<ExclusionKind, long[]> byKind = new EnumMap<>(ExclusionKind.class);
        for (CriterionEntry e : diag.criteria()) {
            long[] c = byKind.computeIfAbsent(e.key().kind(), k -> new long[2]);
            c[0] += e.counts().patientsExcluded();
            c[1] += e.counts().resourcesExcluded();
        }
        List<ExclusionKindCounts> list = byKind.entrySet().stream()
                .map(e -> new ExclusionKindCounts(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .toList();
        return new JobDiagnosticsSummary(diag.cohortPatientsTotal(), diag.finalPatientsTotal(), list);
    }
}
