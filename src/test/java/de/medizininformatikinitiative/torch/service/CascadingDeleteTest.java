package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceAttribute;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CascadingDeleteTest {

    private ResourceBundle coreResourceBundle;
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
        coreResourceBundle = new ResourceBundle();

        resourceAttribute = new ResourceAttribute("test", new AnnotatedAttribute("test", "test", false));
        resourceAttribute2 = new ResourceAttribute("test2", new AnnotatedAttribute("test", "test", false));
        resourceAttribute3 = new ResourceAttribute("test3", new AnnotatedAttribute("test", "test", false));

        resourceGroup = new ResourceGroup("resource1", "group1");
        parentResourceGroup1 = new ResourceGroup("resourceP1", "group3");
        resourceGroup2 = new ResourceGroup("resource1", "group2");
        parentResourceGroup2 = new ResourceGroup("resourceP2", "group4");

        groupMap = new HashMap<>();
        groupMap.put("group1", new AnnotatedAttributeGroup("", "group1", "", "", List.of(), List.of(), true));
        groupMap.put("group2", new AnnotatedAttributeGroup("", "group2", "", "", List.of(), List.of(), true));
        groupMap.put("group3", new AnnotatedAttributeGroup("", "group3", "", "", List.of(), List.of(), true));
        groupMap.put("group4", new AnnotatedAttributeGroup("", "group4", "", "", List.of(), List.of(), false));

        cascadingDelete = new CascadingDelete();
    }

    @Nested
    class HandleBatch {

        @Test
        void testSimpleChain() {
            coreResourceBundle.addAttributeToParent(resourceAttribute, parentResourceGroup1);
            coreResourceBundle.addAttributeToChild(resourceAttribute, resourceGroup);
            coreResourceBundle.addAttributeToParent(resourceAttribute2, resourceGroup);
            coreResourceBundle.addAttributeToChild(resourceAttribute2, resourceGroup2);

            coreResourceBundle.setResourceAttributeValid(resourceAttribute);
            coreResourceBundle.setResourceAttributeValid(resourceAttribute2);
            coreResourceBundle.addResourceGroupValidity(resourceGroup, false);
            coreResourceBundle.addResourceGroupValidity(resourceGroup2, true);
            coreResourceBundle.addResourceGroupValidity(parentResourceGroup1, true);
            PatientBatchWithConsent patientBatchWithConsent = PatientBatchWithConsent.fromList(List.of(new PatientResourceBundle("Core", coreResourceBundle)));

            cascadingDelete.handlePatientBatch(patientBatchWithConsent, groupMap);

            assertThat(coreResourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(resourceAttribute);
            assertThat(coreResourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(resourceAttribute2);
            assertThat(coreResourceBundle.resourceAttributeValid(resourceAttribute)).isFalse();
            assertThat(coreResourceBundle.resourceAttributeValid(resourceAttribute2)).isFalse();
            assertThat(coreResourceBundle.isValidResourceGroup(parentResourceGroup1)).isTrue();
            assertThat(coreResourceBundle.isValidResourceGroup(resourceGroup2)).isFalse();
            assertThat(patientBatchWithConsent.bundles()).containsExactly(Map.entry("Core", new PatientResourceBundle("Core", coreResourceBundle)));
        }
    }

    @Nested
    class HandleBundle {

        @Test
        void testSimpleChain() {
            coreResourceBundle.addAttributeToParent(resourceAttribute, parentResourceGroup1);
            coreResourceBundle.addAttributeToChild(resourceAttribute, resourceGroup);
            coreResourceBundle.addAttributeToParent(resourceAttribute2, resourceGroup);
            coreResourceBundle.addAttributeToChild(resourceAttribute2, resourceGroup2);

            coreResourceBundle.setResourceAttributeValid(resourceAttribute);
            coreResourceBundle.setResourceAttributeValid(resourceAttribute2);
            coreResourceBundle.addResourceGroupValidity(resourceGroup, false);
            coreResourceBundle.addResourceGroupValidity(resourceGroup2, true);
            coreResourceBundle.addResourceGroupValidity(parentResourceGroup1, true);

            cascadingDelete.handleBundle(coreResourceBundle, groupMap);

            assertThat(coreResourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(resourceAttribute);
            assertThat(coreResourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(resourceAttribute2);
            assertThat(coreResourceBundle.resourceAttributeValid(resourceAttribute)).isFalse();
            assertThat(coreResourceBundle.resourceAttributeValid(resourceAttribute2)).isFalse();
            assertThat(coreResourceBundle.isValidResourceGroup(parentResourceGroup1)).isTrue();
            assertThat(coreResourceBundle.isValidResourceGroup(resourceGroup2)).isFalse();
        }

        @Test
        void testRefOnlyCycle() {
            // Establish cyclic dependencies:
            coreResourceBundle.addAttributeToParent(resourceAttribute, parentResourceGroup1);
            coreResourceBundle.addAttributeToChild(resourceAttribute, resourceGroup);
            coreResourceBundle.addAttributeToParent(resourceAttribute2, resourceGroup);
            coreResourceBundle.addAttributeToChild(resourceAttribute2, resourceGroup2);
            coreResourceBundle.addAttributeToParent(resourceAttribute3, resourceGroup2);
            coreResourceBundle.addAttributeToChild(resourceAttribute3, parentResourceGroup1); // Cycle back

            // Mark attributes as valid initially
            coreResourceBundle.setResourceAttributeValid(resourceAttribute);
            coreResourceBundle.setResourceAttributeValid(resourceAttribute2);
            coreResourceBundle.setResourceAttributeValid(resourceAttribute3);

            // Mark resource groups, setting one as false to trigger cascading delete
            coreResourceBundle.addResourceGroupValidity(resourceGroup, false);  // Invalid, should trigger deletions
            coreResourceBundle.addResourceGroupValidity(resourceGroup2, true);
            coreResourceBundle.addResourceGroupValidity(parentResourceGroup1, true);

            // Run full bundle cascading delete
            cascadingDelete.handleBundle(coreResourceBundle, groupMap);

            // Ensure attributes are removed from the child mapping
            assertThat(coreResourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(resourceAttribute);
            assertThat(coreResourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(resourceAttribute2);
            assertThat(coreResourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(resourceAttribute3);

            // Attributes should be marked invalid
            assertThat(coreResourceBundle.resourceAttributeValid(resourceAttribute)).isFalse();
            assertThat(coreResourceBundle.resourceAttributeValid(resourceAttribute2)).isFalse();
            assertThat(coreResourceBundle.resourceAttributeValid(resourceAttribute3)).isFalse();

            // **Everything should be deleted**
            assertThat(coreResourceBundle.isValidResourceGroup(parentResourceGroup1)).isFalse(); // Should be deleted
            assertThat(coreResourceBundle.isValidResourceGroup(resourceGroup2)).isFalse(); // Should be deleted
            assertThat(coreResourceBundle.isValidResourceGroup(resourceGroup)).isFalse(); // Should be deleted
        }

        @Test
        void testCycleWithProtectedGroup() {
            // Establish cyclic dependencies:
            coreResourceBundle.addAttributeToParent(resourceAttribute, parentResourceGroup1);
            coreResourceBundle.addAttributeToChild(resourceAttribute, resourceGroup);
            coreResourceBundle.addAttributeToParent(resourceAttribute2, resourceGroup);
            coreResourceBundle.addAttributeToChild(resourceAttribute2, resourceGroup2);
            coreResourceBundle.addAttributeToParent(resourceAttribute3, resourceGroup2);
            coreResourceBundle.addAttributeToChild(resourceAttribute3, parentResourceGroup1); // Cycle back

            // Mark attributes as valid initially
            coreResourceBundle.setResourceAttributeValid(resourceAttribute);
            coreResourceBundle.setResourceAttributeValid(resourceAttribute2);
            coreResourceBundle.setResourceAttributeValid(resourceAttribute3);

            // Define resource groups with `includeReferenceOnly`
            groupMap.put("group1", new AnnotatedAttributeGroup("", "group1", "", "", List.of(), List.of(), true));  // Can be deleted
            groupMap.put("group2", new AnnotatedAttributeGroup("", "group2", "", "", List.of(), List.of(), true));  // Can be deleted
            groupMap.put("group3", new AnnotatedAttributeGroup("", "group3", "", "", List.of(), List.of(), false)); // Protected (refOnly == false)

            // Set resource group validity (Trigger cascading deletion)
            coreResourceBundle.addResourceGroupValidity(resourceGroup, false); // Mark invalid, triggers deletion
            coreResourceBundle.addResourceGroupValidity(resourceGroup2, true);
            coreResourceBundle.addResourceGroupValidity(parentResourceGroup1, true); // Protected group (should survive)

            // Run full bundle cascading delete
            cascadingDelete.handleBundle(coreResourceBundle, groupMap);

            // Ensure attributes are removed from the child mapping
            assertThat(coreResourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(resourceAttribute);
            assertThat(coreResourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(resourceAttribute2);
            assertThat(coreResourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(resourceAttribute3);

            // Attributes should be marked invalid
            assertThat(coreResourceBundle.resourceAttributeValid(resourceAttribute)).isFalse();
            assertThat(coreResourceBundle.resourceAttributeValid(resourceAttribute2)).isFalse();
            assertThat(coreResourceBundle.resourceAttributeValid(resourceAttribute3)).isFalse();

            // **ResourceGroup2 and resourceGroup should be deleted, but parentResourceGroup1 should be protected**
            assertThat(coreResourceBundle.isValidResourceGroup(parentResourceGroup1)).isTrue(); // Should **survive** (protected)
            assertThat(coreResourceBundle.isValidResourceGroup(resourceGroup2)).isFalse(); // Should be deleted
            assertThat(coreResourceBundle.isValidResourceGroup(resourceGroup)).isFalse(); // Should be deleted
        }

        @Nested
        class HandleBranchingGraphBundle {

            @Test
            void branchingGraphFullDeletion() {
                ResourceAttribute attr1 = new ResourceAttribute("attr1", new AnnotatedAttribute("attr1", "test", false));
                ResourceAttribute attr2 = new ResourceAttribute("attr2", new AnnotatedAttribute("attr2", "test", false));
                ResourceAttribute attr3 = new ResourceAttribute("attr3", new AnnotatedAttribute("attr3", "test", false));

                ResourceGroup root = new ResourceGroup("root", "groupRoot");
                ResourceGroup branch1 = new ResourceGroup("branch1", "groupB1");
                ResourceGroup branch2 = new ResourceGroup("branch2", "groupB2");
                ResourceGroup leaf1 = new ResourceGroup("leaf1", "groupL1");
                ResourceGroup leaf2 = new ResourceGroup("leaf2", "groupL2");

                // Define all groups
                groupMap.put("groupRoot", new AnnotatedAttributeGroup("", "groupRoot", "", "", List.of(), List.of(), true));
                groupMap.put("groupB1", new AnnotatedAttributeGroup("", "groupB1", "", "", List.of(), List.of(), true));
                groupMap.put("groupB2", new AnnotatedAttributeGroup("", "groupB2", "", "", List.of(), List.of(), true));
                groupMap.put("groupL1", new AnnotatedAttributeGroup("", "groupL1", "", "", List.of(), List.of(), true));
                groupMap.put("groupL2", new AnnotatedAttributeGroup("", "groupL2", "", "", List.of(), List.of(), true));

                // Create branching structure:
                //   root → attr1 → branch1 → attr2 → leaf1
                //        → attr3 → branch2 → leaf2

                coreResourceBundle.addAttributeToParent(attr1, root);
                coreResourceBundle.addAttributeToChild(attr1, branch1);
                coreResourceBundle.addAttributeToParent(attr2, branch1);
                coreResourceBundle.addAttributeToChild(attr2, leaf1);
                coreResourceBundle.addAttributeToParent(attr3, root);
                coreResourceBundle.addAttributeToChild(attr3, branch2);
                coreResourceBundle.addAttributeToChild(attr3, leaf2);

                coreResourceBundle.setResourceAttributeValid(attr1);
                coreResourceBundle.setResourceAttributeValid(attr2);
                coreResourceBundle.setResourceAttributeValid(attr3);

                // Mark root as invalid to trigger full deletion
                coreResourceBundle.addResourceGroupValidity(root, false);
                coreResourceBundle.addResourceGroupValidity(branch1, true);
                coreResourceBundle.addResourceGroupValidity(branch2, true);
                coreResourceBundle.addResourceGroupValidity(leaf1, true);
                coreResourceBundle.addResourceGroupValidity(leaf2, true);

                // Run full bundle cascading delete
                cascadingDelete.handleBundle(coreResourceBundle, groupMap);

                // Attributes should all be removed
                assertThat(coreResourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(attr1);
                assertThat(coreResourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(attr2);
                assertThat(coreResourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(attr3);

                // Ensure all dependent groups are deleted
                assertThat(coreResourceBundle.isValidResourceGroup(branch1)).isFalse();
                assertThat(coreResourceBundle.isValidResourceGroup(branch2)).isFalse();
                assertThat(coreResourceBundle.isValidResourceGroup(leaf1)).isFalse();
                assertThat(coreResourceBundle.isValidResourceGroup(leaf2)).isFalse();
            }

            @Test
            void branchingGraphWithProtectedGroup() {
                ResourceAttribute attr1 = new ResourceAttribute("attr1", new AnnotatedAttribute("attr1", "test", false));
                ResourceAttribute attr2 = new ResourceAttribute("attr2", new AnnotatedAttribute("attr2", "test", false));

                ResourceGroup root = new ResourceGroup("root", "groupRoot");
                ResourceGroup branch1 = new ResourceGroup("branch1", "groupB1");
                ResourceGroup branch2 = new ResourceGroup("branch2", "groupB2");

                // Define groups with branch2 protected
                groupMap.put("groupRoot", new AnnotatedAttributeGroup("", "groupRoot", "", "", List.of(), List.of(), true));
                groupMap.put("groupB1", new AnnotatedAttributeGroup("", "groupB1", "", "", List.of(), List.of(), true));
                groupMap.put("groupB2", new AnnotatedAttributeGroup("", "groupB2", "", "", List.of(), List.of(), false)); // Protected group

                // Create branching structure:
                //   root → attr1 → branch1 (deletable)
                //        → attr2 → branch2 (protected)

                coreResourceBundle.addAttributeToParent(attr1, root);
                coreResourceBundle.addAttributeToChild(attr1, branch1);
                coreResourceBundle.addAttributeToParent(attr2, root);
                coreResourceBundle.addAttributeToChild(attr2, branch2);

                coreResourceBundle.setResourceAttributeValid(attr1);
                coreResourceBundle.setResourceAttributeValid(attr2);

                // Mark root as invalid to trigger deletion
                coreResourceBundle.addResourceGroupValidity(root, false);
                coreResourceBundle.addResourceGroupValidity(branch1, true);
                coreResourceBundle.addResourceGroupValidity(branch2, true);

                // Run full bundle cascading delete
                cascadingDelete.handleBundle(coreResourceBundle, groupMap);

                // Ensure branch1 is deleted but branch2 is protected
                assertThat(coreResourceBundle.isValidResourceGroup(branch1)).isFalse();
                assertThat(coreResourceBundle.isValidResourceGroup(branch2)).isTrue(); // Protected
            }

            @Test
            void asymmetricBranchingWithPartialDeletion() {
                ResourceAttribute attr1 = new ResourceAttribute("attr1", new AnnotatedAttribute("attr1", "test", false));
                ResourceAttribute attr2 = new ResourceAttribute("attr2", new AnnotatedAttribute("attr2", "test", false));

                ResourceGroup root = new ResourceGroup("root", "groupRoot");
                ResourceGroup branch1 = new ResourceGroup("branch1", "groupB1");
                ResourceGroup branch2 = new ResourceGroup("branch2", "groupB2");
                ResourceGroup leaf1 = new ResourceGroup("leaf1", "groupL1");

                // Define groups
                groupMap.put("groupRoot", new AnnotatedAttributeGroup("", "groupRoot", "", "", List.of(), List.of(), true));
                groupMap.put("groupB1", new AnnotatedAttributeGroup("", "groupB1", "", "", List.of(), List.of(), true));
                groupMap.put("groupB2", new AnnotatedAttributeGroup("", "groupB2", "", "", List.of(), List.of(), true)); // Should be deleted
                groupMap.put("groupL1", new AnnotatedAttributeGroup("", "groupL1", "", "", List.of(), List.of(), true));

                // Create asymmetric branching structure:
                //   root → attr1 → branch1 → attr2 → leaf1
                //        → attr2 → branch2 (branch2 has no children)

                coreResourceBundle.addAttributeToParent(attr1, root);
                coreResourceBundle.addAttributeToChild(attr1, branch1);
                coreResourceBundle.addAttributeToParent(attr2, branch1);
                coreResourceBundle.addAttributeToChild(attr2, leaf1);
                coreResourceBundle.addAttributeToParent(attr2, root);
                coreResourceBundle.addAttributeToChild(attr2, branch2);

                coreResourceBundle.setResourceAttributeValid(attr1);
                coreResourceBundle.setResourceAttributeValid(attr2);

                // Mark root as invalid to trigger deletion
                coreResourceBundle.addResourceGroupValidity(root, false);
                coreResourceBundle.addResourceGroupValidity(branch1, true);
                coreResourceBundle.addResourceGroupValidity(branch2, true);
                coreResourceBundle.addResourceGroupValidity(leaf1, true);

                // Run full bundle cascading delete
                cascadingDelete.handleBundle(coreResourceBundle, groupMap);

                // Ensure branch1 and leaf1 are deleted
                assertThat(coreResourceBundle.isValidResourceGroup(branch1)).isFalse();
                assertThat(coreResourceBundle.isValidResourceGroup(leaf1)).isFalse();

                // **branch2 should also be deleted because attr2 was invalidated**
                assertThat(coreResourceBundle.isValidResourceGroup(branch2)).isFalse();
            }
        }
    }

    @Nested
    class HandleParents {

        @Test
        void NoMustHaveNotRemoved() {
            coreResourceBundle.addAttributeToParent(resourceAttribute, parentResourceGroup1);
            coreResourceBundle.addAttributeToParent(resourceAttribute, parentResourceGroup2);
            coreResourceBundle.addAttributeToChild(resourceAttribute, resourceGroup);
            coreResourceBundle.setResourceAttributeValid(resourceAttribute);

            Set<ResourceGroup> result = cascadingDelete.handleParents(coreResourceBundle, resourceGroup);

            assertThat(coreResourceBundle.childResourceGroupToResourceAttributesMap()).doesNotContainKey(resourceGroup);
            assertThat(result).isEmpty();
            assertThat(coreResourceBundle.resourceAttributeValid(resourceAttribute)).isFalse();
        }

        @Test
        void mustHaveAttributesTriggerInvalidation() {
            ResourceAttribute mustHaveAttribute = new ResourceAttribute("mustHave", new AnnotatedAttribute("mustHave", "test", true));
            coreResourceBundle.addAttributeToParent(mustHaveAttribute, parentResourceGroup1);
            coreResourceBundle.addAttributeToParent(mustHaveAttribute, parentResourceGroup2);

            coreResourceBundle.addAttributeToChild(mustHaveAttribute, resourceGroup);
            coreResourceBundle.setResourceAttributeValid(mustHaveAttribute);

            Set<ResourceGroup> result = cascadingDelete.handleParents(coreResourceBundle, resourceGroup);

            assertThat(result).containsExactlyInAnyOrder(parentResourceGroup1, parentResourceGroup2);
        }
    }

    @Nested
    class HandleChildren {

        @Test
        void attributeStillAlive() {
            coreResourceBundle.addAttributeToParent(resourceAttribute, parentResourceGroup1);
            coreResourceBundle.addAttributeToParent(resourceAttribute, parentResourceGroup2);
            coreResourceBundle.addAttributeToChild(resourceAttribute, resourceGroup);
            coreResourceBundle.setResourceAttributeValid(resourceAttribute);

            Set<ResourceGroup> result = cascadingDelete.handleChildren(coreResourceBundle, groupMap, parentResourceGroup2);

            assertThat(coreResourceBundle.resourceAttributeToChildResourceGroup()).containsKey(resourceAttribute);
            assertThat(result).isEmpty();
        }

        @Test
        void attributeDeletedAfterAllParentsDied() {
            coreResourceBundle.addAttributeToParent(resourceAttribute, parentResourceGroup1);
            coreResourceBundle.addAttributeToParent(resourceAttribute, parentResourceGroup2);
            coreResourceBundle.addAttributeToChild(resourceAttribute, resourceGroup);
            coreResourceBundle.setResourceAttributeValid(resourceAttribute);

            Set<ResourceGroup> result1 = cascadingDelete.handleChildren(coreResourceBundle, groupMap, parentResourceGroup2);
            Set<ResourceGroup> result2 = cascadingDelete.handleChildren(coreResourceBundle, groupMap, parentResourceGroup1);

            assertThat(result1).isEmpty();
            assertThat(result2).contains(resourceGroup);
            assertThat(coreResourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(resourceAttribute);
        }

        @Test
        void doesRemoveDirectlyLoadedIfEmpty() {
            coreResourceBundle.addAttributeToParent(resourceAttribute, parentResourceGroup1);
            coreResourceBundle.addAttributeToChild(resourceAttribute, resourceGroup);
            coreResourceBundle.setResourceAttributeValid(resourceAttribute);

            Set<ResourceGroup> result = cascadingDelete.handleChildren(coreResourceBundle, groupMap, parentResourceGroup1);

            assertThat(result).contains(resourceGroup);
            assertThat(coreResourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(resourceAttribute);
        }

        @Test
        void doesNotRemoveDirectlyLoaded() {
            coreResourceBundle.addAttributeToParent(resourceAttribute2, parentResourceGroup2);
            coreResourceBundle.addAttributeToChild(resourceAttribute2, resourceGroup2);
            coreResourceBundle.setResourceAttributeValid(resourceAttribute);

            Set<ResourceGroup> result = cascadingDelete.handleChildren(coreResourceBundle, groupMap, parentResourceGroup2);

            assertThat(result).isEmpty();
            assertThat(coreResourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(resourceAttribute);
        }
    }
}
