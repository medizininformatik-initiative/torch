package de.medizininformatikinitiative.torch.consent;

import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import de.medizininformatikinitiative.torch.model.management.TermCode;
import de.medizininformatikinitiative.torch.service.DataStore;
import de.medizininformatikinitiative.torch.util.ResourceUtils;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.DateTimeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsentFetcherTest {

    public static final Set<TermCode> CODES = Set.of(new TermCode("s1", "c1"));
    @Mock
    private DataStore dataStore;

    @Mock
    private ProvisionExtractor extractor;

    @InjectMocks
    private ConsentFetcher consentFetcher;

    @Test
    void failsOnInactiveConsent() {
        Consent consent = new Consent();
        consent.setStatus(Consent.ConsentState.INACTIVE);

        when(dataStore.search(any(), any())).thenReturn(Flux.just(consent));

        var resultBatch = consentFetcher.fetchConsentInfo(CODES, new PatientBatch(List.of("123")));

        StepVerifier.create(resultBatch)
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ConsentViolatedException.class)
                        .hasMessageContaining("No valid consentPeriods found for any patients in batch"))
                .verify();
    }

    @Test
    void skipsConsentWithoutDateTime() {
        // consent with ACTIVE status but missing dateTime
        Consent consent = new Consent();
        consent.setStatus(Consent.ConsentState.ACTIVE);
        consent.setId("cons-no-date");
        consent.setDateTimeElement(new DateTimeType()); // empty datetime triggers branch

        // mock search
        when(dataStore.search(any(), any())).thenReturn(Flux.just(consent));


        // mock static ResourceUtils.patientId
        try (MockedStatic<ResourceUtils> mocked = mockStatic(ResourceUtils.class)) {
            mocked.when(() -> ResourceUtils.patientId(consent)).thenReturn("patient1");

            var result = consentFetcher.fetchConsentInfo(CODES, new PatientBatch(List.of("patient1")));

            StepVerifier.create(result)
                    .expectError(ConsentViolatedException.class) // because the consent is skipped
                    .verify();
        }
    }

    @Test
    void skipsConsentWithDateTimeNull() {
        // consent with ACTIVE status but missing dateTime
        Consent consent = new Consent();
        consent.setStatus(Consent.ConsentState.ACTIVE);
        consent.setId("cons-no-date");
        consent.setDateTimeElement(null);
        // mock search
        when(dataStore.search(any(), any())).thenReturn(Flux.just(consent));


        // mock static ResourceUtils.patientId
        try (MockedStatic<ResourceUtils> mocked = mockStatic(ResourceUtils.class)) {
            mocked.when(() -> ResourceUtils.patientId(consent)).thenReturn("patient1");

            var result = consentFetcher.fetchConsentInfo(CODES, new PatientBatch(List.of("patient1")));

            StepVerifier.create(result)
                    .expectError(ConsentViolatedException.class) // because the consent is skipped
                    .verify();
        }
    }


    @Test
    void failsOnMissingDateTime() {
        Consent consent = new Consent();
        consent.setStatus(Consent.ConsentState.ACTIVE);
        consent.setDateTimeElement(new DateTimeType()); // empty datetime

        when(dataStore.search(any(), any())).thenReturn(Flux.just(consent));

        try (MockedStatic<ResourceUtils> mocked = mockStatic(ResourceUtils.class)) {
            mocked.when(() -> ResourceUtils.patientId(consent)).thenReturn("p1");

            var resultBatch = consentFetcher.fetchConsentInfo(CODES, new PatientBatch(List.of("p1")));

            StepVerifier.create(resultBatch)
                    .expectError(ConsentViolatedException.class)
                    .verify();
        }
    }

    @Test
    void failsOnPatientIdNotFound() {
        Consent consent = new Consent();
        consent.setStatus(Consent.ConsentState.ACTIVE);
        consent.setDateTimeElement(new DateTimeType("2020-01-01"));

        when(dataStore.search(any(), any())).thenReturn(Flux.just(consent));

        try (MockedStatic<ResourceUtils> mocked = mockStatic(ResourceUtils.class)) {
            mocked.when(() -> ResourceUtils.patientId(consent))
                    .thenThrow(new PatientIdNotFoundException("not found"));

            var resultBatch = consentFetcher.fetchConsentInfo(CODES, new PatientBatch(List.of("p1")));

            StepVerifier.create(resultBatch)
                    .expectError(ConsentViolatedException.class)
                    .verify();
        }
    }

    @Test
    void failsWhenExtractProvisionsThrows() throws PatientIdNotFoundException, ConsentViolatedException {
        Consent consent = new Consent();
        consent.setStatus(Consent.ConsentState.ACTIVE);
        consent.setDateTimeElement(new DateTimeType("2020-01-01"));

        when(dataStore.search(any(), any())).thenReturn(Flux.just(consent));

        try (MockedStatic<ResourceUtils> mocked = mockStatic(ResourceUtils.class)) {
            mocked.when(() -> ResourceUtils.patientId(consent)).thenReturn("p1");
            when(extractor.extractProvisionsPeriodByCode(any(), anySet()))
                    .thenThrow(new ConsentViolatedException("bad"));

            var resultBatch = consentFetcher.fetchConsentInfo(CODES, new PatientBatch(List.of("p1")));

            StepVerifier.create(resultBatch)
                    .expectError(ConsentViolatedException.class)
                    .verify();
        }
    }
}
