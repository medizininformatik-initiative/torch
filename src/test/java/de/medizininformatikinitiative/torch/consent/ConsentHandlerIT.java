package de.medizininformatikinitiative.torch.consent;

import de.medizininformatikinitiative.torch.Torch;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import de.medizininformatikinitiative.torch.model.management.TermCode;
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
import reactor.test.StepVerifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.main.allow-bean-definition-overriding=true"}, classes = Torch.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConsentHandlerIT {

    static final String MII_CONSENT_SYSTEM = "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3";
    static final Set<TermCode> MII_CONSENT_CODES = Set.of(
            new TermCode(MII_CONSENT_SYSTEM, "2.16.840.1.113883.3.1937.777.24.5.3.8"),
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
    ConsentHandler consentHandler;
    @Autowired
    ConsentValidator consentValidator;
    @Value("${torch.fhir.testPopulation.path}")
    String testPopulationPath;

    @BeforeAll
    void init() throws IOException {
        webClient.post().bodyValue(Files.readString(Path.of(testPopulationPath))).header("Content-Type", "application/fhir+json").retrieve().toBodilessEntity().block();
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
        var resultBatch = consentHandler.fetchAndBuildConsentInfo(MII_CONSENT_CODES, BATCH);

        StepVerifier.create(resultBatch)
                .assertNext(batch -> {
                    assertThat(batch.patientIds()).containsExactly(PATIENT_ID);
                    assertThat(batch.bundles().get(PATIENT_ID).consentPeriods().periods()).isNotEmpty();
                    assertConsentTrue(batch, "2021-01-02T00:00:00+01:00");
                    assertConsentTrue(batch, "2020-01-01T00:00:00+01:00");
                    // Encounter shifts .8 start from 2021-01-01 to 2019-01-01; retro modifier (.46)
                    // then shifts it further to 1819-01-01 — so 2019-01-01 is now within the window
                    assertConsentTrue(batch, "2019-01-01T00:00:00+01:00");
                    assertConsentFalse(batch, "2026-01-01T00:00:00+01:00");
                }).verifyComplete();
    }
}
