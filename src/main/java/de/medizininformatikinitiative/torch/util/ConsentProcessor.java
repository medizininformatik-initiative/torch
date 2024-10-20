package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component  // This makes the class a Spring bean
public class ConsentProcessor {

    public static final Logger logger = LoggerFactory.getLogger(ConsentProcessor.class);

    private final FhirContext fhirContext;

    @Autowired  // Autowiring the FhirContext
    public ConsentProcessor(FhirContext fhirContext) {
        this.fhirContext = fhirContext;
    }

    public List<Base> extractConsentProvisions(DomainResource domainResource) {
        try {
            // Using the autowired FhirContext to extract Encounter.provision.provision elements from the resource
            return fhirContext.newFhirPath().evaluate(domainResource, "Consent.provision.provision", Base.class);
        } catch (Exception e) {
            logger.error("Error extracting provisions with FHIRPath", e);
            return Collections.emptyList();  // Return an empty list in case of errors
        }
    }

    public Map<String, List<Period>> transformToConsentPeriodByCode(DomainResource domainResource, Set<String> validCodes) throws ConsentViolatedException {
        // Map to hold lists of ConsentPeriod for each code
        validCodes.forEach(code -> logger.debug("validCode {}", code));
        Map<String, List<Period>> consentPeriodMap = new HashMap<>();
        List<Base> provisionPeriodList = extractConsentProvisions(domainResource);

        // Iterate over the provisions to find periods for each valid code
        for (Base provisionBase : provisionPeriodList) {
            try {
                // Assuming provision has a period with start and end dates
                Consent.provisionComponent provision = (Consent.provisionComponent) provisionBase;
                Period period = provision.getPeriod();
                String code = provision.getCode().getFirst().getCoding().getFirst().getCode();

                logger.debug("Period found {} {} Code {}", period.getStart(), period.getEnd(), code);

                // Check if the code is in the list of valid codes
                if (!validCodes.contains(code)) {
                    continue; // Skip if code is not valid
                }

                logger.debug("Found valid code {}", code);

                // Extract start and end dates from the provision's period
                DateTimeType start = period.hasStart() ? period.getStartElement() : null;
                DateTimeType end = period.hasEnd() ? period.getEndElement() : null;

                // If no start or end period is present, skip to the next provision
                if (start == null || end == null) continue;




                // Add the new consent period to the map under the corresponding code
                consentPeriodMap.computeIfAbsent(code, k -> new ArrayList<>()).add(period);

            } catch (Exception e) {
                logger.error("Error processing provision period", e);
            }
        }

        // Handle the case where no valid periods were found for any code
        if (consentPeriodMap.isEmpty()) {
            throw new ConsentViolatedException("No valid start or end dates found for the provided valid codes");
        }

        if (!consentPeriodMap.keySet().equals(validCodes)) {
            throw new ConsentViolatedException("Resource does not have valid consents for every requested code");
        }

        return consentPeriodMap;
    }
}
