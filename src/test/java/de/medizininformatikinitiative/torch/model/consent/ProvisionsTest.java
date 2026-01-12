package de.medizininformatikinitiative.torch.model.consent;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProvisionsTest {

    private de.medizininformatikinitiative.torch.model.consent.Period period(LocalDate start, LocalDate end) {
        return new de.medizininformatikinitiative.torch.model.consent.Period(start, end);
    }


    @Nested
    class Merge {

        @Test
        void mergesMultipleProvisions() {
            var p1 = new Provisions(Map.of(
                    "X", new NonContinuousPeriod(List.of(period(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 6, 1))))
            ));
            var p2 = new Provisions(Map.of(
                    "X", new NonContinuousPeriod(List.of(period(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 6, 1))))
            ));

            var result = Provisions.merge(List.of(p1, p2));

            assertThat(result.periods()).containsOnlyKeys("X");
            assertThat(result.periods().get("X").size()).isEqualTo(2);
        }

        @Test
        void preservesEmptyWhenNothingProvided() {
            var result = Provisions.merge(List.of());
            assertThat(result.isEmpty()).isTrue();
        }
    }

    @Nested
    class Empty {

        @Test
        void reportsEmptyCorrectly() {
            assertThat(Provisions.of().isEmpty()).isTrue();
            assertThat(new Provisions(Map.of("A", new NonContinuousPeriod(List.of(
                    period(LocalDate.of(2022, 1, 1), LocalDate.of(2022, 2, 1))
            )))).isEmpty()).isFalse();
        }
    }
}
