package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.management.ResourceAttribute;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        groupMap.put("group2", new AnnotatedAttributeGroup("", "group2", "", List.of(), List.of(), false));
        parentResourceGroup2 = new ResourceGroup("resourceP2", "group4");
    }

    @Nested
    class HandleChildren {
        @Test
        void attributeStillAlive() {
            Set<ResourceAttribute> resourceAttributes = new HashSet<>();
            resourceAttributes.add(resourceAttribute);
            coreResourceBundle.addAttributeToParent(resourceAttribute, parentResourceGroup1);
            coreResourceBundle.addAttributeToParent(resourceAttribute, parentResourceGroup2);
            coreResourceBundle.addAttributeToChild(resourceAttribute, resourceGroup);

            Set<ResourceGroup> result = cascadingDelete.handleChildren(coreResourceBundle, groupMap, parentResourceGroup2);
            assertTrue(coreResourceBundle.resourceAttributeToChildResourceGroup().containsKey(resourceAttribute));
            assertTrue(result.isEmpty());
        }

        @Test
        void attributeDeletedAfterAllParentsDied() {
            Set<ResourceAttribute> resourceAttributes = new HashSet<>();
            resourceAttributes.add(resourceAttribute);
            coreResourceBundle.addAttributeToParent(resourceAttribute, parentResourceGroup1);
            coreResourceBundle.addAttributeToParent(resourceAttribute, parentResourceGroup2);
            coreResourceBundle.addAttributeToChild(resourceAttribute, resourceGroup);

            Set<ResourceGroup> result1 = cascadingDelete.handleChildren(coreResourceBundle, groupMap, parentResourceGroup2);
            Set<ResourceGroup> result2 = cascadingDelete.handleChildren(coreResourceBundle, groupMap, parentResourceGroup1);
            assertTrue(result1.isEmpty());
            assertTrue(result2.contains(resourceGroup));
            assertFalse(coreResourceBundle.resourceAttributeToChildResourceGroup().containsKey(resourceAttribute));
        }

        @Test
        void doesRemoveDirectlyLoadedIfEmpty() {

            Set<ResourceAttribute> resourceAttributes = new HashSet<>();
            resourceAttributes.add(resourceAttribute);

            coreResourceBundle.addAttributeToParent(resourceAttribute, parentResourceGroup1);
            coreResourceBundle.addAttributeToChild(resourceAttribute, resourceGroup);

            Set<ResourceGroup> result = cascadingDelete.handleChildren(coreResourceBundle, groupMap, parentResourceGroup1);

            assertTrue(result.contains(resourceGroup));
            assertFalse(coreResourceBundle.resourceAttributeToChildResourceGroup().containsKey(resourceAttribute));

        }

        @Test
        void doesNotRemoveDirectlyLoaded() {
            Set<ResourceAttribute> resourceAttributes = new HashSet<>();
            resourceAttributes.add(resourceAttribute2);

            coreResourceBundle.addAttributeToParent(resourceAttribute2, parentResourceGroup2);
            coreResourceBundle.addAttributeToChild(resourceAttribute2, resourceGroup2);

            Set<ResourceGroup> result = cascadingDelete.handleChildren(coreResourceBundle, groupMap, parentResourceGroup2);

            assertTrue(result.isEmpty());
            assertFalse(coreResourceBundle.resourceAttributeToChildResourceGroup().containsKey(resourceAttribute));

        }

    }


}