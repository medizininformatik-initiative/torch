package de.medizininformatikinitiative.torch.diagnostics;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JobDiagnosticsSummaryTest {

    static final UUID JOB_ID = UUID.randomUUID();

    @Nested
    class From {

        @Test
        void emptyExclusions_producesEmptyExclusionList() {
            var diag = new JobDiagnostics(JOB_ID, 10, 10, Map.of(), 0L);

            var summary = JobDiagnosticsSummary.from(diag, List.of());

            assertThat(summary.cohortPatientsTotal()).isEqualTo(10);
            assertThat(summary.finalPatientsTotal()).isEqualTo(10);
            assertThat(summary.exclusionsByKind()).isEmpty();
        }

        @Test
        void singlePatientExclusion_producesOneSummaryEntry() {
            var diag = new JobDiagnostics(JOB_ID, 10, 7, Map.of(), 0L);
            var exclusions = List.of(
                    new ExclusionRecord("p1", ExclusionKind.MUST_HAVE_RESOURCE, "grp", null, null),
                    new ExclusionRecord("p2", ExclusionKind.MUST_HAVE_RESOURCE, "grp", null, null),
                    new ExclusionRecord("p3", ExclusionKind.MUST_HAVE_RESOURCE, "grp", null, null)
            );

            var summary = JobDiagnosticsSummary.from(diag, exclusions);

            assertThat(summary.exclusionsByKind()).hasSize(1);
            var entry = summary.exclusionsByKind().getFirst();
            assertThat(entry.kind()).isEqualTo(ExclusionKind.MUST_HAVE_RESOURCE);
            assertThat(entry.patientsExcluded()).isEqualTo(3);
            assertThat(entry.resourcesExcluded()).isEqualTo(0);
        }

        @Test
        void resourceExclusion_countsInResourcesExcluded() {
            var diag = new JobDiagnostics(JOB_ID, 10, 7, Map.of(), 0L);
            var exclusions = List.of(
                    new ExclusionRecord("p1", ExclusionKind.CONSENT, null, "Observation/o1", null),
                    new ExclusionRecord("p1", ExclusionKind.CONSENT, null, "Observation/o2", null)
            );

            var summary = JobDiagnosticsSummary.from(diag, exclusions);

            assertThat(summary.exclusionsByKind()).hasSize(1);
            var entry = summary.exclusionsByKind().getFirst();
            assertThat(entry.kind()).isEqualTo(ExclusionKind.CONSENT);
            assertThat(entry.patientsExcluded()).isEqualTo(0);
            assertThat(entry.resourcesExcluded()).isEqualTo(2);
        }

        @Test
        void exclusionsOfDifferentKinds_producesOneEntryPerKind() {
            var diag = new JobDiagnostics(JOB_ID, 30, 10, Map.of(), 0L);
            var exclusions = List.of(
                    new ExclusionRecord("p1", ExclusionKind.MUST_HAVE_RESOURCE, "grp", null, null),
                    new ExclusionRecord("p2", ExclusionKind.REFERENCE_NOT_FOUND, "grp", "Obs/o1", null)
            );

            var summary = JobDiagnosticsSummary.from(diag, exclusions);

            assertThat(summary.exclusionsByKind()).hasSize(2);
            assertThat(summary.exclusionsByKind().stream().map(ExclusionKindCounts::kind))
                    .containsExactlyInAnyOrder(ExclusionKind.MUST_HAVE_RESOURCE, ExclusionKind.REFERENCE_NOT_FOUND);
        }
    }
}
