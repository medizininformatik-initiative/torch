package de.medizininformatikinitiative.torch.consent;

import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.service.DataStore;
import org.hl7.fhir.r4.model.Consent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TorchConsentDataClientTest {

    @Mock
    DataStore dataStore;

    TorchConsentDataClient client;

    @BeforeEach
    void setUp() {
        client = new TorchConsentDataClient(dataStore);
    }

    @Test
    void searchActiveConsentsByProfile_whenResourceHasNoPatientReference_skipsItSilently() {
        Consent consentWithoutPatient = new Consent();
        consentWithoutPatient.setId("Consent/no-patient-ref");

        when(dataStore.search(any(Query.class), eq(Consent.class)))
                .thenReturn(Flux.just(consentWithoutPatient));

        StepVerifier.create(client.searchActiveConsentsByProfile(List.of("p1"), "https://example.org/profile"))
                .verifyComplete();
    }
}
