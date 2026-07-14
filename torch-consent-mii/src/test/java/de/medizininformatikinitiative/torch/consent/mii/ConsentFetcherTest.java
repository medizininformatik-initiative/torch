package de.medizininformatikinitiative.torch.consent.mii;

import de.medizininformatikinitiative.torch.consent.ConsentDataClient;
import de.medizininformatikinitiative.torch.consent.ConsentViolatedException;
import de.medizininformatikinitiative.torch.consent.PatientResource;
import de.medizininformatikinitiative.torch.consent.mii.model.ConsentProvisions;
import de.medizininformatikinitiative.torch.consent.mii.model.TermCode;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.DateTimeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsentFetcherTest {

    public static final Set<TermCode> CODES = Set.of(new TermCode("s1", "c1"));

    @Mock
    private ConsentDataClient consentDataClient;

    @Mock
    private ProvisionExtractor extractor;

    @InjectMocks
    private ConsentFetcher consentFetcher;

    @Test
    void failsOnInactiveConsent() {
        Consent consent = new Consent();
        consent.setStatus(Consent.ConsentState.INACTIVE);

        when(consentDataClient.searchActiveConsentsByProfile(any(), any()))
                .thenReturn(Flux.just(new PatientResource<>("123", consent)));

        var resultBatch = consentFetcher.fetchConsentInfo(CODES, List.of("123"));

        StepVerifier.create(resultBatch)
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ConsentViolatedException.class)
                        .hasMessageContaining("No valid consentPeriods found for any patients in batch"))
                .verify();
    }

    @Test
    void skipsConsentWithoutDateTime() {
        // consent with ACTIVE status but empty dateTime
        Consent consent = new Consent();
        consent.setStatus(Consent.ConsentState.ACTIVE);
        consent.setId("cons-no-date");
        consent.setDateTimeElement(new DateTimeType());

        when(consentDataClient.searchActiveConsentsByProfile(any(), any()))
                .thenReturn(Flux.just(new PatientResource<>("patient1", consent)));

        var result = consentFetcher.fetchConsentInfo(CODES, List.of("patient1"));

        StepVerifier.create(result)
                .expectError(ConsentViolatedException.class) // because the consent is skipped
                .verify();
    }

    @Test
    void skipsConsentWithDateTimeNull() {
        Consent consent = new Consent();
        consent.setStatus(Consent.ConsentState.ACTIVE);
        consent.setId("cons-no-date");
        consent.setDateTimeElement(null);

        when(consentDataClient.searchActiveConsentsByProfile(any(), any()))
                .thenReturn(Flux.just(new PatientResource<>("patient1", consent)));

        var result = consentFetcher.fetchConsentInfo(CODES, List.of("patient1"));

        StepVerifier.create(result)
                .expectError(ConsentViolatedException.class) // because the consent is skipped
                .verify();
    }

    @Test
    void succeedsWhenExtractionYieldsValidProvisions() throws ConsentViolatedException {
        Consent consent = new Consent();
        consent.setStatus(Consent.ConsentState.ACTIVE);
        consent.setId("cons-1");
        consent.setDateTimeElement(new DateTimeType("2020-01-01"));

        ConsentProvisions provisions = new ConsentProvisions("p1", consent.getDateTimeElement(), List.of());

        when(consentDataClient.searchActiveConsentsByProfile(any(), any()))
                .thenReturn(Flux.just(new PatientResource<>("p1", consent)));
        when(extractor.extractProvisionsPeriodByCode(any(), any(), anySet()))
                .thenReturn(provisions);

        var resultBatch = consentFetcher.fetchConsentInfo(CODES, List.of("p1"));

        StepVerifier.create(resultBatch)
                .assertNext(result -> assertThat(result).containsEntry("p1", List.of(provisions)))
                .verifyComplete();
    }

    @Test
    void failsWhenExtractProvisionsThrows() throws ConsentViolatedException {
        Consent consent = new Consent();
        consent.setStatus(Consent.ConsentState.ACTIVE);
        consent.setDateTimeElement(new DateTimeType("2020-01-01"));

        when(consentDataClient.searchActiveConsentsByProfile(any(), any()))
                .thenReturn(Flux.just(new PatientResource<>("p1", consent)));
        when(extractor.extractProvisionsPeriodByCode(any(), any(), anySet()))
                .thenThrow(new ConsentViolatedException("bad"));

        var resultBatch = consentFetcher.fetchConsentInfo(CODES, List.of("p1"));

        StepVerifier.create(resultBatch)
                .expectError(ConsentViolatedException.class)
                .verify();
    }
}
