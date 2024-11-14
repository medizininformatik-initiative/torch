package de.medizininformatikinitiative.torch.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class ConsentCodeMapperIT {

    private ConsentCodeMapper consentCodeMapper;

    @BeforeEach
    public void setUp() throws IOException {
        // Use the real JSON file path or load a test JSON file
        String consentFilePath = "src/test/resources/mappings/consent-mappings.json";
        consentCodeMapper = new ConsentCodeMapper(consentFilePath, new ObjectMapper());
    }

    @Test
    public void testGetRelevantCodes_withValidKey() {
        // Test that codes are returned for the key "yes-yes-yes-yes"
        Set<String> relevantCodes = consentCodeMapper.getRelevantCodes("yes-yes-yes-yes");
        assertNotNull(relevantCodes);
        assertFalse(relevantCodes.isEmpty(), "Relevant codes should not be isEmpty");
        assertTrue(relevantCodes.contains("2.16.840.1.113883.3.1937.777.24.5.3.8"), "Should contain code 2.16.840.1.113883.3.1937.777.24.5.3.8");
        assertTrue(relevantCodes.contains("2.16.840.1.113883.3.1937.777.24.5.3.46"), "Should contain code 2.16.840.1.113883.3.1937.777.24.5.3.46");
    }

    @Test
    public void testGetRelevantCodes_withInvalidKey() {
        // Test that an isEmpty list is returned for an invalid key
        Set<String> relevantCodes = consentCodeMapper.getRelevantCodes("invalid-key");
        assertNotNull(relevantCodes);
        assertTrue(relevantCodes.isEmpty(), "Relevant codes should be isEmpty for an invalid key");
    }

    @Test
    public void testConsentMappingLoadedCorrectly() {
        // Ensure that the map is loaded correctly with a valid key
        Set<String> relevantCodes = consentCodeMapper.getRelevantCodes("yes-yes-yes-yes");
        assertEquals(10, relevantCodes.size(), "Should contain 10 relevant codes for key 'yes-yes-yes-yes'");
    }
}
