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
import org.junit.jupiter.api.extension.RegisterExtension;
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
 * Repeats scenarios of {@link ConsentEncounterContextIT} with a different {@code torch.encounterShiftMaxDays}, against a
 * real Blaze. Each nested class runs in its own Spring context with the limit set by its properties; fixtures,
 * assertions and status labels follow {@link ConsentEncounterContextIT}. The table was recorded on 2026-10-27 against
 * v1.0.3 (commit ee05decb2).
 *
 * <table>
 *     <caption>Results per scenario</caption>
 *     <tr><th>Scenario</th><th>Setup</th><th>Collection period</th><th>Eligible</th><th>Status</th></tr>
 *     <tr><td>E2-N30</td><td>as E2, {@code torch.encounterShiftMaxDays=30}</td><td>2024-03-01–2029-12-31, shifted, anchor S</td><td>2024-03-02, 2024-03-12, 2024-03-25</td><td>expected</td></tr>
 *     <tr><td>E1-N0</td><td>as E1, {@code torch.encounterShiftMaxDays=0}</td><td>2024-03-04–2029-12-31, not shifted</td><td>2024-03-12, 2024-03-25</td><td>expected</td></tr>
 * </table>
 * <p>
 * Why each scenario matters:
 * <ul>
 *     <li>E2-N30: a larger limit lets a consent late in a stay cover the entire stay, as the guidance intends.</li>
 *     <li>E1-N0: a limit of 0 effectively disables the shift, while the Encounter search still runs.</li>
 * </ul>
 */
class ConsentEncounterShiftConfigIT {

    @RegisterExtension
    static final ConsentResultsTable RESULTS = new ConsentResultsTable("ConsentEncounterShiftConfigIT");

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
            probes = new ConsentEncounterProbes(webClient, fhirContext, dataStore, consentValidator, RESULTS);
        }

        @Test
        @DisplayName("E2-N30 [expected]: with a 30-day limit, consent on day 15 of an inpatient stay shifts the collection period to the admission day")
        void consentLateInInpatientStay() {
            // given: IMP 2024-03-01–2024-03-20; Consent (2024-03-15)
            probes.storePatient("E2-N30");
            probes.put(encounter("E2-N30-IMP", "E2-N30", "IMP", "2024-03-01", "2024-03-20"));
            probes.put(consent("E2-N30", "2024-03-15"));

            // when
            var batch = batchOf("E2-N30");
            var result = consentHandler.fetchAndBuildConsentInfo(PROSPECTIVE_CODES, batch);

            // then
            probes.assertIncluded(result, "E2-N30", "2024-03-01", "2024-03-02", "2024-03-12", "2024-03-25");
            assertAnchor(batch, "E2-N30-IMP", "2024-03-01");
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
            probes = new ConsentEncounterProbes(webClient, fhirContext, dataStore, consentValidator, RESULTS);
        }

        @Test
        @DisplayName("E1-N0 [expected]: with a 0-day limit, consent on day 4 of an inpatient stay is not shifted")
        void consentEarlyInInpatientStay() {
            // given: IMP 2024-03-01–2024-03-20; Consent (2024-03-04)
            probes.storePatient("E1-N0");
            probes.put(encounter("E1-N0-IMP", "E1-N0", "IMP", "2024-03-01", "2024-03-20"));
            probes.put(consent("E1-N0", "2024-03-04"));

            // when
            var batch = batchOf("E1-N0");
            var result = consentHandler.fetchAndBuildConsentInfo(PROSPECTIVE_CODES, batch);

            // then
            probes.assertIncluded(result, "E1-N0", "2024-03-04", "2024-03-12", "2024-03-25");
            assertNotShifted(batch);
        }
    }
}
