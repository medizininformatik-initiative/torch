package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.ConsentHandler;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Period;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;

public class ConsentProcessor {
    public static final Logger logger = LoggerFactory.getLogger(ConsentProcessor.class);
    public final FhirContext ctx;
    public ConsentProcessor(FhirContext ctx) {
    this.ctx=ctx;
    }


    public List<Base> extractConsentProvisions(DomainResource domainResource) {
        try {
            // Using FHIRPath to extract Encounter.provision.provision elements from the resource


            return ctx.newFhirPath().evaluate(domainResource, "Consent.provision.provision",Base.class);
        } catch (Exception e) {
            logger.error("Error extracting provisions with FHIRPath ", e);
            return Collections.emptyList();  // Return an empty list in case of errors
        }
    }

    public Map<String, List<ConsentPeriod>> transformToConsentPeriodByCode(DomainResource domainResource, List<String> validCodes) {
        // Map to hold lists of ConsentPeriod for each code
        validCodes.forEach( code->logger.debug("validCode {}",code));
        Map<String, List<ConsentPeriod>> consentPeriodMap = new HashMap<>();
        List<Base> provisionPeriodList = extractConsentProvisions(domainResource);

        // Iterate over the provisions to find periods for each valid code
        for (Base provisionBase : provisionPeriodList) {
            try {

                // Assuming provision has a period with start and end dates
                Consent.provisionComponent provision = (Consent.provisionComponent) provisionBase;
                Period period = provision.getPeriod();
                String code = provision.getCode().getFirst().getCoding().getFirst().getCode().toString();

                logger.debug("Period found {} {} Code {}",period.getStart(),period.getEnd(),code);
                // Check if the code is in the list of valid codes
                if (!validCodes.contains(code)) {

                    continue; // Skip if code is not valid
                }
                logger.debug("Found valid code  {]",code);
                // Extract start and end dates from the provision's period
                LocalDateTime start = period.hasStart() ? LocalDateTime.parse(period.getStart().toString()) : null;
                LocalDateTime end = period.hasEnd() ? LocalDateTime.parse(period.getEnd().toString()) : null;

                // If no start or end period is present, skip to the next provision
                if (start == null || end == null) continue;

                // Create a new ConsentPeriod for the provision
                ConsentPeriod consentPeriod = new ConsentPeriod();
                consentPeriod.setStart(start);
                consentPeriod.setEnd(end);
                consentPeriod.setCode(code);

                // Add the new consent period to the map under the corresponding code
                consentPeriodMap.computeIfAbsent(code, k -> new ArrayList<>()).add(consentPeriod);

            } catch (Exception e) {
                logger.error("Error processing provision period", e);
            }
        }

        // Handle the case where no valid periods were found for any code
        if (consentPeriodMap.isEmpty()) {
            throw new IllegalStateException("No valid start or end dates found for the provided valid codes");
        }

        return consentPeriodMap;
    }




}
