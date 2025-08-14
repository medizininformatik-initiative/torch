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
import java.util.Objects;
import java.util.Set;


/**
 * Provides a Map of all consent codes belonging to a consent key e.g. "yes-yes-yes-yes" as
 * defined in the mapping file.
 */
public class ConsentCodeMapper {

    private final Map<ConsentKey, List<String>> consentMap;
    private final ObjectMapper objectMapper;


    /**
     * @param consentFilePath Path to the mapping file e.g. consent-mappings_fhir.json
     * @param objectMapper    Objectmapper for Json Processing
     * @throws IOException
     * @throws ValidationException
     */
    public ConsentCodeMapper(String consentFilePath, ObjectMapper objectMapper) throws IOException, ValidationException {
        this.consentMap = new EnumMap<>(ConsentKey.class);
        Objects.requireNonNull(objectMapper);
        this.objectMapper = objectMapper;
        buildConsentMap(consentFilePath);
    }

    // Method to build the map based on the JSON file
    private void buildConsentMap(String filePath) throws IOException, ValidationException {
        File file = new File(filePath);
        JsonNode consentMappingData = objectMapper.readTree(file.getAbsoluteFile());
        for (JsonNode consent : consentMappingData) {

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
        }
        if (consentMap.size() != ConsentKey.values().length) {
            throw new ValidationException("Consent map size does not match ConsentKey enum size");
        }
    }

    /**
     * @param key Consentkey to be handled
     * @return All codes associated with that key
     * e.g. for "no-no-no-no" in the current version of the codesystem
     * "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3" the codes
     * "2.16.840.1.113883.3.1937.777.24.5.3.47"
     * "2.16.840.1.113883.3.1937.777.24.5.3.49"
     * "2.16.840.1.113883.3.1937.777.24.5.3.9" will be returned
     */
    public Set<String> getRelevantCodes(ConsentKey key) {
        return new HashSet<>(consentMap.getOrDefault(key, Collections.emptyList()));
    }
}
