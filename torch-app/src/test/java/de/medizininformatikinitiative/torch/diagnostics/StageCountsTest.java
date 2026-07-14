package de.medizininformatikinitiative.torch.diagnostics;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StageCountsTest {

    @Nested
    class Constructor {

        @Test
        void rejectsNegativeDuration() {
            assertThatThrownBy(() -> new StageCounts(-1, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsNegativeResources() {
            assertThatThrownBy(() -> new StageCounts(0, -1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class ResourcesPerMinute {

        @Test
        void zeroDuration_returnsZero() {
            assertThat(new StageCounts(0, 100).resourcesPerMinute()).isEqualTo(0);
        }

        @Test
        void zeroResources_returnsZero() {
            assertThat(new StageCounts(60_000L, 0).resourcesPerMinute()).isEqualTo(0);
        }

        @Test
        void oneMinuteDuration_returnsResourceCount() {
            assertThat(new StageCounts(60_000L, 120).resourcesPerMinute()).isEqualTo(120);
        }

        @Test
        void halfMinuteDuration_doublesRate() {
            assertThat(new StageCounts(30_000L, 60).resourcesPerMinute()).isEqualTo(120);
        }
    }

    @Nested
    class Add {

        @Test
        void sumsBothFields() {
            var a = new StageCounts(100, 10);
            var b = new StageCounts(200, 20);

            var sum = a.add(b);

            assertThat(sum.durationMs()).isEqualTo(300);
            assertThat(sum.resourcesProcessed()).isEqualTo(30);
        }
    }
}
