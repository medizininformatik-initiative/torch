package de.medizininformatikinitiative.torch.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.consent.ConsentCodeMapper;
import de.medizininformatikinitiative.torch.model.consent.ConsentCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ConsentCodeMapperIT {

    private ConsentCodeMapper consentCodeMapper;

    @BeforeEach
    void setUp() throws IOException {
        // Use the real JSON file path or load a test JSON file
        String consentFilePath = "src/test/resources/mappings/consent-mappings.json";
        consentCodeMapper = new ConsentCodeMapper(consentFilePath, new ObjectMapper());
    }

    @Test
    void testGetRelevantCodes_withValidKey() {
        Set<ConsentCode> relevantCodes = consentCodeMapper.getCombinedCodes(new ConsentCode("fdpg.mii.cds", "yes-yes-yes-yes"));
        assertNotNull(relevantCodes);
        assertEquals(10, relevantCodes.size(), "Should contain 10 relevant codes for key 'yes-yes-yes-yes'");
        assertTrue(relevantCodes.contains(new ConsentCode("urn:oid:2.16.840.1.113883.3.1937.777.24.5.3", "2.16.840.1.113883.3.1937.777.24.5.3.8")));
    }

    @Test
    void testGetRelevantCodes_withInvalidKey() {
        Set<ConsentCode> relevantCodes = consentCodeMapper.getCombinedCodes(new ConsentCode("fdpg.mii.cds", "invalid-key"));
        assertNotNull(relevantCodes);
        assertTrue(relevantCodes.isEmpty(), "Relevant codes should be isEmpty for an invalid key");
    }

    @Test
    void testGetRelevantCodes_withUnknownSystem() {
        Set<ConsentCode> relevantCodes = consentCodeMapper.getCombinedCodes(new ConsentCode("unknown_System", "invalid-key"));
        assertNotNull(relevantCodes);
        assertTrue(relevantCodes.isEmpty(), "Relevant codes should be isEmpty for an invalid system");
    }
}
