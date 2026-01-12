package de.medizininformatikinitiative.torch.consent;

import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.model.consent.ConsentProvisions;
import de.medizininformatikinitiative.torch.model.consent.Provision;
import de.medizininformatikinitiative.torch.model.management.TermCode;
import de.medizininformatikinitiative.torch.util.ResourceUtils;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Period;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The {@code ConsentProcessor} class is a processing FHIR consent resources to extract the requested codes i.e. valid codes for the data extraction.
 */
@Component
public class ProvisionExtractor {


    public static final Logger logger = LoggerFactory.getLogger(ProvisionExtractor.class);


    /**
     * Transforms the consent consentPeriods within the provided {@link DomainResource} into a map of consent consentPeriods
     * categorized by their respective codes.
     *
     * <p>This method filters the consent consentPeriods based on a set of valid codes and collects the
     * consentPeriods associated with each required code. It ensures that each provision has both start and end dates.</p>
     *
     * @param consent       the FHIR domain resource containing consent consentPeriods
     * @param requiredCodes a set of valid codes to filter the consent consentPeriods
     * @return a {@code Map} where each key is a valid code and the value is a list of {@link Period} objects
     * associated with that code
     */
    public ConsentProvisions extractProvisionsPeriodByCode(Consent consent, Set<TermCode> requiredCodes) throws ConsentViolatedException, PatientIdNotFoundException {
        List<Provision> provisions = new ArrayList<>();
        if (consent.getDateTimeElement().isEmpty()) {
            throw new ConsentViolatedException("Consent resource " + consent.getId() + " has no valid consent date");
        }
        for (Consent.ProvisionComponent provision : consent.getProvision().getProvision()) {

            if (!provision.hasPeriod()) continue;

            Optional<de.medizininformatikinitiative.torch.model.consent.Period> period =
                    de.medizininformatikinitiative.torch.model.consent.Period.fromHapi(provision.getPeriod());

            if (period.isEmpty()) continue;

            boolean permit = provision.getType() == Consent.ConsentProvisionType.PERMIT;

            for (CodeableConcept cc : provision.getCode()) {
                for (Coding coding : cc.getCoding()) {

                    TermCode code = new TermCode(coding.getSystem(), coding.getCode());
                    if (!requiredCodes.contains(code)) continue;
                    provisions.add(
                            new Provision(
                                    code,
                                    period.get(),
                                    permit
                            )
                    );
                }
            }
        }
        return new ConsentProvisions(ResourceUtils.patientId(consent), consent.getDateTimeElement(), provisions);
    }
}
