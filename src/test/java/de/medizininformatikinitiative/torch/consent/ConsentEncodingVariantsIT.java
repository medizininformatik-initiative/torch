package de.medizininformatikinitiative.torch.consent;

import ca.uhn.fhir.context.FhirContext;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.medizininformatikinitiative.torch.Torch;
import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.model.consent.NonContinuousPeriod;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.consent.Period;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import de.medizininformatikinitiative.torch.model.management.TermCode;
import de.medizininformatikinitiative.torch.service.DataStore;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins down how TORCH treats different storage-level encodings of the same patient will against a real Blaze.
 * <p>
 * {@link ConsentFetcher} only sees current versions of active Consents, so inactivating, deleting, overwriting or
 * adding a new deny Consent can lead to different results for the same will. Each variant uses its own patient and
 * asserts inclusion, the stage and cause of an exclusion, the derived collection period and the Observations that
 * pass the consent check. The tests record observed behaviour, not a desired outcome; each test's
 * {@code @DisplayName} carries its status:
 * <ul>
 *     <li><b>expected</b>: follows the agreed rules documented in {@code docs/implementation/consent.md}. For
 *     collection this departs from the note in #1271 that a retrospective refusal does not cancel collection or
 *     research use: under the reset of #1250, a refusal of either retrospective module discards the collection
 *     periods of earlier signing events, while a collection permit of the same or a later signing event still counts.
 *     Research use is never affected by the reset.</li>
 *     <li><b>ambiguous</b>: the will or its encoding is underspecified, so no outcome is clearly right.</li>
 * </ul>
 * Interpretation-only cases (ordering, retro denies within vs. across resources) are covered in
 * {@code ConsentCalculatorTest}.
 * <p>
 * Consent modules are named by their meaning as in {@code docs/implementation/consent.md}: collection = {@code .6}
 * (MDAT erheben, {@code 2.16.840.1.113883.3.1937.777.24.5.3.6}), research use = {@code .8} (MDAT wissenschaftlich
 * nutzen, {@code 2.16.840.1.113883.3.1937.777.24.5.3.8}) and retrospective use = {@code .45}
 * ({@code 2.16.840.1.113883.3.1937.777.24.5.3.45}) or {@code .46} ({@code 2.16.840.1.113883.3.1937.777.24.5.3.46}).
 * <p>
 * Each test stores its own patient, Observations and Consents (given), runs the consent calculation for that patient
 * (when) and asserts the outcome (then). The comment in each test's setup lists the stored Consents and the modules
 * of their provisions. Scenarios 1–3 share the dates consent 2020, withdrawal / retrospective refusal 2023 and
 * re-consent 2024. The collection lookback date is {@code 1900-01-01} ({@code mappings/consent-code-config.json}): an
 * Observation dated before the first consent is only eligible if a retrospective permit extends the collection period
 * back to it. The research-use validity gate must contain the day the test runs, so results are date-relative; the
 * table was recorded on 2026-10-13 against v1.0.3 (commit ee05decb2) and holds until the end of 2045. Requested
 * modules: P = collection + research use, R = P + retrospective use.
 *
 * <table>
 *     <caption>Results per variant</caption>
 *     <tr><th>Variant</th><th>Requested modules</th><th>Included</th><th>Collection period</th><th>Eligible</th><th>Status</th></tr>
 *     <tr><td>1a inactive</td><td>P</td><td>no: no active Consent</td><td>-</td><td>-</td><td>expected</td></tr>
 *     <tr><td>1b deny resource</td><td>P</td><td>no: research-use gate</td><td>-</td><td>-</td><td>expected</td></tr>
 *     <tr><td>1c overwritten</td><td>P</td><td>no: research-use gate</td><td>-</td><td>-</td><td>expected</td></tr>
 *     <tr><td>1d deleted</td><td>P</td><td>no: no active Consent</td><td>-</td><td>-</td><td>expected</td></tr>
 *     <tr><td>2a retrospective refusal separate</td><td>R</td><td>no: no collection period (retrospective refusal resets)</td><td>-</td><td>-</td><td>expected</td></tr>
 *     <tr><td>2b retrospective refusal same resource</td><td>R</td><td>yes</td><td>1900–2024</td><td>2010, 2021, 2023, 2024</td><td>ambiguous</td></tr>
 *     <tr><td>2c collection deny from lookback</td><td>R</td><td>no: no collection period</td><td>-</td><td>-</td><td>expected</td></tr>
 *     <tr><td>3a inactive + re-consent</td><td>P</td><td>yes</td><td>2024–2028</td><td>2024</td><td>ambiguous</td></tr>
 *     <tr><td>3b deny resource + re-consent</td><td>P</td><td>yes</td><td>2020–2022, 2024–2028</td><td>2021, 2024</td><td>expected</td></tr>
 *     <tr><td>3c overwritten + re-consent</td><td>P</td><td>yes</td><td>2020–2022, 2024–2028</td><td>2021, 2024</td><td>expected</td></tr>
 *     <tr><td>3d deleted + re-consent</td><td>P</td><td>yes</td><td>2024–2028</td><td>2024</td><td>ambiguous</td></tr>
 *     <tr><td>4a re-consent with retrospective refusal</td><td>R</td><td>yes</td><td>2025–2030</td><td>2026</td><td>expected</td></tr>
 *     <tr><td>4b as 4a + old-period collection deny + overlapping research-use deny</td><td>R</td><td>no: research-use gate</td><td>-</td><td>-</td><td>ambiguous</td></tr>
 *     <tr><td>4c retrospective refusal only</td><td>R</td><td>no: no collection period (retrospective refusal resets)</td><td>-</td><td>-</td><td>expected</td></tr>
 *     <tr><td>4d retrospective refusal + new collection/research use</td><td>R</td><td>yes</td><td>2025–2030</td><td>2026</td><td>expected</td></tr>
 *     <tr><td>5a retrospective permit in separate resource</td><td>R</td><td>yes</td><td>2001–2004, 2006–2014</td><td>2003, 2010</td><td>ambiguous</td></tr>
 *     <tr><td>5b later non-overlapping collection deny</td><td>P</td><td>yes</td><td>2001–2004, 2006–2014</td><td>2003, 2010</td><td>expected</td></tr>
 *     <tr><td>5c newer permit after deny</td><td>P</td><td>yes</td><td>2001–2004, 2006–2014, 2023–2026</td><td>2003, 2010, 2025</td><td>expected</td></tr>
 *     <tr><td>5d retrospective permit in separate resource, same dateTime</td><td>R</td><td>yes</td><td>1900–2004</td><td>1999, 2003</td><td>expected</td></tr>
 *     <tr><td>5e research use and collection in two resources, same dateTime</td><td>P</td><td>yes</td><td>2020–2024</td><td>2021, 2024</td><td>expected</td></tr>
 *     <tr><td>6 blanket root deny</td><td>P</td><td>yes</td><td>2020–2024</td><td>2021, 2024</td><td>ambiguous</td></tr>
 *     <tr><td>7 expired collection, valid research use</td><td>P</td><td>yes</td><td>2015–2019</td><td>2016</td><td>expected</td></tr>
 * </table>
 * <p>
 * Divergences between variants of the same will:
 * <ul>
 *     <li>Scenario 1: all variants exclude the patient, but 1a/1d fail already in {@link ConsentFetcher} while
 *     1b/1c are excluded by the research-use validity gate in {@link ConsentCalculator}.</li>
 *     <li>Scenario 2: a retrospective refusal resets the whole collection history of everything signed before it. As
 *     a separate Consent without a new collection permit (2a) this excludes the patient. Added to the original Consent
 *     (2b) it only reduces that Consent's retrospective extension where its period overlaps; the refusal period
 *     2023–2024 does not overlap the pre-2020 data it is meant to refuse, so the refusal has no effect. A collection
 *     deny from lookback (2c) also removes the prospective part, so the encoding is broader than the will.</li>
 *     <li>Scenario 3: 3a/3d lose the pre-withdrawal period 2020–2022 because the original Consent is no longer
 *     visible; 3b/3c keep it and only exclude the gap. Whether an inactive or deleted Consent means "withdrawn from
 *     then on" or "never valid" is not specified.</li>
 *     <li>Scenario 4: a retrospective refusal in a later Consent resets the earlier collection periods, so only the
 *     new permit remains (4a, 4d) or the patient is excluded when there is none (4c). Adding a collection deny for the
 *     old period and a research-use deny 2020–2050 to that Consent excludes the patient instead (4b): within one
 *     signing event a deny wins over a permit, so the research-use deny removes the earlier research-use permit and
 *     the Consent's own research-use permit up to 2050, and the validity gate fails. A research-use deny cannot
 *     express "not the old data", so the encoding contradicts itself. #1274 records this question and was closed as
 *     not planned.</li>
 *     <li>Scenario 5: a retrospective permit only extends a collection permit of the same signing event. In 5a the
 *     retrospective permit is a separate signing event (2002), so it does not extend the 2001 collection permit; 5d
 *     shows that a retrospective permit in a separate resource of the same signing event does. Whether separate
 *     signing events can still form one consent document is not specified; #1275 records this question and was
 *     closed as not planned.</li>
 *     <li>Scenario 5d/5e: under v1.0.2 the result depended on whether one signing event was stored as one or two
 *     resources (raised in #1275); #1321 aggregates provisions per signing event.</li>
 *     <li>Scenario 6: a root-level deny without nested provisions has no effect, since only nested provisions are
 *     evaluated. Whether a Consent consisting only of its root provision revokes earlier Consents is not specified.</li>
 *     <li>Scenario 7: collection is checked at the date of the data and research use at extraction time. A single
 *     consent status at extraction time would exclude this patient, since the collection period does not contain
 *     today, although the 2016 data were collected with consent.</li>
 * </ul>
 */
@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.main.allow-bean-definition-overriding=true"}, classes = Torch.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConsentEncodingVariantsIT {

    static final String MII_CONSENT_SYSTEM = "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3";
    static final String MDAT_ERHEBEN = "2.16.840.1.113883.3.1937.777.24.5.3.6";
    static final String MDAT_NUTZEN = "2.16.840.1.113883.3.1937.777.24.5.3.8";
    static final String MDAT_RETRO = "2.16.840.1.113883.3.1937.777.24.5.3.45";
    static final Set<TermCode> PROSPECTIVE_CODES = Set.of(
            new TermCode(MII_CONSENT_SYSTEM, MDAT_NUTZEN),
            new TermCode(MII_CONSENT_SYSTEM, MDAT_ERHEBEN));
    static final Set<TermCode> RETRO_CODES = Set.of(
            new TermCode(MII_CONSENT_SYSTEM, MDAT_NUTZEN),
            new TermCode(MII_CONSENT_SYSTEM, MDAT_ERHEBEN),
            new TermCode(MII_CONSENT_SYSTEM, MDAT_RETRO));
    static final String CONSENT_PROFILE = "https://www.medizininformatik-initiative.de/fhir/modul-consent/StructureDefinition/mii-pr-consent-einwilligung";
    /**
     * Observations of scenarios 1–3: before the consent, consented, after the withdrawal, after the re-consent.
     */
    static final List<String> OBSERVATION_YEARS = List.of("2010", "2021", "2023", "2024");
    static final List<String> RECONSENT_REFUSAL_OBSERVATION_YEARS = List.of("2019", "2021", "2024", "2026");
    static final List<String> SPLIT_OBSERVATION_YEARS = List.of("1999", "2003", "2005", "2010", "2025");

    static final String CONSENT_DATE = "2020-01-01";
    static final String WITHDRAWAL_DATE = "2023-01-01";
    static final String RECONSENT_DATE = "2024-01-01";

    /**
     * Debug messages {@link ConsentCalculator} logs when it excludes a patient, after {@code "Patient <id> excluded: "}.
     */
    static final String CODE_NOT_PERMITTED = "required consent codes not fully permitted";
    static final String GATE_EXCLUDES_TODAY = "validity-gate period does not contain today";

    static final NonContinuousPeriod RETRO_WINDOW = new NonContinuousPeriod(List.of(Period.of("1900-01-01", "2024-12-31")));
    static final NonContinuousPeriod RECONSENT_WINDOW = new NonContinuousPeriod(List.of(Period.of("2024-01-01", "2028-12-31")));
    static final NonContinuousPeriod GAP_WINDOW = new NonContinuousPeriod(List.of(
            Period.of("2020-01-01", "2022-12-31"),
            Period.of("2024-01-01", "2028-12-31")));

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

    @RegisterExtension
    static final ConsentResultsTable RESULTS = new ConsentResultsTable("ConsentEncodingVariantsIT");

    final Logger calculatorLogger = (Logger) LoggerFactory.getLogger(ConsentCalculator.class);
    final ListAppender<ILoggingEvent> calculatorLog = new ListAppender<>();
    Level calculatorLogLevel;

    @BeforeAll
    void captureCalculatorLog() {
        calculatorLogLevel = calculatorLogger.getLevel();
        calculatorLogger.setLevel(Level.DEBUG);
        calculatorLog.start();
        calculatorLogger.addAppender(calculatorLog);
    }

    @AfterAll
    void restoreCalculatorLogger() {
        calculatorLogger.detachAppender(calculatorLog);
        calculatorLogger.setLevel(calculatorLogLevel);
    }

    @Test
    @DisplayName("1a [expected]: withdrawal by inactivating the Consent excludes the patient during fetch")
    void withdrawalByInactivation() {
        // given: Consent A (2020) research-use + collection permit, then set to inactive
        storePatient("1a", OBSERVATION_YEARS);
        put(fullConsent("1a-A", "1a"));
        put(inactive(fullConsent("1a-A", "1a")));

        // when
        var result = consentHandler.fetchAndBuildConsentInfo(PROSPECTIVE_CODES, PatientBatch.of("1a"));

        // then
        assertExcludedDuringFetch(result);
    }

    @Test
    @DisplayName("1b [expected]: withdrawal by a separate deny Consent excludes the patient by the research-use validity gate")
    void withdrawalByDenyResource() {
        // given: Consent A (2020) research-use + collection permit; Consent B (2023) research-use + collection deny
        storePatient("1b", OBSERVATION_YEARS);
        put(fullConsent("1b-A", "1b"));
        put(fullDeny("1b-B", "1b"));

        // when
        var result = consentHandler.fetchAndBuildConsentInfo(PROSPECTIVE_CODES, PatientBatch.of("1b"));

        // then
        assertExcludedDuringCalculation(result, "1b", GATE_EXCLUDES_TODAY);
    }

    @Test
    @DisplayName("1c [expected]: withdrawal by overwriting the Consent excludes the patient by the research-use validity gate")
    void withdrawalByOverwrite() {
        // given: Consent A (2020) research-use + collection permit, overwritten by a version with research-use + collection permit until 2022 and
        // research-use + collection deny from 2023
        storePatient("1c", OBSERVATION_YEARS);
        put(fullConsent("1c-A", "1c"));
        put(overwrittenWithDeny("1c-A", "1c"));

        // when
        var result = consentHandler.fetchAndBuildConsentInfo(PROSPECTIVE_CODES, PatientBatch.of("1c"));

        // then
        assertExcludedDuringCalculation(result, "1c", GATE_EXCLUDES_TODAY);
    }

    @Test
    @DisplayName("1d [expected]: withdrawal by deleting the Consent excludes the patient during fetch")
    void withdrawalByDeletion() {
        // given: Consent A (2020) research-use + collection permit, then deleted
        storePatient("1d", OBSERVATION_YEARS);
        put(fullConsent("1d-A", "1d"));
        delete("Consent", "1d-A");

        // when
        var result = consentHandler.fetchAndBuildConsentInfo(PROSPECTIVE_CODES, PatientBatch.of("1d"));

        // then
        assertExcludedDuringFetch(result);
    }

    @Test
    @DisplayName("2a [expected]: retrospective refusal in a separate Consent resets the collection periods and excludes the patient")
    void retroRefusalInSeparateResource() {
        // given: Consent A (2020) research-use + collection + retrospective permit; Consent B (2023) retrospective refusal
        storePatient("2a", OBSERVATION_YEARS);
        put(retroConsent("2a-A", "2a"));
        put(consent("2a-B", "2a", WITHDRAWAL_DATE,
                deny(MDAT_RETRO, WITHDRAWAL_DATE, "2024-12-31")));

        // when
        var result = consentHandler.fetchAndBuildConsentInfo(RETRO_CODES, PatientBatch.of("2a"));

        // then
        assertExcludedDuringCalculation(result, "2a", CODE_NOT_PERMITTED);
    }

    @Test
    @DisplayName("2b [ambiguous]: retrospective refusal added to the original Consent keeps the retrospective window")
    void retroRefusalInSameResource() {
        // given: Consent A (2020) research-use + collection + retrospective permit, overwritten by a version with retrospective permit until 2022 and
        // retrospective refusal from 2023
        storePatient("2b", OBSERVATION_YEARS);
        put(retroConsent("2b-A", "2b"));
        put(consent("2b-A", "2b", CONSENT_DATE,
                permit(MDAT_NUTZEN, CONSENT_DATE, "2049-12-31"),
                permit(MDAT_ERHEBEN, CONSENT_DATE, "2024-12-31"),
                permit(MDAT_RETRO, CONSENT_DATE, "2022-12-31"),
                deny(MDAT_RETRO, WITHDRAWAL_DATE, "2024-12-31")));

        // when
        var result = consentHandler.fetchAndBuildConsentInfo(RETRO_CODES, PatientBatch.of("2b"));

        // then
        assertIncluded(result, "2b", RETRO_WINDOW, "2010", "2021", "2023", "2024");
    }

    @Test
    @DisplayName("2c [expected]: collection deny from lookback removes the whole collection period and excludes the patient")
    void retroRefusalByMdatDeny() {
        // given: Consent A (2020) research-use + collection + retrospective permit; Consent B (2023) collection deny from the lookback date
        storePatient("2c", OBSERVATION_YEARS);
        put(retroConsent("2c-A", "2c"));
        put(consent("2c-B", "2c", WITHDRAWAL_DATE,
                deny(MDAT_ERHEBEN, "1900-01-01", "2024-12-31")));

        // when
        var result = consentHandler.fetchAndBuildConsentInfo(RETRO_CODES, PatientBatch.of("2c"));

        // then
        assertExcludedDuringCalculation(result, "2c", CODE_NOT_PERMITTED);
    }

    @Test
    @DisplayName("3a [ambiguous]: re-consent after inactivation only covers the new Consent")
    void reConsentAfterInactivation() {
        // given: Consent A (2020) research-use + collection permit, then set to inactive; Consent C (2024) research-use + collection permit; no retrospective use
        storePatient("3a", OBSERVATION_YEARS);
        put(fullConsent("3a-A", "3a"));
        put(inactive(fullConsent("3a-A", "3a")));
        put(reConsent("3a-C", "3a"));

        // when
        var result = consentHandler.fetchAndBuildConsentInfo(PROSPECTIVE_CODES, PatientBatch.of("3a"));

        // then
        assertIncluded(result, "3a", RECONSENT_WINDOW, "2024");
    }

    @Test
    @DisplayName("3b [expected]: re-consent after a deny Consent keeps the pre-withdrawal period and excludes the gap")
    void reConsentAfterDenyResource() {
        // given: Consent A (2020) research-use + collection permit; Consent B (2023) research-use + collection deny; Consent C (2024) research-use + collection permit
        storePatient("3b", OBSERVATION_YEARS);
        put(fullConsent("3b-A", "3b"));
        put(fullDeny("3b-B", "3b"));
        put(reConsent("3b-C", "3b"));

        // when
        var result = consentHandler.fetchAndBuildConsentInfo(PROSPECTIVE_CODES, PatientBatch.of("3b"));

        // then
        assertIncluded(result, "3b", GAP_WINDOW, "2021", "2024");
    }

    @Test
    @DisplayName("3c [expected]: re-consent after overwriting keeps the pre-withdrawal period and excludes the gap")
    void reConsentAfterOverwrite() {
        // given: Consent A (2020) research-use + collection permit, overwritten by a version with research-use + collection permit until 2022 and
        // research-use + collection deny from 2023; Consent C (2024) research-use + collection permit
        storePatient("3c", OBSERVATION_YEARS);
        put(fullConsent("3c-A", "3c"));
        put(overwrittenWithDeny("3c-A", "3c"));
        put(reConsent("3c-C", "3c"));

        // when
        var result = consentHandler.fetchAndBuildConsentInfo(PROSPECTIVE_CODES, PatientBatch.of("3c"));

        // then
        assertIncluded(result, "3c", GAP_WINDOW, "2021", "2024");
    }

    @Test
    @DisplayName("3d [ambiguous]: re-consent after deletion only covers the new Consent")
    void reConsentAfterDeletion() {
        // given: Consent A (2020) research-use + collection permit, then deleted; Consent C (2024) research-use + collection permit
        storePatient("3d", OBSERVATION_YEARS);
        put(fullConsent("3d-A", "3d"));
        delete("Consent", "3d-A");
        put(reConsent("3d-C", "3d"));

        // when
        var result = consentHandler.fetchAndBuildConsentInfo(PROSPECTIVE_CODES, PatientBatch.of("3d"));

        // then
        assertIncluded(result, "3d", RECONSENT_WINDOW, "2024");
    }

    @Test
    @DisplayName("4a [expected]: re-consent refusing retrospective use resets the earlier collection periods and keeps only the new permit")
    void reConsentRefusingRetro() {
        // given: R1 (2020) research-use permit to 2050, collection permit to 2023, retrospective permit to 2025; R2 (2025) research-use + collection permit, retrospective refusal
        storePatient("4a", RECONSENT_REFUSAL_OBSERVATION_YEARS);
        put(retroConsentUntil2023("4a-R1", "4a"));
        put(reConsentRefusingRetro("4a-R2", "4a"));

        // when
        var result = consentHandler.fetchAndBuildConsentInfo(RETRO_CODES, PatientBatch.of("4a"));

        // then
        assertIncluded(result, "4a", periods("2025-01-01", "2030-12-31"), "2026");
    }

    @Test
    @DisplayName("4b [ambiguous]: re-consent with an old-period collection deny and an overlapping research-use deny cancels its own research-use permit and excludes the patient")
    void reConsentRefusingRetroWithExplicitDenies() {
        // given: R1 as in 4a; R2 (2025) research-use permit 2025–2055 and collection permit 2025–2030, retrospective refusal, collection deny 2020–2023
        // and research-use deny 2020–2050
        storePatient("4b", RECONSENT_REFUSAL_OBSERVATION_YEARS);
        put(retroConsentUntil2023("4b-R1", "4b"));
        put(consent("4b-R2", "4b", "2025-01-01",
                permit(MDAT_NUTZEN, "2025-01-01", "2055-12-31"),
                permit(MDAT_ERHEBEN, "2025-01-01", "2030-12-31"),
                deny(MDAT_RETRO, "2025-01-01", "2028-12-31"),
                deny(MDAT_ERHEBEN, "2020-01-01", "2023-12-31"),
                deny(MDAT_NUTZEN, "2020-01-01", "2050-12-31")));

        // when
        var result = consentHandler.fetchAndBuildConsentInfo(RETRO_CODES, PatientBatch.of("4b"));

        // then
        assertExcludedDuringCalculation(result, "4b", GATE_EXCLUDES_TODAY);
    }

    @Test
    @DisplayName("4c [expected]: retrospective refusal without new collection and research-use permits resets the earlier collection periods and excludes the patient")
    void retroRefusalWithoutReConsent() {
        // given: R1 (2020) research-use permit to 2050, collection permit to 2023, no retrospective use; R2 (2025) retrospective refusal only
        storePatient("4c", RECONSENT_REFUSAL_OBSERVATION_YEARS);
        put(consentUntil2023("4c-R1", "4c"));
        put(consent("4c-R2", "4c", "2025-01-01",
                deny(MDAT_RETRO, "2025-01-01", "2028-12-31")));

        // when
        var result = consentHandler.fetchAndBuildConsentInfo(RETRO_CODES, PatientBatch.of("4c"));

        // then
        assertExcludedDuringCalculation(result, "4c", CODE_NOT_PERMITTED);
    }

    @Test
    @DisplayName("4d [expected]: retrospective refusal with new collection and research-use permits resets the earlier collection periods and keeps only the new permit")
    void retroRefusalWithReConsent() {
        // given: R1 (2020) research-use permit to 2050, collection permit to 2023, no retrospective use; R2 (2025) research-use + collection permit, retrospective refusal
        storePatient("4d", RECONSENT_REFUSAL_OBSERVATION_YEARS);
        put(consentUntil2023("4d-R1", "4d"));
        put(reConsentRefusingRetro("4d-R2", "4d"));

        // when
        var result = consentHandler.fetchAndBuildConsentInfo(RETRO_CODES, PatientBatch.of("4d"));

        // then
        assertIncluded(result, "4d", periods("2025-01-01", "2030-12-31"), "2026");
    }

    @Test
    @DisplayName("5a [ambiguous]: retrospective permit in a separate resource does not extend an overlapping collection permit")
    void retroPermitInSeparateResource() {
        // given: R1 (2001) research-use + collection permit; R2 (2002) retrospective permit only; R3 (2006) research-use + collection permit
        storePatient("5a", SPLIT_OBSERVATION_YEARS);
        put(consent("5a-R1", "5a", "2001-01-01",
                permit(MDAT_NUTZEN, "2001-01-01", "2050-12-31"),
                permit(MDAT_ERHEBEN, "2001-01-01", "2004-12-31")));
        put(consent("5a-R2", "5a", "2002-01-01",
                permit(MDAT_RETRO, "2002-01-01", "2005-12-31")));
        put(consent("5a-R3", "5a", "2006-01-01",
                permit(MDAT_NUTZEN, "2006-01-01", "2050-12-31"),
                permit(MDAT_ERHEBEN, "2006-01-01", "2014-12-31")));

        // when
        var result = consentHandler.fetchAndBuildConsentInfo(RETRO_CODES, PatientBatch.of("5a"));

        // then
        assertIncluded(result, "5a", periods("2001-01-01", "2004-12-31", "2006-01-01", "2014-12-31"),
                "2003", "2010");
    }

    @Test
    @DisplayName("5b [expected]: later collection deny without overlap keeps the earlier collection permits")
    void laterNonOverlappingDeny() {
        // given: R1 (2001) and R2 (2006) research-use + collection permit; R3 (2020) collection deny 2020–2024
        storePatient("5b", SPLIT_OBSERVATION_YEARS);
        storeSplitPermitsWithLaterDeny("5b");

        // when
        var result = consentHandler.fetchAndBuildConsentInfo(PROSPECTIVE_CODES, PatientBatch.of("5b"));

        // then
        assertIncluded(result, "5b", periods("2001-01-01", "2004-12-31", "2006-01-01", "2014-12-31"),
                "2003", "2010");
    }

    @Test
    @DisplayName("5c [expected]: newer collection permit beats an older collection deny")
    void newerPermitAfterDeny() {
        // given: as in 5b, plus R4 (2023) research-use + collection permit 2023–2026
        storePatient("5c", SPLIT_OBSERVATION_YEARS);
        storeSplitPermitsWithLaterDeny("5c");
        put(consent("5c-R4", "5c", "2023-01-01",
                permit(MDAT_NUTZEN, "2023-01-01", "2050-12-31"),
                permit(MDAT_ERHEBEN, "2023-01-01", "2026-12-31")));

        // when
        var result = consentHandler.fetchAndBuildConsentInfo(PROSPECTIVE_CODES, PatientBatch.of("5c"));

        // then
        assertIncluded(result, "5c",
                periods("2001-01-01", "2004-12-31", "2006-01-01", "2014-12-31", "2023-01-01", "2026-12-31"),
                "2003", "2010", "2025");
    }

    @Test
    @DisplayName("5d [expected]: retrospective permit in a separate Consent of the same signing event extends the collection period to the lookback date")
    void retroPermitInSeparateResourceOfSameEvent() {
        // given: R1 (2001) research-use + collection permit; R2 (2001) retrospective permit only
        storePatient("5d", SPLIT_OBSERVATION_YEARS);
        put(consent("5d-R1", "5d", "2001-01-01",
                permit(MDAT_NUTZEN, "2001-01-01", "2050-12-31"),
                permit(MDAT_ERHEBEN, "2001-01-01", "2004-12-31")));
        put(consent("5d-R2", "5d", "2001-01-01",
                permit(MDAT_RETRO, "2001-01-01", "2005-12-31")));

        // when
        var result = consentHandler.fetchAndBuildConsentInfo(RETRO_CODES, PatientBatch.of("5d"));

        // then
        assertIncluded(result, "5d", periods("1900-01-01", "2004-12-31"), "1999", "2003");
    }

    @Test
    @DisplayName("5e [expected]: research-use and collection permits in two Consents of the same signing event form the full package")
    void fullPackageSplitAcrossResourcesOfSameEvent() {
        // given: R1 (2020) research-use permit only; R2 (2020) collection permit only
        storePatient("5e", RECONSENT_REFUSAL_OBSERVATION_YEARS);
        put(consent("5e-R1", "5e", CONSENT_DATE,
                permit(MDAT_NUTZEN, CONSENT_DATE, "2050-12-31")));
        put(consent("5e-R2", "5e", CONSENT_DATE,
                permit(MDAT_ERHEBEN, CONSENT_DATE, "2024-12-31")));

        // when
        var result = consentHandler.fetchAndBuildConsentInfo(PROSPECTIVE_CODES, PatientBatch.of("5e"));

        // then
        assertIncluded(result, "5e", periods("2020-01-01", "2024-12-31"), "2021", "2024");
    }

    @Test
    @DisplayName("6 [ambiguous]: blanket deny on the root provision without nested provisions is ignored")
    void blanketDenyIsIgnored() {
        // given: R1 (2020) research-use + collection permit; R2 (2025) root-level deny 2025–2050 without nested provisions
        storePatient("6", RECONSENT_REFUSAL_OBSERVATION_YEARS);
        put(consent("6-R1", "6", CONSENT_DATE,
                permit(MDAT_NUTZEN, CONSENT_DATE, "2050-12-31"),
                permit(MDAT_ERHEBEN, CONSENT_DATE, "2024-12-31")));
        put(blanketDeny("6-R2", "6", "2025-01-01", "2050-12-31"));

        // when
        var result = consentHandler.fetchAndBuildConsentInfo(PROSPECTIVE_CODES, PatientBatch.of("6"));

        // then
        assertIncluded(result, "6", periods("2020-01-01", "2024-12-31"), "2021", "2024");
    }

    @Test
    @DisplayName("7 [expected]: expired collection permit with valid research use keeps the patient; only data from the collection period are eligible")
    void expiredCollectionWithValidUse() {
        // given: Consent (2015) research-use permit 2015–2045 and collection permit 2015–2019
        storePatient("7", List.of("2014", "2016", "2021"));
        put(consent("7-R1", "7", "2015-01-01",
                permit(MDAT_NUTZEN, "2015-01-01", "2045-12-31"),
                permit(MDAT_ERHEBEN, "2015-01-01", "2019-12-31")));

        // when
        var result = consentHandler.fetchAndBuildConsentInfo(PROSPECTIVE_CODES, PatientBatch.of("7"));

        // then
        assertIncluded(result, "7", periods("2015-01-01", "2019-12-31"), "2016");
    }

    /**
     * Builds a period from consecutive start/end date pairs.
     */
    private static NonContinuousPeriod periods(String... startEndPairs) {
        List<Period> result = new ArrayList<>();
        for (int i = 0; i + 1 < startEndPairs.length; i += 2) {
            result.add(Period.of(startEndPairs[i], startEndPairs[i + 1]));
        }
        return new NonContinuousPeriod(result);
    }

    private static void assertExcludedDuringFetch(Mono<PatientBatchWithConsent> result) {
        RESULTS.recordExcluded("no valid consent period during fetch");
        StepVerifier.create(result)
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ConsentViolatedException.class)
                        .hasMessageContaining("No valid consentPeriods found for any patients in batch"))
                .verify();
    }

    /**
     * Asserts that {@link ConsentCalculator} excluded the patient for the given cause, one of the debug messages it
     * logs, since all causes surface as the same {@link ConsentViolatedException}.
     */
    private void assertExcludedDuringCalculation(Mono<PatientBatchWithConsent> result, String patientId, String cause) {
        StepVerifier.create(result)
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ConsentViolatedException.class)
                        .hasMessageContaining("No patients with valid consent periods found in batch"))
                .verify();
        String prefix = "Patient " + patientId + " excluded: ";
        List<String> exclusions = calculatorLog.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.startsWith(prefix))
                .map(message -> message.substring(prefix.length()))
                .toList();
        RESULTS.recordExcluded(String.join("; ", exclusions));
        assertThat(exclusions).singleElement().asString().contains(cause);
    }

    private void assertIncluded(Mono<PatientBatchWithConsent> result, String patientId, NonContinuousPeriod expectedPeriod,
                                String... expectedObservationYears) {
        PatientBatchWithConsent batch = result.block();

        assertThat(batch).isNotNull();
        assertThat(batch.patientIds()).containsExactly(patientId);
        NonContinuousPeriod collectionPeriod = batch.get(patientId).consentPeriods();
        List<String> eligible = eligibleObservationIds(batch, patientId);
        RESULTS.recordIncluded(collectionPeriod, eligible);
        assertThat(collectionPeriod).isEqualTo(expectedPeriod);
        assertThat(eligible).containsExactlyInAnyOrder(
                Arrays.stream(expectedObservationYears).map(year -> observationId(patientId, year)).toArray(String[]::new));
    }

    /**
     * Returns the ids of the patient's Observations in Blaze that pass the consent check.
     */
    private List<String> eligibleObservationIds(PatientBatchWithConsent batch, String patientId) {
        return dataStore.search(Query.of("Observation", PatientBatch.of(patientId).compartmentSearchParam("Observation")), Observation.class)
                .filter(observation -> consentValidator.checkConsent(observation, batch)
                        .map(ConsentCheckResult::included)
                        .orElse(false))
                .map(Observation::getIdPart)
                .collectList()
                .block();
    }

    private void storePatient(String patientId, List<String> observationYears) {
        put(new Patient().setId(patientId));
        observationYears.forEach(year -> {
            Observation observation = new Observation();
            observation.setId(observationId(patientId, year));
            observation.setStatus(Observation.ObservationStatus.FINAL);
            observation.setCode(new CodeableConcept(new Coding("http://loinc.org", "8302-2", "Body height")));
            observation.setSubject(new Reference("Patient/" + patientId));
            observation.setEffective(new DateTimeType(year + "-06-01"));
            put(observation);
        });
    }

    private static String observationId(String patientId, String year) {
        return patientId + "-obs-" + year;
    }

    private void put(Resource resource) {
        String type = resource.fhirType();
        webClient.put()
                .uri("/{type}/{id}", type, resource.getIdElement().getIdPart())
                .header("Content-Type", "application/fhir+json")
                .bodyValue(fhirContext.newJsonParser().encodeResourceToString(resource))
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    private void delete(String type, String id) {
        webClient.delete()
                .uri("/{type}/{id}", type, id)
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    /**
     * Consent A of scenarios 1 and 3 (2020): research-use permit 2020–2049 and collection permit 2020–2024, no retrospective use.
     */
    private static Consent fullConsent(String id, String patientId) {
        return consent(id, patientId, CONSENT_DATE,
                permit(MDAT_NUTZEN, CONSENT_DATE, "2049-12-31"),
                permit(MDAT_ERHEBEN, CONSENT_DATE, "2024-12-31"));
    }

    /**
     * Consent B of scenarios 1 and 3 (2023): research-use deny 2023–2049 and collection deny 2023–2024.
     */
    private static Consent fullDeny(String id, String patientId) {
        return consent(id, patientId, WITHDRAWAL_DATE,
                deny(MDAT_NUTZEN, WITHDRAWAL_DATE, "2049-12-31"),
                deny(MDAT_ERHEBEN, WITHDRAWAL_DATE, "2024-12-31"));
    }

    /**
     * New version of Consent A (2020): research-use and collection permit 2020–2022, research-use deny 2023–2049 and collection deny 2023–2024.
     */
    private static Consent overwrittenWithDeny(String id, String patientId) {
        return consent(id, patientId, CONSENT_DATE,
                permit(MDAT_NUTZEN, CONSENT_DATE, "2022-12-31"),
                permit(MDAT_ERHEBEN, CONSENT_DATE, "2022-12-31"),
                deny(MDAT_NUTZEN, WITHDRAWAL_DATE, "2049-12-31"),
                deny(MDAT_ERHEBEN, WITHDRAWAL_DATE, "2024-12-31"));
    }

    /**
     * Consent C of scenario 3 (2024): research-use permit 2024–2053 and collection permit 2024–2028, no retrospective use.
     */
    private static Consent reConsent(String id, String patientId) {
        return consent(id, patientId, RECONSENT_DATE,
                permit(MDAT_NUTZEN, RECONSENT_DATE, "2053-12-31"),
                permit(MDAT_ERHEBEN, RECONSENT_DATE, "2028-12-31"));
    }

    /**
     * Consent A of scenario 2 (2020): research-use permit 2020–2049, collection permit 2020–2024 and retrospective permit 2020–2024.
     */
    private static Consent retroConsent(String id, String patientId) {
        return consent(id, patientId, CONSENT_DATE,
                permit(MDAT_NUTZEN, CONSENT_DATE, "2049-12-31"),
                permit(MDAT_ERHEBEN, CONSENT_DATE, "2024-12-31"),
                permit(MDAT_RETRO, CONSENT_DATE, "2024-12-31"));
    }

    /**
     * R1 of scenarios 4a/4b (2020): research-use permit 2020–2050, collection permit 2020–2023 and retrospective permit 2020–2025.
     */
    private static Consent retroConsentUntil2023(String id, String patientId) {
        return consent(id, patientId, CONSENT_DATE,
                permit(MDAT_NUTZEN, CONSENT_DATE, "2050-12-31"),
                permit(MDAT_ERHEBEN, CONSENT_DATE, "2023-12-31"),
                permit(MDAT_RETRO, CONSENT_DATE, "2025-12-31"));
    }

    /**
     * R1 of scenarios 4c/4d (2020): research-use permit 2020–2050 and collection permit 2020–2023, no retrospective use.
     */
    private static Consent consentUntil2023(String id, String patientId) {
        return consent(id, patientId, CONSENT_DATE,
                permit(MDAT_NUTZEN, CONSENT_DATE, "2050-12-31"),
                permit(MDAT_ERHEBEN, CONSENT_DATE, "2023-12-31"));
    }

    /**
     * R2 of scenarios 4a/4d (2025): research-use permit 2025–2055, collection permit 2025–2030 and retrospective refusal 2025–2028.
     */
    private static Consent reConsentRefusingRetro(String id, String patientId) {
        return consent(id, patientId, "2025-01-01",
                permit(MDAT_NUTZEN, "2025-01-01", "2055-12-31"),
                permit(MDAT_ERHEBEN, "2025-01-01", "2030-12-31"),
                deny(MDAT_RETRO, "2025-01-01", "2028-12-31"));
    }

    /**
     * R1–R3 of scenarios 5b/5c: R1 (2001) research-use permit 2001–2050 and collection permit 2001–2004, R2 (2006) research-use permit
     * 2006–2050 and collection permit 2006–2014, R3 (2020) collection deny 2020–2024.
     */
    private void storeSplitPermitsWithLaterDeny(String patientId) {
        put(consent(patientId + "-R1", patientId, "2001-01-01",
                permit(MDAT_NUTZEN, "2001-01-01", "2050-12-31"),
                permit(MDAT_ERHEBEN, "2001-01-01", "2004-12-31")));
        put(consent(patientId + "-R2", patientId, "2006-01-01",
                permit(MDAT_NUTZEN, "2006-01-01", "2050-12-31"),
                permit(MDAT_ERHEBEN, "2006-01-01", "2014-12-31")));
        put(consent(patientId + "-R3", patientId, "2020-01-01",
                deny(MDAT_ERHEBEN, "2020-01-01", "2024-12-31")));
    }

    /**
     * Consent with a deny on the root provision and no nested provisions.
     */
    private static Consent blanketDeny(String id, String patientId, String start, String end) {
        Consent consent = consent(id, patientId, start);
        consent.getProvision().setPeriod(new org.hl7.fhir.r4.model.Period()
                .setStartElement(new DateTimeType(start))
                .setEndElement(new DateTimeType(end)));
        return consent;
    }

    private static Consent inactive(Consent consent) {
        return consent.setStatus(Consent.ConsentState.INACTIVE);
    }

    private static Consent consent(String id, String patientId, String dateTime, Consent.ProvisionComponent... provisions) {
        Consent consent = new Consent();
        consent.setId(id);
        consent.getMeta().addProfile(CONSENT_PROFILE);
        consent.setStatus(Consent.ConsentState.ACTIVE);
        consent.setScope(new CodeableConcept(new Coding("http://terminology.hl7.org/CodeSystem/consentscope", "research", null)));
        consent.addCategory(new CodeableConcept(new Coding("http://loinc.org", "57016-8", null)));
        consent.setPatient(new Reference("Patient/" + patientId));
        consent.setDateTimeElement(new DateTimeType(dateTime));
        consent.getProvision().setType(Consent.ConsentProvisionType.DENY);
        for (Consent.ProvisionComponent provision : provisions) {
            consent.getProvision().addProvision(provision);
        }
        return consent;
    }

    private static Consent.ProvisionComponent permit(String code, String start, String end) {
        return provision(Consent.ConsentProvisionType.PERMIT, code, start, end);
    }

    private static Consent.ProvisionComponent deny(String code, String start, String end) {
        return provision(Consent.ConsentProvisionType.DENY, code, start, end);
    }

    private static Consent.ProvisionComponent provision(Consent.ConsentProvisionType type, String code, String start, String end) {
        Consent.ProvisionComponent provision = new Consent.ProvisionComponent();
        provision.setType(type);
        provision.addCode(new CodeableConcept(new Coding(MII_CONSENT_SYSTEM, code, null)));
        provision.setPeriod(new org.hl7.fhir.r4.model.Period()
                .setStartElement(new DateTimeType(start))
                .setEndElement(new DateTimeType(end)));
        return provision;
    }
}
