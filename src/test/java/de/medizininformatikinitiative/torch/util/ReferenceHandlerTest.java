package de.medizininformatikinitiative.torch.util;

import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.exceptions.ReferenceToPatientException;
import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.management.ConsentHandler;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroupWrapper;
import de.medizininformatikinitiative.torch.service.DataStore;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ReferenceHandlerTest {

    @Mock
    private DataStore dataStore;

    @Mock
    private ProfileMustHaveChecker profileMustHaveChecker;

    @Mock
    private CompartmentManager compartmentManager;

    @Mock
    private ConsentHandler consentHandler;

    @InjectMocks
    private ReferenceHandler referenceHandler;

    @BeforeEach
    void setUp() {
        referenceHandler = new ReferenceHandler(dataStore, profileMustHaveChecker, compartmentManager, consentHandler);
    }


    @Nested
    class CoreResource {
        @Test
        public void shouldResolveReferenceSuccessfully() {

            DomainResource coreResource = Mockito.mock(DomainResource.class);


            when(dataStore.fetchDomainResource("Medication/123")).thenReturn(Mono.just(coreResource));


            when(compartmentManager.isInCompartment(coreResource)).thenReturn(false);


            Mono<ResourceGroupWrapper> result = referenceHandler.getResourceGroupWrapperMono(null, true, "Medication/123");


            StepVerifier.create(result)
                    .assertNext(wrapper -> assertThat(wrapper).isNotNull())
                    .verifyComplete();
        }

        @Test
        public void noCoreResourceFound() {

            DomainResource coreResource = Mockito.mock(DomainResource.class);


            when(dataStore.fetchDomainResource("Medication/123")).thenReturn(Mono.just(coreResource));


            when(compartmentManager.isInCompartment(coreResource)).thenReturn(true);


            Mono<ResourceGroupWrapper> result = referenceHandler.getResourceGroupWrapperMono(null, true, "Medication/123");


            StepVerifier.create(result)
                    .expectErrorMatches(throwable ->
                            throwable instanceof ReferenceToPatientException &&
                                    throwable.getMessage().contains("Patient Resource referenced in Core Bundle")
                    )
                    .verify();
        }

        @Test
        public void notResourceFound() {
            DomainResource coreResource = Mockito.mock(DomainResource.class);

            when(dataStore.fetchDomainResource("Medication/123")).thenReturn(Mono.empty());


            Mono<ResourceGroupWrapper> result = referenceHandler.getResourceGroupWrapperMono(null, true, "Medication/123");


            StepVerifier.create(result)
                    .expectErrorMatches(throwable ->
                            throwable instanceof RuntimeException &&
                                    throwable.getMessage().contains("Failed to fetch resource: received empty response")
                    )
                    .verify();

        }
    }

    @Nested
    class PatientResource {
        @Test
        public void shouldResolveReferenceSuccessfullyWithConsent() {

            PatientResourceBundle patientBundle = new PatientResourceBundle("123");

            Patient patientResource = new Patient();
            patientResource.setId("123");


            when(dataStore.fetchDomainResource("Patient/123")).thenReturn(Mono.just(patientResource));


            when(compartmentManager.isInCompartment(patientResource)).thenReturn(true);
            when(consentHandler.checkConsent(patientResource, patientBundle)).thenReturn(true);


            Mono<ResourceGroupWrapper> result = referenceHandler.getResourceGroupWrapperMono(patientBundle, true, "Patient/123");


            StepVerifier.create(result)
                    .assertNext(wrapper -> assertThat(wrapper).isNotNull())
                    .verifyComplete();
        }

        @Test
        public void shouldResolveReferenceSuccessfullyWithoutConsent() {

            PatientResourceBundle patientBundle = new PatientResourceBundle("123");

            Patient patientResource = new Patient();
            patientResource.setId("123");


            when(dataStore.fetchDomainResource("Patient/123")).thenReturn(Mono.just(patientResource));


            when(compartmentManager.isInCompartment(patientResource)).thenReturn(true);


            Mono<ResourceGroupWrapper> result = referenceHandler.getResourceGroupWrapperMono(patientBundle, false, "Patient/123");


            StepVerifier.create(result)
                    .assertNext(wrapper -> assertThat(wrapper).isNotNull())
                    .verifyComplete();
        }

        @Test
        public void ConsentViolatedExceptionShouldBeThrown() {
            PatientResourceBundle patientBundle = new PatientResourceBundle("123");
            Patient patientResource = new Patient();
            patientResource.setId("123");

            when(dataStore.fetchDomainResource("Patient/123")).thenReturn(Mono.just(patientResource));
            when(compartmentManager.isInCompartment(patientResource)).thenReturn(true);
            when(consentHandler.checkConsent(patientResource, patientBundle)).thenReturn(false);


            Mono<ResourceGroupWrapper> result = referenceHandler.getResourceGroupWrapperMono(patientBundle, true, "Patient/123");


            StepVerifier.create(result)
                    .expectErrorMatches(throwable ->
                            throwable instanceof ConsentViolatedException &&
                                    throwable.getMessage().contains("Consent Violated in Patient Resource")
                    )
                    .verify();

        }

        @Test
        public void failsPointingAtOtherPatient() {

            PatientResourceBundle patientBundle = new PatientResourceBundle("123");

            Patient patientResource = new Patient();
            patientResource.setId("False");


            when(dataStore.fetchDomainResource("Patient/123")).thenReturn(Mono.just(patientResource));


            when(compartmentManager.isInCompartment(patientResource)).thenReturn(true);


            Mono<ResourceGroupWrapper> result = referenceHandler.getResourceGroupWrapperMono(patientBundle, false, "Patient/123");


            StepVerifier.create(result)
                    .expectErrorMatches(throwable ->
                            throwable instanceof ReferenceToPatientException &&
                                    throwable.getMessage().contains("Patient loaded Reference belonging to other Patient")
                    )
                    .verify();
        }


    }


}
