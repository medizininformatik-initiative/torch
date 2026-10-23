package de.medizininformatikinitiative.torch.consent;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.Torch;
import de.medizininformatikinitiative.torch.config.TorchProperties;
import de.medizininformatikinitiative.torch.service.DataStore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.WebClient;

import static de.medizininformatikinitiative.torch.consent.ConsentEncounterProbes.PROSPECTIVE_CODES;
import static de.medizininformatikinitiative.torch.consent.ConsentEncounterProbes.assertAnchor;
import static de.medizininformatikinitiative.torch.consent.ConsentEncounterProbes.assertNotShifted;
import static de.medizininformatikinitiative.torch.consent.ConsentEncounterProbes.batchOf;
import static de.medizininformatikinitiative.torch.consent.ConsentEncounterProbes.consent;
import static de.medizininformatikinitiative.torch.consent.ConsentEncounterProbes.encounter;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repeats probes of {@link ConsentEncounterContextIT} with a different {@code torch.encounterShiftMaxDays}, against a
 * real Blaze. Each nested class runs in its own Spring context with the limit set by its properties; fixtures,
 * assertions and status labels follow {@link ConsentEncounterContextIT}. The table was recorded on 2026-10-13 against
 * v1.0.3 (commit ee05decb2).
 *
 * <table>
 *     <caption>Results per probe</caption>
 *     <tr><th>Probe</th><th>Max days</th><th>Signing day</th><th>Encounters</th><th>.6 data period</th><th>Eligible</th><th>Anchor</th><th>Status</th></tr>
 *     <tr><td>E2-30 as E2</td><td>30</td><td>2024-03-15</td><td>S</td><td>2024-03-01–2029-12-31</td><td>2024-03-02, 2024-03-12, 2024-03-25</td><td>S</td><td>expected</td></tr>
 *     <tr><td>E1-0 as E1</td><td>0</td><td>2024-03-04</td><td>S</td><td>2024-03-04–2029-12-31</td><td>2024-03-12, 2024-03-25</td><td>-</td><td>expected</td></tr>
 * </table>
 * <p>
 * Why each probe matters:
 * <ul>
 *     <li>E2-30: a larger limit lets a consent late in a stay cover the entire stay, as the guidance intends.</li>
 *     <li>E1-0: a limit of 0 effectively disables the shift, while the Encounter search still runs.</li>
 * </ul>
 */
class ConsentEncounterShiftConfigIT {

    @Nested
    @ActiveProfiles("test")
    @SpringBootTest(properties = {"spring.main.allow-bean-definition-overriding=true", "torch.encounterShiftMaxDays=30"},
            classes = Torch.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
    class MaxDays30 {

        @Autowired
        @Qualifier("fhirClient")
        WebClient webClient;
        @Autowired
        FhirContext fhirContext;
        @Autowired
        DataStore dataStore;
        @Autowired
        ConsentHandler consentHandler;
        @Autowired
        ConsentValidator consentValidator;
        @Autowired
        TorchProperties torchProperties;

        ConsentEncounterProbes probes;

        @BeforeAll
        void setUp() {
            assertThat(torchProperties.enableEncounterShift()).isTrue();
            assertThat(torchProperties.encounterShiftMaxDays()).isEqualTo(30);
            probes = new ConsentEncounterProbes(webClient, fhirContext, dataStore, consentValidator);
        }

        @Test
        @DisplayName("E2-30 [expected]: with a 30-day limit, consent on day 15 of an inpatient stay shifts .6 to the admission day")
        void consentLateInInpatientStay() {
            // given: IMP 2024-03-01–2024-03-20; Consent (2024-03-15)
            probes.storePatient("E2-30");
            probes.put(encounter("E2-30-IMP", "E2-30", "IMP", "2024-03-01", "2024-03-20"));
            probes.put(consent("E2-30", "2024-03-15"));

            // when
            var batch = batchOf("E2-30");
            var result = consentHandler.fetchAndBuildConsentInfo(PROSPECTIVE_CODES, batch);

            // then
            probes.assertIncluded(result, "E2-30", "2024-03-01", "2024-03-02", "2024-03-12", "2024-03-25");
            assertAnchor(batch, "E2-30-IMP", "2024-03-01");
        }
    }

    @Nested
    @ActiveProfiles("test")
    @SpringBootTest(properties = {"spring.main.allow-bean-definition-overriding=true", "torch.encounterShiftMaxDays=0"},
            classes = Torch.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
    class MaxDays0 {

        @Autowired
        @Qualifier("fhirClient")
        WebClient webClient;
        @Autowired
        FhirContext fhirContext;
        @Autowired
        DataStore dataStore;
        @Autowired
        ConsentHandler consentHandler;
        @Autowired
        ConsentValidator consentValidator;
        @Autowired
        TorchProperties torchProperties;

        ConsentEncounterProbes probes;

        @BeforeAll
        void setUp() {
            assertThat(torchProperties.enableEncounterShift()).isTrue();
            assertThat(torchProperties.encounterShiftMaxDays()).isZero();
            probes = new ConsentEncounterProbes(webClient, fhirContext, dataStore, consentValidator);
        }

        @Test
        @DisplayName("E1-0 [expected]: with a 0-day limit, consent on day 4 of an inpatient stay is not shifted")
        void consentEarlyInInpatientStay() {
            // given: IMP 2024-03-01–2024-03-20; Consent (2024-03-04)
            probes.storePatient("E1-0");
            probes.put(encounter("E1-0-IMP", "E1-0", "IMP", "2024-03-01", "2024-03-20"));
            probes.put(consent("E1-0", "2024-03-04"));

            // when
            var batch = batchOf("E1-0");
            var result = consentHandler.fetchAndBuildConsentInfo(PROSPECTIVE_CODES, batch);

            // then
            probes.assertIncluded(result, "E1-0", "2024-03-04", "2024-03-12", "2024-03-25");
            assertNotShifted(batch);
        }
    }
}
