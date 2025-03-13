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
    private ResourceGroup parentResourceGroup1;
    private ResourceGroup parentResourceGroup2;
    private final CascadingDelete cascadingDelete = new CascadingDelete();
    private Map<String, AnnotatedAttributeGroup> groupMap = new HashMap<>();

    @BeforeEach
    void setUp() {

        coreResourceBundle = new ResourceBundle();

        resourceAttribute = new ResourceAttribute("test", new AnnotatedAttribute("test", "test", "test", false));
        resourceAttribute2 = new ResourceAttribute("test2", new AnnotatedAttribute("test", "test", "test", false));

        resourceGroup = new ResourceGroup("resource1", "group1");
        parentResourceGroup1 = new ResourceGroup("resourceP1", "group3");
        groupMap.put("group1", new AnnotatedAttributeGroup("", "group1", "", List.of(), List.of(), true));
        resourceGroup2 = new ResourceGroup("resource1", "group2");
        groupMap.put("group2", new AnnotatedAttributeGroup("", "group2", "", List.of(), List.of(), true));
        parentResourceGroup2 = new ResourceGroup("resourceP2", "group4");
        groupMap.put("group4", new AnnotatedAttributeGroup("", "group1", "", List.of(), List.of(), false));
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
            assertThat(patientBatchWithConsent.bundles().get("Core")).isEqualTo(new PatientResourceBundle("Core", coreResourceBundle));
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
            ResourceAttribute mustHaveAttribute = new ResourceAttribute("mustHave", new AnnotatedAttribute("mustHave", "test", "test", true));
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