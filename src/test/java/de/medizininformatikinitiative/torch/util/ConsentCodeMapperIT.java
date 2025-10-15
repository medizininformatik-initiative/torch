package de.medizininformatikinitiative.torch.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.consent.ConsentCodeMapper;
import de.medizininformatikinitiative.torch.model.consent.ConsentCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConsentCodeMapperIT {

    public static final ConsentCode MIIConsentCode = new ConsentCode("urn:oid:2.16.840.1.113883.3.1937.777.24.5.3", "2.16.840.1.113883.3.1937.777.24.5.3.8");
    public static final ConsentCode CODE1 = new ConsentCode("s1", "code1");
    private ConsentCodeMapper consentCodeMapper;

    @BeforeEach
    void setUp() throws IOException {
        // Use the real JSON file path or load a test JSON file
        String consentFilePath = "src/test/resources/mappings/consent-mappings.json";
        consentCodeMapper = new ConsentCodeMapper(consentFilePath, new ObjectMapper());
    }

    @Test
    void combinedCodesValidCode() {
        Set<ConsentCode> relevantCodes = consentCodeMapper.getCombinedCodes(
                new ConsentCode("fdpg.mii.cds", "yes-yes-yes-yes")
        );

        assertThat(relevantCodes)
                .isNotNull()
                .hasSize(10)
                .contains(MIIConsentCode);
    }

    @Test
    void combinedCodesInvalidCode() {
        Set<ConsentCode> relevantCodes = consentCodeMapper.getCombinedCodes(
                new ConsentCode("fdpg.mii.cds", "invalid-key")
        );

        assertThat(relevantCodes)
                .isNotNull()
                .isEmpty();
    }

    @Test
    void combinedCodesUnknownSystem() {
        Set<ConsentCode> relevantCodes = consentCodeMapper.getCombinedCodes(
                new ConsentCode("unknown_System", "invalid-key")
        );

        assertThat(relevantCodes)
                .isNotNull()
                .isEmpty();
    }

    @Test
    void expandWithCombinedCodes() {
        Set<ConsentCode> relevantCodes = consentCodeMapper.addCombinedCodes(
                Set.of(CODE1, new ConsentCode("fdpg.mii.cds", "yes-yes-yes-yes"))
        );

        assertThat(relevantCodes)
                .isNotNull()
                .hasSize(11)
                .contains(CODE1, MIIConsentCode);
    }
}
