package de.medizininformatikinitiative.util;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Patient;
import ca.uhn.fhir.util.ReflectionUtil;
import org.hl7.fhir.r4.model.Resource;

import java.util.List;

public class FHIRNavigator {

    private FhirContext fhirContext;

    public FHIRNavigator() {
        fhirContext = FhirContext.forR4();
    }

    /**
     * Navigates to the element with the given ID in the given resource.
     * TODO: Use for more involved navigation.
     * @param resource  The resource to navigate.
     * @param elementId The ID of the element to navigate to.
     * @return The navigated element, or null if the path is invalid.
     */
    public Base navigateToElement(Resource resource, String elementId) {
        // Split the elementId into parts using the dot as a delimiter
        String[] pathParts = elementId.split("\\.");

        // Skip the first part as it refers to the resource type
        Base element = resource;

        // Start from the second part
        for (int i = 1; i < pathParts.length; i++) {
            String part = pathParts[i];

            // Retrieve the child element dynamically
            element = getChildElement(element, part);
            if (element == null) {
                return null; // Return null if the path is invalid
            }
        }

        // Return the final navigated element
        return element;
    }

    private Base getChildElement(Base element, String childName) {
        System.out.println(element.children());
        List<Base> children = element.listChildrenByName(childName);
        if (children.isEmpty()) {
            return null; // No matching child found
        }
        return children.get(0); // Assuming we're interested in the first matching child
    }
}
