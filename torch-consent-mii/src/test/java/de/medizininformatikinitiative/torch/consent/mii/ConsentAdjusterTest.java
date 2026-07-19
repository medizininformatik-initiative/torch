package de.medizininformatikinitiative.torch.consent.mii;

import de.medizininformatikinitiative.torch.consent.ConsentDataClient;
import de.medizininformatikinitiative.torch.consent.PatientResource;
import de.medizininformatikinitiative.torch.consent.mii.model.ConsentProvisions;
import de.medizininformatikinitiative.torch.consent.mii.model.Provision;
import de.medizininformatikinitiative.torch.consent.mii.model.TermCode;
import de.medizininformatikinitiative.torch.model.consent.Period;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsentAdjusterTest {

    @Mock
    private ConsentDataClient consentDataClient;

    private ConsentAdjuster adjuster;

    @BeforeEach
    void setUp() {
        adjuster = new ConsentAdjuster(consentDataClient);
    }

    /**
     * Helper to create an Encounter with start/end dates
     */
    private Encounter createEncounter(LocalDate start, LocalDate end) {
        Encounter encounter = new Encounter();
        org.hl7.fhir.r4.model.Period period = new org.hl7.fhir.r4.model.Period();
        if (start != null) period.setStart(Date.from(start.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        if (end != null) period.setEnd(Date.from(end.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        encounter.setPeriod(period);
        return encounter;
    }

    @Test
    void testAdjustProvisions_noEncounters_returnsSame() {
        Provision p1 = new Provision(new TermCode("s1", "code1"), Period.of(LocalDate.of(2025, 9, 10), LocalDate.of(2025, 9, 30)), true);
        ConsentProvisions consent = new ConsentProvisions("patient1", null, List.of(p1));

        Map<String, List<ConsentProvisions>> updated = adjuster.adjustProvisionsByEncounters(
                Map.of("patient1", List.of(consent)),
                Map.of(),
                Set.of(new TermCode("s1", "code1"))
        );

        assertThat(updated).containsKey("patient1");
        assertThat(updated.get("patient1")).containsExactly(consent);
    }

    @Test
    void testAdjustProvisions_singleOverlap_shiftsStart() {
        Provision p1 = new Provision(new TermCode("s1", "code1"), Period.of(LocalDate.of(2025, 9, 10), LocalDate.of(2025, 9, 30)), true);
        ConsentProvisions consent = new ConsentProvisions("patient1", null, List.of(p1));

        Encounter e1 = createEncounter(LocalDate.of(2025, 9, 5), LocalDate.of(2025, 9, 15));

        Map<String, List<ConsentProvisions>> updated = adjuster.adjustProvisionsByEncounters(
                Map.of("patient1", List.of(consent)),
                Map.of("patient1", List.of(e1)),
                Set.of(new TermCode("s1", "code1"))
        );

        ConsentProvisions u = updated.get("patient1").getFirst();
        assertThat(u.provisions().getFirst().period().start()).isEqualTo(LocalDate.of(2025, 9, 5));
        assertThat(u.provisions().getFirst().period().end()).isEqualTo(LocalDate.of(2025, 9, 30));
    }

    @Test
    void testAdjustProvisions_multipleOverlaps_shiftsToEarliest() {
        Provision p1 = new Provision(new TermCode("s1", "code1"), Period.of(LocalDate.of(2025, 9, 10), LocalDate.of(2025, 9, 30)), true);
        ConsentProvisions consent = new ConsentProvisions("patient1", null, List.of(p1));

        Encounter e1 = createEncounter(LocalDate.of(2025, 9, 8), LocalDate.of(2025, 9, 12));
        Encounter e2 = createEncounter(LocalDate.of(2025, 9, 5), LocalDate.of(2025, 9, 15));

        Map<String, List<ConsentProvisions>> updated = adjuster.adjustProvisionsByEncounters(
                Map.of("patient1", List.of(consent)),
                Map.of("patient1", List.of(e1, e2)),
                Set.of(new TermCode("s1", "code1"))
        );

        ConsentProvisions u = updated.get("patient1").getFirst();
        assertThat(u.provisions().getFirst().period().start()).isEqualTo(LocalDate.of(2025, 9, 5));
        assertThat(u.provisions().getFirst().period().end()).isEqualTo(LocalDate.of(2025, 9, 30));
    }

    @Test
    void testFetchAndAdjust_withMockedConsentDataClient() {
        Provision p1 = new Provision(new TermCode("s1", "code1"), Period.of(LocalDate.of(2025, 9, 10), LocalDate.of(2025, 9, 30)), true);
        Provision p2 = new Provision(new TermCode("s1", "code2"), Period.of(LocalDate.of(2025, 10, 1), LocalDate.of(2025, 10, 31)), true);
        ConsentProvisions consent1 = new ConsentProvisions("patient1", null, List.of(p1));
        ConsentProvisions consent2 = new ConsentProvisions("patient2", null, List.of(p2));

        Encounter e1 = createEncounter(LocalDate.of(2025, 9, 5), LocalDate.of(2025, 9, 15));
        Encounter e2 = createEncounter(LocalDate.of(2025, 9, 28), LocalDate.of(2025, 10, 5));

        when(consentDataClient.searchEncountersByProfile(any(), any()))
                .thenReturn(Flux.just(new PatientResource<>("patient1", e1), new PatientResource<>("patient2", e2)));

        StepVerifier.create(adjuster.fetchEncounterAndAdjustByEncounter(
                        List.of("patient1", "patient2"),
                        Map.of("patient1", List.of(consent1), "patient2", List.of(consent2)),
                        Set.of(new TermCode("s1", "code1"), new TermCode("s1", "code2"))))
                .assertNext(updated -> {
                    assertThat(updated.get("patient1").getFirst().provisions().getFirst().period().start()).isEqualTo(LocalDate.of(2025, 9, 5));
                    assertThat(updated.get("patient2").getFirst().provisions().getFirst().period().start()).isEqualTo(LocalDate.of(2025, 9, 28));
                })
                .verifyComplete();
    }
}
