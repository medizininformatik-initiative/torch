package de.medizininformatikinitiative.torch;

import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.util.*;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Period;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;

import static org.junit.Assert.assertThrows;

public class ConsentProcessorTest extends BaseTest {

    ConsentCodeMapper consentCodeMapper=new ConsentCodeMapper("src/test/resources/mappings/consent-mappings.json");

    public ConsentProcessorTest() throws IOException {
    }

    @Test
    public void testConsentProcessorFail() throws IOException {

        ConsentProcessor processor=new ConsentProcessor(ctx);
        String[] resources = {"VHF006_Consent_Fail.json"};
        Arrays.stream(resources).forEach(resource -> {
            assertThrows(ConsentViolatedException.class, () -> {
                try {

                    DomainResource resourceSrc = (DomainResource) ResourceReader.readResource("src/test/resources/InputResources/Consent/" + resource);
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

        ConsentProcessor processor=new ConsentProcessor(ctx);
        String[] resources = {"VHF006_Consent.json"};
        Arrays.stream(resources).forEach(resource -> {
                try {

                    DomainResource resourceSrc = (DomainResource) ResourceReader.readResource("src/test/resources/InputResources/Consent/" + resource);
                    assert (Objects.equals(resourceSrc.getResourceType().toString(), "Consent"));
                    // Transform to extract patient and consent period information
                    Map<String, List<Period>> consentPeriodMap = processor.transformToConsentPeriodByCode(resourceSrc, consentCodeMapper.getRelevantCodes("yes-yes-yes-yes")); // Adjusted to include provisions
                    logger.debug("Consent map {}", consentPeriodMap.entrySet());
                    // Update the map with the patient's consent periods
                    assert (!consentPeriodMap.get("2.16.840.1.113883.3.1937.777.24.5.3.10").isEmpty());

                } catch (IOException e) {
                    throw new RuntimeException(e);
                } catch (ConsentViolatedException e) {
                    throw new RuntimeException(e);
                }
        });
    }

}