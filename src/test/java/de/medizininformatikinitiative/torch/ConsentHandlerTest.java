package de.medizininformatikinitiative.torch;

import de.medizininformatikinitiative.torch.consent.ConsentAdjuster;
import de.medizininformatikinitiative.torch.consent.ConsentCalculator;
import de.medizininformatikinitiative.torch.consent.ConsentFetcher;
import de.medizininformatikinitiative.torch.consent.ConsentHandler;
import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ConsentHandlerTest {

    public static final String PATIENT_ID = "VHF00006";
    public static final String UNKNOWN_PATIENT_ID = "Unknown";
    public static final PatientBatch BATCH = PatientBatch.of(PATIENT_ID);
    public static final PatientBatch BATCH_UNKNOWN = PatientBatch.of(UNKNOWN_PATIENT_ID);

    @Mock
    ConsentFetcher consentFetcher;
    @Mock
    ConsentAdjuster consentAdjuster;
    @Mock
    ConsentCalculator consentCalculator;
    @InjectMocks
    ConsentHandler consentHandler;

    @Test
    void failsOnNoPatientMatchesConsentKeyBuildingConsent() {
        when(consentFetcher.fetchConsentInfo("yes-no-no-yes", BATCH))
                .thenReturn(Mono.error(new ConsentViolatedException("No valid consentPeriods found for any patients in batch")));

        var resultBatch = consentHandler.fetchAndBuildConsentInfo("yes-no-no-yes", BATCH);

        StepVerifier.create(resultBatch)
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ConsentViolatedException.class)
                        .hasMessageContaining("No valid consentPeriods found for any patients in batch"))
                .verify();
    }

    @Test
    void failsOnUnknownPatientBuildingConsent() {
        when(consentFetcher.fetchConsentInfo("yes-yes-yes-yes", BATCH_UNKNOWN))
                .thenReturn(Mono.error(new ConsentViolatedException("No valid consentPeriods found for any patients in batch")));

        var resultBatch = consentHandler.fetchAndBuildConsentInfo("yes-yes-yes-yes", BATCH_UNKNOWN);

        StepVerifier.create(resultBatch)
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ConsentViolatedException.class)
                        .hasMessageContaining("No valid consentPeriods found for any patients in batch"))
                .verify();
    }

}
