package de.medizininformatikinitiative.torch.testUtil;


import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.model.crtdl.Attribute;
import de.medizininformatikinitiative.torch.model.crtdl.AttributeGroup;
import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;
import de.medizininformatikinitiative.torch.model.crtdl.DataExtraction;
import de.medizininformatikinitiative.torch.util.ResourceReader;
import de.medizininformatikinitiative.torch.util.ResourceUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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

    /**
     * Equals on Entry level to ensure bundle content is correct, while ignoring dynamic metadata such as "Last Updated"
     *
     * @param actualBundles   Resulting bundles indexed by PatID after internal extracting operations e.g. after ResourceTransform
     * @param expectedBundles Expected Bundles indexed by PatID
     */
    public void validate(Map<String, Bundle> actualBundles, Map<String, Bundle> expectedBundles) throws PatientIdNotFoundException {

        for (Map.Entry<String, Bundle> entry : actualBundles.entrySet()) {
            String patientId = entry.getKey();
            Bundle bundle = entry.getValue();
            Bundle expectedBundle = expectedBundles.get(patientId);

            removeMetaLastUpdatedFromEntries(bundle);
            removeMetaLastUpdatedFromEntries(expectedBundle);

            /*Assertions.assertEquals(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle),
                    fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expectedBundle));
*/
            Map<String, Resource> actualResourceMap = mapResourcesByProfile(bundle);
            Map<String, Resource> expectedResourceMap = mapResourcesByProfile(expectedBundle);


            for (Map.Entry<String, Resource> expectedEntry : expectedResourceMap.entrySet()) {
                String profileKey = expectedEntry.getKey();
                Resource expectedResource = expectedEntry.getValue();

                if (!actualResourceMap.containsKey(profileKey)) {
                    throw new AssertionError("Missing resource for profile: " + profileKey + " for Patient: " + patientId);
                }

                Resource actualResource = actualResourceMap.get(profileKey);

                assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(actualResource))
                        .isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expectedResource));
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

    private void removeMetaLastUpdatedFromEntries(Bundle bundle) {
        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            Resource resource = entry.getResource();
            if (resource != null && resource.hasMeta() && resource.getMeta().hasLastUpdated()) {
                resource.getMeta().setLastUpdated(null);
            }
        }
    }

    // Method for checking service health by calling the provided health endpoint
    public static void checkServiceHealth(String service, String healthEndpoint, String host, int port) {
        String url = String.format("http://%s:%d%s", host, port, healthEndpoint);

        WebClient webClient = WebClient.create();
        int attempts = 0;
        int maxAttempts = 10;

        while (attempts < maxAttempts) {
            try {
                Mono<String> responseMono = webClient.get()
                        .uri(url)
                        .retrieve()
                        .bodyToMono(String.class);

                String response = responseMono.block();
                if (response != null) {
                    logger.info("Health check passed for service: {} at {}", service, url);
                    return;
                }
            } catch (Exception e) {
                logger.warn("Health check failed for service: {} at {}. Retrying...", service, url);
            }
            attempts++;
            try {
                Thread.sleep(5000);  // Wait 5 seconds before retrying
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        throw new RuntimeException("Health check failed for service: " + service + " at " + url);
    }


    public static AttributeGroup emptyAttributeGroup(String profile) {
        return createAttributeGroup(profile, List.of());
    }


    public static AttributeGroup createAttributeGroup(String profile, List<Attribute> attributes) {
        return new AttributeGroup("test", profile, attributes, List.of());
    }

    Crtdl createCRTDL(List<AttributeGroup> groupList) {
        JsonNode node = JsonNodeFactory.instance.objectNode();
        Crtdl crtdl = new Crtdl(node, new DataExtraction(groupList));

        return crtdl;
    }

}


