package de.medizininformatikinitiative.torch;

import de.medizininformatikinitiative.torch.consent.ConsentFetcher;
import de.medizininformatikinitiative.torch.consent.ConsentHandler;
import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import de.medizininformatikinitiative.torch.model.mapping.ConsentKey;
import de.medizininformatikinitiative.torch.service.DataStore;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class ConsentHandlerTest {

    public static final String PATIENT_ID = "VHF00006";
    public static final String UNKNOWN_PATIENT_ID = "Unknown";
    public static final PatientBatch BATCH = PatientBatch.of(PATIENT_ID);
    public static final PatientBatch BATCH_UNKNOWN = PatientBatch.of(UNKNOWN_PATIENT_ID);

    ConsentFetcher consentFetcher = Mockito.mock(ConsentFetcher.class);
    DataStore dataStore = Mockito.mock(DataStore.class);
    ConsentHandler consentHandler = new ConsentHandler(dataStore, consentFetcher);

    @Test
    void failsOnNoPatientMatchesConsentKeyBuildingConsent() {
        when(consentFetcher.buildConsentInfo(ConsentKey.YES_NO_NO_YES, BATCH))
                .thenReturn(Mono.error(new ConsentViolatedException("No valid provisions found for any patients in batch")));

        var resultBatch = consentHandler.fetchAndBuildConsentInfo(ConsentKey.YES_NO_NO_YES, BATCH);

        StepVerifier.create(resultBatch)
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ConsentViolatedException.class)
                        .hasMessageContaining("No valid provisions found for any patients in batch"))
                .verify();
    }

    @Test
    void failsOnUnknownPatientBuildingConsent() {
        when(consentFetcher.buildConsentInfo(ConsentKey.YES_YES_YES_YES, BATCH_UNKNOWN))
                .thenReturn(Mono.error(new ConsentViolatedException("No valid provisions found for any patients in batch")));

        var resultBatch = consentHandler.fetchAndBuildConsentInfo(ConsentKey.YES_YES_YES_YES, BATCH_UNKNOWN);

        StepVerifier.create(resultBatch)
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ConsentViolatedException.class)
                        .hasMessageContaining("No valid provisions found for any patients in batch"))
                .verify();
    }

}
