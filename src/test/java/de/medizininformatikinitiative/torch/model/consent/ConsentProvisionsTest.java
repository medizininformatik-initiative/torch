package de.medizininformatikinitiative.torch.model.consent;

import de.medizininformatikinitiative.torch.model.management.TermCode;
import org.hl7.fhir.r4.model.Encounter;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConsentProvisionsTest {

    public static final TermCode CODE = new TermCode("sys1", "code1");
    private static final int MAX_SHIFT_DAYS = 7;

    // Helper to create an Encounter with a specific period
    private Encounter createEncounter(LocalDate start, LocalDate end) {
        return createEncounter("enc-" + start, start, end);
    }

    private Encounter createEncounter(String id, LocalDate start, LocalDate end) {
        Encounter encounter = new Encounter();
        encounter.setId(id);
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
        ConsentProvisions consent = new ConsentProvisions("c1", "patient1", null, List.of(p1));

        ConsentProvisions updated = consent.updateByEncounters(List.of(), Set.of(CODE), MAX_SHIFT_DAYS);

        assertThat(updated.provisions()).containsExactly(p1);
    }

    @Test
    void updateByEncounters_nonOverlappingEncounters_returnsSameProvisions() {
        Provision p1 = new Provision(CODE, Period.of(LocalDate.of(2025, 9, 10), LocalDate.of(2025, 9, 30)), true);
        ConsentProvisions consent = new ConsentProvisions("c1", "patient1", null, List.of(p1));

        Encounter e1 = createEncounter(LocalDate.of(2025, 9, 1), LocalDate.of(2025, 9, 5));
        Encounter e2 = createEncounter(LocalDate.of(2025, 10, 1), LocalDate.of(2025, 10, 5));

        ConsentProvisions updated = consent.updateByEncounters(List.of(e1, e2), Set.of(CODE), MAX_SHIFT_DAYS);

        assertThat(updated.provisions()).containsExactly(p1);
    }

    @Test
    void updateByEncounters_singleOverlappingEncounter_shiftsStart() {
        Provision p1 = new Provision(CODE, Period.of(LocalDate.of(2025, 9, 10), LocalDate.of(2025, 9, 30)), true);
        ConsentProvisions consent = new ConsentProvisions("c1", "patient1", null, List.of(p1));

        Encounter e1 = createEncounter(LocalDate.of(2025, 9, 5), LocalDate.of(2025, 9, 15));

        ConsentProvisions updated = consent.updateByEncounters(List.of(e1), Set.of(CODE), MAX_SHIFT_DAYS);

        assertThat(updated.provisions()).hasSize(1);
        assertThat(updated.provisions().getFirst().period().start()).isEqualTo(LocalDate.of(2025, 9, 5));
        assertThat(updated.provisions().getFirst().period().end()).isEqualTo(LocalDate.of(2025, 9, 30));
    }

    @Test
    void updateByEncounters_shiftedProvision_recordsOriginalStartAndEncounter() {
        Provision p1 = new Provision(CODE, Period.of(LocalDate.of(2025, 9, 10), LocalDate.of(2025, 9, 30)), true);
        ConsentProvisions consent = new ConsentProvisions("c1", "patient1", null, List.of(p1));

        Encounter e1 = createEncounter("enc-1", LocalDate.of(2025, 9, 5), LocalDate.of(2025, 9, 15));

        ConsentProvisions updated = consent.updateByEncounters(List.of(e1), Set.of(CODE), MAX_SHIFT_DAYS);

        assertThat(updated.provisions().getFirst().encounterShift())
                .isEqualTo(new EncounterShift(LocalDate.of(2025, 9, 10), "enc-1"));
    }

    @Test
    void updateByEncounters_overlappingEncountersWithSameStart_recordsLowestEncounterId() {
        Provision p1 = new Provision(CODE, Period.of(LocalDate.of(2025, 9, 10), LocalDate.of(2025, 9, 30)), true);
        ConsentProvisions consent = new ConsentProvisions("c1", "patient1", null, List.of(p1));

        Encounter e1 = createEncounter("enc-b", LocalDate.of(2025, 9, 5), LocalDate.of(2025, 9, 15));
        Encounter e2 = createEncounter("enc-a", LocalDate.of(2025, 9, 5), LocalDate.of(2025, 9, 12));

        ConsentProvisions updated = consent.updateByEncounters(List.of(e1, e2), Set.of(CODE), MAX_SHIFT_DAYS);

        assertThat(updated.provisions().getFirst().encounterShift().encounterId()).isEqualTo("enc-a");
    }

    @Test
    void updateByEncounters_multipleOverlappingEncounters_shiftsToEarliest() {
        Provision p1 = new Provision(CODE, Period.of(LocalDate.of(2025, 9, 10), LocalDate.of(2025, 9, 30)), true);
        ConsentProvisions consent = new ConsentProvisions("c1", "patient1", null, List.of(p1));

        Encounter e1 = createEncounter(LocalDate.of(2025, 9, 8), LocalDate.of(2025, 9, 12));
        Encounter e2 = createEncounter(LocalDate.of(2025, 9, 5), LocalDate.of(2025, 9, 15));

        ConsentProvisions updated = consent.updateByEncounters(List.of(e1, e2), Set.of(CODE), MAX_SHIFT_DAYS);

        assertThat(updated.provisions()).hasSize(1);
        assertThat(updated.provisions().getFirst().period().start()).isEqualTo(LocalDate.of(2025, 9, 5)); // earliest start
        assertThat(updated.provisions().getFirst().period().end()).isEqualTo(LocalDate.of(2025, 9, 30));
    }


    @Test
    void updateByEncounters_denyProvision_isNotShifted() {
        Provision p1 = new Provision(CODE, Period.of(LocalDate.of(2025, 9, 10), LocalDate.of(2025, 9, 30)), false);
        ConsentProvisions consent = new ConsentProvisions("c1", "patient1", null, List.of(p1));

        Encounter e1 = createEncounter(LocalDate.of(2025, 9, 5), LocalDate.of(2025, 9, 15));

        ConsentProvisions updated = consent.updateByEncounters(List.of(e1), Set.of(CODE), MAX_SHIFT_DAYS);

        assertThat(updated.provisions()).containsExactly(p1);
    }

    @Test
    void updateByEncounters_encounterWithNullPeriod_isIgnored() {
        Provision p1 = new Provision(CODE, Period.of(LocalDate.of(2025, 9, 10), LocalDate.of(2025, 9, 30)), true);
        ConsentProvisions consent = new ConsentProvisions("c1", "patient1", null, List.of(p1));

        Encounter nullPeriodEncounter = createEncounter(null, null);
        Encounter overlapping = createEncounter(LocalDate.of(2025, 9, 5), LocalDate.of(2025, 9, 15));

        ConsentProvisions updated = consent.updateByEncounters(List.of(nullPeriodEncounter, overlapping), Set.of(CODE), MAX_SHIFT_DAYS);

        assertThat(updated.provisions()).hasSize(1);
        assertThat(updated.provisions().getFirst().period().start()).isEqualTo(LocalDate.of(2025, 9, 5));
        assertThat(updated.provisions().getFirst().period().end()).isEqualTo(LocalDate.of(2025, 9, 30));
    }

    @Test
    void updateByEncounters_encounterStartAtMaxShiftDays_shiftsStart() {
        Provision p1 = new Provision(CODE, Period.of(LocalDate.of(2025, 9, 10), LocalDate.of(2025, 9, 30)), true);
        ConsentProvisions consent = new ConsentProvisions("c1", "patient1", null, List.of(p1));

        Encounter e1 = createEncounter(LocalDate.of(2025, 9, 3), LocalDate.of(2025, 9, 15));

        ConsentProvisions updated = consent.updateByEncounters(List.of(e1), Set.of(CODE), MAX_SHIFT_DAYS);

        assertThat(updated.provisions().getFirst().period().start()).isEqualTo(LocalDate.of(2025, 9, 3));
    }

    @Test
    void updateByEncounters_encounterStartBeyondMaxShiftDays_isNotShifted() {
        Provision p1 = new Provision(CODE, Period.of(LocalDate.of(2025, 9, 10), LocalDate.of(2025, 9, 30)), true);
        ConsentProvisions consent = new ConsentProvisions("c1", "patient1", null, List.of(p1));

        Encounter e1 = createEncounter(LocalDate.of(2025, 9, 2), LocalDate.of(2025, 9, 15));

        ConsentProvisions updated = consent.updateByEncounters(List.of(e1), Set.of(CODE), MAX_SHIFT_DAYS);

        assertThat(updated.provisions()).containsExactly(p1);
    }

    @Test
    void updateByEncounters_earliestEncounterBeyondMaxShiftDays_doesNotFallBackToLaterEncounter() {
        Provision p1 = new Provision(CODE, Period.of(LocalDate.of(2025, 9, 10), LocalDate.of(2025, 9, 30)), true);
        ConsentProvisions consent = new ConsentProvisions("c1", "patient1", null, List.of(p1));

        Encounter facilityContact = createEncounter(LocalDate.of(2025, 8, 31), LocalDate.of(2025, 9, 15));
        Encounter departmentContact = createEncounter(LocalDate.of(2025, 9, 7), LocalDate.of(2025, 9, 15));

        ConsentProvisions updated = consent.updateByEncounters(List.of(facilityContact, departmentContact),
                Set.of(CODE), MAX_SHIFT_DAYS);

        assertThat(updated.provisions()).containsExactly(p1);
    }

    @Test
    void updateByEncounters_maxShiftDaysZero_isNotShifted() {
        Provision p1 = new Provision(CODE, Period.of(LocalDate.of(2025, 9, 10), LocalDate.of(2025, 9, 30)), true);
        ConsentProvisions consent = new ConsentProvisions("c1", "patient1", null, List.of(p1));

        Encounter e1 = createEncounter(LocalDate.of(2025, 9, 9), LocalDate.of(2025, 9, 15));

        ConsentProvisions updated = consent.updateByEncounters(List.of(e1), Set.of(CODE), 0);

        assertThat(updated.provisions()).containsExactly(p1);
    }
}
