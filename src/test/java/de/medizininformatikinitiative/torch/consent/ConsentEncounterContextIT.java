package de.medizininformatikinitiative.torch.consent;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.Torch;
import de.medizininformatikinitiative.torch.config.TorchProperties;
import de.medizininformatikinitiative.torch.service.DataStore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
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
import static de.medizininformatikinitiative.torch.consent.ConsentEncounterProbes.departmentContact;
import static de.medizininformatikinitiative.torch.consent.ConsentEncounterProbes.encounter;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins down how the encounter shift moves the start of the collection period when a patient signs during a hospital
 * stay, against a real Blaze.
 * <p>
 * Every scenario stores its own patient, Observations, Encounters and one Consent signed on the signing day (given),
 * runs the consent calculation for that patient with the encounter shift enabled (when) and asserts the derived
 * collection period, the Observations that pass the consent check (eligible) and the Encounter that anchored the
 * shift, read from the raw provision diagnostics (then). The Consent has a research-use permit from the signing day to
 * 2054-12-31 and a collection permit from the signing day to 2029-12-31; requested modules are collection and research
 * use (see {@code docs/implementation/consent.md}). Observations are dated 2019-06-01, 2024-02-20, 2024-03-02,
 * 2024-03-12 and 2024-03-25: before the stay, before admission, early in the stay, late in the stay and after
 * discharge. The stay S is an inpatient facility contact 2024-03-01–2024-03-20.
 * <p>
 * How TORCH selects the stay:
 * <ul>
 *     <li>Only Encounters with the MII profile {@code KontaktGesundheitseinrichtung} and the inpatient class
 *     {@code IMP} are fetched; Encounters without a start or an end date are ignored.</li>
 *     <li>Only collection permits are shifted. An Encounter is a candidate if it starts strictly before the permit
 *     start and ends on or after it; the earliest candidate is taken (ties by ID).</li>
 *     <li>The permit start moves to that Encounter's start only if it lies at most {@code torch.encounterShiftMaxDays}
 *     days before the permit start; otherwise the permit stays unshifted, without falling back to a later
 *     Encounter.</li>
 * </ul>
 * The tests record observed behaviour, not a desired outcome; each test's {@code @DisplayName} carries its status:
 * <ul>
 *     <li><b>expected</b>: follows the rules documented in {@code docs/implementation/consent.md}.</li>
 *     <li><b>ambiguous</b>: the encoding is underspecified, so no outcome is clearly right.</li>
 * </ul>
 * The test profile runs with {@code enableEncounterShift: true} and {@code encounterShiftMaxDays: 7};
 * {@link ConsentEncounterShiftConfigIT} varies the limit. The table was recorded on 2026-10-27 against v1.0.3
 * (commit ee05decb2).
 *
 * <table>
 *     <caption>Results per scenario</caption>
 *     <tr><th>Scenario</th><th>Setup</th><th>Collection period</th><th>Eligible</th><th>Status</th></tr>
 *     <tr><td>E1</td><td>signed 2024-03-04 (day 4 of S)</td><td>2024-03-01–2029-12-31, shifted, anchor S</td><td>2024-03-02, 2024-03-12, 2024-03-25</td><td>expected</td></tr>
 *     <tr><td>E2</td><td>signed 2024-03-15 (day 15 of S)</td><td>2024-03-15–2029-12-31, not shifted</td><td>2024-03-25</td><td>expected</td></tr>
 *     <tr><td>E3</td><td>as E1 + outpatient contact 2018-01-01–2025-12-31</td><td>2024-03-01–2029-12-31, shifted, anchor S</td><td>2024-03-02, 2024-03-12, 2024-03-25</td><td>expected</td></tr>
 *     <tr><td>E4</td><td>as E1, S without end, in progress</td><td>2024-03-04–2029-12-31, not shifted</td><td>2024-03-12, 2024-03-25</td><td>expected</td></tr>
 *     <tr><td>E5a</td><td>as E1 + department contact 2024-03-03–2024-03-10 part of S</td><td>2024-03-01–2029-12-31, shifted, anchor S</td><td>2024-03-02, 2024-03-12, 2024-03-25</td><td>expected</td></tr>
 *     <tr><td>E5b</td><td>signed 2024-03-04; facility contact 2024-02-01–2024-03-20 with department contact 2024-03-01–2024-03-20</td><td>2024-03-04–2029-12-31, not shifted</td><td>2024-03-12, 2024-03-25</td><td>ambiguous</td></tr>
 *     <tr><td>E6a</td><td>signed 2024-03-08 (7 days after admission)</td><td>2024-03-01–2029-12-31, shifted, anchor S</td><td>2024-03-02, 2024-03-12, 2024-03-25</td><td>expected</td></tr>
 *     <tr><td>E6b</td><td>signed 2024-03-09 (8 days after admission)</td><td>2024-03-09–2029-12-31, not shifted</td><td>2024-03-12, 2024-03-25</td><td>expected</td></tr>
 * </table>
 * <p>
 * Why each scenario matters:
 * <ul>
 *     <li>E1: the stay began 3 days before signing, within the 7-day limit, so the start moves to the admission day.</li>
 *     <li>E2: the guidance covers the entire current stay, so days 1–14 are excluded by the limit, not by the
 *     guidance.</li>
 *     <li>E3: before #1299 the outpatient contact moved the start to 2018.</li>
 *     <li>E4: a patient still admitted at extraction time gets no shift, because Encounters without an end date are
 *     ignored.</li>
 *     <li>E5a: when a stay is documented as a facility contact with a nested department contact, the shift anchors to
 *     the facility contact's admission day.</li>
 *     <li>E5b: a long facility contact suppresses the shift although a department contact within the limit overlaps;
 *     the earliest overlapping Encounter decides and there is no fallback to a later one.</li>
 *     <li>E6a/E6b: the limit is inclusive at 7 days.</li>
 * </ul>
 * The 2019 and 2024-02-20 Observations are never eligible.
 */
