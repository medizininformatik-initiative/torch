package de.medizininformatikinitiative.torch.management;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.*;

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

    public boolean isInCompartment(String resourceType) {
        return compartmentMap.containsKey(resourceType);
    }

    public boolean isInCompartment(ResourceGroup resourceGroup) {
        String resourceType = resourceGroup.resourceId().split("/")[0];
        return compartmentMap.containsKey(resourceType);
    }

    public Set<String> getCompartment() {
        return compartmentMap.keySet();
    }
}