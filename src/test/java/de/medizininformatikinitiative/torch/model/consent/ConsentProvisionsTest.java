package de.medizininformatikinitiative.torch.model.consent;

import de.medizininformatikinitiative.torch.model.management.TermCode;
import org.hl7.fhir.r4.model.Encounter;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConsentProvisionsTest {

    public static final TermCode CODE = new TermCode("sys1", "code1");

    // Helper to create an Encounter with a specific period
    private Encounter createEncounter(LocalDate start, LocalDate end) {
        Encounter encounter = new Encounter();
        org.hl7.fhir.r4.model.Period period = new org.hl7.fhir.r4.model.Period();
        if (start != null) {
            period.setStart(Date.from(start.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        }
        if (end != null) {
            period.setEnd(Date.from(end.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        }
        encounter.setPeriod(period);
        return encounter;
    }

    @Test
    void updateByEncounters_noEncounters_returnsSameProvisions() {
        Provision p1 = new Provision(CODE, Period.of(LocalDate.of(2025, 9, 1), LocalDate.of(2025, 9, 30)), true);
        ConsentProvisions consent = new ConsentProvisions("patient1", null, List.of(p1));

        ConsentProvisions updated = consent.updateByEncounters(List.of());

        assertThat(updated.provisions()).containsExactly(p1);
    }

    @Test
    void updateByEncounters_nonOverlappingEncounters_returnsSameProvisions() {
        Provision p1 = new Provision(CODE, Period.of(LocalDate.of(2025, 9, 10), LocalDate.of(2025, 9, 30)), true);
        ConsentProvisions consent = new ConsentProvisions("patient1", null, List.of(p1));

        Encounter e1 = createEncounter(LocalDate.of(2025, 9, 1), LocalDate.of(2025, 9, 5));
        Encounter e2 = createEncounter(LocalDate.of(2025, 10, 1), LocalDate.of(2025, 10, 5));

        ConsentProvisions updated = consent.updateByEncounters(List.of(e1, e2));

        assertThat(updated.provisions()).containsExactly(p1);
    }

    @Test
    void updateByEncounters_singleOverlappingEncounter_shiftsStart() {
        Provision p1 = new Provision(CODE, Period.of(LocalDate.of(2025, 9, 10), LocalDate.of(2025, 9, 30)), true);
        ConsentProvisions consent = new ConsentProvisions("patient1", null, List.of(p1));

        Encounter e1 = createEncounter(LocalDate.of(2025, 9, 5), LocalDate.of(2025, 9, 15));

        ConsentProvisions updated = consent.updateByEncounters(List.of(e1));

        assertThat(updated.provisions()).hasSize(1);
        assertThat(updated.provisions().getFirst().period().start()).isEqualTo(LocalDate.of(2025, 9, 5));
        assertThat(updated.provisions().getFirst().period().end()).isEqualTo(LocalDate.of(2025, 9, 30));
    }

    @Test
    void updateByEncounters_multipleOverlappingEncounters_shiftsToEarliest() {
        Provision p1 = new Provision(CODE, Period.of(LocalDate.of(2025, 9, 10), LocalDate.of(2025, 9, 30)), true);
        ConsentProvisions consent = new ConsentProvisions("patient1", null, List.of(p1));

        Encounter e1 = createEncounter(LocalDate.of(2025, 9, 8), LocalDate.of(2025, 9, 12));
        Encounter e2 = createEncounter(LocalDate.of(2025, 9, 5), LocalDate.of(2025, 9, 15));

        ConsentProvisions updated = consent.updateByEncounters(List.of(e1, e2));

        assertThat(updated.provisions()).hasSize(1);
        assertThat(updated.provisions().getFirst().period().start()).isEqualTo(LocalDate.of(2025, 9, 5)); // earliest start
        assertThat(updated.provisions().getFirst().period().end()).isEqualTo(LocalDate.of(2025, 9, 30));
    }
}
