package de.medizininformatikinitiative.torch.consent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.exceptions.ValidationException;
import de.medizininformatikinitiative.torch.model.mapping.ConsentKey;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Provides a Map of all consent codes belonging to a consent key e.g. "yes-yes-yes-yes"
 */
public class ConsentCodeMapper {

    private final Map<ConsentKey, List<String>> consentMap;
    private final ObjectMapper objectMapper;


    public ConsentCodeMapper(String consentFilePath, ObjectMapper objectMapper) throws IOException {
        this.consentMap = new EnumMap<>(ConsentKey.class);
        this.objectMapper = objectMapper;
        buildConsentMap(consentFilePath);
    }

    // Method to build the map based on the JSON file
    private void buildConsentMap(String filePath) throws IOException {
        File file = new File(filePath);
        JsonNode consentMappingData = objectMapper.readTree(file.getAbsoluteFile());
        for (JsonNode consent : consentMappingData) {
            try {
                ConsentKey keyCode = ConsentKey.fromString(consent.get("key").get("code").asText());
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
            } catch (ValidationException e) {
                throw new IllegalStateException("Consent map has invalid key" + e.getMessage());
            }
        }
        if (consentMap.size() != ConsentKey.values().length) {
            throw new IllegalStateException("Consent map size does not match ConsentKey enum size");
        }
    }

    public Set<String> getRelevantCodes(ConsentKey key) {
        return new HashSet<>(consentMap.getOrDefault(key, Collections.emptyList()));
    }
}
