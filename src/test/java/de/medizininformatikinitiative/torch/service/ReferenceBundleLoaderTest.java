package de.medizininformatikinitiative.torch.service;


import de.medizininformatikinitiative.torch.consent.ConsentValidator;
import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionId;
import de.medizininformatikinitiative.torch.model.management.ReferenceWrapper;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReferenceBundleLoaderTest {
    public static final ExtractionId OBSERVATION_REF = ExtractionId.fromRelativeUrl("Observation/12");
    int pageCount = 4;
    @Mock
    private CompartmentManager compartmentManager;
    @Mock
    private DataStore dataStore;
    @Mock
    private ConsentValidator consentValidator;
    private ReferenceBundleLoader referenceBundleLoader;

    AnnotatedAttribute referenceAttribute = new AnnotatedAttribute("Encounter.evidence", "Encounter.evidence", true, List.of("Medication1"));
    ReferenceWrapper referenceWrapper;
    DseMappingTreeBase mappingTree = null;


    @BeforeEach
    void setup() {
        referenceBundleLoader = new ReferenceBundleLoader(compartmentManager, dataStore, consentValidator, pageCount, mappingTree);
        referenceWrapper = new ReferenceWrapper(referenceAttribute, List.of(OBSERVATION_REF), "Encounter1", ExtractionId.fromRelativeUrl("Encounter/123"));
    }

    @Test
    void fetchUnknownResources_skipsMalformedFetchedResourcesWithoutId() {
        // given
        AnnotatedAttributeGroup mockGroup = mock(AnnotatedAttributeGroup.class);
        when(mockGroup.resourceType()).thenReturn("Patient");
        when(mockGroup.queries(any(), any())).thenReturn(List.of()); // no entries needed for this test
        Map<String, AnnotatedAttributeGroup> groupMap = Map.of("linkedGroup", mockGroup);

        // A fetched resource missing an id => ResourceUtils.getRelativeURL(resource) should fail / be considered malformed
        Resource malformed = new org.hl7.fhir.r4.model.Patient(); // no id set

        // A fetched resource with a valid id
        org.hl7.fhir.r4.model.Patient ok = new org.hl7.fhir.r4.model.Patient();
        ok.setId("Patient/42"); // ensure idPart exists

        when(dataStore.executeBundle(any()))
                .thenReturn(Mono.just(List.of(malformed, ok)));

        List<ExtractionId> refs = List.of(
                ExtractionId.fromRelativeUrl("Patient/1"),
                ExtractionId.fromRelativeUrl("Patient/2")
        );

        // when / then
        StepVerifier.create(referenceBundleLoader.fetchUnknownResources(refs, "linkedGroup", groupMap))
                .assertNext(resources -> {
                    assertThat(resources).hasSize(1);
                    assertThat(resources.getFirst().getResourceType().name()).isEqualTo("Patient");
                    assertThat(resources.getFirst().getIdElement().getIdPart()).isEqualTo("42");
                })
                .verifyComplete();
    }

    @Test
    void maxInFlightIsOne() {
        referenceBundleLoader = new ReferenceBundleLoader(compartmentManager, dataStore, consentValidator, 1, mappingTree);
        AnnotatedAttributeGroup mockGroup = mock(AnnotatedAttributeGroup.class);
        when(mockGroup.resourceType()).thenReturn("Patient");
        when(mockGroup.queries(any(), any())).thenReturn(List.of());
        Map<String, AnnotatedAttributeGroup> groupMap = Map.of("linkedGroup", mockGroup);

        AtomicInteger activeCalls = new AtomicInteger(0);
        AtomicInteger maxActive = new AtomicInteger(0);

        when(dataStore.executeBundle(any())).thenAnswer(inv -> {
            // Increment happens when the Mono is created/subscribed
            int current = activeCalls.incrementAndGet();
            maxActive.updateAndGet(prev -> Math.max(prev, current));

            return Mono.just(List.<Resource>of())
                    .delayElement(Duration.ofMillis(50))
                    .flatMap(result -> {
                        activeCalls.decrementAndGet();
                        return Mono.just(result);
                    })
                    .doOnError(e -> activeCalls.decrementAndGet())
                    .doOnCancel(activeCalls::decrementAndGet);
        });

        List<ExtractionId> refs = Stream.of("Patient/1", "Patient/2", "Patient/3").map(ExtractionId::fromRelativeUrl).toList();

        StepVerifier.create(referenceBundleLoader.fetchUnknownResources(refs, "linkedGroup", groupMap))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(maxActive.get())
                .as("Max concurrent executeBundle calls should be 1")
                .isEqualTo(1);
    }

    @Nested
    class TestChunkReferences {
        String REF_1 = "ref-1";
        String REF_2 = "ref-2";
        String REF_3 = "ref-3";

        @Test
        void testSingleChunk() {
            var refsPerLinkedGroup = Stream.of("Resource/" + REF_1, "Resource/" + REF_2, "Resource/" + REF_3).map(ExtractionId::fromRelativeUrl).toList();

            var chunks = referenceBundleLoader.chunkRefs(refsPerLinkedGroup, 10);

            assertThat(chunks).containsExactly(Set.of(REF_1, REF_2, REF_3));
        }

        @Test
        void withChunking() {
            var refsPerLinkedGroup = Stream.of("Resource/" + REF_1, "Resource/" + REF_2, "Resource/" + REF_3).map(ExtractionId::fromRelativeUrl).toList();

            var chunks = referenceBundleLoader.chunkRefs(refsPerLinkedGroup, 2);

            assertThat(chunks).containsExactly(
                    Set.of(REF_1, REF_2),
                    Set.of(REF_3));
        }

    }
}
