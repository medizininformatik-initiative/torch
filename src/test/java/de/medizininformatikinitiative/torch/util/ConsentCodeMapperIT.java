package de.medizininformatikinitiative.torch.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.consent.ConsentCodeMapper;
import de.medizininformatikinitiative.torch.exceptions.ValidationException;
import de.medizininformatikinitiative.torch.model.mapping.ConsentKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class ConsentCodeMapperIT {

    private ConsentCodeMapper consentCodeMapper;

    @BeforeEach
    public void setUp() throws IOException, ValidationException {
        // Use the real JSON file path or load a test JSON file
        String consentFilePath = "src/test/resources/mappings/consent-mappings.json";
        consentCodeMapper = new ConsentCodeMapper(consentFilePath, new ObjectMapper());
    }

    @Test
    public void testGetRelevantCodesWithValidKey() {
        Set<String> relevantCodes = consentCodeMapper.getRelevantCodes(ConsentKey.YES_YES_YES_YES);

        assertThat(relevantCodes).hasSize(10).contains("2.16.840.1.113883.3.1937.777.24.5.3.8", "2.16.840.1.113883.3.1937.777.24.5.3.46");
    }
}
