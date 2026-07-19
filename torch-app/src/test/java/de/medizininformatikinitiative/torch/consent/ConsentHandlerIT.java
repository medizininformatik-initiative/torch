package de.medizininformatikinitiative.torch.consent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.medizininformatikinitiative.torch.Torch;
import de.medizininformatikinitiative.torch.consent.mii.model.TermCode;
import de.medizininformatikinitiative.torch.model.consent.NonContinuousPeriod;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
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
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real, autoconfigured {@link ConsentEvaluator} bean end to end against a live Blaze
 * instance, replicating the {@code ConsentEvaluator} -&gt; {@link PatientBatchWithConsent} conversion
 * {@code ExtractDataService} performs.
 */
@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.main.allow-bean-definition-overriding=true"}, classes = Torch.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConsentHandlerIT {

    static final String MII_CONSENT_SYSTEM = "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3";
    static final Set<TermCode> MII_CONSENT_CODES = Set.of(
            new TermCode(MII_CONSENT_SYSTEM, "2.16.840.1.113883.3.1937.777.24.5.3.8"),
            new TermCode(MII_CONSENT_SYSTEM, "2.16.840.1.113883.3.1937.777.24.5.3.6"),
            new TermCode(MII_CONSENT_SYSTEM, "2.16.840.1.113883.3.1937.777.24.5.3.46"),
            new TermCode(MII_CONSENT_SYSTEM, "2.16.840.1.113883.3.1937.777.24.5.3.10"),
            new TermCode(MII_CONSENT_SYSTEM, "2.16.840.1.113883.3.1937.777.24.5.3.37"),
            new TermCode(MII_CONSENT_SYSTEM, "2.16.840.1.113883.3.1937.777.24.5.3.26"),
            new TermCode(MII_CONSENT_SYSTEM, "2.16.840.1.113883.3.1937.777.24.5.3.27"),
            new TermCode(MII_CONSENT_SYSTEM, "2.16.840.1.113883.3.1937.777.24.5.3.28"),
            new TermCode(MII_CONSENT_SYSTEM, "2.16.840.1.113883.3.1937.777.24.5.3.29"),
            new TermCode(MII_CONSENT_SYSTEM, "2.16.840.1.113883.3.1937.777.24.5.3.31"),
            new TermCode(MII_CONSENT_SYSTEM, "2.16.840.1.113883.3.1937.777.24.5.3.30")
    );

    public static final String PATIENT_ID = "VHF00006";
    public static final PatientBatch BATCH = PatientBatch.of(PATIENT_ID);
    @Autowired
    @Qualifier("fhirClient")
    WebClient webClient;
    @Autowired
    ConsentEvaluator consentEvaluator;
    @Autowired
    ConsentValidator consentValidator;
    @Value("${torch.fhir.testPopulation.path}")
    String testPopulationPath;

    @BeforeAll
    void init() throws IOException {
        webClient.post().bodyValue(Files.readString(Path.of(testPopulationPath))).header("Content-Type", "application/fhir+json").retrieve().toBodilessEntity().block();
    }

    /** Builds a minimal CCDL cohort definition whose inclusion criteria carry the given consent codes. */
    private static JsonNode cohortDefinitionWithConsentCodes(Set<TermCode> codes) {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode termCodes = mapper.createArrayNode();
        for (TermCode code : codes) {
            ObjectNode tc = mapper.createObjectNode();
            tc.put("system", code.system());
            tc.put("code", code.code());
            termCodes.add(tc);
        }

        ObjectNode criteria = mapper.createObjectNode();
        criteria.putObject("context").put("code", "Einwilligung");
        criteria.set("termCodes", termCodes);

        ArrayNode group = mapper.createArrayNode();
        group.add(criteria);

        ArrayNode inclusionCriteria = mapper.createArrayNode();
        inclusionCriteria.add(group);

        ObjectNode root = mapper.createObjectNode();
        root.set("inclusionCriteria", inclusionCriteria);
        return root;
    }

    private void assertConsentTrue(PatientBatchWithConsent batch, String date) {
        Observation observation = new Observation();
        observation.setSubject(new Reference("Patient/" + PATIENT_ID));
        observation.setEffective(new DateTimeType(date));
        assertThat(consentValidator.checkConsent(observation, batch)).isTrue();
    }

    private void assertConsentFalse(PatientBatchWithConsent batch, String date) {
        Observation observation = new Observation();
        observation.setSubject(new Reference("Patient/" + PATIENT_ID));
        observation.setEffective(new DateTimeType(date));
        assertThat(consentValidator.checkConsent(observation, batch)).isFalse();
    }

    @Test
    void successAfterEncounterUpdatesProvisions() {
        ConsentContext crtdl = () -> cohortDefinitionWithConsentCodes(MII_CONSENT_CODES);
        PatientSet batch = BATCH::ids;

        Mono<PatientBatchWithConsent> resultBatch = consentEvaluator.evaluate(crtdl, batch)
                .flatMap(maybePeriods -> maybePeriods
                        .map(periods -> patientBatchWithConsent(periods))
                        .orElseGet(() -> Mono.just(PatientBatchWithConsent.fromBatch(BATCH))));

        StepVerifier.create(resultBatch)
                .assertNext(batch2 -> {
                    assertThat(batch2.patientIds()).containsExactly(PATIENT_ID);
                    assertThat(batch2.bundles().get(PATIENT_ID).consentPeriods().periods()).isNotEmpty();
                    assertConsentTrue(batch2, "2021-01-02T00:00:00+01:00");
                    assertConsentTrue(batch2, "2020-01-01T00:00:00+01:00");
                    // Encounter shifts .6 start from 2021-01-01 to 2019-01-01; retro modifier (.46)
                    // then shifts it further to 1900-01-01 — so 2019-01-01 is now within the window
                    assertConsentTrue(batch2, "2019-01-01T00:00:00+01:00");
                    assertConsentFalse(batch2, "2026-01-01T00:00:00+01:00");
                }).verifyComplete();
    }

    private static Mono<PatientBatchWithConsent> patientBatchWithConsent(Map<String, NonContinuousPeriod> periods) {
        try {
            return Mono.just(PatientBatchWithConsent.fromBatchAndConsent(BATCH, periods));
        } catch (ConsentViolatedException e) {
            return Mono.error(e);
        }
    }
}
