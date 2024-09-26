package de.medizininformatikinitiative.torch.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.*;

@Component
public class ConsentCodeMapper {

    private Map<String, List<String>> consentMap;

    public ConsentCodeMapper(
            @Value("${torch.mapping.consent}") String consentFilePath
    ) throws IOException {
        this.consentMap = new HashMap<>();
        buildConsentMap(consentFilePath);
    }

    // Method to build the map based on the JSON file
    private void buildConsentMap(String filePath) throws IOException {
        System.out.println(System.getProperty("user.name"));
        File file = new File(filePath);
        System.out.println("File exists: " + file.exists());
        System.out.println("Can read: " + file.canRead());
        ObjectMapper mapper = new ObjectMapper();
        JsonNode consentMappingData = mapper.readTree(file.getAbsoluteFile());
        for (JsonNode consent : consentMappingData) {
            String keyCode = consent.get("key").get("code").asText();
            List<String> relevantCodes = new ArrayList<>();

            JsonNode fixedCriteria = consent.get("fixedCriteria");
            if (fixedCriteria != null) {
                for (JsonNode criterion : fixedCriteria) {
                    Iterator<JsonNode> values = criterion.get("value").elements();
                    while (values.hasNext()) {
                        JsonNode value = values.next();
                        relevantCodes.add(value.get("code").asText());
                    }
                }
            }

            consentMap.put(keyCode, relevantCodes);
        }
    }

    // Method to get the relevant codes for a given key (e.g., "yes-yes-yes-yes")
    public List<String> getRelevantCodes(String key) {
        return consentMap.getOrDefault(key, Collections.emptyList());
    }
}