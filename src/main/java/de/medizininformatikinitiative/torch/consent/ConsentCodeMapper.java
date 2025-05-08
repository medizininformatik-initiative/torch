package de.medizininformatikinitiative.torch.consent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.*;


/**
 * Provides a Map of all consent codes belonging to a consent key e.g. "yes-yes-yes-yes"
 */
public class ConsentCodeMapper {

    private final Map<String, List<String>> consentMap;
    private final ObjectMapper objectMapper;


    public ConsentCodeMapper(String consentFilePath, ObjectMapper objectMapper) throws IOException {
        this.consentMap = new HashMap<>();
        this.objectMapper = objectMapper;
        buildConsentMap(consentFilePath);
    }

    // Method to build the map based on the JSON file
    private void buildConsentMap(String filePath) throws IOException {
        //Class get Resource as Stream
        //init method
        File file = new File(filePath);
        JsonNode consentMappingData = objectMapper.readTree(file.getAbsoluteFile());
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

    public Set<String> getRelevantCodes(String key) {
        return new HashSet<>(consentMap.getOrDefault(key, Collections.emptyList()));
    }
}