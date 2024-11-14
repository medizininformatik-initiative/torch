package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.model.consent.NonContinuousPeriod;
import de.medizininformatikinitiative.torch.model.consent.Provisions;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Period;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The {@code ConsentProcessor} class is a processing FHIR consent resources to extract the requested codes i.e. valid codes for the data extraction.
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
     * <p>This method utilizes FHIRPath expressions to evaluate and retrieve the "consent.provision.provision"
     * elements from the provided resource.</p>
     *
     * @param domainResource the FHIR domain resource containing consent provisions
     * @return a list of {@link Base} elements representing the extracted consent provisions;
     * returns an isEmpty list if an error occurs during extraction
     */
    public List<Base> extractConsentProvisions(DomainResource domainResource) {
        return fhirContext.newFhirPath().evaluate(domainResource, "Consent.provision.provision", Base.class);
    }

    /**
     * Transforms the consent provisions within the provided {@link DomainResource} into a map of consent provisions
     * categorized by their respective codes.
     *
     * <p>This method filters the consent provisions based on a set of valid codes and collects the
     * provisions associated with each required code. It ensures that each provision has both start and end dates.</p>
     *
     * @param consent       the FHIR domain resource containing consent provisions
     * @param requiredCodes a set of valid codes to filter the consent provisions
     * @return a {@code Map} where each key is a valid code and the value is a list of {@link Period} objects
     * associated with that code
     * @throws ConsentViolatedException if no valid provisions are found for the provided codes or if the resource
     *                                  does not contain valid consents for every requested code
     */
    public Provisions transformToConsentPeriodByCode(Consent consent, Set<String> requiredCodes) throws ConsentViolatedException {
        Map<String, List<de.medizininformatikinitiative.torch.model.consent.Period>> consentPeriodMap = new HashMap<>();
        List<Base> provisionPeriodList = extractConsentProvisions(consent);
        for (Base provisionBase : provisionPeriodList) {
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
            consentPeriodMap.computeIfAbsent(code, k -> new ArrayList<>()).add(de.medizininformatikinitiative.torch.model.consent.Period.fromHapi(period));

        }
        if (!consentPeriodMap.keySet().equals(requiredCodes)) {
            throw new ConsentViolatedException("Resource does not have valid consents for every requested code");
        }
        return new Provisions(consentPeriodMap.entrySet().stream().collect(
                Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> new NonContinuousPeriod(entry.getValue())
                )));
    }
}
