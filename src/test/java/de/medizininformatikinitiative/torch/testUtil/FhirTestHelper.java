package de.medizininformatikinitiative.torch.testUtil;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Component
public class FhirTestHelper {

    private final FhirContext fhirContext;

    @Autowired
    public FhirTestHelper(FhirContext fhirContext) {
        this.fhirContext = fhirContext;
    }

    /**
     * Equals on Entry level to ensure bundle content is correct, while ignoring dynamic metadata such as "Last Updated"
     *
     * @param actualBundles   Resulting bundles indexed by PatID after internal extracting operations e.g. after ResourceTransform
     * @param expectedBundles Expected Bundles indexed by PatID
     */
    public void validate(PatientBatchWithConsent actualBundles, Map<String, Bundle> expectedBundles) {

        for (String key : actualBundles.patientIds()) {
            Bundle bundle = actualBundles.get(key).getResourceBundle().toFhirBundle();
            Bundle expectedBundle = expectedBundles.get(key);

            removeMetaLastUpdatedFromEntries(bundle);
            removeMetaLastUpdatedFromEntries(expectedBundle);

            Map<String, Resource> actualResourceMap = mapResourcesByID(bundle);
            Map<String, Resource> expectedResourceMap = mapResourcesByID(expectedBundle);

            for (Map.Entry<String, Resource> expectedEntry : expectedResourceMap.entrySet()) {
                String profileKey = expectedEntry.getKey();
                Resource expectedResource = expectedEntry.getValue();

                if (!actualResourceMap.containsKey(profileKey)) {
                    throw new AssertionError("Missing resource for profile: " + profileKey + " for Patient: " + key);
                }

                Resource actualResource = actualResourceMap.get(profileKey);

                assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(actualResource))
                        .isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expectedResource));
            }
        }
    }

    // Helper static function to map resources by their id
    private Map<String, Resource> mapResourcesByID(Bundle bundle) {
        Map<String, Resource> resourceMap = new HashMap<>();
        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            Resource resource = entry.getResource();
            resourceMap.put(resource.getIdPart(), resource);
        }
        return resourceMap;
    }

    private void removeMetaLastUpdatedFromEntries(Bundle bundle) {
        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            Resource resource = entry.getResource();
            if (resource != null && resource.hasMeta() && resource.getMeta().hasLastUpdated()) {
                resource.getMeta().setLastUpdated(null);
            }
        }
    }
}
