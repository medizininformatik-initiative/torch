package de.medizininformatikinitiative.torch.consent.mii;

import de.medizininformatikinitiative.torch.consent.ConsentContext;
import de.medizininformatikinitiative.torch.consent.ConsentFormatException;
import de.medizininformatikinitiative.torch.consent.ConsentViolatedException;
import de.medizininformatikinitiative.torch.consent.PatientSet;
import de.medizininformatikinitiative.torch.consent.mii.model.ConsentCodeConfig;
import de.medizininformatikinitiative.torch.consent.mii.model.ConsentProvisions;
import de.medizininformatikinitiative.torch.consent.mii.model.TermCode;
import de.medizininformatikinitiative.torch.model.consent.NonContinuousPeriod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MiiConsentEvaluatorTest {

    public static final String PATIENT_ID = "VHF00006";
    public static final String UNKNOWN_PATIENT_ID = "Unknown";
    public static final ConsentContext CRTDL = () -> null;
    public static final PatientSet BATCH = () -> List.of(PATIENT_ID);
    public static final PatientSet BATCH_UNKNOWN = () -> List.of(UNKNOWN_PATIENT_ID);
    public static final Set<TermCode> CODES = Set.of(new TermCode("sys", "code1"));

    @Mock
    CrtdlConsentValidator crtdlConsentValidator;
    @Mock
    ConsentCodeConfig consentCodeConfig;
    @Mock
    ConsentFetcher consentFetcher;
    @Mock
    ConsentAdjuster consentAdjuster;
    @Mock
    ConsentCalculator consentCalculator;

    @InjectMocks
    MiiConsentEvaluator miiConsentEvaluator;

    @Test
    void validate_whenConsentCodesPresent_delegatesCoOccurrenceCheck() throws ConsentFormatException {
        when(crtdlConsentValidator.extractConsentCodes(CRTDL)).thenReturn(Optional.of(CODES));

        assertThat(miiConsentEvaluator.validate(CRTDL)).isTrue();

        verify(consentCodeConfig).validateCodeCoOccurrence(CODES);
    }

    @Test
    void validate_whenNoConsentCodes_skipsCoOccurrenceCheck() throws ConsentFormatException {
        when(crtdlConsentValidator.extractConsentCodes(CRTDL)).thenReturn(Optional.empty());

        assertThat(miiConsentEvaluator.validate(CRTDL)).isTrue();

        verifyNoInteractions(consentCodeConfig);
    }

    @Test
    void evaluate_whenExtractConsentCodesThrows_propagatesAsError() throws ConsentFormatException {
        when(crtdlConsentValidator.extractConsentCodes(CRTDL))
                .thenThrow(new ConsentFormatException("malformed consent criteria"));

        StepVerifier.create(miiConsentEvaluator.evaluate(CRTDL, BATCH))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ConsentFormatException.class)
                        .hasMessageContaining("malformed consent criteria"))
                .verify();
    }

    @Test
    void evaluate_happyPath_returnsCalculatedConsentPeriods() throws ConsentFormatException {
        var codes = CODES;
        when(crtdlConsentValidator.extractConsentCodes(CRTDL)).thenReturn(Optional.of(codes));
        when(consentCodeConfig.extractRequestedProspectiveCodes(codes)).thenReturn(codes);
        when(consentCodeConfig.withRetroModifiers(codes, codes)).thenReturn(codes);
        when(consentCodeConfig.nonGateCodes(codes)).thenReturn(codes);

        Map<String, List<ConsentProvisions>> fetched = Map.of();
        Map<String, List<ConsentProvisions>> adjusted = Map.of();
        Map<String, NonContinuousPeriod> calculated = Map.of(PATIENT_ID, NonContinuousPeriod.of());

        when(consentFetcher.fetchConsentInfo(codes, List.of(PATIENT_ID))).thenReturn(Mono.just(fetched));
        when(consentAdjuster.fetchEncounterAndAdjustByEncounter(List.of(PATIENT_ID), fetched, codes))
                .thenReturn(Mono.just(adjusted));
        when(consentCalculator.calculateConsent(codes, adjusted)).thenReturn(calculated);

        StepVerifier.create(miiConsentEvaluator.evaluate(CRTDL, BATCH))
                .assertNext(result -> assertThat(result).contains(calculated))
                .verifyComplete();
    }

    @Test
    void evaluateReturnsEmptyOptionalWhenCrtdlHasNoConsentCodes() throws ConsentFormatException {
        when(crtdlConsentValidator.extractConsentCodes(CRTDL)).thenReturn(Optional.empty());

        StepVerifier.create(miiConsentEvaluator.evaluate(CRTDL, BATCH))
                .assertNext(result -> assertThat(result).isEmpty())
                .verifyComplete();
    }

    @Test
    void failsOnNoPatientMatchesConsentKeyBuildingConsent() throws ConsentFormatException {
        var codes = CODES;
        when(crtdlConsentValidator.extractConsentCodes(CRTDL)).thenReturn(Optional.of(codes));
        when(consentCodeConfig.extractRequestedProspectiveCodes(codes)).thenReturn(codes);
        when(consentCodeConfig.withRetroModifiers(codes, codes)).thenReturn(codes);
        when(consentFetcher.fetchConsentInfo(codes, List.of(PATIENT_ID)))
                .thenReturn(Mono.error(new ConsentViolatedException("No valid consentPeriods found for any patients in batch")));

        StepVerifier.create(miiConsentEvaluator.evaluate(CRTDL, BATCH))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ConsentViolatedException.class)
                        .hasMessageContaining("No valid consentPeriods found for any patients in batch"))
                .verify();
    }

    @Test
    void failsOnUnknownPatientBuildingConsent() throws ConsentFormatException {
        var codes = CODES;
        when(crtdlConsentValidator.extractConsentCodes(CRTDL)).thenReturn(Optional.of(codes));
        when(consentCodeConfig.extractRequestedProspectiveCodes(codes)).thenReturn(codes);
        when(consentCodeConfig.withRetroModifiers(codes, codes)).thenReturn(codes);
        when(consentFetcher.fetchConsentInfo(codes, List.of(UNKNOWN_PATIENT_ID)))
                .thenReturn(Mono.error(new ConsentViolatedException("No valid consentPeriods found for any patients in batch")));

        StepVerifier.create(miiConsentEvaluator.evaluate(CRTDL, BATCH_UNKNOWN))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ConsentViolatedException.class)
                        .hasMessageContaining("No valid consentPeriods found for any patients in batch"))
                .verify();
    }
}
