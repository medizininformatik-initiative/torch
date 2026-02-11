package de.medizininformatikinitiative.torch.service;


import de.medizininformatikinitiative.torch.consent.ConsentValidator;
import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReferenceBundleLoaderTest {
    public static final String OBSERVATION_REF = "Observation/12";
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
        referenceWrapper = new ReferenceWrapper(referenceAttribute, List.of(OBSERVATION_REF), "Encounter1", "Encounter/123");
    }


    @Nested
    class TestChunkReferences {
        String REF_1 = "ref-1";
        String REF_2 = "ref-2";
        String REF_3 = "ref-3";

        @Test
        void testSingleChunk() {
            var refsPerLinkedGroup = List.of("Resource/" + REF_1, "Resource/" + REF_2, "Resource/" + REF_3);

            var chunks = referenceBundleLoader.chunkRefs(refsPerLinkedGroup, 10);

            assertThat(chunks).containsExactly(Set.of(REF_1, REF_2, REF_3));
        }

        @Test
        void withChunking() {
            var refsPerLinkedGroup = List.of("Resource/" + REF_1, "Resource/" + REF_2, "Resource/" + REF_3);

            var chunks = referenceBundleLoader.chunkRefs(refsPerLinkedGroup, 2);

            assertThat(chunks).containsExactly(
                    Set.of(REF_1, REF_2),
                    Set.of(REF_3));
        }

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

        List<String> refs = List.of("Patient/1", "Patient/2", "Patient/3");

        StepVerifier.create(referenceBundleLoader.fetchUnknownResources(refs, "linkedGroup", groupMap))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(maxActive.get())
                .as("Max concurrent executeBundle calls should be 1")
                .isEqualTo(1);
    }
}
