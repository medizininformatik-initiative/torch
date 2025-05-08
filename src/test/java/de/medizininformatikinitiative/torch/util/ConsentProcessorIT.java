package de.medizininformatikinitiative.torch.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.consent.ConsentCodeMapper;
import de.medizininformatikinitiative.torch.consent.ConsentProcessor;
import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.model.consent.Provisions;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import org.hl7.fhir.r4.model.Consent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

public class ConsentProcessorIT {

    protected static final Logger logger = LoggerFactory.getLogger(ConsentProcessorIT.class);

    private final IntegrationTestSetup integrationTestSetup = new IntegrationTestSetup();
    private final ConsentCodeMapper consentCodeMapper;

    public ConsentProcessorIT() throws IOException {
        // Initialize the ConsentCodeMapper as before
        consentCodeMapper = new ConsentCodeMapper("src/test/resources/mappings/consent-mappings.json", new ObjectMapper());
    }

    @Test
    public void testConsentProcessorFail() throws IOException {

        ConsentProcessor processor = new ConsentProcessor(integrationTestSetup.fhirContext());
        String[] resources = {"VHF006_Consent_Fail.json"};

        Arrays.stream(resources).forEach(resource -> {
            assertThrows(ConsentViolatedException.class, () -> {
                try {
                    Consent resourceSrc = (Consent) integrationTestSetup.readResource("src/test/resources/InputResources/Consent/" + resource);
                    Provisions provisions = processor.transformToConsentPeriodByCode(resourceSrc, consentCodeMapper.getRelevantCodes("yes-yes-yes-yes")); // Adjusted to include provisions
                    logger.debug("map size {}", provisions.periods().entrySet());
                    assertThat(provisions.periods().get("2.16.840.1.113883.3.1937.777.24.5.3.10").isEmpty()).isFalse();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        });
    }


    @ParameterizedTest
    @ValueSource(strings = {"VHF006_Consent.json"})
    public void testConsentProcessor(String resource) throws IOException, ConsentViolatedException {
        ConsentProcessor processor = new ConsentProcessor(integrationTestSetup.fhirContext());
        Consent resourceSrc = (Consent) integrationTestSetup.readResource("src/test/resources/InputResources/Consent/" + resource);
        Provisions provisions = processor.transformToConsentPeriodByCode(
                resourceSrc, consentCodeMapper.getRelevantCodes("yes-yes-yes-yes")
        );
        assertThat(provisions.periods().get("2.16.840.1.113883.3.1937.777.24.5.3.10").isEmpty()).isFalse();
    }
}
