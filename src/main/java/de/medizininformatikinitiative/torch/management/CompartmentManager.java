package de.medizininformatikinitiative.torch.management;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import org.hl7.fhir.r4.model.Resource;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class CompartmentManager {

    private final Set<String> compartment;

    public CompartmentManager(String fileName) throws IOException {
        File file = new File(Objects.requireNonNull(getClass().getClassLoader().getResource(fileName)).getFile());
        JsonNode rootNode = new ObjectMapper().readTree(file);
        Set<String> codes = new HashSet<>();
        if (rootNode.has("resource")) {
            rootNode.get("resource").forEach(resourceNode -> {
                if (resourceNode.has("code")) {
                    if (resourceNode.has("param")) {
                        if (!resourceNode.get("param").isEmpty()) {
                            codes.add(resourceNode.get("code").asText());
                        }
                    }

                }
            });
        }
        this.compartment = Set.copyOf(codes);
    }

    public boolean isInCompartment(Resource resource) {
        return compartment.contains(resource.fhirType());
    }

    public boolean isInCompartment(String resourceType) {
        return compartment.contains(resourceType);
    }

    public boolean isInCompartment(ResourceGroup resourceGroup) {
        String resourceType = resourceGroup.resourceId().split("/")[0];
        return compartment.contains(resourceType);
    }

    public Set<String> getCompartment() {
        return compartment;
    }
}