package de.medizininformatikinitiative.torch.consent;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.diagnostics.consent.RawProvisionEvent;
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
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fixtures and assertions shared by the encounter-shift probes {@link ConsentEncounterContextIT} and
 * {@link ConsentEncounterShiftConfigIT}: stores a patient with Observations, Encounters and one Consent in Blaze and
 * checks the derived {@code .6} data period, the eligible Observations and the Encounter that anchored the shift.
 */
class ConsentEncounterProbes {

    static final String MII_CONSENT_SYSTEM = "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3";
    static final String MDAT_ERHEBEN = "2.16.840.1.113883.3.1937.777.24.5.3.6";
    static final String MDAT_NUTZEN = "2.16.840.1.113883.3.1937.777.24.5.3.8";
    static final Set<TermCode> PROSPECTIVE_CODES = Set.of(
            new TermCode(MII_CONSENT_SYSTEM, MDAT_NUTZEN),
            new TermCode(MII_CONSENT_SYSTEM, MDAT_ERHEBEN));
    static final String CONSENT_PROFILE = "https://www.medizininformatik-initiative.de/fhir/modul-consent/StructureDefinition/mii-pr-consent-einwilligung";
    static final String ENCOUNTER_PROFILE = "https://www.medizininformatik-initiative.de/fhir/core/modul-fall/StructureDefinition/KontaktGesundheitseinrichtung";
    static final String ACT_CODE_SYSTEM = "http://terminology.hl7.org/CodeSystem/v3-ActCode";
    static final List<String> OBSERVATION_DATES = List.of("2019-06-01", "2024-02-20", "2024-03-02", "2024-03-12", "2024-03-25");

    private final WebClient webClient;
    private final FhirContext fhirContext;
    private final DataStore dataStore;
    private final ConsentValidator consentValidator;

    ConsentEncounterProbes(WebClient webClient, FhirContext fhirContext, DataStore dataStore,
                           ConsentValidator consentValidator) {
        this.webClient = webClient;
        this.fhirContext = fhirContext;
        this.dataStore = dataStore;
        this.consentValidator = consentValidator;
    }

    /**
     * Batch of the single patient with consent diagnostics enabled, so that the encounter shift can be read back
     * after the calculation.
     */
    static PatientBatch batchOf(String patientId) {
        return PatientBatch.of(patientId).withConsentDiagnosticsEnabled(true);
    }

    /**
     * Asserts that the patient is included with a {@code .6} data period from {@code expectedStart} to the end of the
     * Consent's {@code .6} permit, and that exactly the Observations of the given dates are eligible.
     */
    void assertIncluded(Mono<PatientBatchWithConsent> result, String patientId, String expectedStart,
                        String... expectedObservationDates) {
        PatientBatchWithConsent batch = result.block();

        assertThat(batch).isNotNull();
        assertThat(batch.patientIds()).containsExactly(patientId);
        assertThat(batch.get(patientId).consentPeriods())
                .isEqualTo(new NonContinuousPeriod(List.of(Period.of(expectedStart, "2029-12-31"))));
        assertThat(eligibleObservationIds(batch, patientId)).containsExactlyInAnyOrder(
                Arrays.stream(expectedObservationDates).map(date -> observationId(patientId, date)).toArray(String[]::new));
    }

    /**
     * Asserts which Encounter anchored the shift of the {@code .6} permit, as recorded in the batch's raw provision
     * diagnostics; {@code expectedEncounterId} {@code null} means the permit was not shifted. Call after the result
     * has completed.
     */
    static void assertAnchor(PatientBatch batch, String expectedEncounterId, String expectedShiftedStart) {
        assertThat(batch.diagnostics().consentDiagnostics().getRawProvisions())
                .filteredOn(event -> event.permit() && event.code().equals(MDAT_ERHEBEN))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.encounterId()).isEqualTo(expectedEncounterId);
                    assertThat(event.shiftedPeriodStart())
                            .isEqualTo(expectedShiftedStart == null ? null : LocalDate.parse(expectedShiftedStart));
                });
    }

    static void assertNotShifted(PatientBatch batch) {
        assertAnchor(batch, null, null);
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

    void storePatient(String patientId) {
        put(new Patient().setId(patientId));
        OBSERVATION_DATES.forEach(date -> {
            Observation observation = new Observation();
            observation.setId(observationId(patientId, date));
            observation.setStatus(Observation.ObservationStatus.FINAL);
            observation.setCode(new CodeableConcept(new Coding("http://loinc.org", "8302-2", "Body height")));
            observation.setSubject(new Reference("Patient/" + patientId));
            observation.setEffective(new DateTimeType(date));
            put(observation);
        });
    }

    private static String observationId(String patientId, String date) {
        return patientId + "-obs-" + date;
    }

    void put(Resource resource) {
        String type = resource.fhirType();
        webClient.put()
                .uri("/{type}/{id}", type, resource.getIdElement().getIdPart())
                .header("Content-Type", "application/fhir+json")
                .bodyValue(fhirContext.newJsonParser().encodeResourceToString(resource))
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    /**
     * Encounter of the patient with the given {@code v3-ActCode} class and a day-precise period; {@code end} may be
     * {@code null} for a stay that has not ended.
     */
    static Encounter encounter(String id, String patientId, String encounterClass, String start, String end) {
        Encounter encounter = new Encounter();
        encounter.setId(id);
        encounter.getMeta().addProfile(ENCOUNTER_PROFILE);
        encounter.setStatus(end == null ? Encounter.EncounterStatus.INPROGRESS : Encounter.EncounterStatus.FINISHED);
        encounter.setClass_(new Coding(ACT_CODE_SYSTEM, encounterClass, null));
        encounter.setSubject(new Reference("Patient/" + patientId));
        encounter.getPeriod().setStartElement(new DateTimeType(start));
        if (end != null) {
            encounter.getPeriod().setEndElement(new DateTimeType(end));
        }
        return encounter;
    }

    /**
     * Consent signed on {@code signingDay}: .8 permit signingDay–2054-12-31 and .6 permit signingDay–2029-12-31.
     */
    static Consent consent(String patientId, String signingDay) {
        Consent consent = new Consent();
        consent.setId(patientId + "-consent");
        consent.getMeta().addProfile(CONSENT_PROFILE);
        consent.setStatus(Consent.ConsentState.ACTIVE);
        consent.setScope(new CodeableConcept(new Coding("http://terminology.hl7.org/CodeSystem/consentscope", "research", null)));
        consent.addCategory(new CodeableConcept(new Coding("http://loinc.org", "57016-8", null)));
        consent.setPatient(new Reference("Patient/" + patientId));
        consent.setDateTimeElement(new DateTimeType(signingDay));
        consent.getProvision().setType(Consent.ConsentProvisionType.DENY);
        consent.getProvision().addProvision(permit(MDAT_NUTZEN, signingDay, "2054-12-31"));
        consent.getProvision().addProvision(permit(MDAT_ERHEBEN, signingDay, "2029-12-31"));
        return consent;
    }

    private static Consent.ProvisionComponent permit(String code, String start, String end) {
        Consent.ProvisionComponent provision = new Consent.ProvisionComponent();
        provision.setType(Consent.ConsentProvisionType.PERMIT);
        provision.addCode(new CodeableConcept(new Coding(MII_CONSENT_SYSTEM, code, null)));
        provision.setPeriod(new org.hl7.fhir.r4.model.Period()
                .setStartElement(new DateTimeType(start))
                .setEndElement(new DateTimeType(end)));
        return provision;
    }
}
