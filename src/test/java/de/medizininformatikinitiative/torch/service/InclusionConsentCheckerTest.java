package de.medizininformatikinitiative.torch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.management.TestPatientLinkResolver;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.PatientExclusionEvent;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.PatientExclusionStage;
import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.exceptions.DataStoreException;
import de.medizininformatikinitiative.torch.exceptions.ValidationException;
import de.medizininformatikinitiative.torch.model.consent.ConsentSearchParams;
import de.medizininformatikinitiative.torch.model.consent.NonContinuousPeriod;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.consent.Period;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.fhir.QueryParams;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import de.medizininformatikinitiative.torch.model.mapping.MappingException;
import de.medizininformatikinitiative.torch.model.sq.Criterion;
import de.medizininformatikinitiative.torch.model.sq.TermCode;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.conceptValue;
import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.stringValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InclusionConsentCheckerTest {

    static final ObjectMapper MAPPER = new ObjectMapper();
    static final Query DIABETES_QUERY = Query.of("Condition", QueryParams.of("code", conceptValue(TermCode.of("icd10", "E11", "E11"))));
    static final Query ASTHMA_QUERY = Query.of("Condition", QueryParams.of("code", conceptValue(TermCode.of("icd10", "J45", "J45"))));
    static final Query FEMALE_QUERY = Query.of("Patient", QueryParams.of("gender", stringValue("female")));
    static final Duration REFRESH = Duration.ofHours(24);
    static final NonContinuousPeriod CONSENT_2020 = NonContinuousPeriod.of(Period.of("2020-01-01", "2020-12-31"));

    @Mock
    CriterionQueryTranslator translator;

    @Mock
    DataStore dataStore;

    InclusionConsentChecker checker;

    MutableClock clock = new MutableClock();

    /**
     * The queries of each {@link DataStore#countBatch} call, in call order.
     */
    List<List<String>> countedQueries = new ArrayList<>();

    @BeforeEach
    void setUp() {
        var consentSearchParams = ConsentSearchParams.of(
                Map.of("Condition", "Condition.recordedDate", "Patient", "", "Procedure", "Procedure.performed"),
                Map.of("Condition", "recorded-date", "Procedure", "date"));
        checker = new InclusionConsentChecker(translator, dataStore, consentSearchParams,
                TestPatientLinkResolver.get(), MAPPER, 4, REFRESH, clock);
    }

    /**
     * A clock that only moves when a test advances it.
     */
    static class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }
    }

    Mono<PatientBatchWithConsent> filter(JsonNode cohort, PatientBatchWithConsent batch) throws Exception {
        return checker.filter(checker.translate(cohort), batch);
    }

    static PatientBatchWithConsent batch(NonContinuousPeriod consent, String... ids) throws ConsentViolatedException {
        var periods = Arrays.stream(ids).collect(Collectors.toMap(id -> id, id -> consent));
        return PatientBatchWithConsent.fromBatchAndConsent(PatientBatch.of(ids), periods);
    }

    static JsonNode criterion(String context, String code) throws Exception {
        return MAPPER.readTree("""
                {"context": {"system": "fdpg.mii.cds", "code": "%s", "display": "%s"},
                 "termCodes": [{"system": "icd10", "code": "%s", "display": "%s"}]}
                """.formatted(context, context, code, code));
    }

    static JsonNode cohort(List<List<JsonNode>> inclusionCriteria) {
        var cohort = MAPPER.createObjectNode();
        cohort.set("inclusionCriteria", MAPPER.valueToTree(inclusionCriteria));
        return cohort;
    }

    static Criterion hasCode(String code) {
        return argThat(c -> c != null && c.concept().concept().termCodes().stream().anyMatch(tc -> tc.code().equals(code)));
    }

    void translate(String code, Query query) throws MappingException {
        when(translator.toQuery(hasCode(code))).thenReturn(List.of(query));
    }

    void serverSupportsAllParams() {
        var statement = new CapabilityStatement();
        var rest = statement.addRest();
        rest.addResource().setType("Condition").addSearchParam().setName("patient");
        rest.getResource().getFirst().addSearchParam().setName("code");
        rest.getResource().getFirst().addSearchParam().setName("recorded-date");
        rest.addResource().setType("Patient").addSearchParam().setName("gender");
        when(dataStore.capabilityStatement()).thenReturn(Mono.just(statement));
    }

    /**
     * Answers each count query with 1 if it contains one of {@code hits}, otherwise with 0.
     */
    void counts(String... hits) {
        when(dataStore.countBatch(any())).thenAnswer(invocation -> {
            List<Query> queries = invocation.getArgument(0);
            var strings = queries.stream().map(Query::toString).toList();
            countedQueries.add(strings);
            return Mono.just(strings.stream()
                    .map(query -> Arrays.stream(hits).anyMatch(query::contains) ? 1 : 0)
                    .toList());
        });
    }

    @Test
    void keepsPatientWithHitAndRemovesPatientWithout() throws Exception {
        var batch = batch(CONSENT_2020, "p1", "p2");
        translate("E11", DIABETES_QUERY);
        serverSupportsAllParams();
        counts("Patient/p1&");

        StepVerifier.create(filter(cohort(List.of(List.of(criterion("Diagnose", "E11")))), batch))
                .assertNext(result -> assertThat(result.patientIds()).containsExactly("p1"))
                .verifyComplete();

        assertThat(batch.batchExclusions().getPatientExclusions())
                .containsExactly(new PatientExclusionEvent(PatientExclusionStage.INCLUSION_CONSENT, "p2"));
    }

    @Test
    void countsEachQueryOncePerConsentPeriodInsideThePeriod() throws Exception {
        var consent = NonContinuousPeriod.of(Period.of("2020-01-01", "2020-06-30"))
                .merge(NonContinuousPeriod.of(Period.of("2021-01-01", "2021-12-31")));
        var batch = batch(consent, "p1");
        translate("E11", DIABETES_QUERY);
        serverSupportsAllParams();
        counts();

        StepVerifier.create(filter(cohort(List.of(List.of(criterion("Diagnose", "E11")))), batch))
                .verifyComplete();

        assertThat(countedQueries).containsExactly(List.of(
                "Condition?patient=Patient/p1&recorded-date=sa2019-12-31&recorded-date=eb2020-07-01&code=icd10|E11",
                "Condition?patient=Patient/p1&recorded-date=sa2020-12-31&recorded-date=eb2022-01-01&code=icd10|E11"));
    }

    @Test
    void sendsOneBundlePerPatient() throws Exception {
        var batch = batch(CONSENT_2020, "p1", "p2");
        translate("E11", DIABETES_QUERY);
        serverSupportsAllParams();
        counts("icd10|E11");

        StepVerifier.create(filter(cohort(List.of(List.of(criterion("Diagnose", "E11")))), batch))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(countedQueries).hasSize(2).allSatisfy(queries -> assertThat(queries).hasSize(1));
    }

    @Test
    void orJoinsCodesOfAnOrGroup() throws Exception {
        var batch = batch(CONSENT_2020, "p1");
        translate("E11", DIABETES_QUERY);
        translate("J45", ASTHMA_QUERY);
        serverSupportsAllParams();
        counts("icd10|J45");

        var cohort = cohort(List.of(List.of(criterion("Diagnose", "E11"), criterion("Diagnose", "J45"))));

        StepVerifier.create(filter(cohort, batch))
                .assertNext(result -> assertThat(result.patientIds()).containsExactly("p1"))
                .verifyComplete();

        assertThat(countedQueries).containsExactly(List.of(
                "Condition?patient=Patient/p1&recorded-date=sa2019-12-31&recorded-date=eb2021-01-01&code=icd10|E11,icd10|J45"));
    }

    @Test
    void everyAndGroupNeedsAHit() throws Exception {
        var batch = batch(CONSENT_2020, "p1", "p2");
        translate("E11", DIABETES_QUERY);
        translate("J45", ASTHMA_QUERY);
        serverSupportsAllParams();
        counts("icd10|E11", "Patient/p1&recorded-date=sa2019-12-31&recorded-date=eb2021-01-01&code=icd10|J45");

        var cohort = cohort(List.of(List.of(criterion("Diagnose", "E11")), List.of(criterion("Diagnose", "J45"))));

        StepVerifier.create(filter(cohort, batch))
                .assertNext(result -> assertThat(result.patientIds()).containsExactly("p1"))
                .verifyComplete();

        assertThat(countedQueries).allSatisfy(queries -> assertThat(queries).hasSize(2));
    }

    @Test
    void consentCriteriaAreNotChecked() throws Exception {
        var batch = batch(CONSENT_2020, "p1");
        translate("E11", DIABETES_QUERY);
        serverSupportsAllParams();
        counts("icd10|E11");

        var cohort = cohort(List.of(List.of(criterion("Einwilligung", "consent-code")), List.of(criterion("Diagnose", "E11"))));

        StepVerifier.create(filter(cohort, batch))
                .assertNext(result -> assertThat(result.patientIds()).containsExactly("p1"))
                .verifyComplete();

        verify(translator, never()).toQuery(hasCode("consent-code"));
    }

    /**
     * A patient who qualified via the consent alternative of an OR-group needs no other criterion of that group.
     */
    @Test
    void orGroupWithConsentCriterionIsNotChecked() throws Exception {
        var batch = batch(CONSENT_2020, "p1");
        translate("J45", ASTHMA_QUERY);
        serverSupportsAllParams();
        counts("icd10|J45");

        var cohort = cohort(List.of(
                List.of(criterion("Diagnose", "E11"), criterion("Einwilligung", "consent-code")),
                List.of(criterion("Diagnose", "J45"))));

        StepVerifier.create(filter(cohort, batch))
                .assertNext(result -> assertThat(result.patientIds()).containsExactly("p1"))
                .verifyComplete();

        verify(translator, never()).toQuery(hasCode("E11"));
    }

    @Test
    void typesWithoutConsentDateAreNotRestrictedToTheConsentPeriods() throws Exception {
        var batch = batch(CONSENT_2020, "p1");
        translate("female", FEMALE_QUERY);
        serverSupportsAllParams();
        counts("gender=female");

        StepVerifier.create(filter(cohort(List.of(List.of(criterion("Patient", "female")))), batch))
                .assertNext(result -> assertThat(result.patientIds()).containsExactly("p1"))
                .verifyComplete();

        assertThat(countedQueries).containsExactly(List.of("Patient?_id=p1&gender=female"));
    }

    @Test
    void emptyWhenNoPatientRemains() throws Exception {
        var batch = batch(CONSENT_2020, "p1");
        translate("E11", DIABETES_QUERY);
        serverSupportsAllParams();
        counts();

        StepVerifier.create(filter(cohort(List.of(List.of(criterion("Diagnose", "E11")))), batch))
                .verifyComplete();
    }

    @Test
    void searchParamUnsupportedByTheServerFails() throws Exception {
        var batch = batch(CONSENT_2020, "p1");
        translate("E11", DIABETES_QUERY);
        var statement = new CapabilityStatement();
        var condition = statement.addRest().addResource().setType("Condition");
        condition.addSearchParam().setName("patient");
        condition.addSearchParam().setName("recorded-date");
        when(dataStore.capabilityStatement()).thenReturn(Mono.just(statement));

        StepVerifier.create(filter(cohort(List.of(List.of(criterion("Diagnose", "E11")))), batch))
                .expectErrorMessage("The FHIR server does not support the search param Condition.code")
                .verify();

        verify(dataStore, never()).countBatch(any());
    }

    @Test
    void chainedParamsAreCheckedByTheirReferenceParam() throws Exception {
        var batch = batch(CONSENT_2020, "p1");
        var medication = QueryParams.of("medication.code", conceptValue(TermCode.of("atc", "A10", "A10")));
        translate("A10", Query.of("Condition", medication));
        serverSupportsAllParams();

        StepVerifier.create(filter(cohort(List.of(List.of(criterion("Medikation", "A10")))), batch))
                .expectErrorMessage("The FHIR server does not support the search param Condition.medication")
                .verify();
    }

    @Test
    void capabilityStatementIsReusedWithinTheRefreshInterval() throws Exception {
        translate("E11", DIABETES_QUERY);
        serverSupportsAllParams();
        counts("icd10|E11");
        var cohort = cohort(List.of(List.of(criterion("Diagnose", "E11"))));

        filter(cohort, batch(CONSENT_2020, "p1")).block();
        clock.advance(REFRESH.minusSeconds(1));
        filter(cohort, batch(CONSENT_2020, "p2")).block();

        verify(dataStore, times(1)).capabilityStatement();
    }

    @Test
    void capabilityStatementIsRefetchedAfterTheRefreshInterval() throws Exception {
        translate("E11", DIABETES_QUERY);
        var cohort = cohort(List.of(List.of(criterion("Diagnose", "E11"))));
        var withoutCode = new CapabilityStatement();
        withoutCode.addRest().addResource().setType("Condition").addSearchParam().setName("patient");
        withoutCode.getRest().getFirst().getResource().getFirst().addSearchParam().setName("recorded-date");
        var withCode = withoutCode.copy();
        withCode.getRest().getFirst().getResource().getFirst().addSearchParam().setName("code");
        when(dataStore.capabilityStatement()).thenReturn(Mono.just(withoutCode), Mono.just(withCode));
        counts("icd10|E11");

        StepVerifier.create(filter(cohort, batch(CONSENT_2020, "p1")))
                .expectErrorMessage("The FHIR server does not support the search param Condition.code")
                .verify();
        clock.advance(REFRESH);
        StepVerifier.create(filter(cohort, batch(CONSENT_2020, "p1")))
                .assertNext(result -> assertThat(result.patientIds()).containsExactly("p1"))
                .verifyComplete();

        verify(dataStore, times(2)).capabilityStatement();
    }

    @Test
    void failedRefreshKeepsTheLastCapabilityStatementAndRetriesOnTheNextAccess() throws Exception {
        translate("E11", DIABETES_QUERY);
        var statement = new CapabilityStatement();
        var condition = statement.addRest().addResource().setType("Condition");
        List.of("patient", "code", "recorded-date").forEach(name -> condition.addSearchParam().setName(name));
        when(dataStore.capabilityStatement()).thenReturn(Mono.just(statement),
                Mono.error(new DataStoreException("down")), Mono.error(new DataStoreException("down")));
        counts("icd10|E11");
        var cohort = cohort(List.of(List.of(criterion("Diagnose", "E11"))));

        filter(cohort, batch(CONSENT_2020, "p1")).block();
        clock.advance(REFRESH);
        StepVerifier.create(filter(cohort, batch(CONSENT_2020, "p1")))
                .assertNext(result -> assertThat(result.patientIds()).containsExactly("p1"))
                .verifyComplete();
        StepVerifier.create(filter(cohort, batch(CONSENT_2020, "p1")))
                .assertNext(result -> assertThat(result.patientIds()).containsExactly("p1"))
                .verifyComplete();

        verify(dataStore, times(3)).capabilityStatement();
    }

    @Test
    void failsAndRetriesWhileNoCapabilityStatementWasEverFetched() throws Exception {
        translate("E11", DIABETES_QUERY);
        var cohort = cohort(List.of(List.of(criterion("Diagnose", "E11"))));
        when(dataStore.capabilityStatement()).thenReturn(Mono.error(new DataStoreException("down")));

        StepVerifier.create(filter(cohort, batch(CONSENT_2020, "p1"))).verifyErrorMessage("down");
        StepVerifier.create(filter(cohort, batch(CONSENT_2020, "p1"))).verifyErrorMessage("down");

        verify(dataStore, times(2)).capabilityStatement();
    }

    @Test
    void concurrentBatchesShareOneCapabilityStatementFetch() throws Exception {
        translate("E11", DIABETES_QUERY);
        var statement = new CapabilityStatement();
        var condition = statement.addRest().addResource().setType("Condition");
        List.of("patient", "code", "recorded-date").forEach(name -> condition.addSearchParam().setName(name));
        Sinks.One<CapabilityStatement> response = Sinks.one();
        when(dataStore.capabilityStatement()).thenReturn(response.asMono());
        counts("icd10|E11");
        var orGroups = checker.translate(cohort(List.of(List.of(criterion("Diagnose", "E11")))));

        var first = checker.filter(orGroups, batch(CONSENT_2020, "p1")).toFuture();
        var second = checker.filter(orGroups, batch(CONSENT_2020, "p2")).toFuture();
        response.tryEmitValue(statement);

        assertThat(first.get().patientIds()).containsExactly("p1");
        assertThat(second.get().patientIds()).containsExactly("p2");
        verify(dataStore, times(1)).capabilityStatement();
    }

    @Test
    void countErrorFails() throws Exception {
        var batch = batch(CONSENT_2020, "p1");
        translate("E11", DIABETES_QUERY);
        serverSupportsAllParams();
        when(dataStore.countBatch(any())).thenReturn(Mono.error(new DataStoreException("entry failed")));

        StepVerifier.create(filter(cohort(List.of(List.of(criterion("Diagnose", "E11")))), batch))
                .expectErrorMessage("entry failed")
                .verify();
    }

    @Test
    void translateFailsOnUntranslatableCriterion() throws Exception {
        when(translator.toQuery(any())).thenThrow(new MappingException("no mapping"));

        assertThatThrownBy(() -> checker.translate(cohort(List.of(List.of(criterion("Diagnose", "E11"))))))
                .isInstanceOf(MappingException.class)
                .hasMessage("no mapping");
    }

    @Test
    void translateKeepsTheDateParamOfEachOrQuery() throws Exception {
        translate("E11", DIABETES_QUERY);
        translate("female", FEMALE_QUERY);

        var orGroups = checker.translate(cohort(List.of(List.of(criterion("Diagnose", "E11")),
                List.of(criterion("Patient", "female")))));

        assertThat(orGroups).containsExactly(
                List.of(OrQuery.of(DIABETES_QUERY, Optional.of("recorded-date"))),
                List.of(OrQuery.of(FEMALE_QUERY, Optional.empty())));
    }

    @Test
    void validateRejectsSearchParamUnsupportedByTheLastFetchedCapabilityStatement() throws Exception {
        translate("E11", DIABETES_QUERY);
        translate("J45", ASTHMA_QUERY);
        serverSupportsAllParams();
        counts("icd10|E11");
        filter(cohort(List.of(List.of(criterion("Diagnose", "E11")))), batch(CONSENT_2020, "p1")).block();
        translate("A10", Query.of("Condition", QueryParams.of("medication.code", conceptValue(TermCode.of("atc", "A10", "A10")))));

        var unsupported = checker.translate(cohort(List.of(List.of(criterion("Medikation", "A10")))));

        assertThatThrownBy(() -> checker.validate(unsupported))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Inclusion criterion cannot be checked against consent: "
                        + "The FHIR server does not support the search param Condition.medication");
        checker.validate(checker.translate(cohort(List.of(List.of(criterion("Diagnose", "J45"))))));
    }

    @Test
    void validateSkipsTheSearchParamCheckWhileNoCapabilityStatementWasFetched() throws Exception {
        translate("A10", Query.of("Condition", QueryParams.of("medication.code", conceptValue(TermCode.of("atc", "A10", "A10")))));

        checker.validate(checker.translate(cohort(List.of(List.of(criterion("Medikation", "A10"))))));

        verify(dataStore, never()).capabilityStatement();
    }

    @Test
    void translateRejectsResourceTypesNotInTypeToConsent() throws Exception {
        translate("E11", Query.of("Observation", QueryParams.of("code", stringValue("loinc|1"))));

        assertThatThrownBy(() -> checker.translate(cohort(List.of(List.of(criterion("Diagnose", "E11"))))))
                .isInstanceOf(MappingException.class)
                .hasMessageContaining("No consent date mapping for resource type Observation");
    }

    @Test
    void translateSkipsConsentCriteria() throws Exception {
        translate("E11", DIABETES_QUERY);

        var orGroups = checker.translate(cohort(List.of(List.of(criterion("Einwilligung", "consent-code")),
                List.of(criterion("Diagnose", "E11")))));

        assertThat(orGroups).containsExactly(List.of(OrQuery.of(DIABETES_QUERY, Optional.of("recorded-date"))));
        verify(translator, never()).toQuery(hasCode("consent-code"));
    }

    static JsonNode procedureWithReasonDiagnosis() throws Exception {
        return MAPPER.readTree("""
                {"context": {"system": "fdpg.mii.cds", "code": "Prozedur", "display": "Prozedur"},
                 "termCodes": [{"system": "ops", "code": "5-470", "display": "Appendektomie"}],
                 "attributeFilters": [{"type": "reference",
                   "attributeCode": {"system": "http://hl7.org/fhir/StructureDefinition", "code": "reasonReference", "display": "reason"},
                   "criteria": [{"context": {"system": "fdpg.mii.cds", "code": "Diagnose", "display": "Diagnose"},
                                 "termCodes": [{"system": "icd10", "code": "K35", "display": "Appendizitis"}]}]}]}
                """);
    }

    @Test
    void referenceAttributeFiltersAreCountedWithTheConsentPeriodsOfTheReferencingResource() throws Exception {
        var batch = batch(CONSENT_2020, "p1");
        translate("5-470", Query.of("Procedure", QueryParams.of("code", conceptValue(TermCode.of("ops", "5-470", "5-470")))
                .appendParam("reason-reference.code", conceptValue(TermCode.of("icd10", "K35", "K35")))));
        var statement = new CapabilityStatement();
        var procedure = statement.addRest().addResource().setType("Procedure");
        List.of("patient", "date", "code", "reason-reference").forEach(name -> procedure.addSearchParam().setName(name));
        when(dataStore.capabilityStatement()).thenReturn(Mono.just(statement));
        counts("reason-reference.code=icd10|K35");

        StepVerifier.create(filter(cohort(List.of(List.of(procedureWithReasonDiagnosis()))), batch))
                .assertNext(result -> assertThat(result.patientIds()).containsExactly("p1"))
                .verifyComplete();

        assertThat(countedQueries).containsExactly(List.of(
                "Procedure?patient=Patient/p1&date=sa2019-12-31&date=eb2021-01-01&code=ops|5-470&reason-reference.code=icd10|K35"));
    }

    @Test
    void cohortWithoutCheckableCriteriaKeepsTheBatch() throws Exception {
        var batch = batch(CONSENT_2020, "p1");

        StepVerifier.create(filter(cohort(List.of(List.of(criterion("Einwilligung", "consent-code")))), batch))
                .assertNext(result -> assertThat(result).isSameAs(batch))
                .verifyComplete();

        verify(dataStore, never()).capabilityStatement();
    }
}
