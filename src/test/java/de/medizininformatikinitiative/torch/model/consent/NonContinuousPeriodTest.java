package de.medizininformatikinitiative.torch.model.consent;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NonContinuousPeriodTest {

    private static Period p(String start, String end) {
        return Period.of(LocalDate.parse(start), LocalDate.parse(end));
    }

    @Nested
    class Merge {

        @Test
        void combinesPeriods() {
            var a = new NonContinuousPeriod(List.of(p("2024-01-01", "2024-01-10")));
            var b = new NonContinuousPeriod(List.of(p("2024-02-01", "2024-02-10")));

            var result = a.merge(b);

            assertThat(result.periods()).containsExactly(
                    p("2024-01-01", "2024-01-10"),
                    p("2024-02-01", "2024-02-10")
            );
        }
    }

    @Nested
    class Update {

        @Test
        void adjustsStartWhenInsideEncounter() {
            var consent = new NonContinuousPeriod(List.of(p("2024-01-05", "2024-01-10")));
            var encounter = p("2024-01-01", "2024-01-06");

            var updated = consent.update(encounter);

            assertThat(updated.periods())
                    .containsExactly(p("2024-01-01", "2024-01-10"));
        }

        @Test
        void leavesUnchangedIfOutside() {
            var consent = new NonContinuousPeriod(List.of(p("2024-01-20", "2024-01-30")));
            var encounter = p("2024-01-01", "2024-01-10");

            var updated = consent.update(encounter);

            assertThat(updated.periods()).containsExactly(p("2024-01-20", "2024-01-30"));
        }
    }

    @Nested
    class Within {

        @Test
        void trueWhenInsidePeriod() {
            var ncp = new NonContinuousPeriod(List.of(p("2024-01-01", "2024-01-10")));
            var resource = p("2024-01-02", "2024-01-05");

            assertThat(ncp.within(resource)).isTrue();
        }

        @Test
        void falseWhenOutside() {
            var ncp = new NonContinuousPeriod(List.of(p("2024-01-01", "2024-01-10")));
            var resource = p("2024-01-11", "2024-01-12");

            assertThat(ncp.within(resource)).isFalse();
        }
    }

    @Nested
    class Meta {

        @Test
        void emptyTrueWhenNoPeriods() {
            assertThat(new NonContinuousPeriod(List.of()).isEmpty()).isTrue();
        }

        @Test
        void sizeAndGet() {
            var p1 = p("2024-01-01", "2024-01-02");
            var p2 = p("2024-01-03", "2024-01-04");
            var ncp = new NonContinuousPeriod(List.of(p1, p2));

            assertThat(ncp.size()).isEqualTo(2);
            assertThat(ncp.get(0)).isEqualTo(p1);
            assertThat(ncp.get(1)).isEqualTo(p2);
        }

    }

    @Nested
    class IsStartBetween {

        @Test
        void trueWhenStartIsInsideOtherPeriod() {
            var target = p("2024-01-05", "2024-01-10");
            var ref = p("2024-01-01", "2024-01-06");

            assertThat(target.isStartBetween(ref)).isTrue();
        }

        @Test
        void falseWhenOutside() {
            var target = p("2024-01-10", "2024-01-20");
            var ref = p("2024-01-01", "2024-01-05");

            assertThat(target.isStartBetween(ref)).isFalse();
        }
    }
}
