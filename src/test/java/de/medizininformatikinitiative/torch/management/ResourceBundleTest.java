package de.medizininformatikinitiative.torch.management;

import com.fasterxml.jackson.databind.node.TextNode;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.management.CachelessResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceAttribute;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import de.medizininformatikinitiative.torch.model.management.ResourceGroupWrapper;
import de.medizininformatikinitiative.torch.util.ResourceUtils;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static de.medizininformatikinitiative.torch.assertions.Assertions.assertThat;
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

    @Test
    void toFhirBundleTest() {
        ResourceBundle cache = new ResourceBundle();
        cache.put(wrapper1);

        var fhirBundle = cache.toFhirBundle();
        assertThat(fhirBundle.getEntry().getFirst().getRequest().getUrl()).isEqualTo("Patient/patient1");
        assertThat(fhirBundle)
                .containsNEntries(1)
                .extractResources()
                .satisfiesExactly(resource -> assertThat(resource).extractElementsAt("id").containsExactly(new TextNode("patient1")));
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

            assertThat(cache.childResourceGroupToResourceAttributesMap())
                    .containsKey(CHILD_1);

            assertThat(cache.childResourceGroupToResourceAttributesMap().get(CHILD_1))
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

            assertThat(cache.parentResourceGroupToResourceAttributesMap())
                    .containsKey(PARENT_1);

            assertThat(cache.parentResourceGroupToResourceAttributesMap().get(PARENT_1))
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

            assertThat(cache.childResourceGroupToResourceAttributesMap())
                    .containsKeys(CHILD_1, CHILD_2);

            assertThat(cache.childResourceGroupToResourceAttributesMap().get(CHILD_1))
                    .containsExactly(ATTRIBUTE_3);

            assertThat(cache.childResourceGroupToResourceAttributesMap().get(CHILD_2))
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

            assertThat(cache.parentResourceGroupToResourceAttributesMap())
                    .containsKeys(PARENT_1, PARENT_2);

            assertThat(cache.parentResourceGroupToResourceAttributesMap().get(PARENT_1))
                    .containsExactly(ATTRIBUTE_4);

            assertThat(cache.parentResourceGroupToResourceAttributesMap().get(PARENT_2))
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

    @Nested
    class CacheLess {

        private final ResourceGroup patientGroup = new ResourceGroup("Patient/101", "GroupA");
        private final ResourceGroup medicationGroup1 = new ResourceGroup("Medication/201", "GroupA");
        private final ResourceGroup medicationGroup2 = new ResourceGroup("Medication/202", "GroupA");
        private final ResourceGroup organizationGroup = new ResourceGroup("Organization/501", "GroupX");
        private final AnnotatedAttribute annotatedAttribute1 = new AnnotatedAttribute("test", "test", "test", false);
        private final AnnotatedAttribute annotatedAttribute2 = new AnnotatedAttribute("med", "med", "med", false);
        private final ResourceAttribute attribute1 = new ResourceAttribute("attr1", annotatedAttribute1); // Should remain
        private final ResourceAttribute attribute3 = new ResourceAttribute("attr3", annotatedAttribute2); // Should be added
        private final ResourceAttribute attribute4 = new ResourceAttribute("attr4", annotatedAttribute2); // Should be added
        private ResourceBundle resourceBundle;
        private CachelessResourceBundle cachelessResourceBundle;

        @BeforeEach
        void setUp() {
            resourceBundle = new ResourceBundle();

            // Initial state: ResourceBundle already contains some valid groups and attributes
            resourceBundle.addResourceGroupValidity(patientGroup, true);
            resourceBundle.addAttributeToParent(attribute1, patientGroup);
            resourceBundle.addAttributeToChild(attribute1, medicationGroup1);
            resourceBundle.addResourceGroupValidity(medicationGroup1, true);
            resourceBundle.setResourceAttributeValid(attribute1);

            // Prepare ImmutableResourceBundle with additional data to merge
            cachelessResourceBundle = new CachelessResourceBundle(
                    Map.of(
                            attribute3, Set.of(medicationGroup1),
                            attribute4, Set.of(medicationGroup2)
                    ),
                    Map.of(
                            attribute3, Set.of(organizationGroup),
                            attribute4, Set.of(organizationGroup)
                    ),
                    Map.of(
                            medicationGroup1, true,
                            medicationGroup2, true,
                            organizationGroup, true
                    ),
                    Map.of(
                            attribute3, true,
                            attribute4, true
                    ),
                    Map.of(
                            medicationGroup1, Set.of(attribute3),
                            medicationGroup2, Set.of(attribute4)
                    ),
                    Map.of(
                            organizationGroup, Set.of(attribute3, attribute4)
                    )
            );
        }

        @Test
        void mergeCachelessBundlesIntoResourceBundle() {
            // Merge the immutable bundle into the resource bundle
            resourceBundle.merge(cachelessResourceBundle);

            // **Step 1: Validate all expected groups are present (existing + new)**
            assertThat(resourceBundle.resourceGroupValidity()).containsAllEntriesOf(Map.of(
                    medicationGroup1, true,
                    medicationGroup2, true,
                    organizationGroup, true
            ));
            // Ensure no unexpected removals
            assertThat(resourceBundle.resourceGroupValidity()).containsKey(patientGroup);

            // **Step 2: Validate correct attribute validity**
            assertThat(resourceBundle.resourceAttributeValidity()).containsAllEntriesOf(Map.of(
                    attribute3, true,
                    attribute4, true
            ));
            // Ensure pre-existing attribute is still valid
            assertThat(resourceBundle.resourceAttributeValidity()).containsKey(attribute1);

            // **Step 3: Verify Parent-Child relationships**
            assertThat(resourceBundle.resourceAttributeToParentResourceGroup()).containsAllEntriesOf(Map.of(
                    attribute3, Set.of(medicationGroup1),
                    attribute4, Set.of(medicationGroup2)
            ));

            assertThat(resourceBundle.resourceAttributeToChildResourceGroup()).containsAllEntriesOf(Map.of(
                    attribute3, Set.of(organizationGroup),
                    attribute4, Set.of(organizationGroup)
            ));

            // **Step 4: Validate group-to-attribute mappings**
            assertThat(resourceBundle.parentResourceGroupToResourceAttributesMap()).containsAllEntriesOf(Map.of(
                    medicationGroup1, Set.of(attribute3),
                    medicationGroup2, Set.of(attribute4)
            ));

            assertThat(resourceBundle.childResourceGroupToResourceAttributesMap()).containsAllEntriesOf(Map.of(
                    organizationGroup, Set.of(attribute3, attribute4)
            ));
        }
    }
}
