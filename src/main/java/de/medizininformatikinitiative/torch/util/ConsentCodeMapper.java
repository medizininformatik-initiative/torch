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

    // Use @Value to inject values from the YAML file directly into the constructor
    public ConsentCodeMapper(
            @Value("${orch.profile.dir}") String profileDir,
            @Value("${orch.mapping.consent}") String consentFilePath
    ) throws IOException {
        this.consentMap = new HashMap<>();
        System.out.println("Profile Directory: " + profileDir); // This can be used if needed later
        System.out.println("Consent File Path: " + consentFilePath);
        buildConsentMap(consentFilePath);
    }

    // Method to build the map based on the JSON file
    private void buildConsentMap(String filePath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode consentMappingData = mapper.readTree(new File(filePath));

        // Iterate over the consentMappingData and build the map
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

            // Store the list of codes under the key in the map
            consentMap.put(keyCode, relevantCodes);
        }
    }

    // Method to get the relevant codes for a given key (e.g., "yes-yes-yes-yes")
    public List<String> getRelevantCodes(String key) {
        return consentMap.getOrDefault(key, Collections.emptyList());
    }
}