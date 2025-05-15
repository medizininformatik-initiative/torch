package de.medizininformatikinitiative.torch.management;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CompartmentManager {

    private final Map<String, List<String>> compartmentMap;

    public CompartmentManager(String fileName) throws IOException {
        ClassPathResource resource = new ClassPathResource(fileName);
        JsonNode rootNode = new ObjectMapper().readTree(resource.getInputStream());


        Map<String, List<String>> map = new HashMap<>();
        if (rootNode.has("resource")) {
            rootNode.get("resource").forEach(resourceNode -> {
                if (resourceNode.has("code") && resourceNode.has("param")) {
                    String code = resourceNode.get("code").asText();
                    List<String> params = new ArrayList<>();
                    resourceNode.get("param").forEach(paramNode -> params.add(paramNode.asText()));
                    map.put(code, params);
                }
            });
        }
        this.compartmentMap = Map.copyOf(map);
    }

    public List<String> getParams(String resourceType) {
        return compartmentMap.get(resourceType);
    }

    public boolean isInCompartment(Resource resource) {
        return compartmentMap.containsKey(resource.fhirType());
    }

    /**
     * Checks for a ResourceType or ReferenceString (RelativeURL) String if it is in Compartment
     *
     * @param input String to be handled
     * @return true if prefeix
     */
    public boolean isInCompartment(String input) {
        String resourceType;

        if (input.contains("/")) {
            // Looks like a reference string, get the part before '/'
            resourceType = input.split("/")[0];
        } else {
            // Assume it's a resourceType string already
            resourceType = input;
        }

        return compartmentMap.containsKey(resourceType);
    }

    public boolean isInCompartment(ResourceGroup resourceGroup) {
        return isInCompartment(resourceGroup.resourceId());
    }

    public Set<String> getCompartment() {
        return compartmentMap.keySet();
    }
}
