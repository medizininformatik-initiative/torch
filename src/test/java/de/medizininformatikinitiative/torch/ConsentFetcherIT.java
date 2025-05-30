package de.medizininformatikinitiative.torch;

import de.medizininformatikinitiative.torch.consent.ConsentFetcher;
import de.medizininformatikinitiative.torch.consent.ConsentValidator;
import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import de.medizininformatikinitiative.torch.model.mapping.ConsentKey;
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

import static org.assertj.core.api.Assertions.assertThat;


@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.main.allow-bean-definition-overriding=true"}, classes = Torch.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConsentFetcherIT {

    public static final String PATIENT_ID = "VHF00006";
    public static final String UNKNOWN_PATIENT_ID = "Unknown";
    public static final PatientBatch BATCH = PatientBatch.of(PATIENT_ID);
    public static final PatientBatch BATCH_UNKNOWN = PatientBatch.of(UNKNOWN_PATIENT_ID);
    @Autowired
    @Qualifier("fhirClient")
    WebClient webClient;
    @Autowired
    ConsentFetcher consentFetcher;
    @Autowired
    ConsentValidator consentValidator;
    @Value("${torch.fhir.testPopulation.path}")
    String testPopulationPath;


    @BeforeAll
    void init() throws IOException {
        webClient.post().bodyValue(Files.readString(Path.of(testPopulationPath))).header("Content-Type", "application/fhir+json").retrieve().toBodilessEntity().block();
    }

    private void assertConsentTrue(PatientBatchWithConsent batch, String patientId, String date) {
        Observation observation = new Observation();
        observation.setSubject(new Reference("Patient/" + patientId));
        observation.setEffective(new DateTimeType(date));
        assertThat(consentValidator.checkConsent(observation, batch)).isTrue();
    }

    private void assertConsentFalse(PatientBatchWithConsent batch, String patientId, String date) {
        Observation observation = new Observation();
        observation.setSubject(new Reference("Patient/" + patientId));
        observation.setEffective(new DateTimeType(date));
        assertThat(consentValidator.checkConsent(observation, batch)).isFalse();
    }

    @Test
    void failsOnNoPatientMatchesConsentKeyBuildingConsent() {
        var resultBatch = consentFetcher.buildConsentInfo(ConsentKey.YES_NO_NO_YES, BATCH);

        StepVerifier.create(resultBatch)
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ConsentViolatedException.class)
                        .hasMessageContaining("No valid provisions found for any patients in batch"))
                .verify();
    }

    @Test
    void failsOnUnknownPatientBuildingConsent() {
        var resultBatch = consentFetcher.buildConsentInfo(ConsentKey.YES_YES_YES_YES, BATCH_UNKNOWN);

        StepVerifier.create(resultBatch)
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ConsentViolatedException.class)
                        .hasMessageContaining("No valid provisions found for any patients in batch"))
                .verify();
    }

    @Test
    void successBuildingConsent() {
        var resultBatch = consentFetcher.buildConsentInfo(ConsentKey.YES_YES_YES_YES, BATCH);

        StepVerifier.create(resultBatch)
                .assertNext(batch -> {
                    assertThat(batch.patientIds()).containsExactly(PATIENT_ID);
                    assertThat(batch.bundles().get(PATIENT_ID).provisions().periods()).isNotEmpty();
                    assertConsentTrue(batch, PATIENT_ID, "2021-01-02T00:00:00+01:00");
                    assertConsentFalse(batch, PATIENT_ID, "2020-01-01T00:00:00+01:00");
                }).verifyComplete();
    }
}
