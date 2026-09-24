package de.medizininformatikinitiative.torch.consent;

import ca.uhn.fhir.context.FhirContext;
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
 * asserts inclusion, the derived {@code .6} data period and the Observations that pass the consent check. The tests
 * document current behaviour, not a desired outcome. Interpretation-only cases (ordering, retro denies within vs.
 * across resources) are covered in {@code ConsentCalculatorTest}.
 * <p>
 * Common timeline: consent signed 2020, withdrawal / retrospective refusal 2023, re-consent 2024. The {@code .6}
 * lookback date is {@code 1900-01-01} ({@code mappings/consent-code-config.json}). Each patient has Observations
 * dated 2010 (retrospective only), 2021 (consented), 2023 (after withdrawal) and 2024 (after re-consent).
 * Scenarios 4 and 6 use Observations dated 2019, 2021, 2024 and 2026, scenario 5 uses 1999, 2003, 2005, 2010 and 2025.
 *
 * <table>
 *     <caption>Results per variant</caption>
 *     <tr><th>Variant</th><th>Included</th><th>.6 data period</th><th>Extracted</th></tr>
 *     <tr><td>1a inactive</td><td>no (no active Consent found)</td><td>-</td><td>-</td></tr>
 *     <tr><td>1b deny resource</td><td>no (.8 gate ends 2022)</td><td>-</td><td>-</td></tr>
 *     <tr><td>1c overwritten</td><td>no (.8 gate ends 2022)</td><td>-</td><td>-</td></tr>
 *     <tr><td>1d deleted</td><td>no (no active Consent found)</td><td>-</td><td>-</td></tr>
 *     <tr><td>2a .45 deny separate</td><td>yes</td><td>1900–2024</td><td>2010, 2021, 2023, 2024</td></tr>
 *     <tr><td>2b .45 deny same resource</td><td>yes</td><td>1900–2024</td><td>2010, 2021, 2023, 2024</td></tr>
 *     <tr><td>2c .6 deny from lookback</td><td>no (.6 empty)</td><td>-</td><td>-</td></tr>
 *     <tr><td>3a inactive + re-consent</td><td>yes</td><td>2024–2028</td><td>2024</td></tr>
 *     <tr><td>3b deny resource + re-consent</td><td>yes</td><td>2020–2022, 2024–2028</td><td>2021, 2024</td></tr>
 *     <tr><td>3c overwritten + re-consent</td><td>yes</td><td>2020–2022, 2024–2028</td><td>2021, 2024</td></tr>
 *     <tr><td>3d deleted + re-consent</td><td>yes</td><td>2024–2028</td><td>2024</td></tr>
 *     <tr><td>4a re-consent with .45 deny</td><td>yes</td><td>1900–2023, 2025–2030</td><td>2019, 2021, 2026</td></tr>
 *     <tr><td>4b as 4a + old-period denies</td><td>no (.8 gate: own deny cancels own permit, #1274)</td><td>-</td><td>-</td></tr>
 *     <tr><td>4c .45 deny only</td><td>yes</td><td>2020–2023</td><td>2021</td></tr>
 *     <tr><td>4d .45 deny + new .6/.8</td><td>yes</td><td>2020–2023, 2025–2030</td><td>2021, 2026</td></tr>
 *     <tr><td>5a .45 in separate resource</td><td>yes</td><td>2001–2004, 2006–2014</td><td>2003, 2010</td></tr>
 *     <tr><td>5b later non-overlapping .6 deny</td><td>yes</td><td>2001–2004, 2006–2014</td><td>2003, 2010</td></tr>
 *     <tr><td>5c newer permit after deny</td><td>yes</td><td>2001–2004, 2006–2014, 2023–2026</td><td>2003, 2010, 2025</td></tr>
 *     <tr><td>6 blanket root deny</td><td>yes (ignored)</td><td>2020–2024</td><td>2021, 2024</td></tr>
 * </table>
 * <p>
 * Divergences between variants of the same will:
 * <ul>
 *     <li>Scenario 1: all variants exclude the patient, but 1a/1d fail already in {@link ConsentFetcher} while
 *     1b/1c are excluded by the {@code .8} validity gate in {@link ConsentCalculator}.</li>
 *     <li>Scenario 2: a later {@code .45} deny (2a, 2b) does not remove the retrospective window because the
 *     retro extension is only reduced by same-resource {@code .45} denies, and those only cover 2023 onwards.
 *     A {@code .6} deny from lookback (2c) removes the whole {@code .6} period, including the prospective part.</li>
 *     <li>Scenario 3: 3a/3d lose the pre-withdrawal period 2020–2022 because the original Consent is no longer
 *     visible; 3b/3c keep it and only exclude the gap.</li>
 *     <li>Scenario 4: a .45 deny in a later Consent keeps the earlier retrospective window (4a). Adding explicit
 *     .6/.8 denies for the old period to that Consent excludes the patient instead (4b, #1274).</li>
 *     <li>Scenario 5: a .45 permit only extends a .6 permit of the same resource (5a, #1275).</li>
 *     <li>Scenario 6: a root-level deny without nested provisions has no effect.</li>
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
    static final List<String> OBSERVATION_YEARS = List.of("2010", "2021", "2023", "2024");
    static final List<String> RECONSENT_REFUSAL_OBSERVATION_YEARS = List.of("2019", "2021", "2024", "2026");
    static final List<String> SPLIT_OBSERVATION_YEARS = List.of("1999", "2003", "2005", "2010", "2025");

    static final String CONSENT_DATE = "2020-01-01";
    static final String WITHDRAWAL_DATE = "2023-01-01";
    static final String RECONSENT_DATE = "2024-01-01";

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

    @BeforeAll
    void init() {
        // Scenario 1: full withdrawal after consent
        storePatient("1a");
        put(fullConsent("1a-A", "1a"));
        put(inactive(fullConsent("1a-A", "1a")));

        storePatient("1b");
        put(fullConsent("1b-A", "1b"));
        put(fullDeny("1b-B", "1b"));

        storePatient("1c");
        put(fullConsent("1c-A", "1c"));
        put(overwrittenWithDeny("1c-A", "1c"));

        storePatient("1d");
        put(fullConsent("1d-A", "1d"));
        delete("Consent", "1d-A");

        // Scenario 2: later retrospective refusal after a consent with .45 permit
        storePatient("2a");
        put(retroConsent("2a-A", "2a"));
        put(consent("2a-B", "2a", WITHDRAWAL_DATE,
                deny(MDAT_RETRO, WITHDRAWAL_DATE, "2024-12-31")));

        storePatient("2b");
        put(retroConsent("2b-A", "2b"));
        put(consent("2b-A", "2b", CONSENT_DATE,
                permit(MDAT_NUTZEN, CONSENT_DATE, "2049-12-31"),
                permit(MDAT_ERHEBEN, CONSENT_DATE, "2024-12-31"),
                permit(MDAT_RETRO, CONSENT_DATE, "2022-12-31"),
                deny(MDAT_RETRO, WITHDRAWAL_DATE, "2024-12-31")));

        storePatient("2c");
        put(retroConsent("2c-A", "2c"));
        put(consent("2c-B", "2c", WITHDRAWAL_DATE,
                deny(MDAT_ERHEBEN, "1900-01-01", "2024-12-31")));

        // Scenario 3: re-consent after withdrawal
        storePatient("3a");
        put(fullConsent("3a-A", "3a"));
        put(inactive(fullConsent("3a-A", "3a")));
        put(reConsent("3a-C", "3a"));

        storePatient("3b");
        put(fullConsent("3b-A", "3b"));
        put(fullDeny("3b-B", "3b"));
        put(reConsent("3b-C", "3b"));

        storePatient("3c");
        put(fullConsent("3c-A", "3c"));
        put(overwrittenWithDeny("3c-A", "3c"));
        put(reConsent("3c-C", "3c"));

        storePatient("3d");
        put(fullConsent("3d-A", "3d"));
        delete("Consent", "3d-A");
        put(reConsent("3d-C", "3d"));

        // Scenario 4: re-consent with a retrospective refusal
        storePatient("4a", RECONSENT_REFUSAL_OBSERVATION_YEARS);
        put(retroConsentUntil2023("4a-R1", "4a"));
        put(reConsentRefusingRetro("4a-R2", "4a"));

        storePatient("4b", RECONSENT_REFUSAL_OBSERVATION_YEARS);
        put(retroConsentUntil2023("4b-R1", "4b"));
        put(consent("4b-R2", "4b", "2025-01-01",
                permit(MDAT_NUTZEN, "2025-01-01", "2055-12-31"),
                permit(MDAT_ERHEBEN, "2025-01-01", "2030-12-31"),
                deny(MDAT_RETRO, "2025-01-01", "2028-12-31"),
                deny(MDAT_ERHEBEN, "2020-01-01", "2023-12-31"),
                deny(MDAT_NUTZEN, "2020-01-01", "2050-12-31")));

        storePatient("4c", RECONSENT_REFUSAL_OBSERVATION_YEARS);
        put(consentUntil2023("4c-R1", "4c"));
        put(consent("4c-R2", "4c", "2025-01-01",
                deny(MDAT_RETRO, "2025-01-01", "2028-12-31")));

        storePatient("4d", RECONSENT_REFUSAL_OBSERVATION_YEARS);
        put(consentUntil2023("4d-R1", "4d"));
        put(reConsentRefusingRetro("4d-R2", "4d"));

        // Scenario 5: provisions split across resources and ordering
        storePatient("5a", SPLIT_OBSERVATION_YEARS);
        put(consent("5a-R1", "5a", "2001-01-01",
                permit(MDAT_NUTZEN, "2001-01-01", "2050-12-31"),
                permit(MDAT_ERHEBEN, "2001-01-01", "2004-12-31")));
        put(consent("5a-R2", "5a", "2002-01-01",
                permit(MDAT_RETRO, "2002-01-01", "2005-12-31")));
        put(consent("5a-R3", "5a", "2006-01-01",
                permit(MDAT_NUTZEN, "2006-01-01", "2050-12-31"),
                permit(MDAT_ERHEBEN, "2006-01-01", "2014-12-31")));

        storePatient("5b", SPLIT_OBSERVATION_YEARS);
        storeSplitPermitsWithLaterDeny("5b");

        storePatient("5c", SPLIT_OBSERVATION_YEARS);
        storeSplitPermitsWithLaterDeny("5c");
        put(consent("5c-R4", "5c", "2023-01-01",
                permit(MDAT_NUTZEN, "2023-01-01", "2050-12-31"),
                permit(MDAT_ERHEBEN, "2023-01-01", "2026-12-31")));

        // Scenario 6: blanket deny on the root provision
        storePatient("6", RECONSENT_REFUSAL_OBSERVATION_YEARS);
        put(consent("6-R1", "6", CONSENT_DATE,
                permit(MDAT_NUTZEN, CONSENT_DATE, "2050-12-31"),
                permit(MDAT_ERHEBEN, CONSENT_DATE, "2024-12-31")));
        put(blanketDeny("6-R2", "6", "2025-01-01", "2050-12-31"));
    }

    @Test
    @DisplayName("1a: withdrawal by inactivating the Consent excludes the patient during fetch")
    void withdrawalByInactivation() {
        assertExcludedDuringFetch("1a");
    }

    @Test
    @DisplayName("1b: withdrawal by a separate deny Consent excludes the patient by the .8 validity gate")
    void withdrawalByDenyResource() {
        assertExcludedDuringCalculation("1b", PROSPECTIVE_CODES);
    }

    @Test
    @DisplayName("1c: withdrawal by overwriting the Consent excludes the patient by the .8 validity gate")
    void withdrawalByOverwrite() {
        assertExcludedDuringCalculation("1c", PROSPECTIVE_CODES);
    }

    @Test
    @DisplayName("1d: withdrawal by deleting the Consent excludes the patient during fetch")
    void withdrawalByDeletion() {
        assertExcludedDuringFetch("1d");
    }

    @Test
    @DisplayName("2a: .45 deny in a separate Consent keeps the retrospective window")
    void retroRefusalInSeparateResource() {
        assertIncluded("2a", RETRO_CODES, RETRO_WINDOW, "2010", "2021", "2023", "2024");
    }

    @Test
    @DisplayName("2b: .45 deny added to the original Consent keeps the retrospective window")
    void retroRefusalInSameResource() {
        assertIncluded("2b", RETRO_CODES, RETRO_WINDOW, "2010", "2021", "2023", "2024");
    }

    @Test
    @DisplayName("2c: .6 deny from lookback removes the whole .6 period and excludes the patient")
    void retroRefusalByMdatDeny() {
        assertExcludedDuringCalculation("2c", RETRO_CODES);
    }

    @Test
    @DisplayName("3a: re-consent after inactivation only covers the new Consent")
    void reConsentAfterInactivation() {
        assertIncluded("3a", PROSPECTIVE_CODES, RECONSENT_WINDOW, "2024");
    }

    @Test
    @DisplayName("3b: re-consent after a deny Consent keeps the pre-withdrawal period and excludes the gap")
    void reConsentAfterDenyResource() {
        assertIncluded("3b", PROSPECTIVE_CODES, GAP_WINDOW, "2021", "2024");
    }

    @Test
    @DisplayName("3c: re-consent after overwriting keeps the pre-withdrawal period and excludes the gap")
    void reConsentAfterOverwrite() {
        assertIncluded("3c", PROSPECTIVE_CODES, GAP_WINDOW, "2021", "2024");
    }

    @Test
    @DisplayName("3d: re-consent after deletion only covers the new Consent")
    void reConsentAfterDeletion() {
        assertIncluded("3d", PROSPECTIVE_CODES, RECONSENT_WINDOW, "2024");
    }

    @Test
    @DisplayName("4a: re-consent refusing .45 keeps the retrospective window of the earlier Consent")
    void reConsentRefusingRetro() {
        assertIncluded("4a", RETRO_CODES, periods("1900-01-01", "2023-12-31", "2025-01-01", "2030-12-31"),
                "2019", "2021", "2026");
    }

    @Test
    @DisplayName("4b: re-consent with explicit denies for the old period cancels its own .8 permit and excludes the patient (#1274)")
    void reConsentRefusingRetroWithExplicitDenies() {
        assertExcludedDuringCalculation("4b", RETRO_CODES);
    }

    @Test
    @DisplayName("4c: .45 deny without a new .6/.8 does not cancel the earlier .6/.8")
    void retroRefusalWithoutReConsent() {
        assertIncluded("4c", RETRO_CODES, periods("2020-01-01", "2023-12-31"), "2021");
    }

    @Test
    @DisplayName("4d: .45 deny with a new .6/.8 permit does not cancel the earlier .6/.8")
    void retroRefusalWithReConsent() {
        assertIncluded("4d", RETRO_CODES, periods("2020-01-01", "2023-12-31", "2025-01-01", "2030-12-31"),
                "2021", "2026");
    }

    @Test
    @DisplayName("5a: .45 permit in a separate resource does not extend an overlapping .6 permit (#1275)")
    void retroPermitInSeparateResource() {
        assertIncluded("5a", RETRO_CODES, periods("2001-01-01", "2004-12-31", "2006-01-01", "2014-12-31"),
                "2003", "2010");
    }

    @Test
    @DisplayName("5b: later .6 deny without overlap keeps the earlier .6 permits")
    void laterNonOverlappingDeny() {
        assertIncluded("5b", PROSPECTIVE_CODES, periods("2001-01-01", "2004-12-31", "2006-01-01", "2014-12-31"),
                "2003", "2010");
    }

    @Test
    @DisplayName("5c: newer .6 permit beats an older .6 deny")
    void newerPermitAfterDeny() {
        assertIncluded("5c", PROSPECTIVE_CODES,
                periods("2001-01-01", "2004-12-31", "2006-01-01", "2014-12-31", "2023-01-01", "2026-12-31"),
                "2003", "2010", "2025");
    }

    @Test
    @DisplayName("6: blanket deny on the root provision without nested provisions is ignored")
    void blanketDenyIsIgnored() {
        assertIncluded("6", PROSPECTIVE_CODES, periods("2020-01-01", "2024-12-31"), "2021", "2024");
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

    private void assertExcludedDuringFetch(String patientId) {
        StepVerifier.create(consentHandler.fetchAndBuildConsentInfo(PROSPECTIVE_CODES, PatientBatch.of(patientId)))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ConsentViolatedException.class)
                        .hasMessageContaining("No valid consentPeriods found for any patients in batch"))
                .verify();
    }

    private void assertExcludedDuringCalculation(String patientId, Set<TermCode> codes) {
        StepVerifier.create(consentHandler.fetchAndBuildConsentInfo(codes, PatientBatch.of(patientId)))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ConsentViolatedException.class)
                        .hasMessageContaining("No patients with valid consent periods found in batch"))
                .verify();
    }

    private void assertIncluded(String patientId, Set<TermCode> codes, NonContinuousPeriod expectedPeriod,
                                String... expectedObservationYears) {
        PatientBatchWithConsent batch = consentHandler.fetchAndBuildConsentInfo(codes, PatientBatch.of(patientId)).block();

        assertThat(batch).isNotNull();
        assertThat(batch.patientIds()).containsExactly(patientId);
        assertThat(batch.get(patientId).consentPeriods()).isEqualTo(expectedPeriod);
        assertThat(extractedObservationIds(batch, patientId)).containsExactlyInAnyOrder(
                Arrays.stream(expectedObservationYears).map(year -> observationId(patientId, year)).toArray(String[]::new));
    }

    /**
     * Returns the ids of the patient's Observations in Blaze that pass the consent check applied during extraction.
     */
    private List<String> extractedObservationIds(PatientBatchWithConsent batch, String patientId) {
        return dataStore.search(Query.of("Observation", PatientBatch.of(patientId).compartmentSearchParam("Observation")), Observation.class)
                .filter(observation -> consentValidator.checkConsent(observation, batch))
                .map(Observation::getIdPart)
                .collectList()
                .block();
    }

    private void storePatient(String patientId) {
        storePatient(patientId, OBSERVATION_YEARS);
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
     * Consent A: .8 and .6 permit from 2020.
     */
    private static Consent fullConsent(String id, String patientId) {
        return consent(id, patientId, CONSENT_DATE,
                permit(MDAT_NUTZEN, CONSENT_DATE, "2049-12-31"),
                permit(MDAT_ERHEBEN, CONSENT_DATE, "2024-12-31"));
    }

    /**
     * Consent B: .8 and .6 deny from the withdrawal on.
     */
    private static Consent fullDeny(String id, String patientId) {
        return consent(id, patientId, WITHDRAWAL_DATE,
                deny(MDAT_NUTZEN, WITHDRAWAL_DATE, "2049-12-31"),
                deny(MDAT_ERHEBEN, WITHDRAWAL_DATE, "2024-12-31"));
    }

    /**
     * Consent A v2: permits end before the withdrawal, followed by .8 and .6 denies.
     */
    private static Consent overwrittenWithDeny(String id, String patientId) {
        return consent(id, patientId, CONSENT_DATE,
                permit(MDAT_NUTZEN, CONSENT_DATE, "2022-12-31"),
                permit(MDAT_ERHEBEN, CONSENT_DATE, "2022-12-31"),
                deny(MDAT_NUTZEN, WITHDRAWAL_DATE, "2049-12-31"),
                deny(MDAT_ERHEBEN, WITHDRAWAL_DATE, "2024-12-31"));
    }

    /**
     * Consent C: new full consent from 2024.
     */
    private static Consent reConsent(String id, String patientId) {
        return consent(id, patientId, RECONSENT_DATE,
                permit(MDAT_NUTZEN, RECONSENT_DATE, "2053-12-31"),
                permit(MDAT_ERHEBEN, RECONSENT_DATE, "2028-12-31"));
    }

    /**
     * Consent A of scenario 2: .8, .6 and .45 permit from 2020.
     */
    private static Consent retroConsent(String id, String patientId) {
        return consent(id, patientId, CONSENT_DATE,
                permit(MDAT_NUTZEN, CONSENT_DATE, "2049-12-31"),
                permit(MDAT_ERHEBEN, CONSENT_DATE, "2024-12-31"),
                permit(MDAT_RETRO, CONSENT_DATE, "2024-12-31"));
    }

    /**
     * Consent A of scenario 4: .8 permit from 2020, .6 permit 2020–2023 and .45 permit 2020–2025.
     */
    private static Consent retroConsentUntil2023(String id, String patientId) {
        return consent(id, patientId, CONSENT_DATE,
                permit(MDAT_NUTZEN, CONSENT_DATE, "2050-12-31"),
                permit(MDAT_ERHEBEN, CONSENT_DATE, "2023-12-31"),
                permit(MDAT_RETRO, CONSENT_DATE, "2025-12-31"));
    }

    /**
     * Consent A of scenario 4 without .45: .8 permit from 2020 and .6 permit 2020–2023.
     */
    private static Consent consentUntil2023(String id, String patientId) {
        return consent(id, patientId, CONSENT_DATE,
                permit(MDAT_NUTZEN, CONSENT_DATE, "2050-12-31"),
                permit(MDAT_ERHEBEN, CONSENT_DATE, "2023-12-31"));
    }

    /**
     * Consent B of scenario 4: new .8/.6 permit from 2025 that refuses .45.
     */
    private static Consent reConsentRefusingRetro(String id, String patientId) {
        return consent(id, patientId, "2025-01-01",
                permit(MDAT_NUTZEN, "2025-01-01", "2055-12-31"),
                permit(MDAT_ERHEBEN, "2025-01-01", "2030-12-31"),
                deny(MDAT_RETRO, "2025-01-01", "2028-12-31"));
    }

    /**
     * Consents A–C of scenarios 5b/5c: .6 permits 2001–2004 and 2006–2014, then a .6 deny 2020–2024.
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
