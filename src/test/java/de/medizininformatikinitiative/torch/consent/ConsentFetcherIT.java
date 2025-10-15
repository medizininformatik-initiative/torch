package de.medizininformatikinitiative.torch.consent;

import de.medizininformatikinitiative.torch.Torch;
import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.model.consent.ConsentCode;
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
    ConsentCodeMapper consentCodeMapper;
    @Autowired
    @Value("${torch.fhir.testPopulation.path}")
    String testPopulationPath;


    @BeforeAll
    void init() throws IOException {
        webClient.post().bodyValue(Files.readString(Path.of(testPopulationPath))).header("Content-Type", "application/fhir+json").retrieve().toBodilessEntity().block();
    }


    @Test
    void failsOnUnknownPatientBuildingConsent() {
        var codes = consentCodeMapper.getCombinedCodes(new ConsentCode("fdpg.mii.cds", "yes-yes-yes-yes"));
        var result = consentFetcher.fetchConsentInfo(codes, BATCH_UNKNOWN);

        StepVerifier.create(result)
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ConsentViolatedException.class)
                        .hasMessageContaining("No valid consentPeriods found for any patients in batch"))
                .verify();
    }

    @Test
    void successBuildingConsent() {

        var codes = consentCodeMapper.getCombinedCodes(new ConsentCode("fdpg.mii.cds", "yes-yes-yes-yes"));
        var result = consentFetcher.fetchConsentInfo(codes, BATCH);

        StepVerifier.create(result)
                .assertNext(provisionsMap -> {
                    assertThat(provisionsMap).hasSize(1);
                    assertThat(provisionsMap.keySet()).containsExactly(PATIENT_ID);
                    assertThat(provisionsMap.get(PATIENT_ID).getFirst().provisions()).hasSize(10);
                }).verifyComplete();
    }
}
