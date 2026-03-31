package de.medizininformatikinitiative.torch.diagnostics;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CriterionCountsTest {

    @Nested
    class Constructor {

        @Test
        void throwsWhenPatientsExcludedIsNegative() {
            assertThatThrownBy(() -> new CriterionCounts(-1, 0, 0, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("patientsExcluded");
        }

        @Test
        void throwsWhenResourcesExcludedIsNegative() {
            assertThatThrownBy(() -> new CriterionCounts(0, -1, 0, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("resourcesExcluded");
        }

        @Test
        void throwsWhenTotalDurationNanosIsNegative() {
            assertThatThrownBy(() -> new CriterionCounts(0, 0, -1, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("totalDurationNanos");
        }

        @Test
        void throwsWhenInvocationsIsNegative() {
            assertThatThrownBy(() -> new CriterionCounts(0, 0, 0, -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("invocations");
        }

        @Test
        void convenienceConstructor_zeroesDurationAndInvocations() {
            var counts = new CriterionCounts(3, 7);

            assertThat(counts.totalDurationNanos()).isZero();
            assertThat(counts.invocations()).isZero();
        }
    }

    @Test
    void empty_returnsZeroValues() {
        var counts = CriterionCounts.empty();

        assertThat(counts.patientsExcluded()).isZero();
        assertThat(counts.resourcesExcluded()).isZero();
        assertThat(counts.totalDurationNanos()).isZero();
        assertThat(counts.invocations()).isZero();
    }

    @Test
    void empty_returnsSameInstance() {
        assertThat(CriterionCounts.empty()).isSameAs(CriterionCounts.empty());
    }

    @Nested
    class Add {

        @Test
        void sumsBothExclusionFields() {
            var a = new CriterionCounts(3, 7);
            var b = new CriterionCounts(5, 2);

            var result = a.add(b);

            assertThat(result.patientsExcluded()).isEqualTo(8);
            assertThat(result.resourcesExcluded()).isEqualTo(9);
        }

        @Test
        void sumsTimingFields() {
            var a = new CriterionCounts(0, 0, 100, 2);
            var b = new CriterionCounts(0, 0, 300, 3);

            var result = a.add(b);

            assertThat(result.totalDurationNanos()).isEqualTo(400);
            assertThat(result.invocations()).isEqualTo(5);
        }

        @Test
        void withEmpty_returnsOriginalValues() {
            var a = new CriterionCounts(3, 7);

            var result = a.add(CriterionCounts.empty());

            assertThat(result.patientsExcluded()).isEqualTo(3);
            assertThat(result.resourcesExcluded()).isEqualTo(7);
        }
    }

    @Nested
    class PlusPatients {

        @Test
        void withZeroDelta_returnsSameInstance() {
            var counts = new CriterionCounts(3, 7);

            assertThat(counts.plusPatients(0)).isSameAs(counts);
        }

        @Test
        void throwsWhenNegative() {
            var counts = new CriterionCounts(3, 7);

            assertThatThrownBy(() -> counts.plusPatients(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("delta");
        }

        @Test
        void withPositiveDelta_incrementsOnlyPatients() {
            var counts = new CriterionCounts(3, 7);

            var result = counts.plusPatients(5);

            assertThat(result.patientsExcluded()).isEqualTo(8);
            assertThat(result.resourcesExcluded()).isEqualTo(7);
        }

        @Test
        void preservesTimingFields() {
            var counts = new CriterionCounts(1, 2, 500, 3);

            var result = counts.plusPatients(10);

            assertThat(result.totalDurationNanos()).isEqualTo(500);
            assertThat(result.invocations()).isEqualTo(3);
        }
    }

    @Nested
    class PlusResources {

        @Test
        void withZeroDelta_returnsSameInstance() {
            var counts = new CriterionCounts(3, 7);

            assertThat(counts.plusResources(0)).isSameAs(counts);
        }

        @Test
        void throwsWhenNegative() {
            var counts = new CriterionCounts(3, 7);

            assertThatThrownBy(() -> counts.plusResources(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("delta");
        }

        @Test
        void withPositiveDelta_incrementsOnlyResources() {
            var counts = new CriterionCounts(3, 7);

            var result = counts.plusResources(4);

            assertThat(result.patientsExcluded()).isEqualTo(3);
            assertThat(result.resourcesExcluded()).isEqualTo(11);
        }

        @Test
        void preservesTimingFields() {
            var counts = new CriterionCounts(1, 2, 500, 3);

            var result = counts.plusResources(10);

            assertThat(result.totalDurationNanos()).isEqualTo(500);
            assertThat(result.invocations()).isEqualTo(3);
        }
    }

    @Nested
    class PlusDuration {

        @Test
        void withZeroDuration_returnsSameInstance() {
            var counts = new CriterionCounts(3, 7);

            assertThat(counts.plusDuration(0)).isSameAs(counts);
        }

        @Test
        void accumulatesDurationAndInvocations() {
            var result = CriterionCounts.empty()
                    .plusDuration(100)
                    .plusDuration(200);

            assertThat(result.totalDurationNanos()).isEqualTo(300);
            assertThat(result.invocations()).isEqualTo(2);
        }

        @Test
        void preservesExclusionFields() {
            var counts = new CriterionCounts(3, 7);

            var result = counts.plusDuration(100);

            assertThat(result.patientsExcluded()).isEqualTo(3);
            assertThat(result.resourcesExcluded()).isEqualTo(7);
        }

        @Test
        void throwsWhenNegative() {
            assertThatThrownBy(() -> CriterionCounts.empty().plusDuration(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("durationNanos");
        }
    }

    @Nested
    class AverageDurationNanos {

        @Test
        void returnsZeroWhenNoInvocations() {
            var counts = CriterionCounts.empty();

            assertThat(counts.averageDurationNanos()).isZero();
        }

        @Test
        void returnsTotalDividedByInvocations() {
            var counts = new CriterionCounts(0, 0, 300, 3);

            assertThat(counts.averageDurationNanos()).isEqualTo(100);
        }
    }
}
