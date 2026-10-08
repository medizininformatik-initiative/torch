package de.medizininformatikinitiative.torch.consent;

import de.medizininformatikinitiative.torch.Torch;
import de.medizininformatikinitiative.torch.model.consent.ConsentProvisions;
import de.medizininformatikinitiative.torch.model.consent.Period;
import de.medizininformatikinitiative.torch.model.consent.Provision;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import de.medizininformatikinitiative.torch.model.management.TermCode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.main.allow-bean-definition-overriding=true"}, classes = Torch.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConsentAdjusterIT {

    static final String PATIENT_ID = "consent-adjuster-it";
    static final TermCode CODE = new TermCode("urn:oid:2.16.840.1.113883.3.1937.777.24.5.3", "2.16.840.1.113883.3.1937.777.24.5.3.6");

    @Autowired
    @Qualifier("fhirClient")
    WebClient webClient;
    @Autowired
    ConsentAdjuster consentAdjuster;

    @BeforeAll
    void init() throws IOException {
        webClient.post().bodyValue(Files.readString(Path.of("src/test/resources/ConsentAdjusterIT/bundle.json")))
                .header("Content-Type", "application/fhir+json").retrieve().toBodilessEntity().block();
    }

    @Test
    void shiftsOnlyByInpatientEncounters() {
        Provision provision = new Provision(CODE, Period.of(LocalDate.of(2023, 1, 1), LocalDate.of(2050, 1, 1)), true);
        ConsentProvisions consent = new ConsentProvisions("consent-1", PATIENT_ID, null, List.of(provision));

        StepVerifier.create(consentAdjuster.fetchEncounterAndAdjustByEncounter(
                        PatientBatch.of(PATIENT_ID), Map.of(PATIENT_ID, List.of(consent)), Set.of(CODE), 7))
                .assertNext(adjusted -> assertThat(adjusted.get(PATIENT_ID).getFirst().provisions().getFirst().period().start())
                        .isEqualTo(LocalDate.of(2022, 12, 27)))
                .verifyComplete();
    }
}
