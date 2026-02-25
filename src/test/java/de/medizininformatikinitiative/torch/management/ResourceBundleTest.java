package de.medizininformatikinitiative.torch.management;

import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionId;
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

import java.util.Optional;
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
    ExtractionId id;

    @BeforeEach
    void setUp() {
        patient1.setId("http://blaze.com/fhir/Patient/patient1");
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
    class PutTests {

        @Test
        @SuppressWarnings("ConstantValue")
        void putWrapper_null_shouldReturnFalse() {
            ResourceBundle bundle = new ResourceBundle();
            boolean result = bundle.put((ResourceGroupWrapper) null);
            assertThat(result).isFalse();
            assertThat(bundle.cache()).isEmpty();
        }

        @Test
        @SuppressWarnings("ConstantValue")
        void putResource_null_shouldReturnFalse() {
            ResourceBundle bundle = new ResourceBundle();
            boolean result = bundle.put((Resource) null);
            assertThat(result).isFalse();
            assertThat(bundle.cache()).isEmpty();
        }

        @Test
        void putResource_invalidId_shouldReturnFalse() {
            ResourceBundle bundle = new ResourceBundle();
            Patient patient = new Patient();
            patient.setId("");
            boolean result = bundle.put(patient);
            assertThat(result).isFalse();
            assertThat(bundle.cache()).isEmpty();
        }

        @Test
        void putWrapper_shouldStoreResourceInCache_andRegisterValidGroups() {
            ResourceBundle bundle = new ResourceBundle();

            boolean result = bundle.put(wrapper1);

            assertThat(result).isTrue();
            assertThat(bundle.cache()).containsKey(id);
            assertThat(bundle.get(id)).isEqualTo(Optional.of(patient1));

            // groups from wrapper1: group1, group2
            assertThat(bundle.getValidResourceGroups())
                    .contains(new ResourceGroup(id, "group1"))
                    .contains(new ResourceGroup(id, "group2"));
        }

        @Test
        void putWrapper_invalidId_shouldReturnFalse() {
            ResourceBundle bundle = new ResourceBundle();
            Patient patient = new Patient();
            patient.setId("");
            ResourceGroupWrapper wrapper = new ResourceGroupWrapper(patient, Set.of("group1"));
            boolean result = bundle.put(wrapper);
            assertThat(result).isFalse();
            assertThat(bundle.cache()).isEmpty();
        }

        @Test
        void putWrapper_sameResourceDifferentGroups_shouldAddNewGroups_notRemoveOldOnes() {
            ResourceBundle bundle = new ResourceBundle();

            bundle.put(wrapper1);     // group1, group2
            bundle.put(wrapper1Mod);  // group3

            // resource still there
            assertThat(bundle.get(id)).isEqualTo(Optional.of(patient1));

            // all groups should be present (union semantics)
            assertThat(bundle.getValidResourceGroups())
                    .contains(new ResourceGroup(id, "group1"))
                    .contains(new ResourceGroup(id, "group2"))
                    .contains(new ResourceGroup(id, "group3"));
        }

        @Test
        void putResource_validRelativeId_shouldStoreResourceAndGroup() {
            ResourceBundle bundle = new ResourceBundle();

            boolean result = bundle.put(patient2, "group1", true);

            assertThat(result).isTrue();

            ExtractionId id2 = ResourceUtils.getRelativeURL(patient2);
            assertThat(bundle.get(id2)).isEqualTo(Optional.of(patient2));
            assertThat(bundle.getValidResourceGroups()).contains(new ResourceGroup(id2, "group1"));
        }

        @Test
        void putIgnoresInvalidId() {
            ResourceBundle bundle = new ResourceBundle();

            Patient patient = new Patient();
            patient.setId("");
            boolean result = bundle.put(patient, "group1", true);

            assertThat(result).isFalse();
            assertThat(bundle.cache()).isEmpty();
            assertThat(bundle.getValidResourceGroups()).isEmpty();
        }

        @Test
        void putIgnoresNull() {
            ResourceBundle bundle = new ResourceBundle();
            boolean result = bundle.put(null, "group1", true);

            assertThat(result).isFalse();
            assertThat(bundle.cache()).isEmpty();
            assertThat(bundle.getValidResourceGroups()).isEmpty();
        }
    }

    @Nested
    class RetrievalTests {

        @Test
        void getMatch() {
            var cache = new ResourceBundle();
            cache.put(wrapper1);

            var result = cache.get(id);

            assertThat(result).isEqualTo(Optional.of(patient1));
        }

        @Test
        void isEmpty() {
            var cache = new ResourceBundle();

            var result = cache.get(id);

            assertThat(result).isNull();
        }
    }

    @Nested
    class RemovalTests {

        @Test
        void removeExistingEntry() {
            ResourceBundle cache = new ResourceBundle();
            cache.put(wrapper1);
            cache.remove(id);

            var result = cache.get(id);

            assertThat(result).isNull();
        }
    }

    @Nested
    class BidirectionalRelationMapping {
        private static final AnnotatedAttribute ATTRIBUTE = new AnnotatedAttribute("test", "test", false);

        private static final ResourceAttribute ATTRIBUTE_1 = new ResourceAttribute(ExtractionId.fromRelativeUrl("Patient/attribute1"), ATTRIBUTE);
        private static final ResourceAttribute ATTRIBUTE_2 = new ResourceAttribute(ExtractionId.fromRelativeUrl("Patient/attribute2"), ATTRIBUTE);
        private static final ResourceAttribute ATTRIBUTE_3 = new ResourceAttribute(ExtractionId.fromRelativeUrl("Patient/attribute4"), ATTRIBUTE);
        private static final ResourceAttribute ATTRIBUTE_4 = new ResourceAttribute(ExtractionId.fromRelativeUrl("Patient/attribute4"), ATTRIBUTE);
        private static final ResourceAttribute ATTRIBUTE_5 = new ResourceAttribute(ExtractionId.fromRelativeUrl("Patient/attribute5"), ATTRIBUTE);
        private static final ResourceAttribute ATTRIBUTE_6 = new ResourceAttribute(ExtractionId.fromRelativeUrl("Patient/attribute6"), ATTRIBUTE);

        private static final ResourceGroup CHILD_1 = new ResourceGroup(ExtractionId.fromRelativeUrl("Patient/child1"), "group1");
        private static final ResourceGroup CHILD_2 = new ResourceGroup(ExtractionId.fromRelativeUrl("Patient/child2"), "group1");
        private static final ResourceGroup PARENT_1 = new ResourceGroup(ExtractionId.fromRelativeUrl("Patient/parent1"), "group1");
        private static final ResourceGroup PARENT_2 = new ResourceGroup(ExtractionId.fromRelativeUrl("Patient/parent2"), "group2");

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
            assertThat(cache.resourceAttributeToChildResourceGroup()).containsKey(ATTRIBUTE_1);

            assertThat(cache.resourceAttributeToChildResourceGroup().get(ATTRIBUTE_1)).containsExactly(CHILD_1);

            assertThat(cache.childResourceGroupToResourceAttributesMap()).containsKey(CHILD_1);

            assertThat(cache.childResourceGroupToResourceAttributesMap().get(CHILD_1)).containsExactly(ATTRIBUTE_1);
        }

        @Test
        void shouldAddAttributeToParent_whenNoExistingMappings() {
            // When
            cache.addAttributeToParent(ATTRIBUTE_2, PARENT_1);

            // Then
            assertThat(cache.resourceAttributeToParentResourceGroup()).containsKey(ATTRIBUTE_2);

            assertThat(cache.resourceAttributeToParentResourceGroup().get(ATTRIBUTE_2)).containsExactly(PARENT_1);

            assertThat(cache.parentResourceGroupToResourceAttributesMap()).containsKey(PARENT_1);

            assertThat(cache.parentResourceGroupToResourceAttributesMap().get(PARENT_1)).containsExactly(ATTRIBUTE_2);
        }

        @Test
        void shouldCorrectlyHandleAddingMultipleChildrenToSameAttribute() {
            // When
            cache.addAttributeToChild(ATTRIBUTE_3, CHILD_1);
            cache.addAttributeToChild(ATTRIBUTE_3, CHILD_2);

            // Then
            assertThat(cache.resourceAttributeToChildResourceGroup()).containsKey(ATTRIBUTE_3);

            assertThat(cache.resourceAttributeToChildResourceGroup().get(ATTRIBUTE_3)).containsExactlyInAnyOrder(CHILD_1, CHILD_2);

            assertThat(cache.childResourceGroupToResourceAttributesMap()).containsKeys(CHILD_1, CHILD_2);

            assertThat(cache.childResourceGroupToResourceAttributesMap().get(CHILD_1)).containsExactly(ATTRIBUTE_3);

            assertThat(cache.childResourceGroupToResourceAttributesMap().get(CHILD_2)).containsExactly(ATTRIBUTE_3);
        }

        @Test
        void shouldCorrectlyHandleAddingMultipleParentsToSameAttribute() {
            // When
            cache.addAttributeToParent(ATTRIBUTE_4, PARENT_1);
            cache.addAttributeToParent(ATTRIBUTE_4, PARENT_2);

            // Then
            assertThat(cache.resourceAttributeToParentResourceGroup()).containsKey(ATTRIBUTE_4);

            assertThat(cache.resourceAttributeToParentResourceGroup().get(ATTRIBUTE_4)).containsExactlyInAnyOrder(PARENT_1, PARENT_2);

            assertThat(cache.parentResourceGroupToResourceAttributesMap()).containsKeys(PARENT_1, PARENT_2);

            assertThat(cache.parentResourceGroupToResourceAttributesMap().get(PARENT_1)).containsExactly(ATTRIBUTE_4);

            assertThat(cache.parentResourceGroupToResourceAttributesMap().get(PARENT_2)).containsExactly(ATTRIBUTE_4);
        }

        @Test
        void shouldCorrectlyHandlePreExistingSets() {
            // Pre-fill with another attribute
            cache.resourceAttributeToChildResourceGroup().computeIfAbsent(ATTRIBUTE_5, k -> ConcurrentHashMap.newKeySet()).add(CHILD_2);

            cache.resourceAttributeToParentResourceGroup().computeIfAbsent(ATTRIBUTE_5, k -> ConcurrentHashMap.newKeySet()).add(PARENT_2);

            // When
            cache.addAttributeToChild(ATTRIBUTE_5, CHILD_1);
            cache.addAttributeToParent(ATTRIBUTE_5, PARENT_1);

            // Then
            assertThat(cache.resourceAttributeToChildResourceGroup().get(ATTRIBUTE_5)).containsExactlyInAnyOrder(CHILD_1, CHILD_2);

            assertThat(cache.resourceAttributeToParentResourceGroup().get(ATTRIBUTE_5)).containsExactlyInAnyOrder(PARENT_1, PARENT_2);
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
            assertThat(cache.resourceAttributeToChildResourceGroup().get(ATTRIBUTE_6)).contains(CHILD_1);

            assertThat(cache.resourceAttributeToParentResourceGroup().get(ATTRIBUTE_6)).contains(PARENT_1);
        }
    }
}
