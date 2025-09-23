package de.medizininformatikinitiative.torch.consent;

import de.medizininformatikinitiative.torch.model.consent.ConsentProvisions;
import de.medizininformatikinitiative.torch.model.consent.Period;
import de.medizininformatikinitiative.torch.model.consent.Provision;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import de.medizininformatikinitiative.torch.service.DataStore;
import org.hl7.fhir.r4.model.Encounter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsentAdjusterUnitTest {

    @Mock
    private DataStore dataStore;

    private ConsentAdjuster adjuster;

    @BeforeEach
    void setUp() {
        adjuster = new ConsentAdjuster(dataStore);
    }

    /**
     * Helper to create an Encounter with start/end dates and patient reference
     */
    private Encounter createEncounter(String patientId, LocalDate start, LocalDate end) {
        Encounter encounter = new Encounter();
        org.hl7.fhir.r4.model.Period period = new org.hl7.fhir.r4.model.Period();
        if (start != null) period.setStart(Date.from(start.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        if (end != null) period.setEnd(Date.from(end.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        encounter.setPeriod(period);
        encounter.setSubject(new org.hl7.fhir.r4.model.Reference("Patient/" + patientId));
        return encounter;
    }

    @Test
    void testAdjustProvisions_noEncounters_returnsSame() {
        Provision p1 = new Provision("code1", Period.of(LocalDate.of(2025, 9, 10), LocalDate.of(2025, 9, 30)), true);
        ConsentProvisions consent = new ConsentProvisions("patient1", null, List.of(p1));

        Map<String, List<ConsentProvisions>> updated = adjuster.adjustProvisionsByEncounters(
                Map.of("patient1", List.of(consent)),
                Map.of()
        );

        assertThat(updated).containsKey("patient1");
        assertThat(updated.get("patient1")).containsExactly(consent);
    }

    @Test
    void testFetchAndAdjust_skipsEncounterWithoutPatientId() {
        PatientBatch batch = new PatientBatch(List.of("patient1"));

        Provision p1 = new Provision("code1", Period.of(LocalDate.of(2025, 9, 10), LocalDate.of(2025, 9, 30)), true);
        ConsentProvisions consent = new ConsentProvisions("patient1", null, List.of(p1));

        // Encounter that will throw PatientIdNotFoundException
        Encounter invalidEncounter = new Encounter();
        invalidEncounter.setId("invalid");

        Encounter validEncounter = createEncounter("patient1", LocalDate.of(2025, 9, 5), LocalDate.of(2025, 9, 15));

        when(dataStore.search(any(Query.class), eq(Encounter.class)))
                .thenReturn(Flux.just(invalidEncounter, validEncounter));

        StepVerifier.create(adjuster.fetchEncounterAndAdjustByEncounter(batch, Map.of("patient1", List.of(consent))))
                .assertNext(updated -> {
                    // Only the valid encounter affects the provision
                    ConsentProvisions adjusted = updated.get("patient1").getFirst();
                    assertThat(adjusted.provisions().getFirst().period().start()).isEqualTo(LocalDate.of(2025, 9, 5));
                    assertThat(adjusted.provisions().getFirst().period().end()).isEqualTo(LocalDate.of(2025, 9, 30));
                })
                .verifyComplete();
    }

    @Test
    void testAdjustProvisions_singleOverlap_shiftsStart() {
        Provision p1 = new Provision("code1", Period.of(LocalDate.of(2025, 9, 10), LocalDate.of(2025, 9, 30)), true);
        ConsentProvisions consent = new ConsentProvisions("patient1", null, List.of(p1));

        Encounter e1 = createEncounter("patient1", LocalDate.of(2025, 9, 5), LocalDate.of(2025, 9, 15));

        Map<String, List<ConsentProvisions>> updated = adjuster.adjustProvisionsByEncounters(
                Map.of("patient1", List.of(consent)),
                Map.of("patient1", List.of(e1))
        );

        ConsentProvisions u = updated.get("patient1").getFirst();
        assertThat(u.provisions().getFirst().period().start()).isEqualTo(LocalDate.of(2025, 9, 5));
        assertThat(u.provisions().getFirst().period().end()).isEqualTo(LocalDate.of(2025, 9, 30));
    }

    @Test
    void testAdjustProvisions_multipleOverlaps_shiftsToEarliest() {
        Provision p1 = new Provision("code1", Period.of(LocalDate.of(2025, 9, 10), LocalDate.of(2025, 9, 30)), true);
        ConsentProvisions consent = new ConsentProvisions("patient1", null, List.of(p1));

        Encounter e1 = createEncounter("patient1", LocalDate.of(2025, 9, 8), LocalDate.of(2025, 9, 12));
        Encounter e2 = createEncounter("patient1", LocalDate.of(2025, 9, 5), LocalDate.of(2025, 9, 15));

        Map<String, List<ConsentProvisions>> updated = adjuster.adjustProvisionsByEncounters(
                Map.of("patient1", List.of(consent)),
                Map.of("patient1", List.of(e1, e2))
        );

        ConsentProvisions u = updated.get("patient1").getFirst();
        assertThat(u.provisions().getFirst().period().start()).isEqualTo(LocalDate.of(2025, 9, 5));
        assertThat(u.provisions().getFirst().period().end()).isEqualTo(LocalDate.of(2025, 9, 30));
    }

    @Test
    void testFetchAndAdjust_withMockedDataStore() {
        PatientBatch batch = new PatientBatch(List.of("patient1", "patient2"));

        Provision p1 = new Provision("code1", Period.of(LocalDate.of(2025, 9, 10), LocalDate.of(2025, 9, 30)), true);
        Provision p2 = new Provision("code2", Period.of(LocalDate.of(2025, 10, 1), LocalDate.of(2025, 10, 31)), true);
        ConsentProvisions consent1 = new ConsentProvisions("patient1", null, List.of(p1));
        ConsentProvisions consent2 = new ConsentProvisions("patient2", null, List.of(p2));

        Encounter e1 = createEncounter("patient1", LocalDate.of(2025, 9, 5), LocalDate.of(2025, 9, 15));
        Encounter e2 = createEncounter("patient2", LocalDate.of(2025, 9, 28), LocalDate.of(2025, 10, 5));

        when(dataStore.search(any(Query.class), eq(Encounter.class)))
                .thenReturn(Flux.just(e1, e2));

        StepVerifier.create(adjuster.fetchEncounterAndAdjustByEncounter(batch, Map.of("patient1", List.of(consent1), "patient2", List.of(consent2))))
                .assertNext(updated -> {
                    assertThat(updated.get("patient1").getFirst().provisions().getFirst().period().start()).isEqualTo(LocalDate.of(2025, 9, 5));
                    assertThat(updated.get("patient2").getFirst().provisions().getFirst().period().start()).isEqualTo(LocalDate.of(2025, 9, 28));
                })
                .verifyComplete();
    }
}
