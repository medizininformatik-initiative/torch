package de.medizininformatikinitiative.torch.consent;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import de.medizininformatikinitiative.torch.service.DataStore;
import org.hl7.fhir.r4.model.Consent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsentFetcherTest {

    @Mock
    private DataStore dataStore;

    @Mock
    private ConsentCodeMapper mapper;

    @Mock
    private FhirContext fhirContext;


    @InjectMocks
    private ConsentFetcher consentFetcher;


    @Test
    void failsOnInactiveConsent() {
        Consent consent = new Consent();
        consent.setStatus(Consent.ConsentState.INACTIVE);
        when(dataStore.search(any(), any())).thenReturn(Flux.just(consent));

        var resultBatch = consentFetcher.buildConsentInfo("validCode", new PatientBatch(List.of("123")));


        StepVerifier.create(resultBatch)
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ConsentViolatedException.class)
                        .hasMessageContaining("No valid provisions found for any patients in batch"))
                .verify();
    }

}
