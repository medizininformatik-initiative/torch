package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.ConsentInfo;
import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
     * <p>This method filters the consent provisions based on a set of valid codes and collects the
     * periods associated with each required code. It ensures that each provision has both start and end dates.</p>
     *
     * @param consent       the FHIR domain resource containing consent provisions
     * @param requiredCodes a set of valid codes to filter the consent provisions
     * @return a {@code Map} where each key is a valid code and the value is a list of {@link Period} objects
     * associated with that code
     * @throws ConsentViolatedException if no valid periods are found for the provided codes or if the resource
     *                                  does not contain valid consents for every requested code
     */
    public Map<String, ConsentInfo.NonContinousPeriod> transformToConsentPeriodByCode(Consent consent, Set<String> requiredCodes) throws ConsentViolatedException {
        Map<String, List<ConsentInfo.Period>> consentPeriodMap = new HashMap<>();
        List<Base> provisionPeriodList = extractConsentProvisions(consent);
        for (Base provisionBase : provisionPeriodList) {
            try {
                Consent.provisionComponent provision = (Consent.provisionComponent) provisionBase;
                String code = provision.getCode().getFirst().getCoding().getFirst().getCode();
                if (!requiredCodes.contains(code)) {
                    continue; // Skip if code is not valid
                }
                Period period = provision.getPeriod();
                DateTimeType start = period.hasStart() ? period.getStartElement() : null;
                DateTimeType end = period.hasEnd() ? period.getEndElement() : null;

                // If no start or end period is present, skip to the next provision
                if (start == null || end == null) continue;
                // Add the new consent period to the map under the corresponding code
                consentPeriodMap.computeIfAbsent(code, k -> new ArrayList<>()).add(ConsentInfo.Period.fromHapi(period));
            } catch (Exception e) {
                logger.error("Error processing provision period", e);
            }
        }
        if (!consentPeriodMap.keySet().equals(requiredCodes)) {
            throw new ConsentViolatedException("Resource does not have valid consents for every requested code");
        }
        HashMap<String, ConsentInfo.NonContinousPeriod> consentMap = new HashMap<>();
        consentPeriodMap.forEach((key, value) -> consentMap.put(key, new ConsentInfo.NonContinousPeriod(value)));
        return consentMap;
    }
}
