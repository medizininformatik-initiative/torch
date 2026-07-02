package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionId;
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

    private ResourceBundle resourceBundle;
    private ResourceAttribute resourceAttribute;
    private ResourceGroup resourceGroup;
    private ResourceAttribute resourceAttribute2;
    private ResourceGroup resourceGroup2;
    private ResourceAttribute resourceAttribute3;
    private ResourceGroup parentResourceGroup1;
    private ResourceGroup parentResourceGroup2;
    private CascadingDelete cascadingDelete;
    private Map<String, AnnotatedAttributeGroup> groupMap;

    private static ExtractionId rid(String relativeUrl) {
        return ExtractionId.fromRelativeUrl(relativeUrl);
    }

    @BeforeEach
    void setUp() {
        resourceBundle = new ResourceBundle();

        resourceAttribute = new ResourceAttribute(rid("r/test1"), new AnnotatedAttribute("test", "test", false));
        resourceAttribute2 = new ResourceAttribute(rid("r/test2"), new AnnotatedAttribute("test", "test", false));
        resourceAttribute3 = new ResourceAttribute(rid("r/test3"), new AnnotatedAttribute("test", "test", false));

        resourceGroup = new ResourceGroup(rid("r/resource1"), "group1");
        parentResourceGroup1 = new ResourceGroup(rid("r/resourceP1"), "group3");
        resourceGroup2 = new ResourceGroup(rid("r/resource1"), "group2");
        parentResourceGroup2 = new ResourceGroup(rid("r/resourceP2"), "group4");

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
            // parentResourceGroup1 is the unreferenced root here, so it must be directly loaded (refOnly == false)
            groupMap.put("group3", new AnnotatedAttributeGroup("", "group3", "", "", List.of(), List.of(), false));

            resourceBundle.addAttributeToParent(resourceAttribute, parentResourceGroup1);
            resourceBundle.addAttributeToChild(resourceAttribute, resourceGroup);
            resourceBundle.addAttributeToParent(resourceAttribute2, resourceGroup);
            resourceBundle.addAttributeToChild(resourceAttribute2, resourceGroup2);

            resourceBundle.setResourceAttributeValid(resourceAttribute);
            resourceBundle.setResourceAttributeValid(resourceAttribute2);
            resourceBundle.addResourceGroupValidity(resourceGroup, false);
            resourceBundle.addResourceGroupValidity(resourceGroup2, true);
            resourceBundle.addResourceGroupValidity(parentResourceGroup1, true);
            PatientBatchWithConsent patientBatchWithConsent = PatientBatchWithConsent.fromList(List.of(new PatientResourceBundle("Core", resourceBundle)));

            cascadingDelete.handlePatientBatch(patientBatchWithConsent, groupMap);

            assertThat(resourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(resourceAttribute);
            assertThat(resourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(resourceAttribute2);
            assertThat(resourceBundle.resourceAttributeValid(resourceAttribute)).isFalse();
            assertThat(resourceBundle.resourceAttributeValid(resourceAttribute2)).isFalse();
            assertThat(resourceBundle.isValidResourceGroup(parentResourceGroup1)).isTrue();
            assertThat(resourceBundle.isValidResourceGroup(resourceGroup2)).isFalse();
            assertThat(patientBatchWithConsent.bundles()).containsExactly(Map.entry("Core", new PatientResourceBundle("Core", resourceBundle)));
        }
    }

    @Nested
    class HandleBundle {

        @Test
        void testSimpleChain() {
            // parentResourceGroup1 is the unreferenced root here, so it must be directly loaded (refOnly == false)
            groupMap.put("group3", new AnnotatedAttributeGroup("", "group3", "", "", List.of(), List.of(), false));

            resourceBundle.addAttributeToParent(resourceAttribute, parentResourceGroup1);
            resourceBundle.addAttributeToChild(resourceAttribute, resourceGroup);
            resourceBundle.addAttributeToParent(resourceAttribute2, resourceGroup);
            resourceBundle.addAttributeToChild(resourceAttribute2, resourceGroup2);

            resourceBundle.setResourceAttributeValid(resourceAttribute);
            resourceBundle.setResourceAttributeValid(resourceAttribute2);
            resourceBundle.addResourceGroupValidity(resourceGroup, false);
            resourceBundle.addResourceGroupValidity(resourceGroup2, true);
            resourceBundle.addResourceGroupValidity(parentResourceGroup1, true);

            cascadingDelete.handleBundle(resourceBundle, groupMap);

            assertThat(resourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(resourceAttribute);
            assertThat(resourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(resourceAttribute2);
            assertThat(resourceBundle.resourceAttributeValid(resourceAttribute)).isFalse();
            assertThat(resourceBundle.resourceAttributeValid(resourceAttribute2)).isFalse();
            assertThat(resourceBundle.isValidResourceGroup(parentResourceGroup1)).isTrue();
            assertThat(resourceBundle.isValidResourceGroup(resourceGroup2)).isFalse();
        }

        @Test
        void validAnchorGroundsReferencedCycle() {
            // D (non-refOnly, valid) → A (refOnly) → B (refOnly) → A
            // D is valid so the sweep BFS reaches A and B through D — both stay valid.
            ExtractionId dId = rid("r/D");
            ExtractionId aId = rid("r/A");
            ExtractionId bId = rid("r/B");

            ResourceGroup d = new ResourceGroup(dId, "groupD");
            ResourceGroup a = new ResourceGroup(aId, "groupA");
            ResourceGroup b = new ResourceGroup(bId, "groupB");

            groupMap.put("groupD", new AnnotatedAttributeGroup("", "groupD", "", "", List.of(), List.of(), false));
            groupMap.put("groupA", new AnnotatedAttributeGroup("", "groupA", "", "", List.of(), List.of(), true));
            groupMap.put("groupB", new AnnotatedAttributeGroup("", "groupB", "", "", List.of(), List.of(), true));

            ResourceAttribute attrDA = new ResourceAttribute(dId, new AnnotatedAttribute("attrDA", "test", false));
            ResourceAttribute attrAB = new ResourceAttribute(aId, new AnnotatedAttribute("attrAB", "test", false));
            ResourceAttribute attrBA = new ResourceAttribute(bId, new AnnotatedAttribute("attrBA", "test", false));

            resourceBundle.addAttributeToParent(attrDA, d);
            resourceBundle.addAttributeToChild(attrDA, a);
            resourceBundle.addAttributeToParent(attrAB, a);
            resourceBundle.addAttributeToChild(attrAB, b);
            resourceBundle.addAttributeToParent(attrBA, b);
            resourceBundle.addAttributeToChild(attrBA, a);

            resourceBundle.setResourceAttributeValid(attrDA);
            resourceBundle.setResourceAttributeValid(attrAB);
            resourceBundle.setResourceAttributeValid(attrBA);

            resourceBundle.addResourceGroupValidity(d, true);
            resourceBundle.addResourceGroupValidity(a, true);
            resourceBundle.addResourceGroupValidity(b, true);

            cascadingDelete.handleBundle(resourceBundle, groupMap);

            assertThat(resourceBundle.isValidResourceGroup(a)).isTrue();
            assertThat(resourceBundle.isValidResourceGroup(b)).isTrue();
        }

        @Test
        void testRefOnlyCycle() {
            // Establish cyclic dependencies:
            resourceBundle.addAttributeToParent(resourceAttribute, parentResourceGroup1);
            resourceBundle.addAttributeToChild(resourceAttribute, resourceGroup);
            resourceBundle.addAttributeToParent(resourceAttribute2, resourceGroup);
            resourceBundle.addAttributeToChild(resourceAttribute2, resourceGroup2);
            resourceBundle.addAttributeToParent(resourceAttribute3, resourceGroup2);
            resourceBundle.addAttributeToChild(resourceAttribute3, parentResourceGroup1); // Cycle back

            // Mark attributes as valid initially
            resourceBundle.setResourceAttributeValid(resourceAttribute);
            resourceBundle.setResourceAttributeValid(resourceAttribute2);
            resourceBundle.setResourceAttributeValid(resourceAttribute3);

            // Mark resource groups, setting one as false to trigger cascading delete
            resourceBundle.addResourceGroupValidity(resourceGroup, false);  // Invalid, should trigger deletions
            resourceBundle.addResourceGroupValidity(resourceGroup2, true);
            resourceBundle.addResourceGroupValidity(parentResourceGroup1, true);

            // Run full bundle cascading delete
            cascadingDelete.handleBundle(resourceBundle, groupMap);

            // Ensure attributes are removed from the child mapping
            assertThat(resourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(resourceAttribute);
            assertThat(resourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(resourceAttribute2);
            assertThat(resourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(resourceAttribute3);

            // Attributes should be marked invalid
            assertThat(resourceBundle.resourceAttributeValid(resourceAttribute)).isFalse();
            assertThat(resourceBundle.resourceAttributeValid(resourceAttribute2)).isFalse();
            assertThat(resourceBundle.resourceAttributeValid(resourceAttribute3)).isFalse();

            // **Everything should be deleted**
            assertThat(resourceBundle.isValidResourceGroup(parentResourceGroup1)).isFalse(); // Should be deleted
            assertThat(resourceBundle.isValidResourceGroup(resourceGroup2)).isFalse(); // Should be deleted
            assertThat(resourceBundle.isValidResourceGroup(resourceGroup)).isFalse(); // Should be deleted
        }

        @Test
        void unfoundedCycleIsInvalidatedBySweep() {
            // D (non-refOnly) → A (refOnly) → B (refOnly) → A
            // D is the only external anchor. When D is invalidated, A keeps B as a parent and
            // B keeps A as a parent, so the main BFS never enqueues either. The post-processing
            // sweep must catch and invalidate both.
            ExtractionId dId = rid("r/D");
            ExtractionId aId = rid("r/A");
            ExtractionId bId = rid("r/B");

            ResourceGroup d = new ResourceGroup(dId, "groupD");
            ResourceGroup a = new ResourceGroup(aId, "groupA");
            ResourceGroup b = new ResourceGroup(bId, "groupB");

            groupMap.put("groupD", new AnnotatedAttributeGroup("", "groupD", "", "", List.of(), List.of(), false));
            groupMap.put("groupA", new AnnotatedAttributeGroup("", "groupA", "", "", List.of(), List.of(), true));
            groupMap.put("groupB", new AnnotatedAttributeGroup("", "groupB", "", "", List.of(), List.of(), true));

            ResourceAttribute attrDA = new ResourceAttribute(dId, new AnnotatedAttribute("attrDA", "test", false));
            ResourceAttribute attrAB = new ResourceAttribute(aId, new AnnotatedAttribute("attrAB", "test", false));
            ResourceAttribute attrBA = new ResourceAttribute(bId, new AnnotatedAttribute("attrBA", "test", false));

            resourceBundle.addAttributeToParent(attrDA, d);
            resourceBundle.addAttributeToChild(attrDA, a);
            resourceBundle.addAttributeToParent(attrAB, a);
            resourceBundle.addAttributeToChild(attrAB, b);
            resourceBundle.addAttributeToParent(attrBA, b);
            resourceBundle.addAttributeToChild(attrBA, a);

            resourceBundle.setResourceAttributeValid(attrDA);
            resourceBundle.setResourceAttributeValid(attrAB);
            resourceBundle.setResourceAttributeValid(attrBA);

            resourceBundle.addResourceGroupValidity(d, false);
            resourceBundle.addResourceGroupValidity(a, true);
            resourceBundle.addResourceGroupValidity(b, true);

            cascadingDelete.handleBundle(resourceBundle, groupMap);

            assertThat(resourceBundle.isValidResourceGroup(a)).isFalse();
            assertThat(resourceBundle.isValidResourceGroup(b)).isFalse();
        }

        @Test
        void testCycleWithProtectedGroup() {
            // Establish cyclic dependencies:
            resourceBundle.addAttributeToParent(resourceAttribute, parentResourceGroup1);
            resourceBundle.addAttributeToChild(resourceAttribute, resourceGroup);
            resourceBundle.addAttributeToParent(resourceAttribute2, resourceGroup);
            resourceBundle.addAttributeToChild(resourceAttribute2, resourceGroup2);
            resourceBundle.addAttributeToParent(resourceAttribute3, resourceGroup2);
            resourceBundle.addAttributeToChild(resourceAttribute3, parentResourceGroup1); // Cycle back

            // Mark attributes as valid initially
            resourceBundle.setResourceAttributeValid(resourceAttribute);
            resourceBundle.setResourceAttributeValid(resourceAttribute2);
            resourceBundle.setResourceAttributeValid(resourceAttribute3);

            // Define resource groups with `includeReferenceOnly`
            groupMap.put("group1", new AnnotatedAttributeGroup("", "group1", "", "", List.of(), List.of(), true));  // Can be deleted
            groupMap.put("group2", new AnnotatedAttributeGroup("", "group2", "", "", List.of(), List.of(), true));  // Can be deleted
            groupMap.put("group3", new AnnotatedAttributeGroup("", "group3", "", "", List.of(), List.of(), false)); // Protected (refOnly == false)

            // Set resource group validity (Trigger cascading deletion)
            resourceBundle.addResourceGroupValidity(resourceGroup, false); // Mark invalid, triggers deletion
            resourceBundle.addResourceGroupValidity(resourceGroup2, true);
            resourceBundle.addResourceGroupValidity(parentResourceGroup1, true); // Protected group (should survive)

            // Run full bundle cascading delete
            cascadingDelete.handleBundle(resourceBundle, groupMap);

            // Ensure attributes are removed from the child mapping
            assertThat(resourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(resourceAttribute);
            assertThat(resourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(resourceAttribute2);
            assertThat(resourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(resourceAttribute3);

            // Attributes should be marked invalid
            assertThat(resourceBundle.resourceAttributeValid(resourceAttribute)).isFalse();
            assertThat(resourceBundle.resourceAttributeValid(resourceAttribute2)).isFalse();
            assertThat(resourceBundle.resourceAttributeValid(resourceAttribute3)).isFalse();

            // **ResourceGroup2 and resourceGroup should be deleted, but parentResourceGroup1 should be protected**
            assertThat(resourceBundle.isValidResourceGroup(parentResourceGroup1)).isTrue(); // Should **survive** (protected)
            assertThat(resourceBundle.isValidResourceGroup(resourceGroup2)).isFalse(); // Should be deleted
            assertThat(resourceBundle.isValidResourceGroup(resourceGroup)).isFalse(); // Should be deleted
        }

        @Nested
        class HandleBranchingGraphBundle {

            @Test
            void branchingGraphFullDeletion() {
                ResourceAttribute attr1 = new ResourceAttribute(rid("r/attr1"), new AnnotatedAttribute("attr1", "test", false));
                ResourceAttribute attr2 = new ResourceAttribute(rid("r/attr2"), new AnnotatedAttribute("attr2", "test", false));
                ResourceAttribute attr3 = new ResourceAttribute(rid("r/attr3"), new AnnotatedAttribute("attr3", "test", false));

                ResourceGroup root = new ResourceGroup(rid("r/root"), "groupRoot");
                ResourceGroup branch1 = new ResourceGroup(rid("r/branch1"), "groupB1");
                ResourceGroup branch2 = new ResourceGroup(rid("r/branch2"), "groupB2");
                ResourceGroup leaf1 = new ResourceGroup(rid("r/leaf1"), "groupL1");
                ResourceGroup leaf2 = new ResourceGroup(rid("r/leaf2"), "groupL2");

                // Define all groups
                groupMap.put("groupRoot", new AnnotatedAttributeGroup("", "groupRoot", "", "", List.of(), List.of(), true));
                groupMap.put("groupB1", new AnnotatedAttributeGroup("", "groupB1", "", "", List.of(), List.of(), true));
                groupMap.put("groupB2", new AnnotatedAttributeGroup("", "groupB2", "", "", List.of(), List.of(), true));
                groupMap.put("groupL1", new AnnotatedAttributeGroup("", "groupL1", "", "", List.of(), List.of(), true));
                groupMap.put("groupL2", new AnnotatedAttributeGroup("", "groupL2", "", "", List.of(), List.of(), true));

                // Create branching structure:
                //   root → attr1 → branch1 → attr2 → leaf1
                //        → attr3 → branch2 → leaf2

                resourceBundle.addAttributeToParent(attr1, root);
                resourceBundle.addAttributeToChild(attr1, branch1);
                resourceBundle.addAttributeToParent(attr2, branch1);
                resourceBundle.addAttributeToChild(attr2, leaf1);
                resourceBundle.addAttributeToParent(attr3, root);
                resourceBundle.addAttributeToChild(attr3, branch2);
                resourceBundle.addAttributeToChild(attr3, leaf2);

                resourceBundle.setResourceAttributeValid(attr1);
                resourceBundle.setResourceAttributeValid(attr2);
                resourceBundle.setResourceAttributeValid(attr3);

                // Mark root as invalid to trigger full deletion
                resourceBundle.addResourceGroupValidity(root, false);
                resourceBundle.addResourceGroupValidity(branch1, true);
                resourceBundle.addResourceGroupValidity(branch2, true);
                resourceBundle.addResourceGroupValidity(leaf1, true);
                resourceBundle.addResourceGroupValidity(leaf2, true);

                // Run full bundle cascading delete
                cascadingDelete.handleBundle(resourceBundle, groupMap);

                // Attributes should all be removed
                assertThat(resourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(attr1);
                assertThat(resourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(attr2);
                assertThat(resourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(attr3);

                // Ensure all dependent groups are deleted
                assertThat(resourceBundle.isValidResourceGroup(branch1)).isFalse();
                assertThat(resourceBundle.isValidResourceGroup(branch2)).isFalse();
                assertThat(resourceBundle.isValidResourceGroup(leaf1)).isFalse();
                assertThat(resourceBundle.isValidResourceGroup(leaf2)).isFalse();
            }

            @Test
            void branchingGraphWithProtectedGroup() {
                ResourceAttribute attr1 = new ResourceAttribute(rid("r/attr1"), new AnnotatedAttribute("attr1", "test", false));
                ResourceAttribute attr2 = new ResourceAttribute(rid("r/attr2"), new AnnotatedAttribute("attr2", "test", false));

                ResourceGroup root = new ResourceGroup(rid("r/root"), "groupRoot");
                ResourceGroup branch1 = new ResourceGroup(rid("r/branch1"), "groupB1");
                ResourceGroup branch2 = new ResourceGroup(rid("r/branch2"), "groupB2");

                // Define groups with branch2 protected
                groupMap.put("groupRoot", new AnnotatedAttributeGroup("", "groupRoot", "", "", List.of(), List.of(), true));
                groupMap.put("groupB1", new AnnotatedAttributeGroup("", "groupB1", "", "", List.of(), List.of(), true));
                groupMap.put("groupB2", new AnnotatedAttributeGroup("", "groupB2", "", "", List.of(), List.of(), false)); // Protected group

                // Create branching structure:
                //   root → attr1 → branch1 (deletable)
                //        → attr2 → branch2 (protected)

                resourceBundle.addAttributeToParent(attr1, root);
                resourceBundle.addAttributeToChild(attr1, branch1);
                resourceBundle.addAttributeToParent(attr2, root);
                resourceBundle.addAttributeToChild(attr2, branch2);

                resourceBundle.setResourceAttributeValid(attr1);
                resourceBundle.setResourceAttributeValid(attr2);

                // Mark root as invalid to trigger deletion
                resourceBundle.addResourceGroupValidity(root, false);
                resourceBundle.addResourceGroupValidity(branch1, true);
                resourceBundle.addResourceGroupValidity(branch2, true);

                // Run full bundle cascading delete
                cascadingDelete.handleBundle(resourceBundle, groupMap);

                // Ensure branch1 is deleted but branch2 is protected
                assertThat(resourceBundle.isValidResourceGroup(branch1)).isFalse();
                assertThat(resourceBundle.isValidResourceGroup(branch2)).isTrue(); // Protected
            }

            @Test
            void asymmetricBranchingWithPartialDeletion() {
                ResourceAttribute attr1 = new ResourceAttribute(rid("r/attr1"), new AnnotatedAttribute("attr1", "test", false));
                ResourceAttribute attr2 = new ResourceAttribute(rid("r/attr2"), new AnnotatedAttribute("attr2", "test", false));

                ResourceGroup root = new ResourceGroup(rid("r/root"), "groupRoot");
                ResourceGroup branch1 = new ResourceGroup(rid("r/branch1"), "groupB1");
                ResourceGroup branch2 = new ResourceGroup(rid("r/branch2"), "groupB2");
                ResourceGroup leaf1 = new ResourceGroup(rid("r/reaf1"), "groupL1");

                // Define groups
                groupMap.put("groupRoot", new AnnotatedAttributeGroup("", "groupRoot", "", "", List.of(), List.of(), true));
                groupMap.put("groupB1", new AnnotatedAttributeGroup("", "groupB1", "", "", List.of(), List.of(), true));
                groupMap.put("groupB2", new AnnotatedAttributeGroup("", "groupB2", "", "", List.of(), List.of(), true)); // Should be deleted
                groupMap.put("groupL1", new AnnotatedAttributeGroup("", "groupL1", "", "", List.of(), List.of(), true));

                // Create asymmetric branching structure:
                //   root → attr1 → branch1 → attr2 → leaf1
                //        → attr2 → branch2 (branch2 has no children)

                resourceBundle.addAttributeToParent(attr1, root);
                resourceBundle.addAttributeToChild(attr1, branch1);
                resourceBundle.addAttributeToParent(attr2, branch1);
                resourceBundle.addAttributeToChild(attr2, leaf1);
                resourceBundle.addAttributeToParent(attr2, root);
                resourceBundle.addAttributeToChild(attr2, branch2);

                resourceBundle.setResourceAttributeValid(attr1);
                resourceBundle.setResourceAttributeValid(attr2);

                // Mark root as invalid to trigger deletion
                resourceBundle.addResourceGroupValidity(root, false);
                resourceBundle.addResourceGroupValidity(branch1, true);
                resourceBundle.addResourceGroupValidity(branch2, true);
                resourceBundle.addResourceGroupValidity(leaf1, true);

                // Run full bundle cascading delete
                cascadingDelete.handleBundle(resourceBundle, groupMap);

                // Ensure branch1 and leaf1 are deleted
                assertThat(resourceBundle.isValidResourceGroup(branch1)).isFalse();
                assertThat(resourceBundle.isValidResourceGroup(leaf1)).isFalse();

                // **branch2 should also be deleted because attr2 was invalidated**
                assertThat(resourceBundle.isValidResourceGroup(branch2)).isFalse();
            }
        }
    }

    @Nested
    class HandleParents {

        @Test
        void NoMustHaveNotRemoved() {
            resourceBundle.addAttributeToParent(resourceAttribute, parentResourceGroup1);
            resourceBundle.addAttributeToParent(resourceAttribute, parentResourceGroup2);
            resourceBundle.addAttributeToChild(resourceAttribute, resourceGroup);
            resourceBundle.setResourceAttributeValid(resourceAttribute);

            Set<ResourceGroup> result = cascadingDelete.handleParents(resourceBundle, resourceGroup);

            assertThat(resourceBundle.childResourceGroupToResourceAttributesMap()).doesNotContainKey(resourceGroup);
            assertThat(result).isEmpty();
            assertThat(resourceBundle.resourceAttributeValid(resourceAttribute)).isFalse();
        }

        @Test
        void mustHaveAttributesTriggerInvalidation() {
            ResourceAttribute mustHaveAttribute = new ResourceAttribute(rid("r/mustHave"), new AnnotatedAttribute("mustHave", "test", true));
            resourceBundle.addAttributeToParent(mustHaveAttribute, parentResourceGroup1);
            resourceBundle.addAttributeToParent(mustHaveAttribute, parentResourceGroup2);

            resourceBundle.addAttributeToChild(mustHaveAttribute, resourceGroup);
            resourceBundle.setResourceAttributeValid(mustHaveAttribute);

            Set<ResourceGroup> result = cascadingDelete.handleParents(resourceBundle, resourceGroup);

            assertThat(result).containsExactlyInAnyOrder(parentResourceGroup1, parentResourceGroup2);
        }

        @Test
        void everyMustHaveAttributeSharingATargetIsEscalated() {
            // Three DISTINCT attributes, owned by three different resources, all reference the same
            // resourceGroup and are each must-have. "wasLastChild" must be evaluated per attribute:
            // whichever attribute happens to be processed last in the shared target's incoming set must
            // not be the only one escalated - each attribute independently lost its only child.
            ResourceGroup parentResourceGroup3 = new ResourceGroup(rid("r/resourceP3"), "group5");
            groupMap.put("group5", new AnnotatedAttributeGroup("", "group5", "", "", List.of(), List.of(), true));

            ResourceAttribute mustHaveAttribute1 = new ResourceAttribute(rid("r/mustHaveOwner1"), new AnnotatedAttribute("mustHaveAttr1", "test", true));
            ResourceAttribute mustHaveAttribute2 = new ResourceAttribute(rid("r/mustHaveOwner2"), new AnnotatedAttribute("mustHaveAttr2", "test", true));
            ResourceAttribute mustHaveAttribute3 = new ResourceAttribute(rid("r/mustHaveOwner3"), new AnnotatedAttribute("mustHaveAttr3", "test", true));

            resourceBundle.addAttributeToParent(mustHaveAttribute1, parentResourceGroup1);
            resourceBundle.addAttributeToChild(mustHaveAttribute1, resourceGroup);

            resourceBundle.addAttributeToParent(mustHaveAttribute2, parentResourceGroup2);
            resourceBundle.addAttributeToChild(mustHaveAttribute2, resourceGroup);

            resourceBundle.addAttributeToParent(mustHaveAttribute3, parentResourceGroup3);
            resourceBundle.addAttributeToChild(mustHaveAttribute3, resourceGroup);

            resourceBundle.setResourceAttributeValid(mustHaveAttribute1);
            resourceBundle.setResourceAttributeValid(mustHaveAttribute2);
            resourceBundle.setResourceAttributeValid(mustHaveAttribute3);

            Set<ResourceGroup> result = cascadingDelete.handleParents(resourceBundle, resourceGroup);

            assertThat(result).containsExactlyInAnyOrder(parentResourceGroup1, parentResourceGroup2, parentResourceGroup3);
        }

        @Test
        void mustHaveAttributeWithRemainingChildIsNotEscalated() {
            // mustHaveAttribute references TWO children: resourceGroup and resourceGroup2.
            // Losing resourceGroup alone must not invalidate the attribute or escalate to its
            // parent, since resourceGroup2 is still a valid child.
            ResourceAttribute mustHaveAttribute = new ResourceAttribute(rid("r/mustHaveMultiChild"), new AnnotatedAttribute("mustHaveMultiChild", "test", true));

            resourceBundle.addAttributeToParent(mustHaveAttribute, parentResourceGroup1);
            resourceBundle.addAttributeToChild(mustHaveAttribute, resourceGroup);
            resourceBundle.addAttributeToChild(mustHaveAttribute, resourceGroup2);
            resourceBundle.setResourceAttributeValid(mustHaveAttribute);

            Set<ResourceGroup> result = cascadingDelete.handleParents(resourceBundle, resourceGroup);

            assertThat(result).isEmpty();
            assertThat(resourceBundle.resourceAttributeValid(mustHaveAttribute)).isTrue();
        }
    }

    @Nested
    class HandleChildren {

        @Test
        void attributeStillAlive() {
            resourceBundle.addAttributeToParent(resourceAttribute, parentResourceGroup1);
            resourceBundle.addAttributeToParent(resourceAttribute, parentResourceGroup2);
            resourceBundle.addAttributeToChild(resourceAttribute, resourceGroup);
            resourceBundle.setResourceAttributeValid(resourceAttribute);

            Set<ResourceGroup> result = cascadingDelete.handleChildren(resourceBundle, groupMap, parentResourceGroup2);

            assertThat(resourceBundle.resourceAttributeToChildResourceGroup()).containsKey(resourceAttribute);
            assertThat(result).isEmpty();
        }

        @Test
        void attributeDeletedAfterAllParentsDied() {
            resourceBundle.addAttributeToParent(resourceAttribute, parentResourceGroup1);
            resourceBundle.addAttributeToParent(resourceAttribute, parentResourceGroup2);
            resourceBundle.addAttributeToChild(resourceAttribute, resourceGroup);
            resourceBundle.setResourceAttributeValid(resourceAttribute);

            Set<ResourceGroup> result1 = cascadingDelete.handleChildren(resourceBundle, groupMap, parentResourceGroup2);
            Set<ResourceGroup> result2 = cascadingDelete.handleChildren(resourceBundle, groupMap, parentResourceGroup1);

            assertThat(result1).isEmpty();
            assertThat(result2).contains(resourceGroup);
            assertThat(resourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(resourceAttribute);
        }

        @Test
        void doesRemoveDirectlyLoadedIfEmpty() {
            resourceBundle.addAttributeToParent(resourceAttribute, parentResourceGroup1);
            resourceBundle.addAttributeToChild(resourceAttribute, resourceGroup);
            resourceBundle.setResourceAttributeValid(resourceAttribute);

            Set<ResourceGroup> result = cascadingDelete.handleChildren(resourceBundle, groupMap, parentResourceGroup1);

            assertThat(result).contains(resourceGroup);
            assertThat(resourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(resourceAttribute);
        }

        @Test
        void doesNotRemoveDirectlyLoaded() {
            resourceBundle.addAttributeToParent(resourceAttribute2, parentResourceGroup2);
            resourceBundle.addAttributeToChild(resourceAttribute2, resourceGroup2);
            resourceBundle.setResourceAttributeValid(resourceAttribute);

            Set<ResourceGroup> result = cascadingDelete.handleChildren(resourceBundle, groupMap, parentResourceGroup2);

            assertThat(result).isEmpty();
            assertThat(resourceBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(resourceAttribute);
        }
    }
}
