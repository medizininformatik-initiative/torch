package de.medizininformatikinitiative.torch.consent;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.Torch;
import de.medizininformatikinitiative.torch.config.TorchProperties;
import de.medizininformatikinitiative.torch.service.DataStore;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
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
 * Pins down how the encounter shift moves the start of the {@code .6} data period when a patient signs during a
 * hospital stay, against a real Blaze.
 * <p>
 * Every probe stores its own patient, Observations, Encounters and one Consent signed on the signing day (given),
 * runs the consent calculation for that patient with the encounter shift enabled (when) and asserts the derived
 * {@code .6} data period, the Observations that pass the consent check (eligible) and the Encounter that anchored the
 * shift, read from the raw provision diagnostics (then). The Consent has {@code .8} permit signing day–2054 and
 * {@code .6} permit signing day–2029; requested codes are {@code .6}+{@code .8}. Observations are dated 2019-06-01,
 * 2024-02-20, 2024-03-02, 2024-03-12 and 2024-03-25: before the stay, before admission, early in the stay, late in the
 * stay and after discharge. The stay S is IMP 2024-03-01–2024-03-20. The tests record observed behaviour, not a
 * desired outcome; each test's {@code @DisplayName} carries its status:
 * <ul>
 *     <li><b>expected</b>: follows the rules documented in {@code docs/implementation/consent.md}.</li>
 *     <li><b>ambiguous</b>: the encoding is underspecified, so no outcome is clearly right.</li>
 * </ul>
 * The test profile runs with {@code enableEncounterShift: true} and {@code encounterShiftMaxDays: 7};
 * {@link ConsentEncounterShiftConfigIT} varies the limit. The table was recorded on 2026-10-13 against v1.0.3
 * (commit ee05decb2).
 *
 * <table>
 *     <caption>Results per probe</caption>
 *     <tr><th>Probe</th><th>Signing day</th><th>Encounters</th><th>.6 data period</th><th>Eligible</th><th>Anchor</th><th>Status</th></tr>
 *     <tr><td>E1 day 4 of the stay</td><td>2024-03-04</td><td>S</td><td>2024-03-01–2029-12-31</td><td>2024-03-02, 2024-03-12, 2024-03-25</td><td>S</td><td>expected</td></tr>
 *     <tr><td>E2 day 15 of the stay</td><td>2024-03-15</td><td>S</td><td>2024-03-15–2029-12-31</td><td>2024-03-25</td><td>-</td><td>expected</td></tr>
 *     <tr><td>E3 as E1 + outpatient umbrella</td><td>2024-03-04</td><td>AMB 2018-01-01–2025-12-31, S</td><td>2024-03-01–2029-12-31</td><td>2024-03-02, 2024-03-12, 2024-03-25</td><td>S</td><td>expected</td></tr>
 *     <tr><td>E4 as E1, stay without end</td><td>2024-03-04</td><td>IMP from 2024-03-01, no end</td><td>2024-03-04–2029-12-31</td><td>2024-03-12, 2024-03-25</td><td>-</td><td>ambiguous</td></tr>
 *     <tr><td>E5 nested contacts</td><td>2024-03-04</td><td>IMP facility 2024-02-01–2024-03-20, IMP department 2024-03-01–2024-03-20 part of facility</td><td>2024-03-04–2029-12-31</td><td>2024-03-12, 2024-03-25</td><td>-</td><td>ambiguous</td></tr>
 *     <tr><td>E6a signed 7 days after admission</td><td>2024-03-08</td><td>S</td><td>2024-03-01–2029-12-31</td><td>2024-03-02, 2024-03-12, 2024-03-25</td><td>S</td><td>expected</td></tr>
 *     <tr><td>E6b signed 8 days after admission</td><td>2024-03-09</td><td>S</td><td>2024-03-09–2029-12-31</td><td>2024-03-12, 2024-03-25</td><td>-</td><td>expected</td></tr>
 * </table>
 * <p>
 * Why each probe matters:
 * <ul>
 *     <li>E1: the stay began 3 days before signing, within the 7-day limit, so the start moves to the admission day.</li>
 *     <li>E2: the guidance covers the entire current stay, so days 1–14 are excluded by the limit, not by the
 *     guidance.</li>
 *     <li>E3: before #1299 the umbrella encounter moved the start to 2018.</li>
 *     <li>E4: a patient still admitted at extraction time gets no shift, because Encounters without an end date are
 *     ignored.</li>
 *     <li>E5: a long facility contact suppresses the shift although a department contact within the limit overlaps;
 *     the earliest overlapping Encounter decides and there is no fallback to a later one.</li>
 *     <li>E6a/E6b: the limit is inclusive at 7 days.</li>
 * </ul>
 */
@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.main.allow-bean-definition-overriding=true"}, classes = Torch.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConsentEncounterContextIT {

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
        probes = new ConsentEncounterProbes(webClient, fhirContext, dataStore, consentValidator);
    }

    @Test
    @DisplayName("E1 [expected]: consent on day 4 of an inpatient stay shifts .6 to the admission day")
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
    @DisplayName("E3 [expected]: an outpatient umbrella encounter is ignored and only the inpatient stay shifts .6")
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
    @DisplayName("E4 [ambiguous]: an inpatient stay without end date does not shift .6")
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
    @DisplayName("E5 [ambiguous]: a facility contact beyond the 7-day limit suppresses the shift to a nested department contact")
    void earliestNestedContactBeyondLimitSuppressesShift() {
        // given: IMP facility 2024-02-01–2024-03-20; IMP department 2024-03-01–2024-03-20 part of facility;
        // Consent (2024-03-04)
        probes.storePatient("E5");
        probes.put(encounter("E5-facility", "E5", "IMP", "2024-02-01", "2024-03-20"));
        probes.put(encounter("E5-department", "E5", "IMP", "2024-03-01", "2024-03-20")
                .setPartOf(new Reference("Encounter/E5-facility")));
        probes.put(consent("E5", "2024-03-04"));

        // when
        var batch = batchOf("E5");
        var result = consentHandler.fetchAndBuildConsentInfo(PROSPECTIVE_CODES, batch);

        // then
        probes.assertIncluded(result, "E5", "2024-03-04", "2024-03-12", "2024-03-25");
        assertNotShifted(batch);
    }

    @Test
    @DisplayName("E6a [expected]: consent exactly 7 days after admission shifts .6 to the admission day")
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
