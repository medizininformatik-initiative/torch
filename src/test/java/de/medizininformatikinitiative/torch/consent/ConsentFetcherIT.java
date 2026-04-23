package de.medizininformatikinitiative.torch.consent;

import de.medizininformatikinitiative.torch.Torch;
import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import de.medizininformatikinitiative.torch.model.management.TermCode;
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
class ConsentFetcherIT {

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
    public static final String UNKNOWN_PATIENT_ID = "Unknown";
    public static final PatientBatch BATCH = PatientBatch.of(PATIENT_ID);
    public static final PatientBatch BATCH_UNKNOWN = PatientBatch.of(UNKNOWN_PATIENT_ID);
    @Autowired
    @Qualifier("fhirClient")
    WebClient webClient;
    @Autowired
    ConsentFetcher consentFetcher;
    @Value("${torch.fhir.testPopulation.path}")
    String testPopulationPath;


    @BeforeAll
    void init() throws IOException {
        webClient.post().bodyValue(Files.readString(Path.of(testPopulationPath))).header("Content-Type", "application/fhir+json").retrieve().toBodilessEntity().block();
    }


    @Test
    void failsOnUnknownPatientBuildingConsent() {
        var result = consentFetcher.fetchConsentInfo(MII_CONSENT_CODES, BATCH_UNKNOWN);

        StepVerifier.create(result)
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ConsentViolatedException.class)
                        .hasMessageContaining("No valid consentPeriods found for any patients in batch"))
                .verify();
    }

    @Test
    void successBuildingConsent() {

        var result = consentFetcher.fetchConsentInfo(MII_CONSENT_CODES, BATCH);

        StepVerifier.create(result)
                .assertNext(provisionsMap -> {
                    assertThat(provisionsMap).hasSize(1);
                    assertThat(provisionsMap.keySet()).containsExactly(PATIENT_ID);
                    assertThat(provisionsMap.get(PATIENT_ID).getFirst().provisions()).hasSize(10);
                }).verifyComplete();
    }
}
