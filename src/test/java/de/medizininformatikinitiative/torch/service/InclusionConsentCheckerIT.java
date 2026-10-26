package de.medizininformatikinitiative.torch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.Torch;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.PatientExclusionEvent;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.PatientExclusionStage;
import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.model.consent.NonContinuousPeriod;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.consent.Period;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.main.allow-bean-definition-overriding=true", "torch.inclusion-consent-check.enabled=true"}, classes = Torch.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InclusionConsentCheckerIT {

    // VHF00006 has Condition I95.0 (recorded 2022-01-01) and Observation LOINC 33762-6 (effective 2022-01-01)
    static final String PATIENT_ID = "VHF00006";

    @Autowired
    @Qualifier("fhirClient")
    WebClient webClient;
    @Autowired
    InclusionConsentChecker checker;
    @Autowired
    ObjectMapper mapper;
    @Value("${torch.fhir.testPopulation.path}")
    String testPopulationPath;

    @BeforeAll
    void init() throws IOException {
        webClient.post().bodyValue(Files.readString(Path.of(testPopulationPath))).header("Content-Type", "application/fhir+json").retrieve().toBodilessEntity().block();
    }

    static PatientBatchWithConsent consentedBetween(String start, String end) throws ConsentViolatedException {
        return PatientBatchWithConsent.fromBatchAndConsent(PatientBatch.of(PATIENT_ID),
                Map.of(PATIENT_ID, NonContinuousPeriod.of(Period.of(start, end))));
    }

    JsonNode cohort(String inclusionCriteria) throws IOException {
        return mapper.readTree("""
                {"version": "http://to_be_decided.com/draft-1/schema#", "inclusionCriteria": %s}
                """.formatted(inclusionCriteria));
    }

    static final String DIAGNOSIS_I95 = """
            {"context": {"code": "Diagnose", "display": "Diagnose", "system": "fdpg.mii.cds", "version": "1.0.0"},
             "termCodes": [{"code": "I95", "display": "Hypotonie", "system": "http://fhir.de/CodeSystem/bfarm/icd-10-gm", "version": "2026"}]}
            """;

    static final String LAB_NT_PRO_BNP_2022 = """
            {"context": {"code": "Laboruntersuchung", "display": "Laboruntersuchung", "system": "fdpg.mii.cds", "version": "1.0.0"},
             "termCodes": [{"code": "33762-6", "display": "NT-proBNP", "system": "http://loinc.org", "version": "2.81"}],
             "timeRestriction": {"afterDate": "2021-06-01", "beforeDate": "2022-06-01"}}
            """;

    static final String CONSENT = """
            {"context": {"code": "Einwilligung", "display": "Einwilligung", "system": "fdpg.mii.cds", "version": "1.0.0"},
             "termCodes": [{"code": "2.16.840.1.113883.3.1937.777.24.5.3.8", "display": "MDAT wissenschaftlich nutzen",
                            "system": "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3", "version": "1.0.7"}]}
            """;

    @Test
    void keepsPatientWhoseExpandedDiagnosisLiesInsideConsent() throws Exception {
        var batch = consentedBetween("2021-01-01", "2022-12-31");

        StepVerifier.create(checker.filter(cohort("[[%s], [%s]]".formatted(CONSENT, DIAGNOSIS_I95)), batch))
                .assertNext(result -> assertThat(result.patientIds()).containsExactly(PATIENT_ID))
                .verifyComplete();
    }

    @Test
    void removesPatientWhoseDiagnosisLiesOutsideConsent() throws Exception {
        var batch = consentedBetween("2021-01-01", "2021-06-30");

        StepVerifier.create(checker.filter(cohort("[[%s], [%s]]".formatted(CONSENT, DIAGNOSIS_I95)), batch))
                .verifyComplete();

        assertThat(batch.batchExclusions().getPatientExclusions())
                .containsExactly(new PatientExclusionEvent(PatientExclusionStage.INCLUSION_CONSENT, PATIENT_ID));
    }

    /**
     * The FHIR server compares consent boundaries as UTC days: the diagnosis recorded at 2022-01-01T00:00:00+01:00
     * lies on 2021-12-31 in UTC, unlike in the extraction's consent filter, which uses the date as written.
     */
    @Test
    void comparesConsentBoundariesAsUtcDays() throws Exception {
        var endingTheDayBefore = consentedBetween("2021-01-01", "2021-12-31");
        var onlyTheDayAsWritten = consentedBetween("2022-01-01", "2022-01-01");

        StepVerifier.create(checker.filter(cohort("[[%s]]".formatted(DIAGNOSIS_I95)), endingTheDayBefore))
                .assertNext(result -> assertThat(result.patientIds()).containsExactly(PATIENT_ID))
                .verifyComplete();
        StepVerifier.create(checker.filter(cohort("[[%s]]".formatted(DIAGNOSIS_I95)), onlyTheDayAsWritten))
                .verifyComplete();
    }

    static final String RADIOTHERAPY_PATIENT_ID = "INCLUSION-CONSENT-RT";

    static final String RADIOTHERAPY_FOR_I95 = """
            {"context": {"code": "MII_Onkologie_Bestrahlung", "display": "MII Onkologie Bestrahlung", "system": "fdpg.mii.cds", "version": "1.0.0"},
             "termCodes": [{"code": "171425002", "display": "Bestrahlung", "system": "http://snomed.info/sct",
                            "version": "http://snomed.info/sct/900000000000207008/version/20240701"}],
             "attributeFilters": [{"type": "reference",
               "attributeCode": {"code": "reasonReference", "display": "reasonReference", "system": "http://hl7.org/fhir/StructureDefinition"},
               "criteria": [%s]}]}
            """.formatted(DIAGNOSIS_I95);

    /**
     * A radiotherapy in 2022-03 whose reason is a hypotension diagnosis recorded in 2019.
     */
    static final String RADIOTHERAPY_BUNDLE = """
            {"resourceType": "Bundle", "type": "transaction", "entry": [
             {"resource": {"resourceType": "Patient", "id": "%1$s"}, "request": {"method": "PUT", "url": "Patient/%1$s"}},
             {"resource": {"resourceType": "Condition", "id": "%1$s-CD", "subject": {"reference": "Patient/%1$s"},
                           "code": {"coding": [{"system": "http://fhir.de/CodeSystem/bfarm/icd-10-gm", "code": "I95.0"}]},
                           "recordedDate": "2019-06-01"},
              "request": {"method": "PUT", "url": "Condition/%1$s-CD"}},
             {"resource": {"resourceType": "Procedure", "id": "%1$s-PR", "status": "completed", "subject": {"reference": "Patient/%1$s"},
                           "code": {"coding": [{"system": "http://snomed.info/sct", "code": "171425002"}]},
                           "performedDateTime": "2022-03-01", "reasonReference": [{"reference": "Condition/%1$s-CD"}]},
              "request": {"method": "PUT", "url": "Procedure/%1$s-PR"}}]}
            """.formatted(RADIOTHERAPY_PATIENT_ID);

    static final String RADIOTHERAPY_DELETE_BUNDLE = """
            {"resourceType": "Bundle", "type": "transaction", "entry": [
             {"request": {"method": "DELETE", "url": "Procedure/%1$s-PR"}},
             {"request": {"method": "DELETE", "url": "Condition/%1$s-CD"}},
             {"request": {"method": "DELETE", "url": "Patient/%1$s"}}]}
            """.formatted(RADIOTHERAPY_PATIENT_ID);

    void transact(String bundle) {
        webClient.post().bodyValue(bundle).header("Content-Type", "application/fhir+json").retrieve().toBodilessEntity().block();
    }

    /**
     * Only the referencing resource has to lie inside the consent window: its consent covers its references.
     */
    @Test
    void checksConsentOfTheResourceWithTheReferenceAttribute() throws Exception {
        transact(RADIOTHERAPY_BUNDLE);
        try {
            var consentedProcedure = PatientBatchWithConsent.fromBatchAndConsent(PatientBatch.of(RADIOTHERAPY_PATIENT_ID),
                    Map.of(RADIOTHERAPY_PATIENT_ID, NonContinuousPeriod.of(Period.of("2022-01-01", "2022-12-31"))));
            var consentedDiagnosisOnly = PatientBatchWithConsent.fromBatchAndConsent(PatientBatch.of(RADIOTHERAPY_PATIENT_ID),
                    Map.of(RADIOTHERAPY_PATIENT_ID, NonContinuousPeriod.of(Period.of("2019-01-01", "2019-12-31"))));

            StepVerifier.create(checker.filter(cohort("[[%s]]".formatted(RADIOTHERAPY_FOR_I95)), consentedProcedure))
                    .assertNext(result -> assertThat(result.patientIds()).containsExactly(RADIOTHERAPY_PATIENT_ID))
                    .verifyComplete();
            StepVerifier.create(checker.filter(cohort("[[%s]]".formatted(RADIOTHERAPY_FOR_I95)), consentedDiagnosisOnly))
                    .verifyComplete();
        } finally {
            transact(RADIOTHERAPY_DELETE_BUNDLE);
        }
    }

    @Test
    void appliesTimeRestrictionOfLabCriterion() throws Exception {
        var batch = consentedBetween("2021-01-01", "2022-12-31");

        StepVerifier.create(checker.filter(cohort("[[%s]]".formatted(LAB_NT_PRO_BNP_2022)), batch))
                .assertNext(result -> assertThat(result.patientIds()).containsExactly(PATIENT_ID))
                .verifyComplete();
    }

    @Test
    void timeRestrictionOutsideTheResourceDateFindsNothing() throws Exception {
        var batch = consentedBetween("2021-01-01", "2022-12-31");
        var outside = LAB_NT_PRO_BNP_2022.replace("2021-06-01", "2023-01-01").replace("2022-06-01", "2023-12-31");

        StepVerifier.create(checker.filter(cohort("[[%s]]".formatted(outside)), batch))
                .verifyComplete();
    }
}
