package de.medizininformatikinitiative.torch.model.consent;

import org.hl7.fhir.r4.model.Encounter;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProvisionsTest {

    private Encounter encounter(LocalDate start, LocalDate end) {
        var hapiPeriod = new org.hl7.fhir.r4.model.Period();
        hapiPeriod.setStart(java.sql.Date.valueOf(start));
        hapiPeriod.setEnd(java.sql.Date.valueOf(end));

        var encounter = new Encounter();
        encounter.setPeriod(hapiPeriod);
        return encounter;
    }

    private de.medizininformatikinitiative.torch.model.consent.Period period(LocalDate start, LocalDate end) {
        return new de.medizininformatikinitiative.torch.model.consent.Period(start, end);
    }

    @Nested
    class Update {

        @Test
        void returnsSameIfEmpty() {
            var provisions = new Provisions(Map.of(
                    "A", new NonContinuousPeriod(List.of(period(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31))))
            ));

            var updated = provisions.updateConsentPeriodsByPatientEncounters(List.of());
            assertThat(updated).isEqualTo(provisions);
        }

        @Test
        void adjustsStartIfOverlappingEncounter() {
            var consent = new NonContinuousPeriod(List.of(period(LocalDate.of(2024, 5, 1), LocalDate.of(2024, 10, 1))));
            var provisions = new Provisions(Map.of("A", consent));

            var updated = provisions.updateConsentPeriodsByPatientEncounters(List.of(
                    encounter(LocalDate.of(2024, 4, 20), LocalDate.of(2024, 5, 2))
            ));

            assertThat(updated.periods().get("A").get(0).start()).isEqualTo(LocalDate.of(2024, 4, 20));
            assertThat(updated.periods().get("A").get(0).end()).isEqualTo(LocalDate.of(2024, 10, 1));
        }

        @Test
        void noChangeIfNoOverlap() {
            var original = period(LocalDate.of(2024, 6, 1), LocalDate.of(2024, 12, 1));
            var provisions = new Provisions(Map.of("A", new NonContinuousPeriod(List.of(original))));

            var updated = provisions.updateConsentPeriodsByPatientEncounters(List.of(
                    encounter(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 1))
            ));

            assertThat(updated.periods().get("A").get(0)).isEqualTo(original);
        }
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
