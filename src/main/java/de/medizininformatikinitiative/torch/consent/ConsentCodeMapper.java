package de.medizininformatikinitiative.torch.consent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.model.consent.ConsentCode;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * Provides a Map of all consent codes belonging to a consent key e.g. "yes-yes-yes-yes"
 */
public class ConsentCodeMapper {

    private final Map<ConsentCode, List<ConsentCode>> consentMap;
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
            JsonNode context = consent.get("context");
            String system = context.get("system").asText();
            String keyCode = consent.get("key").get("code").asText();
            List<ConsentCode> relevantCodes = new ArrayList<>();
            JsonNode fixedCriteria = consent.get("fixedCriteria");
            if (fixedCriteria != null) {
                for (JsonNode criterion : fixedCriteria) {
                    Iterator<JsonNode> values = criterion.get("value").elements();

                    while (values.hasNext()) {
                        JsonNode value = values.next();
                        relevantCodes.add(new ConsentCode(value.get("system").asText(), value.get("code").asText()));
                    }
                }
            }

            consentMap.put(new ConsentCode(system, keyCode), relevantCodes);
        }
    }

    public Set<ConsentCode> getCombinedCodes(ConsentCode key) {
        return new HashSet<>(consentMap.getOrDefault(key, Collections.emptyList()));
    }

    /**
     * Adds all combined codes for the given set of keys.
     * Replaced any expandable key with its combined codes and keeps the original key if no combined codes exist.
     *
     * @param keys codes that could be expandable
     * @return a set of combined codes and non expandable codes
     */
    public Set<ConsentCode> addCombinedCodes(Set<ConsentCode> keys) {
        return keys.stream()
                .flatMap(key -> {
                    Set<ConsentCode> combined = getCombinedCodes(key);
                    // if there are no combined codes, include the original key
                    return combined.isEmpty() ? Stream.of(key) : combined.stream();
                })
                .collect(Collectors.toSet());
    }
}
