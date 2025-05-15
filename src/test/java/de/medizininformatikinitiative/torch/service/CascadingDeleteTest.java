package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceAttribute;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import de.medizininformatikinitiative.torch.model.management.cachingResourceBundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CascadingDeleteTest {

    private cachingResourceBundle coreCachingResourceBundle;
    private ResourceAttribute resourceAttribute;
    private ResourceGroup resourceGroup;
    private ResourceAttribute resourceAttribute2;
    private ResourceGroup resourceGroup2;
    private ResourceAttribute resourceAttribute3;
    private ResourceGroup parentResourceGroup1;
    private ResourceGroup parentResourceGroup2;
    private CascadingDelete cascadingDelete;
    private Map<String, AnnotatedAttributeGroup> groupMap;

    @BeforeEach
    void setUp() {
        coreCachingResourceBundle = new cachingResourceBundle();

        resourceAttribute = new ResourceAttribute("test", new AnnotatedAttribute("test", "test", "test", false));
        resourceAttribute2 = new ResourceAttribute("test2", new AnnotatedAttribute("test", "test", "test", false));
        resourceAttribute3 = new ResourceAttribute("test3", new AnnotatedAttribute("test", "test", "test", false));

        resourceGroup = new ResourceGroup("resource1", "group1");
        parentResourceGroup1 = new ResourceGroup("resourceP1", "group3");
        resourceGroup2 = new ResourceGroup("resource1", "group2");
        parentResourceGroup2 = new ResourceGroup("resourceP2", "group4");

        groupMap = new HashMap<>();
        groupMap.put("group1", new AnnotatedAttributeGroup("", "group1", "", List.of(), List.of(), null, true));
        groupMap.put("group2", new AnnotatedAttributeGroup("", "group2", "", List.of(), List.of(), null, true));
        groupMap.put("group3", new AnnotatedAttributeGroup("", "group3", "", List.of(), List.of(), null, true));
        groupMap.put("group4", new AnnotatedAttributeGroup("", "group4", "", List.of(), List.of(), null, false));

        cascadingDelete = new CascadingDelete();
    }

    @Nested
    class HandleBatch {

        @Test
        void testSimpleChain() {
            coreCachingResourceBundle.addAttributeToParent(resourceAttribute, parentResourceGroup1);
            coreCachingResourceBundle.addAttributeToChild(resourceAttribute, resourceGroup);
            coreCachingResourceBundle.addAttributeToParent(resourceAttribute2, resourceGroup);
            coreCachingResourceBundle.addAttributeToChild(resourceAttribute2, resourceGroup2);

            coreCachingResourceBundle.setResourceAttributeValid(resourceAttribute);
            coreCachingResourceBundle.setResourceAttributeValid(resourceAttribute2);
            coreCachingResourceBundle.addResourceGroupValidity(resourceGroup, false);
            coreCachingResourceBundle.addResourceGroupValidity(resourceGroup2, true);
            coreCachingResourceBundle.addResourceGroupValidity(parentResourceGroup1, true);
            PatientBatchWithConsent patientBatchWithConsent = PatientBatchWithConsent.fromList(List.of(new PatientResourceBundle("Core", coreCachingResourceBundle)));

            cascadingDelete.handlePatientBatch(patientBatchWithConsent, groupMap);

            assertThat(coreCachingResourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(resourceAttribute);
            assertThat(coreCachingResourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(resourceAttribute2);
            assertThat(coreCachingResourceBundle.resourceAttributeValid(resourceAttribute)).isFalse();
            assertThat(coreCachingResourceBundle.resourceAttributeValid(resourceAttribute2)).isFalse();
            assertThat(coreCachingResourceBundle.isValidResourceGroup(parentResourceGroup1)).isTrue();
            assertThat(coreCachingResourceBundle.isValidResourceGroup(resourceGroup2)).isFalse();
            assertThat(patientBatchWithConsent.bundles()).containsExactly(Map.entry("Core", new PatientResourceBundle("Core", coreCachingResourceBundle)));
        }
    }

    @Nested
    class HandleBundle {

        @Test
        void testSimpleChain() {
            coreCachingResourceBundle.addAttributeToParent(resourceAttribute, parentResourceGroup1);
            coreCachingResourceBundle.addAttributeToChild(resourceAttribute, resourceGroup);
            coreCachingResourceBundle.addAttributeToParent(resourceAttribute2, resourceGroup);
            coreCachingResourceBundle.addAttributeToChild(resourceAttribute2, resourceGroup2);

            coreCachingResourceBundle.setResourceAttributeValid(resourceAttribute);
            coreCachingResourceBundle.setResourceAttributeValid(resourceAttribute2);
            coreCachingResourceBundle.addResourceGroupValidity(resourceGroup, false);
            coreCachingResourceBundle.addResourceGroupValidity(resourceGroup2, true);
            coreCachingResourceBundle.addResourceGroupValidity(parentResourceGroup1, true);

            cascadingDelete.handleBundle(coreCachingResourceBundle, groupMap);

            assertThat(coreCachingResourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(resourceAttribute);
            assertThat(coreCachingResourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(resourceAttribute2);
            assertThat(coreCachingResourceBundle.resourceAttributeValid(resourceAttribute)).isFalse();
            assertThat(coreCachingResourceBundle.resourceAttributeValid(resourceAttribute2)).isFalse();
            assertThat(coreCachingResourceBundle.isValidResourceGroup(parentResourceGroup1)).isTrue();
            assertThat(coreCachingResourceBundle.isValidResourceGroup(resourceGroup2)).isFalse();
        }

        @Test
        void testRefOnlyCycle() {
            // Establish cyclic dependencies:
            coreCachingResourceBundle.addAttributeToParent(resourceAttribute, parentResourceGroup1);
            coreCachingResourceBundle.addAttributeToChild(resourceAttribute, resourceGroup);
            coreCachingResourceBundle.addAttributeToParent(resourceAttribute2, resourceGroup);
            coreCachingResourceBundle.addAttributeToChild(resourceAttribute2, resourceGroup2);
            coreCachingResourceBundle.addAttributeToParent(resourceAttribute3, resourceGroup2);
            coreCachingResourceBundle.addAttributeToChild(resourceAttribute3, parentResourceGroup1); // Cycle back

            // Mark attributes as valid initially
            coreCachingResourceBundle.setResourceAttributeValid(resourceAttribute);
            coreCachingResourceBundle.setResourceAttributeValid(resourceAttribute2);
            coreCachingResourceBundle.setResourceAttributeValid(resourceAttribute3);

            // Mark resource groups, setting one as false to trigger cascading delete
            coreCachingResourceBundle.addResourceGroupValidity(resourceGroup, false);  // Invalid, should trigger deletions
            coreCachingResourceBundle.addResourceGroupValidity(resourceGroup2, true);
            coreCachingResourceBundle.addResourceGroupValidity(parentResourceGroup1, true);

            // Run full bundle cascading delete
            cascadingDelete.handleBundle(coreCachingResourceBundle, groupMap);

            // Ensure attributes are removed from the child mapping
            assertThat(coreCachingResourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(resourceAttribute);
            assertThat(coreCachingResourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(resourceAttribute2);
            assertThat(coreCachingResourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(resourceAttribute3);

            // Attributes should be marked invalid
            assertThat(coreCachingResourceBundle.resourceAttributeValid(resourceAttribute)).isFalse();
            assertThat(coreCachingResourceBundle.resourceAttributeValid(resourceAttribute2)).isFalse();
            assertThat(coreCachingResourceBundle.resourceAttributeValid(resourceAttribute3)).isFalse();

            // **Everything should be deleted**
            assertThat(coreCachingResourceBundle.isValidResourceGroup(parentResourceGroup1)).isFalse(); // Should be deleted
            assertThat(coreCachingResourceBundle.isValidResourceGroup(resourceGroup2)).isFalse(); // Should be deleted
            assertThat(coreCachingResourceBundle.isValidResourceGroup(resourceGroup)).isFalse(); // Should be deleted
        }

        @Test
        void testCycleWithProtectedGroup() {
            // Establish cyclic dependencies:
            coreCachingResourceBundle.addAttributeToParent(resourceAttribute, parentResourceGroup1);
            coreCachingResourceBundle.addAttributeToChild(resourceAttribute, resourceGroup);
            coreCachingResourceBundle.addAttributeToParent(resourceAttribute2, resourceGroup);
            coreCachingResourceBundle.addAttributeToChild(resourceAttribute2, resourceGroup2);
            coreCachingResourceBundle.addAttributeToParent(resourceAttribute3, resourceGroup2);
            coreCachingResourceBundle.addAttributeToChild(resourceAttribute3, parentResourceGroup1); // Cycle back

            // Mark attributes as valid initially
            coreCachingResourceBundle.setResourceAttributeValid(resourceAttribute);
            coreCachingResourceBundle.setResourceAttributeValid(resourceAttribute2);
            coreCachingResourceBundle.setResourceAttributeValid(resourceAttribute3);

            // Define resource groups with `includeReferenceOnly`
            groupMap.put("group1", new AnnotatedAttributeGroup("", "group1", "", List.of(), List.of(), null, true));  // Can be deleted
            groupMap.put("group2", new AnnotatedAttributeGroup("", "group2", "", List.of(), List.of(), null, true));  // Can be deleted
            groupMap.put("group3", new AnnotatedAttributeGroup("", "group3", "", List.of(), List.of(), null, false)); // Protected (refOnly == false)

            // Set resource group validity (Trigger cascading deletion)
            coreCachingResourceBundle.addResourceGroupValidity(resourceGroup, false); // Mark invalid, triggers deletion
            coreCachingResourceBundle.addResourceGroupValidity(resourceGroup2, true);
            coreCachingResourceBundle.addResourceGroupValidity(parentResourceGroup1, true); // Protected group (should survive)

            // Run full bundle cascading delete
            cascadingDelete.handleBundle(coreCachingResourceBundle, groupMap);

            // Ensure attributes are removed from the child mapping
            assertThat(coreCachingResourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(resourceAttribute);
            assertThat(coreCachingResourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(resourceAttribute2);
            assertThat(coreCachingResourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(resourceAttribute3);

            // Attributes should be marked invalid
            assertThat(coreCachingResourceBundle.resourceAttributeValid(resourceAttribute)).isFalse();
            assertThat(coreCachingResourceBundle.resourceAttributeValid(resourceAttribute2)).isFalse();
            assertThat(coreCachingResourceBundle.resourceAttributeValid(resourceAttribute3)).isFalse();

            // **ResourceGroup2 and resourceGroup should be deleted, but parentResourceGroup1 should be protected**
            assertThat(coreCachingResourceBundle.isValidResourceGroup(parentResourceGroup1)).isTrue(); // Should **survive** (protected)
            assertThat(coreCachingResourceBundle.isValidResourceGroup(resourceGroup2)).isFalse(); // Should be deleted
            assertThat(coreCachingResourceBundle.isValidResourceGroup(resourceGroup)).isFalse(); // Should be deleted
        }

        @Nested
        class HandleBranchingGraphBundle {

            @Test
            void branchingGraphFullDeletion() {
                ResourceAttribute attr1 = new ResourceAttribute("attr1", new AnnotatedAttribute("attr1", "test", "test", false));
                ResourceAttribute attr2 = new ResourceAttribute("attr2", new AnnotatedAttribute("attr2", "test", "test", false));
                ResourceAttribute attr3 = new ResourceAttribute("attr3", new AnnotatedAttribute("attr3", "test", "test", false));

                ResourceGroup root = new ResourceGroup("root", "groupRoot");
                ResourceGroup branch1 = new ResourceGroup("branch1", "groupB1");
                ResourceGroup branch2 = new ResourceGroup("branch2", "groupB2");
                ResourceGroup leaf1 = new ResourceGroup("leaf1", "groupL1");
                ResourceGroup leaf2 = new ResourceGroup("leaf2", "groupL2");

                // Define all groups
                groupMap.put("groupRoot", new AnnotatedAttributeGroup("", "groupRoot", "", List.of(), List.of(), null, true));
                groupMap.put("groupB1", new AnnotatedAttributeGroup("", "groupB1", "", List.of(), List.of(), null, true));
                groupMap.put("groupB2", new AnnotatedAttributeGroup("", "groupB2", "", List.of(), List.of(), null, true));
                groupMap.put("groupL1", new AnnotatedAttributeGroup("", "groupL1", "", List.of(), List.of(), null, true));
                groupMap.put("groupL2", new AnnotatedAttributeGroup("", "groupL2", "", List.of(), List.of(), null, true));

                // Create branching structure:
                //   root → attr1 → branch1 → attr2 → leaf1
                //        → attr3 → branch2 → leaf2

                coreCachingResourceBundle.addAttributeToParent(attr1, root);
                coreCachingResourceBundle.addAttributeToChild(attr1, branch1);
                coreCachingResourceBundle.addAttributeToParent(attr2, branch1);
                coreCachingResourceBundle.addAttributeToChild(attr2, leaf1);
                coreCachingResourceBundle.addAttributeToParent(attr3, root);
                coreCachingResourceBundle.addAttributeToChild(attr3, branch2);
                coreCachingResourceBundle.addAttributeToChild(attr3, leaf2);

                coreCachingResourceBundle.setResourceAttributeValid(attr1);
                coreCachingResourceBundle.setResourceAttributeValid(attr2);
                coreCachingResourceBundle.setResourceAttributeValid(attr3);

                // Mark root as invalid to trigger full deletion
                coreCachingResourceBundle.addResourceGroupValidity(root, false);
                coreCachingResourceBundle.addResourceGroupValidity(branch1, true);
                coreCachingResourceBundle.addResourceGroupValidity(branch2, true);
                coreCachingResourceBundle.addResourceGroupValidity(leaf1, true);
                coreCachingResourceBundle.addResourceGroupValidity(leaf2, true);

                // Run full bundle cascading delete
                cascadingDelete.handleBundle(coreCachingResourceBundle, groupMap);

                // Attributes should all be removed
                assertThat(coreCachingResourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(attr1);
                assertThat(coreCachingResourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(attr2);
                assertThat(coreCachingResourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(attr3);

                // Ensure all dependent groups are deleted
                assertThat(coreCachingResourceBundle.isValidResourceGroup(branch1)).isFalse();
                assertThat(coreCachingResourceBundle.isValidResourceGroup(branch2)).isFalse();
                assertThat(coreCachingResourceBundle.isValidResourceGroup(leaf1)).isFalse();
                assertThat(coreCachingResourceBundle.isValidResourceGroup(leaf2)).isFalse();
            }

            @Test
            void branchingGraphWithProtectedGroup() {
                ResourceAttribute attr1 = new ResourceAttribute("attr1", new AnnotatedAttribute("attr1", "test", "test", false));
                ResourceAttribute attr2 = new ResourceAttribute("attr2", new AnnotatedAttribute("attr2", "test", "test", false));

                ResourceGroup root = new ResourceGroup("root", "groupRoot");
                ResourceGroup branch1 = new ResourceGroup("branch1", "groupB1");
                ResourceGroup branch2 = new ResourceGroup("branch2", "groupB2");

                // Define groups with branch2 protected
                groupMap.put("groupRoot", new AnnotatedAttributeGroup("", "groupRoot", "", List.of(), List.of(), null, true));
                groupMap.put("groupB1", new AnnotatedAttributeGroup("", "groupB1", "", List.of(), List.of(), null, true));
                groupMap.put("groupB2", new AnnotatedAttributeGroup("", "groupB2", "", List.of(), List.of(), null, false)); // Protected group

                // Create branching structure:
                //   root → attr1 → branch1 (deletable)
                //        → attr2 → branch2 (protected)

                coreCachingResourceBundle.addAttributeToParent(attr1, root);
                coreCachingResourceBundle.addAttributeToChild(attr1, branch1);
                coreCachingResourceBundle.addAttributeToParent(attr2, root);
                coreCachingResourceBundle.addAttributeToChild(attr2, branch2);

                coreCachingResourceBundle.setResourceAttributeValid(attr1);
                coreCachingResourceBundle.setResourceAttributeValid(attr2);

                // Mark root as invalid to trigger deletion
                coreCachingResourceBundle.addResourceGroupValidity(root, false);
                coreCachingResourceBundle.addResourceGroupValidity(branch1, true);
                coreCachingResourceBundle.addResourceGroupValidity(branch2, true);

                // Run full bundle cascading delete
                cascadingDelete.handleBundle(coreCachingResourceBundle, groupMap);

                // Ensure branch1 is deleted but branch2 is protected
                assertThat(coreCachingResourceBundle.isValidResourceGroup(branch1)).isFalse();
                assertThat(coreCachingResourceBundle.isValidResourceGroup(branch2)).isTrue(); // Protected
            }

            @Test
            void asymmetricBranchingWithPartialDeletion() {
                ResourceAttribute attr1 = new ResourceAttribute("attr1", new AnnotatedAttribute("attr1", "test", "test", false));
                ResourceAttribute attr2 = new ResourceAttribute("attr2", new AnnotatedAttribute("attr2", "test", "test", false));

                ResourceGroup root = new ResourceGroup("root", "groupRoot");
                ResourceGroup branch1 = new ResourceGroup("branch1", "groupB1");
                ResourceGroup branch2 = new ResourceGroup("branch2", "groupB2");
                ResourceGroup leaf1 = new ResourceGroup("leaf1", "groupL1");

                // Define groups
                groupMap.put("groupRoot", new AnnotatedAttributeGroup("", "groupRoot", "", List.of(), List.of(), null, true));
                groupMap.put("groupB1", new AnnotatedAttributeGroup("", "groupB1", "", List.of(), List.of(), null, true));
                groupMap.put("groupB2", new AnnotatedAttributeGroup("", "groupB2", "", List.of(), List.of(), null, true)); // Should be deleted
                groupMap.put("groupL1", new AnnotatedAttributeGroup("", "groupL1", "", List.of(), List.of(), null, true));

                // Create asymmetric branching structure:
                //   root → attr1 → branch1 → attr2 → leaf1
                //        → attr2 → branch2 (branch2 has no children)

                coreCachingResourceBundle.addAttributeToParent(attr1, root);
                coreCachingResourceBundle.addAttributeToChild(attr1, branch1);
                coreCachingResourceBundle.addAttributeToParent(attr2, branch1);
                coreCachingResourceBundle.addAttributeToChild(attr2, leaf1);
                coreCachingResourceBundle.addAttributeToParent(attr2, root);
                coreCachingResourceBundle.addAttributeToChild(attr2, branch2);

                coreCachingResourceBundle.setResourceAttributeValid(attr1);
                coreCachingResourceBundle.setResourceAttributeValid(attr2);

                // Mark root as invalid to trigger deletion
                coreCachingResourceBundle.addResourceGroupValidity(root, false);
                coreCachingResourceBundle.addResourceGroupValidity(branch1, true);
                coreCachingResourceBundle.addResourceGroupValidity(branch2, true);
                coreCachingResourceBundle.addResourceGroupValidity(leaf1, true);

                // Run full bundle cascading delete
                cascadingDelete.handleBundle(coreCachingResourceBundle, groupMap);

                // Ensure branch1 and leaf1 are deleted
                assertThat(coreCachingResourceBundle.isValidResourceGroup(branch1)).isFalse();
                assertThat(coreCachingResourceBundle.isValidResourceGroup(leaf1)).isFalse();

                // **branch2 should also be deleted because attr2 was invalidated**
                assertThat(coreCachingResourceBundle.isValidResourceGroup(branch2)).isFalse();
            }
        }
    }

    @Nested
    class HandleParents {

        @Test
        void NoMustHaveNotRemoved() {
            coreCachingResourceBundle.addAttributeToParent(resourceAttribute, parentResourceGroup1);
            coreCachingResourceBundle.addAttributeToParent(resourceAttribute, parentResourceGroup2);
            coreCachingResourceBundle.addAttributeToChild(resourceAttribute, resourceGroup);
            coreCachingResourceBundle.setResourceAttributeValid(resourceAttribute);

            Set<ResourceGroup> result = cascadingDelete.handleParents(coreCachingResourceBundle, resourceGroup);

            assertThat(coreCachingResourceBundle.childResourceGroupToResourceAttributesMap()).doesNotContainKey(resourceGroup);
            assertThat(result).isEmpty();
            assertThat(coreCachingResourceBundle.resourceAttributeValid(resourceAttribute)).isFalse();
        }

        @Test
        void mustHaveAttributesTriggerInvalidation() {
            ResourceAttribute mustHaveAttribute = new ResourceAttribute("mustHave", new AnnotatedAttribute("mustHave", "test", "test", true));
            coreCachingResourceBundle.addAttributeToParent(mustHaveAttribute, parentResourceGroup1);
            coreCachingResourceBundle.addAttributeToParent(mustHaveAttribute, parentResourceGroup2);

            coreCachingResourceBundle.addAttributeToChild(mustHaveAttribute, resourceGroup);
            coreCachingResourceBundle.setResourceAttributeValid(mustHaveAttribute);

            Set<ResourceGroup> result = cascadingDelete.handleParents(coreCachingResourceBundle, resourceGroup);

            assertThat(result).containsExactlyInAnyOrder(parentResourceGroup1, parentResourceGroup2);
        }
    }

    @Nested
    class HandleChildren {

        @Test
        void attributeStillAlive() {
            coreCachingResourceBundle.addAttributeToParent(resourceAttribute, parentResourceGroup1);
            coreCachingResourceBundle.addAttributeToParent(resourceAttribute, parentResourceGroup2);
            coreCachingResourceBundle.addAttributeToChild(resourceAttribute, resourceGroup);
            coreCachingResourceBundle.setResourceAttributeValid(resourceAttribute);

            Set<ResourceGroup> result = cascadingDelete.handleChildren(coreCachingResourceBundle, groupMap, parentResourceGroup2);

            assertThat(coreCachingResourceBundle.resourceAttributeToChildResourceGroup()).containsKey(resourceAttribute);
            assertThat(result).isEmpty();
        }

        @Test
        void attributeDeletedAfterAllParentsDied() {
            coreCachingResourceBundle.addAttributeToParent(resourceAttribute, parentResourceGroup1);
            coreCachingResourceBundle.addAttributeToParent(resourceAttribute, parentResourceGroup2);
            coreCachingResourceBundle.addAttributeToChild(resourceAttribute, resourceGroup);
            coreCachingResourceBundle.setResourceAttributeValid(resourceAttribute);

            Set<ResourceGroup> result1 = cascadingDelete.handleChildren(coreCachingResourceBundle, groupMap, parentResourceGroup2);
            Set<ResourceGroup> result2 = cascadingDelete.handleChildren(coreCachingResourceBundle, groupMap, parentResourceGroup1);

            assertThat(result1).isEmpty();
            assertThat(result2).contains(resourceGroup);
            assertThat(coreCachingResourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(resourceAttribute);
        }

        @Test
        void doesRemoveDirectlyLoadedIfEmpty() {
            coreCachingResourceBundle.addAttributeToParent(resourceAttribute, parentResourceGroup1);
            coreCachingResourceBundle.addAttributeToChild(resourceAttribute, resourceGroup);
            coreCachingResourceBundle.setResourceAttributeValid(resourceAttribute);

            Set<ResourceGroup> result = cascadingDelete.handleChildren(coreCachingResourceBundle, groupMap, parentResourceGroup1);

            assertThat(result).contains(resourceGroup);
            assertThat(coreCachingResourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(resourceAttribute);
        }

        @Test
        void doesNotRemoveDirectlyLoaded() {
            coreCachingResourceBundle.addAttributeToParent(resourceAttribute2, parentResourceGroup2);
            coreCachingResourceBundle.addAttributeToChild(resourceAttribute2, resourceGroup2);
            coreCachingResourceBundle.setResourceAttributeValid(resourceAttribute);

            Set<ResourceGroup> result = cascadingDelete.handleChildren(coreCachingResourceBundle, groupMap, parentResourceGroup2);

            assertThat(result).isEmpty();
            assertThat(coreCachingResourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(resourceAttribute);
        }
    }
}
