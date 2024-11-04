package de.medizininformatikinitiative.torch.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Period;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
                    DomainResource resourceSrc = integrationTestSetup.readResource("src/test/resources/InputResources/Consent/" + resource);
                    assert (Objects.equals(resourceSrc.getResourceType().toString(), "Consent"));

                    // Transform to extract patient and consent period information
                    Map<String, List<Period>> consentPeriodMap = processor.transformToConsentPeriodByCode(resourceSrc, consentCodeMapper.getRelevantCodes("yes-yes-yes-yes")); // Adjusted to include provisions
                    logger.debug("map size {}", consentPeriodMap.entrySet());

                    // Update the map with the patient's consent periods
                    assert (!consentPeriodMap.get("2.16.840.1.113883.3.1937.777.24.5.3.10").isEmpty());

                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        });
    }

    @Test
    public void testConsentProcessor() throws IOException {
        ConsentProcessor processor = new ConsentProcessor(integrationTestSetup.fhirContext());
        String[] resources = {"VHF006_Consent.json"};

        Arrays.stream(resources).forEach(resource -> {
            try {
                DomainResource resourceSrc = integrationTestSetup.readResource("src/test/resources/InputResources/Consent/" + resource);
                assert (Objects.equals(resourceSrc.getResourceType().toString(), "Consent"));

                // Transform to extract patient and consent period information
                Map<String, List<Period>> consentPeriodMap = processor.transformToConsentPeriodByCode(
                        resourceSrc, consentCodeMapper.getRelevantCodes("yes-yes-yes-yes")
                ); // Adjusted to include provisions

                logger.debug("Consent map {}", consentPeriodMap.entrySet());

                // Update the map with the patient's consent periods
                assert (!consentPeriodMap.get("2.16.840.1.113883.3.1937.777.24.5.3.10").isEmpty());

            } catch (IOException | ConsentViolatedException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
