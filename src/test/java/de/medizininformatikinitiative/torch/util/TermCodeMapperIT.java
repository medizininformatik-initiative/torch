package de.medizininformatikinitiative.torch.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.consent.ConsentCodeMapper;
import de.medizininformatikinitiative.torch.model.management.TermCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TermCodeMapperIT {

    public static final TermCode MIIConsentCode = new TermCode("urn:oid:2.16.840.1.113883.3.1937.777.24.5.3", "2.16.840.1.113883.3.1937.777.24.5.3.8");
    public static final TermCode CODE1 = new TermCode("s1", "code1");
    private ConsentCodeMapper consentCodeMapper;

    @BeforeEach
    void setUp() throws IOException {
        // Use the real JSON file path or load a test JSON file
        String consentFilePath = "src/test/resources/mappings/consent-mappings.json";
        consentCodeMapper = new ConsentCodeMapper(consentFilePath, new ObjectMapper());
    }

    @Test
    void combinedCodesValidCode() {
        Set<TermCode> relevantCodes = consentCodeMapper.getCombinedCodes(
                new TermCode("fdpg.mii.cds", "yes-yes-yes-yes")
        );

        assertThat(relevantCodes)
                .isNotNull()
                .hasSize(10)
                .contains(MIIConsentCode);
    }

    @Test
    void combinedCodesInvalidCode() {
        Set<TermCode> relevantCodes = consentCodeMapper.getCombinedCodes(
                new TermCode("fdpg.mii.cds", "invalid-key")
        );

        assertThat(relevantCodes)
                .isNotNull()
                .isEmpty();
    }

    @Test
    void combinedCodesUnknownSystem() {
        Set<TermCode> relevantCodes = consentCodeMapper.getCombinedCodes(
                new TermCode("unknown_System", "invalid-key")
        );

        assertThat(relevantCodes)
                .isNotNull()
                .isEmpty();
    }

    @Test
    void expandWithCombinedCodes() {
        Set<TermCode> relevantCodes = consentCodeMapper.addCombinedCodes(
                Set.of(CODE1, new TermCode("fdpg.mii.cds", "yes-yes-yes-yes"))
        );

        assertThat(relevantCodes)
                .isNotNull()
                .hasSize(11)
                .contains(CODE1, MIIConsentCode);
    }
}
