package de.medizininformatikinitiative.torch.util;

import de.medizininformatikinitiative.torch.consent.ConsentValidator;
import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.service.DataStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReferenceHandlerTest {

    @Mock
    private DataStore dataStore;

    @Mock
    private ProfileMustHaveChecker profileMustHaveChecker;

    @Mock
    private CompartmentManager compartmentManager;

    @Mock
    private ConsentValidator consentValidator;

    @InjectMocks
    private ReferenceHandler referenceHandler;

    @BeforeEach
    void setUp() {
        referenceHandler = new ReferenceHandler(profileMustHaveChecker);
    }

/*
    @Nested
    class CoreResource {
        @Test
        void shouldResolveReferenceSuccessfully() {

            Resource coreResource = mock(Resource.class);


            when(dataStore.fetchResourceByReference("Medication/123")).thenReturn(Mono.just(coreResource));


            when(compartmentManager.isInCompartment(coreResource)).thenReturn(false);


            Mono<Resource> result = referenceHandler.getResourceMono(null, true, "Medication/123");


            StepVerifier.create(result).assertNext(resource -> assertThat(resource).isNotNull()).verifyComplete();
        }

        @Test
        void noCoreResourceFound() {

            Resource coreResource = mock(Resource.class);


            when(dataStore.fetchResourceByReference("Medication/123")).thenReturn(Mono.just(coreResource));


            when(compartmentManager.isInCompartment(coreResource)).thenReturn(true);


            Mono<Resource> result = referenceHandler.getResourceMono(null, true, "Medication/123");


            StepVerifier.create(result).expectErrorMatches(throwable -> throwable instanceof ReferenceToPatientException && throwable.getMessage().contains("Patient Resource referenced in Core Bundle")).verify();
        }

        @Test
        void shouldReturnEmptyWhenEmptyRespource() {
            when(dataStore.fetchResourceByReference("Medication/123")).thenReturn(Mono.empty());

            Mono<Resource> result = referenceHandler.getResourceMono(null, true, "Medication/123");

            StepVerifier.create(result).expectComplete().verify();
        }

        @Test
        void shouldReturnErrorOnConnectionError() {
            // Simulate a connection error (like when the host is unreachable)
            WebClientRequestException connectionException = new WebClientRequestException(
                    new UnknownHostException("Host not found"),    // Cause of the error
                    HttpMethod.GET,                                // HTTP method used
                    URI.create("http://localhost/Medication/123"),  // URI of the request (can be any valid URI)
                    HttpHeaders.EMPTY                              // Headers (optional, can be empty)
            );

            // Mock the behavior of dataStore.fetchResourceByReference to return the exception
            when(dataStore.fetchResourceByReference("Medication/123")).thenReturn(Mono.error(connectionException));

            Mono<Resource> result = referenceHandler.getResourceMono(null, true, "Medication/123");

            // Now expecting an error (not complete)
            StepVerifier.create(result)
                    .expectError(WebClientRequestException.class)  // Expect the WebClientRequestException error type
                    .verify();
        }

    }

    @Nested
    class PatientResource {
        @Test
        void shouldResolveReferenceSuccessfullyWithConsent() {

            PatientResourceBundle patientBundle = new PatientResourceBundle("123");

            Patient patientResource = new Patient();
            patientResource.setId("123");


            when(dataStore.fetchResourceByReference("Patient/123")).thenReturn(Mono.just(patientResource));


            when(compartmentManager.isInCompartment(patientResource)).thenReturn(true);
            when(consentValidator.checkConsent(patientResource, patientBundle)).thenReturn(true);


            Mono<Resource> result = referenceHandler.getResourceMono(patientBundle, true, "Patient/123");


            StepVerifier.create(result).assertNext(wrapper -> assertThat(wrapper).isNotNull()).verifyComplete();
        }

        @Test
        void shouldResolveReferenceSuccessfullyWithoutConsent() {

            PatientResourceBundle patientBundle = new PatientResourceBundle("123");

            Patient patientResource = new Patient();
            patientResource.setId("123");


            when(dataStore.fetchResourceByReference("Patient/123")).thenReturn(Mono.just(patientResource));


            when(compartmentManager.isInCompartment(patientResource)).thenReturn(true);


            Mono<Resource> result = referenceHandler.getResourceMono(patientBundle, false, "Patient/123");


            StepVerifier.create(result).assertNext(wrapper -> assertThat(wrapper).isNotNull()).verifyComplete();
        }

        @Test
        void ConsentViolatedExceptionShouldBeThrown() {
            PatientResourceBundle patientBundle = new PatientResourceBundle("123");
            Patient patientResource = new Patient();
            patientResource.setId("123");

            when(dataStore.fetchResourceByReference("Patient/123")).thenReturn(Mono.just(patientResource));
            when(compartmentManager.isInCompartment(patientResource)).thenReturn(true);
            when(consentValidator.checkConsent(patientResource, patientBundle)).thenReturn(false);


            Mono<Resource> result = referenceHandler.getResourceMono(patientBundle, true, "Patient/123");


            StepVerifier.create(result).expectErrorMatches(throwable -> throwable instanceof ConsentViolatedException && throwable.getMessage().contains("Consent Violated in Patient Resource")).verify();

        }

        @Test
        void failsPointingAtOtherPatient() {

            PatientResourceBundle patientBundle = new PatientResourceBundle("123");

            Patient patientResource = new Patient();
            patientResource.setId("False");


            when(dataStore.fetchResourceByReference("Patient/123")).thenReturn(Mono.just(patientResource));


            when(compartmentManager.isInCompartment(patientResource)).thenReturn(true);


            Mono<Resource> result = referenceHandler.getResourceMono(patientBundle, false, "Patient/123");


            StepVerifier.create(result).expectErrorMatches(throwable -> throwable instanceof ReferenceToPatientException && throwable.getMessage().contains("Patient loaded reference belonging to another patient")).verify();
        }

        @Test
        void shouldLogAndReturnEmptyMonoOnError() {
            // Simulate a connection error (like when the host is unreachable)
            when(dataStore.fetchResourceByReference("Broken/999")).thenReturn(Mono.error(new RuntimeException("Connection failed")));

            Mono<Resource> result = referenceHandler.getResourceMono(null, true, "Broken/999");

            // Expecting a RuntimeException with the message "Connection failed"
            StepVerifier.create(result)
                    .expectErrorMatches(throwable ->
                            throwable instanceof RuntimeException &&
                                    throwable.getMessage().contains("Connection failed")
                    )
                    .verify();
        }


    }
*/

}
