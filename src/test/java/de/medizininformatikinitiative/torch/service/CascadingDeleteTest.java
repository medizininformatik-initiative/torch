package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.management.ResourceAttribute;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class CascadingDeleteTest {


    private ResourceBundle coreResourceBundle;
    private ResourceBundle patientResourceBundle;
    private ResourceAttribute resourceAttribute;
    private ResourceGroup resourceGroup;
    private final CascadingDelete cascadingDelete = new CascadingDelete();


    @BeforeEach
    void setUp() {

        coreResourceBundle = new ResourceBundle();
        patientResourceBundle = new ResourceBundle();
        resourceAttribute = new ResourceAttribute("test", new AnnotatedAttribute("test", "test", "test", false));

        resourceGroup = mock(ResourceGroup.class);

    }

    @Nested
    class HandleChildren {
        @Test
        void testHandleChildren_RemovesAttributesCorrectly() {
            Set<ResourceAttribute> resourceAttributes = new HashSet<>();
            resourceAttributes.add(resourceAttribute);

            coreResourceBundle.resourceAttributeToChildResourceGroup().put(resourceAttribute, new HashSet<>(Collections.singletonList(resourceGroup)));

            coreResourceBundle.parentToAttributesMap().put(resourceGroup, new HashSet<>(Collections.singletonList(resourceAttribute)));

            Set<ResourceGroup> result = cascadingDelete.handleChildren(resourceAttributes, coreResourceBundle);

            assertTrue(result.contains(resourceGroup));
            assertFalse(coreResourceBundle.resourceAttributeToChildResourceGroup().containsKey(resourceAttribute));

        }

    }


}