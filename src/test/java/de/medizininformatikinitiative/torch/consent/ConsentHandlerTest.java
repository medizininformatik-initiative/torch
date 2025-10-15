package de.medizininformatikinitiative.torch.consent;

import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.model.consent.ConsentCode;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ConsentHandlerTest {

    public static final String PATIENT_ID = "VHF00006";
    public static final String UNKNOWN_PATIENT_ID = "Unknown";
    public static final PatientBatch BATCH = PatientBatch.of(PATIENT_ID);
    public static final PatientBatch BATCH_UNKNOWN = PatientBatch.of(UNKNOWN_PATIENT_ID);
    public static final Set<ConsentCode> CODES = Set.of(new ConsentCode("sys", "code1"));

    @Mock
    ConsentFetcher consentFetcher;
    @Mock
    ConsentAdjuster consentAdjuster;
    @Mock
    ConsentCalculator consentCalculator;

    @Mock
    ConsentCalculator consentCalculationFailed;
    @Mock
    ConsentCodeMapper consentCodeMapper;

    @InjectMocks
    ConsentHandler consentHandler;

    @Test
    void failsOnNoPatientMatchesConsentKeyBuildingConsent() {
        var codes = CODES;
        when(consentFetcher.fetchConsentInfo(codes, BATCH))
                .thenReturn(Mono.error(new ConsentViolatedException("No valid consentPeriods found for any patients in batch")));
        when(consentCodeMapper.addCombinedCodes(codes)).thenReturn(codes);

        var resultBatch = consentHandler.fetchAndBuildConsentInfo(codes, BATCH);

        StepVerifier.create(resultBatch)
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ConsentViolatedException.class)
                        .hasMessageContaining("No valid consentPeriods found for any patients in batch"))
                .verify();
    }

    @Test
    void failsOnUnknownPatientBuildingConsent() {

        var codes = CODES;
        when(consentFetcher.fetchConsentInfo(codes, BATCH_UNKNOWN))
                .thenReturn(Mono.error(new ConsentViolatedException("No valid consentPeriods found for any patients in batch")));
        when(consentCodeMapper.addCombinedCodes(codes)).thenReturn(codes);

        var resultBatch = consentHandler.fetchAndBuildConsentInfo(codes, BATCH_UNKNOWN);

        StepVerifier.create(resultBatch)
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ConsentViolatedException.class)
                        .hasMessageContaining("No valid consentPeriods found for any patients in batch"))
                .verify();
    }

}
