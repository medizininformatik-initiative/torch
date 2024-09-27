package de.medizininformatikinitiative.torch;

import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.util.*;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.DomainResource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;

public class ConsentTest extends BaseTest {


    @Test
    public void testConsentHandler() throws IOException {
        ConsentCodeMapper consentCodeMapper=new ConsentCodeMapper("src/test/resources/mappings/consent-mappings.json");
        ConsentProcessor processor=new ConsentProcessor(cds.ctx);
        String[] resources = {"VHF006_Consent.json"};
        Arrays.stream(resources).forEach(resource -> {
            try {

                DomainResource resourceSrc = (DomainResource) ResourceReader.readResource("src/test/resources/InputResources/Consent/" + resource);
                assert(Objects.equals(resourceSrc.getResourceType().toString(), "Consent"));
                List<Base> provisionList = processor.extractConsentProvisions(resourceSrc);

                // Transform to extract patient and consent period information
                Map<String, List<ConsentPeriod>> consentPeriodMap = processor.transformToConsentPeriodByCode(resourceSrc, consentCodeMapper.getRelevantCodes("yes-yes-yes-yes")); // Adjusted to include provisions
                String patient = ResourceUtils.getPatientId(resourceSrc);

                // Update the map with the patient's consent periods
                assert(!consentPeriodMap.get("VHF-0006").isEmpty());

            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (PatientIdNotFoundException e) {
                throw new RuntimeException(e);
            }
        });
    }

}