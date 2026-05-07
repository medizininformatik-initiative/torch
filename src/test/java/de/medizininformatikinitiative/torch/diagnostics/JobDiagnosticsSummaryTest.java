package de.medizininformatikinitiative.torch.diagnostics;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JobDiagnosticsSummaryTest {

    static final UUID JOB_ID = UUID.randomUUID();

    @Nested
    class From {

        @Test
        void emptyDiagnostics_producesEmptyExclusionList() {
            var diag = new JobDiagnostics(JOB_ID, 10, 10, List.of());

            var summary = JobDiagnosticsSummary.from(diag);

            assertThat(summary.cohortPatientsTotal()).isEqualTo(10);
            assertThat(summary.finalPatientsTotal()).isEqualTo(10);
            assertThat(summary.exclusionsByKind()).isEmpty();
        }

        @Test
        void singleCriterion_producesOneSummaryEntry() {
            var key = new CriterionKey(ExclusionKind.MUST_HAVE, "obs", null, null, null);
            var counts = new CriterionCounts(3, 5);
            var diag = new JobDiagnostics(JOB_ID, 10, 7, List.of(new CriterionEntry(key, counts)));

            var summary = JobDiagnosticsSummary.from(diag);

            assertThat(summary.exclusionsByKind()).hasSize(1);
            var entry = summary.exclusionsByKind().getFirst();
            assertThat(entry.kind()).isEqualTo(ExclusionKind.MUST_HAVE);
            assertThat(entry.patientsExcluded()).isEqualTo(3);
            assertThat(entry.resourcesExcluded()).isEqualTo(5);
        }

        @Test
        void multipleCriteriaOfSameKind_aggregatesCounts() {
            var key1 = new CriterionKey(ExclusionKind.CONSENT, "a", null, null, null);
            var key2 = new CriterionKey(ExclusionKind.CONSENT, "b", null, null, null);
            var diag = new JobDiagnostics(JOB_ID, 20, 10, List.of(
                    new CriterionEntry(key1, new CriterionCounts(2, 4)),
                    new CriterionEntry(key2, new CriterionCounts(3, 6))
            ));

            var summary = JobDiagnosticsSummary.from(diag);

            assertThat(summary.exclusionsByKind()).hasSize(1);
            var entry = summary.exclusionsByKind().getFirst();
            assertThat(entry.kind()).isEqualTo(ExclusionKind.CONSENT);
            assertThat(entry.patientsExcluded()).isEqualTo(5);
            assertThat(entry.resourcesExcluded()).isEqualTo(10);
        }

        @Test
        void criteriaOfDifferentKinds_producesOneEntryPerKind() {
            var diag = new JobDiagnostics(JOB_ID, 30, 10, List.of(
                    new CriterionEntry(new CriterionKey(ExclusionKind.MUST_HAVE, null, null, null, null), new CriterionCounts(5, 0)),
                    new CriterionEntry(new CriterionKey(ExclusionKind.REFERENCE_NOT_FOUND, null, null, null, null), new CriterionCounts(0, 7))
            ));

            var summary = JobDiagnosticsSummary.from(diag);

            assertThat(summary.exclusionsByKind()).hasSize(2);
            assertThat(summary.exclusionsByKind().stream().map(ExclusionKindCounts::kind))
                    .containsExactlyInAnyOrder(ExclusionKind.MUST_HAVE, ExclusionKind.REFERENCE_NOT_FOUND);
        }
    }
}
