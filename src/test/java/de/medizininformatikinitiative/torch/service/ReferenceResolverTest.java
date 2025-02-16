package de.medizininformatikinitiative.torch.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

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

    private final String reference = "Patient/123";

    @Mock
    private ResourceGroupWrapper resourceWrapper;

    private ReferenceResolver referenceResolver;

    @BeforeEach
    void setUp() {
        referenceResolver = new ReferenceResolver(
                referenceExtractor, dataStore, profileMustHaveChecker,
                compartmentManager, consentHandler, attributeGroupMap, coreBundle
        );
    }

    @Test
    void handleReference_whenReferenceExistsInPatientBundle_shouldNotFetch() {
        AnnotatedAttribute annotatedAttribute = mock(AnnotatedAttribute.class);
        AnnotatedAttributeGroup attributeGroup = mock(AnnotatedAttributeGroup.class);
        DomainResource domainResource = mock(DomainResource.class);

        when(referenceWrapper.references()).thenReturn(List.of(reference));
        when(patientBundle.contains(reference)).thenReturn(true);
        when(patientBundle.get(reference)).thenReturn(Mono.just(resourceWrapper));
        when(referenceWrapper.refAttribute()).thenReturn(annotatedAttribute);
        when(referenceWrapper.refAttribute().linkedGroups()).thenReturn(List.of("group1"));
        when(attributeGroupMap.get("group1")).thenReturn(attributeGroup);
        when(resourceWrapper.resource()).thenReturn(domainResource);
        when(profileMustHaveChecker.fulfilled(domainResource, attributeGroup)).thenReturn(true);

        StepVerifier.create(referenceResolver.handleReference(referenceWrapper, Optional.of(patientBundle)))
                .expectNextMatches(resourceWrappers -> resourceWrappers.size() == 1)
                .verifyComplete();

        verify(profileMustHaveChecker).fulfilled(domainResource, attributeGroup);
        verify(patientBundle).get(reference);
        verifyNoInteractions(dataStore);
    }

    @Test
    void handleReference_whenReferenceExistsInCoreBundle_shouldNotFetch() {
        AnnotatedAttribute annotatedAttribute = mock(AnnotatedAttribute.class);
        AnnotatedAttributeGroup attributeGroup = mock(AnnotatedAttributeGroup.class);
        DomainResource domainResource = mock(DomainResource.class);

        when(referenceWrapper.references()).thenReturn(List.of(reference));
        when(coreBundle.contains(reference)).thenReturn(true);
        when(coreBundle.get(reference)).thenReturn(Mono.just(resourceWrapper));
        when(referenceWrapper.refAttribute()).thenReturn(annotatedAttribute);
        when(referenceWrapper.refAttribute().linkedGroups()).thenReturn(List.of("group1"));
        when(attributeGroupMap.get("group1")).thenReturn(attributeGroup);
        when(resourceWrapper.resource()).thenReturn(domainResource);
        when(profileMustHaveChecker.fulfilled(domainResource, attributeGroup)).thenReturn(true);

        StepVerifier.create(referenceResolver.handleReference(referenceWrapper, Optional.empty()))
                .expectNextMatches(resourceWrappers -> resourceWrappers.size() == 1)
                .verifyComplete();

        verify(profileMustHaveChecker).fulfilled(domainResource, attributeGroup);
        verify(coreBundle).get(reference);
        verifyNoInteractions(dataStore);
    }

    @Test
    void handleReference_whenReferenceIsNotInPatientOrCoreBundle_shouldFetch() {
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

        StepVerifier.create(referenceResolver.handleReference(referenceWrapper, Optional.empty()))
                .expectNextMatches(resourceWrappers -> resourceWrappers.size() == 1)
                .verifyComplete();

        verify(profileMustHaveChecker).fulfilled(domainResource, attributeGroup);
        verify(dataStore).fetchResourceByReference(reference);
    }

    @Test
    void handleReference_whenReferenceIsNotInPatientOrCoreBundleAndMustHaveFails_shouldReturnEmpty() {
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
        StepVerifier.create(referenceResolver.handleReference(referenceWrapper, Optional.empty()))
                .expectNextMatches(list -> list.isEmpty()) // Expecting an empty list
                .verifyComplete(); // Expecting the Mono to complete

        // Verifying interactions
        verify(profileMustHaveChecker).fulfilled(domainResource, attributeGroup);
        verify(dataStore).fetchResourceByReference(reference);
    }

}
