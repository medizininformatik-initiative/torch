package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.exceptions.ReferenceToPatientException;
import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.management.ConsentHandler;
import de.medizininformatikinitiative.torch.model.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.ReferenceWrapper;
import de.medizininformatikinitiative.torch.model.ResourceBundle;
import de.medizininformatikinitiative.torch.model.ResourceGroupWrapper;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.util.ProfileMustHaveChecker;
import de.medizininformatikinitiative.torch.util.ReferenceExtractor;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReferenceResolverTest {

    @Mock
    private ReferenceExtractor referenceExtractor;

    @Mock
    private DataStore dataStore;

    @Mock
    private ProfileMustHaveChecker profileMustHaveChecker;

    @Mock
    private CompartmentManager compartmentManager;

    @Mock
    private ConsentHandler consentHandler;

    @Mock
    private Map<String, AnnotatedAttributeGroup> attributeGroupMap;

    @Mock
    private ResourceBundle coreBundle;

    @Mock
    private PatientResourceBundle patientBundle;

    @Mock
    private ReferenceWrapper referenceWrapper;

    @Mock
    private DomainResource domainResource;


    private final String reference = "Patient/123";

    @Mock
    private ResourceGroupWrapper resourceGroupWrapper;

    private ReferenceResolver referenceResolver;

    @BeforeEach
    void setUp() {
        referenceResolver = new ReferenceResolver(
                referenceExtractor, dataStore, profileMustHaveChecker,
                compartmentManager, consentHandler, attributeGroupMap
        );
    }

    @Test
    void resolveCoreBundle_shouldCompleteWithoutErrors_whenCoreBundleIsEmpty() {
        when(coreBundle.values()).thenReturn(Collections.emptyList());

        StepVerifier.create(referenceResolver.resolveCoreBundle(coreBundle))
                .verifyComplete();

        verifyNoInteractions(dataStore, compartmentManager, consentHandler);
    }

    @Test
    void resolveCoreBundle_shouldProcessCoreBundleReferences() throws MustHaveViolatedException {
        when(coreBundle.values()).thenReturn(List.of(resourceGroupWrapper));
        when(referenceExtractor.extract(resourceGroupWrapper)).thenReturn(Collections.emptyList());

        StepVerifier.create(referenceResolver.resolveCoreBundle(coreBundle))
                .verifyComplete();

        verify(referenceExtractor).extract(resourceGroupWrapper);
    }

    @Test
    void resolvePatient_shouldCompleteWithoutErrors_whenPatientBundleIsEmpty() {
        when(patientBundle.values()).thenReturn(Collections.emptyList());

        StepVerifier.create(referenceResolver.resolvePatient(patientBundle, coreBundle, false))
                .expectNext(patientBundle)
                .verifyComplete();

        verifyNoInteractions(dataStore, compartmentManager, consentHandler);
    }

    @Test
    void resolvePatient_shouldProcessPatientBundleReferences() throws MustHaveViolatedException {
        when(patientBundle.values()).thenReturn(List.of(resourceGroupWrapper));
        when(referenceExtractor.extract(resourceGroupWrapper)).thenReturn(Collections.emptyList());

        StepVerifier.create(referenceResolver.resolvePatient(patientBundle, coreBundle, true))
                .expectNext(patientBundle)
                .verifyComplete();

        verify(referenceExtractor).extract(resourceGroupWrapper);
    }


    @Test
    void handleReference_whenPatientReferenceExistsInPatientBundle_shouldNotFetch() {
        AnnotatedAttribute annotatedAttribute = mock(AnnotatedAttribute.class);
        AnnotatedAttributeGroup attributeGroup = mock(AnnotatedAttributeGroup.class);
        DomainResource domainResource = mock(DomainResource.class);

        when(referenceWrapper.references()).thenReturn(List.of(reference));
        when(patientBundle.contains(reference)).thenReturn(true);
        when(patientBundle.get(reference)).thenReturn(Mono.just(resourceGroupWrapper));
        when(referenceWrapper.refAttribute()).thenReturn(annotatedAttribute);
        when(referenceWrapper.refAttribute().linkedGroups()).thenReturn(List.of("group1"));
        when(attributeGroupMap.get("group1")).thenReturn(attributeGroup);
        when(resourceGroupWrapper.resource()).thenReturn(domainResource);
        when(profileMustHaveChecker.fulfilled(domainResource, attributeGroup)).thenReturn(true);

        StepVerifier.create(referenceResolver.handleReference(referenceWrapper, Optional.of(patientBundle), coreBundle, false))
                .expectNextMatches(resourceWrappers -> resourceWrappers.size() == 1)
                .verifyComplete();

        verify(profileMustHaveChecker).fulfilled(domainResource, attributeGroup);
        verify(patientBundle).get(reference);
        verifyNoInteractions(dataStore);
    }

    @Test
    void handleReference_whenPatientReferenceExistsInCoreBundle_shouldNotFetch() {
        AnnotatedAttribute annotatedAttribute = mock(AnnotatedAttribute.class);
        AnnotatedAttributeGroup attributeGroup = mock(AnnotatedAttributeGroup.class);
        DomainResource domainResource = mock(DomainResource.class);

        when(referenceWrapper.references()).thenReturn(List.of(reference));
        when(coreBundle.contains(reference)).thenReturn(true);
        when(coreBundle.get(reference)).thenReturn(Mono.just(resourceGroupWrapper));
        when(referenceWrapper.refAttribute()).thenReturn(annotatedAttribute);
        when(referenceWrapper.refAttribute().linkedGroups()).thenReturn(List.of("group1"));
        when(attributeGroupMap.get("group1")).thenReturn(attributeGroup);
        when(resourceGroupWrapper.resource()).thenReturn(domainResource);
        when(profileMustHaveChecker.fulfilled(domainResource, attributeGroup)).thenReturn(true);

        StepVerifier.create(referenceResolver.handleReference(referenceWrapper, Optional.empty(), coreBundle, true))
                .expectNextMatches(resourceWrappers -> resourceWrappers.size() == 1)
                .verifyComplete();

        verify(profileMustHaveChecker).fulfilled(domainResource, attributeGroup);
        verify(coreBundle).get(reference);
        verifyNoInteractions(dataStore);
    }

    @Test
    void handleReference_whenPatientReferenceIsNotInPatientOrCoreBundle_shouldFetch() {
        AnnotatedAttribute annotatedAttribute = mock(AnnotatedAttribute.class);
        AnnotatedAttributeGroup attributeGroup = mock(AnnotatedAttributeGroup.class);
        DomainResource domainResource = mock(DomainResource.class);

        when(referenceWrapper.references()).thenReturn(List.of(reference));
        when(coreBundle.contains(reference)).thenReturn(false);
        when(dataStore.fetchResourceByReference(reference)).thenReturn(Mono.just(domainResource));
        when(referenceWrapper.refAttribute()).thenReturn(annotatedAttribute);
        when(referenceWrapper.refAttribute().linkedGroups()).thenReturn(List.of("group1"));
        when(attributeGroupMap.get("group1")).thenReturn(attributeGroup);
        when(profileMustHaveChecker.fulfilled(domainResource, attributeGroup)).thenReturn(true);

        StepVerifier.create(referenceResolver.handleReference(referenceWrapper, Optional.empty(), coreBundle, false))
                .expectNextMatches(resourceWrappers -> resourceWrappers.size() == 1)
                .verifyComplete();

        verify(profileMustHaveChecker).fulfilled(domainResource, attributeGroup);
        verify(dataStore).fetchResourceByReference(reference);
    }

    @Test
    void handleReference_whenPatientReferenceIsNotInPatientOrCoreBundleAndMustHaveFails_shouldReturnEmpty() {
        // Mocking dependencies
        AnnotatedAttribute annotatedAttribute = mock(AnnotatedAttribute.class);
        AnnotatedAttributeGroup attributeGroup = mock(AnnotatedAttributeGroup.class);
        DomainResource domainResource = mock(DomainResource.class);

        // Setting up mocks
        when(referenceWrapper.references()).thenReturn(List.of(reference));
        when(coreBundle.contains(reference)).thenReturn(false);
        when(dataStore.fetchResourceByReference(reference)).thenReturn(Mono.just(domainResource));
        when(referenceWrapper.refAttribute()).thenReturn(annotatedAttribute);
        when(referenceWrapper.refAttribute().mustHave()).thenReturn(true);
        when(referenceWrapper.refAttribute().linkedGroups()).thenReturn(List.of("group1"));
        when(attributeGroupMap.get("group1")).thenReturn(attributeGroup);
        when(profileMustHaveChecker.fulfilled(domainResource, attributeGroup)).thenReturn(false);

        // Verifying the behavior
        StepVerifier.create(referenceResolver.handleReference(referenceWrapper, Optional.empty(), coreBundle, false))
                .expectNextMatches(List::isEmpty) // Expecting an empty list
                .verifyComplete(); // Expecting the Mono to complete

        // Verifying interactions
        verify(profileMustHaveChecker).fulfilled(domainResource, attributeGroup);
        verify(dataStore).fetchResourceByReference(reference);
    }


    @Test
    void shouldReturnResourceIfNotInCompartment() {
        when(dataStore.fetchResourceByReference(reference)).thenReturn(Mono.just(domainResource));
        when(compartmentManager.isInCompartment(domainResource)).thenReturn(false);

        StepVerifier.create(referenceResolver.getResourceGroupWrapperMono(Optional.empty(), true, reference))
                .expectNextMatches(wrapper -> wrapper.resource().equals(domainResource))
                .verifyComplete();

        verify(compartmentManager).isInCompartment(domainResource);
        verifyNoInteractions(consentHandler, patientBundle);
    }

    @Test
    void shouldThrowExceptionIfResourceInCompartmentButNoPatientBundle() {
        when(dataStore.fetchResourceByReference(reference)).thenReturn(Mono.just(domainResource));
        when(compartmentManager.isInCompartment(domainResource)).thenReturn(true);

        StepVerifier.create(referenceResolver.getResourceGroupWrapperMono(Optional.empty(), true, reference))
                .verifyError(ReferenceToPatientException.class);
    }

    @Test
    void shouldReturnResourceIfInCompartmentAndConsentNotApplied() {
        when(dataStore.fetchResourceByReference(reference)).thenReturn(Mono.just(domainResource));
        when(compartmentManager.isInCompartment(domainResource)).thenReturn(true);

        StepVerifier.create(referenceResolver.getResourceGroupWrapperMono(Optional.of(patientBundle), false, reference))
                .expectNextMatches(wrapper -> wrapper.resource().equals(domainResource))
                .verifyComplete();
    }

    @Test
    void shouldReturnResourceIfConsentAppliedAndAllowed() throws PatientIdNotFoundException {
        // Mocking dependencies
        DomainResource domainResource = new Patient();
        domainResource.setId("123");

        // Mocking return values
        when(dataStore.fetchResourceByReference(reference)).thenReturn(Mono.just(domainResource));
        when(compartmentManager.isInCompartment(domainResource)).thenReturn(true);
        when(patientBundle.patientId()).thenReturn("123");
        when(consentHandler.checkConsent(domainResource, patientBundle)).thenReturn(true);

        // Execute test
        StepVerifier.create(referenceResolver.getResourceGroupWrapperMono(Optional.of(patientBundle), true, reference))
                .expectNextMatches(wrapper -> wrapper.resource().equals(domainResource))
                .verifyComplete();

        // Verify interactions
        verify(consentHandler).checkConsent(domainResource, patientBundle);
    }

    @Test
    void shouldReturnEmptyIfConsentAppliedAndDenied() {
        when(dataStore.fetchResourceByReference(reference)).thenReturn(Mono.just(domainResource));
        when(compartmentManager.isInCompartment(domainResource)).thenReturn(true);
        when(consentHandler.checkConsent(domainResource, patientBundle)).thenReturn(false);

        StepVerifier.create(referenceResolver.getResourceGroupWrapperMono(Optional.of(patientBundle), true, reference))
                .verifyComplete();

        verify(consentHandler).checkConsent(domainResource, patientBundle);
    }

}
