package de.medizininformatikinitiative.torch.util;

import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.DateTimeType;

import java.util.*;

/**
 * The {@code ConsentProcessor} class is a processing FHIR Consent resources to extract the requested codes i.e. valid codes for the data extraction.
 */
@Component
public class ConsentProcessor {


    public static final Logger logger = LoggerFactory.getLogger(ConsentProcessor.class);


    private final FhirContext fhirContext;


    @Autowired
    public ConsentProcessor(FhirContext fhirContext) {
        this.fhirContext = fhirContext;
    }

    /**
     * Extracts the consent provision elements from the given FHIR {@link DomainResource}.
     *
     * <p>This method utilizes FHIRPath expressions to evaluate and retrieve the "Consent.provision.provision"
     * elements from the provided resource.</p>
     *
     * @param domainResource the FHIR domain resource containing consent provisions
     * @return a list of {@link Base} elements representing the extracted consent provisions;
     * returns an empty list if an error occurs during extraction
     */
    public List<Base> extractConsentProvisions(DomainResource domainResource) {
        try {
            // Using the autowired FhirContext to extract Encounter.provision.provision elements from the resource
            return fhirContext.newFhirPath().evaluate(domainResource, "Consent.provision.provision", Base.class);
        } catch (Exception e) {
            logger.error("Error extracting provisions with FHIRPath", e);
            return Collections.emptyList();  // Return an empty list in case of errors
        }
    }

    /**
     * Transforms the consent provisions within the provided {@link DomainResource} into a map of consent periods
     * categorized by their respective codes.
     *
     * <p>This method filters the consent provisions based on a set of valid codes and organizes the
     * periods associated with each valid code. It ensures that each provision has both start and end dates.</p>
     *
     * @param domainResource the FHIR domain resource containing consent provisions
     * @param validCodes     a set of valid codes to filter the consent provisions
     * @return a {@code Map} where each key is a valid code and the value is a list of {@link Period} objects
     * associated with that code
     * @throws ConsentViolatedException if no valid periods are found for the provided codes or if the resource
     *                                  does not contain valid consents for every requested code
     */
    public Map<String, List<Period>> transformToConsentPeriodByCode(DomainResource domainResource, Set<String> validCodes) throws ConsentViolatedException {
        // Log each valid code at debug level
        validCodes.forEach(code -> logger.debug("validCode {}", code));

        // Map to hold lists of ConsentPeriod for each code
        Map<String, List<Period>> consentPeriodMap = new HashMap<>();

        // Extract consent provisions from the domain resource
        List<Base> provisionPeriodList = extractConsentProvisions(domainResource);

        // Iterate over the provisions to find periods for each valid code
        for (Base provisionBase : provisionPeriodList) {
            try {
                // Cast the base provision to Consent.provisionComponent
                Consent.provisionComponent provision = (Consent.provisionComponent) provisionBase;

                // Retrieve the period of the provision
                Period period = provision.getPeriod();

                // Extract the code associated with the provision
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

        // Check if all valid codes have corresponding consent periods
        if (!consentPeriodMap.keySet().equals(validCodes)) {
            throw new ConsentViolatedException("Resource does not have valid consents for every requested code");
        }

        return consentPeriodMap;
    }
}