@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.main.allow-bean-definition-overriding=true"}, classes = Torch.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConsentEncounterContextIT {

    @RegisterExtension
    static final ConsentResultsTable RESULTS = new ConsentResultsTable("ConsentEncounterContextIT");

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
        assertThat(torchProperties.encounterShiftMaxDays()).isEqualTo(7);
        probes = new ConsentEncounterProbes(webClient, fhirContext, dataStore, consentValidator, RESULTS);
    }

    @Test
    @DisplayName("E1 [expected]: consent on day 4 of an inpatient stay shifts the collection period to the admission day")
    void consentEarlyInInpatientStay() {
        // given: IMP 2024-03-01–2024-03-20; Consent (2024-03-04)
        probes.storePatient("E1");
        probes.put(encounter("E1-IMP", "E1", "IMP", "2024-03-01", "2024-03-20"));
        probes.put(consent("E1", "2024-03-04"));

        // when
        var batch = batchOf("E1");
        var result = consentHandler.fetchAndBuildConsentInfo(PROSPECTIVE_CODES, batch);

        // then
        probes.assertIncluded(result, "E1", "2024-03-01", "2024-03-02", "2024-03-12", "2024-03-25");
        assertAnchor(batch, "E1-IMP", "2024-03-01");
    }

    @Test
    @DisplayName("E2 [expected]: consent on day 15 of an inpatient stay is not shifted beyond the 7-day limit")
    void consentLateInInpatientStay() {
        // given: IMP 2024-03-01–2024-03-20; Consent (2024-03-15)
        probes.storePatient("E2");
        probes.put(encounter("E2-IMP", "E2", "IMP", "2024-03-01", "2024-03-20"));
        probes.put(consent("E2", "2024-03-15"));

        // when
        var batch = batchOf("E2");
        var result = consentHandler.fetchAndBuildConsentInfo(PROSPECTIVE_CODES, batch);

        // then
        probes.assertIncluded(result, "E2", "2024-03-15", "2024-03-25");
        assertNotShifted(batch);
    }

    @Test
    @DisplayName("E3 [expected]: an outpatient contact is ignored and only the inpatient stay shifts the collection period")
    void outpatientUmbrellaEncounterIsIgnored() {
        // given: AMB 2018-01-01–2025-12-31; IMP 2024-03-01–2024-03-20; Consent (2024-03-04)
        probes.storePatient("E3");
        probes.put(encounter("E3-AMB", "E3", "AMB", "2018-01-01", "2025-12-31"));
        probes.put(encounter("E3-IMP", "E3", "IMP", "2024-03-01", "2024-03-20"));
        probes.put(consent("E3", "2024-03-04"));

        // when
        var batch = batchOf("E3");
        var result = consentHandler.fetchAndBuildConsentInfo(PROSPECTIVE_CODES, batch);

        // then
        probes.assertIncluded(result, "E3", "2024-03-01", "2024-03-02", "2024-03-12", "2024-03-25");
        assertAnchor(batch, "E3-IMP", "2024-03-01");
    }

    @Test
    @DisplayName("E4 [expected]: an inpatient stay without end date does not shift the collection period")
    void openEndedInpatientStayIsIgnored() {
        // given: IMP from 2024-03-01 without end; Consent (2024-03-04)
        probes.storePatient("E4");
        probes.put(encounter("E4-IMP", "E4", "IMP", "2024-03-01", null));
        probes.put(consent("E4", "2024-03-04"));

        // when
        var batch = batchOf("E4");
        var result = consentHandler.fetchAndBuildConsentInfo(PROSPECTIVE_CODES, batch);

        // then
        probes.assertIncluded(result, "E4", "2024-03-04", "2024-03-12", "2024-03-25");
        assertNotShifted(batch);
    }

    @Test
    @DisplayName("E5a [expected]: a department contact nested in the inpatient stay does not change the anchor; collection shifts to the facility contact's admission day")
    void nestedDepartmentContactAnchorsToFacilityContact() {
        // given: IMP facility 2024-03-01–2024-03-20; IMP department 2024-03-03–2024-03-10 part of facility;
        // Consent (2024-03-04)
        probes.storePatient("E5a");
        probes.put(encounter("E5a-facility", "E5a", "IMP", "2024-03-01", "2024-03-20"));
        probes.put(departmentContact("E5a-department", "E5a", "E5a-facility", "2024-03-03", "2024-03-10"));
        probes.put(consent("E5a", "2024-03-04"));

        // when
        var batch = batchOf("E5a");
        var result = consentHandler.fetchAndBuildConsentInfo(PROSPECTIVE_CODES, batch);

        // then
        probes.assertIncluded(result, "E5a", "2024-03-01", "2024-03-02", "2024-03-12", "2024-03-25");
        assertAnchor(batch, "E5a-facility", "2024-03-01");
    }

    @Test
    @DisplayName("E5b [ambiguous]: a facility contact beyond the 7-day limit suppresses the shift to a nested department contact")
    void earliestNestedContactBeyondLimitSuppressesShift() {
        // given: IMP facility 2024-02-01–2024-03-20; IMP department 2024-03-01–2024-03-20 part of facility;
        // Consent (2024-03-04)
        probes.storePatient("E5b");
        probes.put(encounter("E5b-facility", "E5b", "IMP", "2024-02-01", "2024-03-20"));
        probes.put(departmentContact("E5b-department", "E5b", "E5b-facility", "2024-03-01", "2024-03-20"));
        probes.put(consent("E5b", "2024-03-04"));

        // when
        var batch = batchOf("E5b");
        var result = consentHandler.fetchAndBuildConsentInfo(PROSPECTIVE_CODES, batch);

        // then
        probes.assertIncluded(result, "E5b", "2024-03-04", "2024-03-12", "2024-03-25");
        assertNotShifted(batch);
    }

    @Test
    @DisplayName("E6a [expected]: consent exactly 7 days after admission shifts the collection period to the admission day")
    void consentSevenDaysAfterAdmission() {
        // given: IMP 2024-03-01–2024-03-20; Consent (2024-03-08)
        probes.storePatient("E6a");
        probes.put(encounter("E6a-IMP", "E6a", "IMP", "2024-03-01", "2024-03-20"));
        probes.put(consent("E6a", "2024-03-08"));

        // when
        var batch = batchOf("E6a");
        var result = consentHandler.fetchAndBuildConsentInfo(PROSPECTIVE_CODES, batch);

        // then
        probes.assertIncluded(result, "E6a", "2024-03-01", "2024-03-02", "2024-03-12", "2024-03-25");
        assertAnchor(batch, "E6a-IMP", "2024-03-01");
    }

    @Test
    @DisplayName("E6b [expected]: consent 8 days after admission is not shifted")
    void consentEightDaysAfterAdmission() {
        // given: IMP 2024-03-01–2024-03-20; Consent (2024-03-09)
        probes.storePatient("E6b");
        probes.put(encounter("E6b-IMP", "E6b", "IMP", "2024-03-01", "2024-03-20"));
        probes.put(consent("E6b", "2024-03-09"));

        // when
        var batch = batchOf("E6b");
        var result = consentHandler.fetchAndBuildConsentInfo(PROSPECTIVE_CODES, batch);

        // then
        probes.assertIncluded(result, "E6b", "2024-03-09", "2024-03-12", "2024-03-25");
        assertNotShifted(batch);
    }
}
