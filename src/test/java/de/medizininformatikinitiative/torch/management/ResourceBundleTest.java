package de.medizininformatikinitiative.torch.management;

import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.management.ResourceAttribute;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import de.medizininformatikinitiative.torch.model.management.ResourceGroupWrapper;
import de.medizininformatikinitiative.torch.util.ResourceUtils;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceBundleTest {

    Patient patient1 = new Patient();
    Patient patient2 = new Patient();
    Patient patient3 = new Patient();
    ResourceGroupWrapper wrapper1;
    ResourceGroupWrapper wrapper2;
    ResourceGroupWrapper wrapper3;
    ResourceGroupWrapper wrapper1Mod;
    String id;

    @BeforeEach
    void setUp() {
        patient1.setId("patient1");
        patient2.setId("patient2");
        patient3.setId("patient3");
        Set<String> attributeGroups1 = Set.of("group1", "group2");
        Set<String> attributeGroups2 = Set.of("group3");
        wrapper1 = new ResourceGroupWrapper(patient1, attributeGroups1);
        wrapper2 = new ResourceGroupWrapper(patient2, attributeGroups1);
        wrapper3 = new ResourceGroupWrapper(patient3, attributeGroups1);
        wrapper1Mod = new ResourceGroupWrapper(patient1, attributeGroups2);
        id = ResourceUtils.getRelativeURL(patient1);
    }

    @Nested
    class RetrievalTests {
        @Test
        void getMatch() {
            ResourceBundle cache = new ResourceBundle();
            cache.mergingPut(wrapper1);

            Mono<Resource> result = cache.get(id);

            StepVerifier.create(result)
                    .expectNext(patient1)
                    .verifyComplete();
        }

        @Test
        void isEmpty() {
            ResourceBundle cache = new ResourceBundle();
            Mono<?> result = cache.get(id);
            StepVerifier.create(result).verifyComplete();
        }
    }


    @Nested
    class RemovalTests {
        @Test
        void removeExistingEntry() {
            ResourceBundle cache = new ResourceBundle();
            cache.mergingPut(wrapper1);
            cache.remove(id);

            Mono<Resource> result = cache.get(id);
            StepVerifier.create(result).verifyComplete();
        }
    }


    @Nested
    class BidirectionalRelationMapping {
        private static final AnnotatedAttribute ATTRIBUTE = new AnnotatedAttribute("test", "test", "test", false);

        private static final ResourceAttribute ATTRIBUTE_1 = new ResourceAttribute("attribute1", ATTRIBUTE);
        private static final ResourceAttribute ATTRIBUTE_2 = new ResourceAttribute("attribute2", ATTRIBUTE);
        private static final ResourceAttribute ATTRIBUTE_3 = new ResourceAttribute("attribute3", ATTRIBUTE);
        private static final ResourceAttribute ATTRIBUTE_4 = new ResourceAttribute("attribute4", ATTRIBUTE);
        private static final ResourceAttribute ATTRIBUTE_5 = new ResourceAttribute("attribute5", ATTRIBUTE);
        private static final ResourceAttribute ATTRIBUTE_6 = new ResourceAttribute("attribute6", ATTRIBUTE);

        private static final ResourceGroup CHILD_1 = new ResourceGroup("child1", "group1");
        private static final ResourceGroup CHILD_2 = new ResourceGroup("child2", "group1");
        private static final ResourceGroup PARENT_1 = new ResourceGroup("parent1", "group1");
        private static final ResourceGroup PARENT_2 = new ResourceGroup("parent2", "group2");

        private ResourceBundle cache;

        @BeforeEach
        void setUp() {
            cache = new ResourceBundle();
        }

        @Test
        void shouldAddAttributeToChild_whenNoExistingMappings() {
            // When
            cache.addAttributeToChild(ATTRIBUTE_1, CHILD_1);

            // Then
            assertThat(cache.resourceAttributeToChildResourceGroup())
                    .containsKey(ATTRIBUTE_1);

            assertThat(cache.resourceAttributeToChildResourceGroup().get(ATTRIBUTE_1))
                    .containsExactly(CHILD_1);

            assertThat(cache.childToAttributeMap())
                    .containsKey(CHILD_1);

            assertThat(cache.childToAttributeMap().get(CHILD_1))
                    .containsExactly(ATTRIBUTE_1);
        }

        @Test
        void shouldAddAttributeToParent_whenNoExistingMappings() {
            // When
            cache.addAttributeToParent(ATTRIBUTE_2, PARENT_1);

            // Then
            assertThat(cache.resourceAttributeToParentResourceGroup())
                    .containsKey(ATTRIBUTE_2);

            assertThat(cache.resourceAttributeToParentResourceGroup().get(ATTRIBUTE_2))
                    .containsExactly(PARENT_1);

            assertThat(cache.parentToAttributesMap())
                    .containsKey(PARENT_1);

            assertThat(cache.parentToAttributesMap().get(PARENT_1))
                    .containsExactly(ATTRIBUTE_2);
        }

        @Test
        void shouldCorrectlyHandleAddingMultipleChildrenToSameAttribute() {
            // When
            cache.addAttributeToChild(ATTRIBUTE_3, CHILD_1);
            cache.addAttributeToChild(ATTRIBUTE_3, CHILD_2);

            // Then
            assertThat(cache.resourceAttributeToChildResourceGroup())
                    .containsKey(ATTRIBUTE_3);

            assertThat(cache.resourceAttributeToChildResourceGroup().get(ATTRIBUTE_3))
                    .containsExactlyInAnyOrder(CHILD_1, CHILD_2);

            assertThat(cache.childToAttributeMap())
                    .containsKeys(CHILD_1, CHILD_2);

            assertThat(cache.childToAttributeMap().get(CHILD_1))
                    .containsExactly(ATTRIBUTE_3);

            assertThat(cache.childToAttributeMap().get(CHILD_2))
                    .containsExactly(ATTRIBUTE_3);
        }

        @Test
        void shouldCorrectlyHandleAddingMultipleParentsToSameAttribute() {
            // When
            cache.addAttributeToParent(ATTRIBUTE_4, PARENT_1);
            cache.addAttributeToParent(ATTRIBUTE_4, PARENT_2);

            // Then
            assertThat(cache.resourceAttributeToParentResourceGroup())
                    .containsKey(ATTRIBUTE_4);

            assertThat(cache.resourceAttributeToParentResourceGroup().get(ATTRIBUTE_4))
                    .containsExactlyInAnyOrder(PARENT_1, PARENT_2);

            assertThat(cache.parentToAttributesMap())
                    .containsKeys(PARENT_1, PARENT_2);

            assertThat(cache.parentToAttributesMap().get(PARENT_1))
                    .containsExactly(ATTRIBUTE_4);

            assertThat(cache.parentToAttributesMap().get(PARENT_2))
                    .containsExactly(ATTRIBUTE_4);
        }

        @Test
        void shouldCorrectlyHandlePreExistingSets() {
            // Pre-fill with another attribute
            cache.resourceAttributeToChildResourceGroup()
                    .computeIfAbsent(ATTRIBUTE_5, k -> ConcurrentHashMap.newKeySet())
                    .add(CHILD_2);

            cache.resourceAttributeToParentResourceGroup()
                    .computeIfAbsent(ATTRIBUTE_5, k -> ConcurrentHashMap.newKeySet())
                    .add(PARENT_2);

            // When
            cache.addAttributeToChild(ATTRIBUTE_5, CHILD_1);
            cache.addAttributeToParent(ATTRIBUTE_5, PARENT_1);

            // Then
            assertThat(cache.resourceAttributeToChildResourceGroup().get(ATTRIBUTE_5))
                    .containsExactlyInAnyOrder(CHILD_1, CHILD_2);

            assertThat(cache.resourceAttributeToParentResourceGroup().get(ATTRIBUTE_5))
                    .containsExactlyInAnyOrder(PARENT_1, PARENT_2);
        }

        @Test
        void shouldBeThreadSafe_whenAddingMultipleEntriesConcurrently() throws InterruptedException {
            // When running in parallel
            Thread thread1 = new Thread(() -> cache.addAttributeToChild(ATTRIBUTE_6, CHILD_1));
            Thread thread2 = new Thread(() -> cache.addAttributeToParent(ATTRIBUTE_6, PARENT_1));

            thread1.start();
            thread2.start();
            thread1.join();
            thread2.join();

            // Then - No race conditions should occur
            assertThat(cache.resourceAttributeToChildResourceGroup().get(ATTRIBUTE_6))
                    .contains(CHILD_1);

            assertThat(cache.resourceAttributeToParentResourceGroup().get(ATTRIBUTE_6))
                    .contains(PARENT_1);
        }
    }


}