package de.medizininformatikinitiative.torch.testUtil;


import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.util.ResourceReader;
import de.medizininformatikinitiative.torch.util.ResourceUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class FhirTestHelper {


    private static final Logger logger = LoggerFactory.getLogger(FhirTestHelper.class);
    private final FhirContext fhirContext;
    private final ResourceReader resourceReader;

    @Autowired
    public FhirTestHelper(FhirContext fhirContext, ResourceReader resourceReader) {
        this.fhirContext = fhirContext;
        this.resourceReader = resourceReader;
    }


    public Map<String, Bundle> loadExpectedResources(List<String> filePaths) throws IOException, PatientIdNotFoundException {
        Map<String, Bundle> expectedResources = new HashMap<>();
        for (String filePath : filePaths) {
            Bundle bundle = (Bundle) resourceReader.readResource(filePath);
            String patientId = ResourceUtils.getPatientIdFromBundle(bundle);
            expectedResources.put(patientId, bundle);
        }
        return expectedResources;
    }

    public void validateBundles(Map<String, Bundle> bundles, Map<String, Bundle> expectedResources) {
        for (Map.Entry<String, Bundle> entry : bundles.entrySet()) {
            String patientId = entry.getKey();
            Bundle bundle = entry.getValue();
            Bundle expectedBundle = expectedResources.get(patientId);


            // Remove meta.lastUpdated from all contained resources in both bundles
            removeMetaLastUpdated(bundle);
            removeMetaLastUpdated(expectedBundle);

            if (expectedBundle == null) {
                throw new AssertionError("No expected bundle found for patientId " + patientId);
            }

            // Get resources from both bundles and map them based on their profile
            Map<String, Resource> actualResourceMap = mapResourcesByProfile(bundle);
            Map<String, Resource> expectedResourceMap = mapResourcesByProfile(expectedBundle);

            logger.debug(" Actual Bundle  \n {}", fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle));
            logger.debug(" Expected Bundle \n {}", fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expectedBundle));


            // Compare the two maps
            for (Map.Entry<String, Resource> expectedEntry : expectedResourceMap.entrySet()) {
                String profileKey = expectedEntry.getKey();
                Resource expectedResource = expectedEntry.getValue();

                if (!actualResourceMap.containsKey(profileKey)) {
                    throw new AssertionError("Missing resource for profile: " + profileKey + " for Patient: " + patientId);
                }

                Resource actualResource = actualResourceMap.get(profileKey);

                // Compare the actual and expected resources as strings after encoding
                if (!fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expectedResource)
                        .equals(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(actualResource))) {
                    throw new AssertionError("Expected resource for profile " + profileKey + " does not match actual resource.");
                }
            }
        }
    }

    // Helper static function to map resources by their profile
    private Map<String, Resource> mapResourcesByProfile(Bundle bundle) {
        Map<String, Resource> resourceMap = new HashMap<>();
        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            Resource resource = entry.getResource();
            String profileKey = extractProfileFromResource(resource);  // Extract the profile URL
            if (profileKey != null) {
                resourceMap.put(profileKey, resource);
            }
        }
        return resourceMap;
    }

    // Implement a method to extract the profile from a resource
    private String extractProfileFromResource(Resource resource) {
        // Extract the first profile URL from the resource's meta field
        if (resource.getMeta() != null && resource.getMeta().hasProfile()) {
            return resource.getMeta().getProfile().get(0).getValue();  // Use the first profile URL as the key
        }
        return null;  // Return null if no profile is found
    }

    private void removeMetaLastUpdated(Bundle bundle) {
        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            Resource resource = entry.getResource();
            if (resource != null && resource.hasMeta() && resource.getMeta().hasLastUpdated()) {
                logger.info("Removed lastUpdated ");
                resource.getMeta().setLastUpdated(null);
            }
        }
    }
}


