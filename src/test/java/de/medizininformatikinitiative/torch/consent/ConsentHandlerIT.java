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

    private void assertConsentFalse(PatientBatchWithConsent batch) {
        Observation observation = new Observation();
        observation.setSubject(new Reference("Patient/" + PATIENT_ID));
        observation.setEffective(new DateTimeType("2019-01-01T00:00:00+01:00"));
        assertThat(consentValidator.checkConsent(observation, batch)).isFalse();
    }

    @Test
    void successAfterEncounterUpdatesProvisions() {
        var resultBatch = consentHandler.fetchAndBuildConsentInfo(Set.of(new TermCode("fdpg.mii.cds", "yes-yes-yes-yes")), BATCH);

        StepVerifier.create(resultBatch)
                .assertNext(batch -> {
                    assertThat(batch.patientIds()).containsExactly(PATIENT_ID);
                    assertThat(batch.bundles().get(PATIENT_ID).consentPeriods().periods()).isNotEmpty();
                    assertConsentTrue(batch, "2021-01-02T00:00:00+01:00");
                    assertConsentTrue(batch, "2020-01-01T00:00:00+01:00");
                    assertConsentFalse(batch);
                }).verifyComplete();
    }
}
