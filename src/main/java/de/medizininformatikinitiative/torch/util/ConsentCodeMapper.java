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
           String consentFilePath
    ) throws IOException {
        this.consentMap = new HashMap<>();
        buildConsentMap(consentFilePath);
    }

    // Method to build the map based on the JSON file
    private void buildConsentMap(String filePath) throws IOException {
        File file = new File(filePath);

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

    public Set<String> getRelevantCodes(String key) {
        return new HashSet<>(consentMap.getOrDefault(key, Collections.emptyList()));
    }
}