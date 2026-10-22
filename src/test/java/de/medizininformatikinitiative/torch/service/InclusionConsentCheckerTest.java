package de.medizininformatikinitiative.torch.service;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.consent.ConsentValidator;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.PatientExclusionEvent;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.PatientExclusionStage;
import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.exceptions.ValidationException;
import de.medizininformatikinitiative.torch.model.consent.NonContinuousPeriod;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.consent.Period;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.fhir.QueryParams;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import de.medizininformatikinitiative.torch.model.mapping.MappingException;
import de.medizininformatikinitiative.torch.model.sq.Criterion;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.stringValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InclusionConsentCheckerTest {

    static final ObjectMapper MAPPER = new ObjectMapper();
    static final Query DIABETES_QUERY = Query.of("Condition", QueryParams.of("code", stringValue("icd10|E11")));
    static final Query ASTHMA_QUERY = Query.of("Condition", QueryParams.of("code", stringValue("icd10|J45")));
    static final Query FEMALE_QUERY = Query.of("Patient", QueryParams.of("gender", stringValue("female")));

    @Mock
    CriterionQueryTranslator translator;

    @Mock
    DataStore dataStore;

    InclusionConsentChecker checker;

    @BeforeEach
    void setUp() throws Exception {
        JsonNode typeToConsent = MAPPER.readTree("""
                {"Condition": "Condition.recordedDate", "Patient": "", "Specimen": "Specimen.collection.collected"}
                """);
        checker = new InclusionConsentChecker(translator, dataStore,
                new ConsentValidator(FhirContext.forR4(), typeToConsent), MAPPER);
    }

    static PatientBatchWithConsent batchConsentedIn2020(String... ids) throws ConsentViolatedException {
        var consent = NonContinuousPeriod.of(Period.of("2020-01-01", "2020-12-31"));
        var periods = Arrays.stream(ids).collect(Collectors.toMap(id -> id, id -> consent));
        return PatientBatchWithConsent.fromBatchAndConsent(PatientBatch.of(ids), periods);
    }

    static Condition condition(String patientId, String recordedDate) {
        var condition = new Condition();
        condition.setId("c-" + patientId + "-" + recordedDate);
        condition.setSubject(new Reference("Patient/" + patientId));
        condition.setRecordedDateElement(new DateTimeType(recordedDate));
        return condition;
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

    void found(Query query, DomainResource... resources) {
        when(dataStore.search(argThat(q -> q != null && q.params().toString().contains(query.params().toString())),
                eq(DomainResource.class))).thenReturn(Flux.just(resources));
    }

    @Test
    void keepsPatientWithConsentedHitAndRemovesPatientWithOnlyUnconsentedHit() throws Exception {
        var batch = batchConsentedIn2020("p1", "p2");
        translate("E11", DIABETES_QUERY);
        found(DIABETES_QUERY, condition("p1", "2020-05-01"), condition("p2", "2019-05-01"));

        StepVerifier.create(checker.filter(cohort(List.of(List.of(criterion("Diagnose", "E11")))), batch))
                .assertNext(result -> assertThat(result.patientIds()).containsExactly("p1"))
                .verifyComplete();

        assertThat(batch.batchExclusions().getPatientExclusions())
                .containsExactly(new PatientExclusionEvent(PatientExclusionStage.INCLUSION_CONSENT, "p2"));
    }

    @Test
    void oneConsentedAlternativeInAnOrGroupIsEnough() throws Exception {
        var batch = batchConsentedIn2020("p1");
        translate("E11", DIABETES_QUERY);
        translate("J45", ASTHMA_QUERY);
        found(DIABETES_QUERY, condition("p1", "2019-05-01"));
        found(ASTHMA_QUERY, condition("p1", "2020-05-01"));

        var cohort = cohort(List.of(List.of(criterion("Diagnose", "E11"), criterion("Diagnose", "J45"))));

        StepVerifier.create(checker.filter(cohort, batch))
                .assertNext(result -> assertThat(result.patientIds()).containsExactly("p1"))
                .verifyComplete();
    }

    @Test
    void everyAndGroupNeedsAConsentedHit() throws Exception {
        var batch = batchConsentedIn2020("p1", "p2");
        translate("E11", DIABETES_QUERY);
        translate("J45", ASTHMA_QUERY);
        found(DIABETES_QUERY, condition("p1", "2020-05-01"), condition("p2", "2020-05-01"));
        found(ASTHMA_QUERY, condition("p1", "2020-06-01"), condition("p2", "2021-06-01"));

        var cohort = cohort(List.of(List.of(criterion("Diagnose", "E11")), List.of(criterion("Diagnose", "J45"))));

        StepVerifier.create(checker.filter(cohort, batch))
                .assertNext(result -> assertThat(result.patientIds()).containsExactly("p1"))
                .verifyComplete();
    }

    @Test
    void consentCriteriaAreNotChecked() throws Exception {
        var batch = batchConsentedIn2020("p1");
        translate("E11", DIABETES_QUERY);
        found(DIABETES_QUERY, condition("p1", "2020-05-01"));

        var cohort = cohort(List.of(List.of(criterion("Einwilligung", "consent-code")), List.of(criterion("Diagnose", "E11"))));

        StepVerifier.create(checker.filter(cohort, batch))
                .assertNext(result -> assertThat(result.patientIds()).containsExactly("p1"))
                .verifyComplete();

        verify(translator, never()).toQuery(hasCode("consent-code"));
    }

    @Test
    void resourcesWithoutConsentDateCountAsConsented() throws Exception {
        var batch = batchConsentedIn2020("p1");
        translate("female", FEMALE_QUERY);
        var patient = new Patient();
        patient.setId("p1");
        patient.setGender(Enumerations.AdministrativeGender.FEMALE);
        found(FEMALE_QUERY, patient);

        StepVerifier.create(checker.filter(cohort(List.of(List.of(criterion("Patient", "female")))), batch))
                .assertNext(result -> assertThat(result.patientIds()).containsExactly("p1"))
                .verifyComplete();
    }

    @Test
    void queriesAreRestrictedToTheBatchAndReducedToPatientAndConsentDate() throws Exception {
        var batch = batchConsentedIn2020("p1");
        translate("E11", DIABETES_QUERY);
        when(dataStore.search(any(Query.class), eq(DomainResource.class))).thenReturn(Flux.empty());

        StepVerifier.create(checker.filter(cohort(List.of(List.of(criterion("Diagnose", "E11")))), batch))
                .verifyComplete();

        var query = ArgumentCaptor.forClass(Query.class);
        verify(dataStore).search(query.capture(), eq(DomainResource.class));
        assertThat(query.getValue()).hasToString("Condition?patient=Patient/p1&code=icd10|E11&_elements=subject,recordedDate");
    }

    @Test
    void emptyWhenNoPatientRemains() throws Exception {
        var batch = batchConsentedIn2020("p1");
        translate("E11", DIABETES_QUERY);
        found(DIABETES_QUERY, condition("p1", "2019-05-01"));

        StepVerifier.create(checker.filter(cohort(List.of(List.of(criterion("Diagnose", "E11")))), batch))
                .verifyComplete();
    }

    @Test
    void translationErrorFails() throws Exception {
        var batch = batchConsentedIn2020("p1");
        when(translator.toQuery(any())).thenThrow(new MappingException("no mapping"));

        StepVerifier.create(checker.filter(cohort(List.of(List.of(criterion("Diagnose", "E11")))), batch))
                .expectErrorMessage("no mapping")
                .verify();
    }

    @Test
    void validateRejectsUntranslatableCriterion() throws Exception {
        when(translator.toQuery(any())).thenThrow(new MappingException("no mapping"));

        assertThatThrownBy(() -> checker.validate(cohort(List.of(List.of(criterion("Diagnose", "E11"))))))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("no mapping");
    }

    @Test
    void validateAcceptsTranslatableCriteriaAndSkipsConsentCriteria() throws Exception {
        translate("E11", DIABETES_QUERY);

        checker.validate(cohort(List.of(List.of(criterion("Einwilligung", "consent-code")),
                List.of(criterion("Diagnose", "E11")))));

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
    void validateRejectsReferenceAttributeFilters() throws Exception {
        assertThatThrownBy(() -> checker.validate(cohort(List.of(List.of(procedureWithReasonDiagnosis())))))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("reference");

        verify(translator, never()).toQuery(any());
    }

    @Test
    void filterFailsOnReferenceAttributeFilters() throws Exception {
        var batch = batchConsentedIn2020("p1");

        StepVerifier.create(checker.filter(cohort(List.of(List.of(procedureWithReasonDiagnosis()))), batch))
                .expectError(MappingException.class)
                .verify();
    }

    @Test
    void cohortWithoutCheckableCriteriaKeepsTheBatch() throws Exception {
        var batch = batchConsentedIn2020("p1");

        StepVerifier.create(checker.filter(cohort(List.of(List.of(criterion("Einwilligung", "consent-code")))), batch))
                .assertNext(result -> assertThat(result).isSameAs(batch))
                .verifyComplete();
    }

    @Test
    void elementsForPatientAndNestedDate() {
        assertThat(checker.elements("Patient")).isEqualTo("id");
        assertThat(checker.elements("Specimen")).isEqualTo("subject,collection");
        assertThat(checker.elements("Consent")).isEqualTo("patient");
    }
}
