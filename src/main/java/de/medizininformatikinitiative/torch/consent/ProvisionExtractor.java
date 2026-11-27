package de.medizininformatikinitiative.torch.consent;

import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.management.PatientLinkResolver;
import de.medizininformatikinitiative.torch.model.consent.ConsentProvisions;
import de.medizininformatikinitiative.torch.model.consent.Provision;
import de.medizininformatikinitiative.torch.model.management.TermCode;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Period;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * The {@code ConsentProcessor} class is a processing FHIR consent resources to extract the requested codes i.e. valid codes for the data extraction.
 */
@Component
public class ProvisionExtractor {


    public static final Logger logger = LoggerFactory.getLogger(ProvisionExtractor.class);

    private final PatientLinkResolver patientLinkResolver;

    public ProvisionExtractor(PatientLinkResolver patientLinkResolver) {
        this.patientLinkResolver = requireNonNull(patientLinkResolver);
    }


    /**
     * Transforms the consent consentPeriods within the provided {@link DomainResource} into a map of consent consentPeriods
     * categorized by their respective codes.
     *
     * <p>This method filters the consent consentPeriods based on a set of valid codes and collects the
     * consentPeriods associated with each required code. It ensures that each provision has both start and end dates.</p>
     *
     * <p>A consent without any level 2 (nested) provision — regardless of their codes and periods — only carries the
     * level 1 deny, which revokes everything. For it, a virtual deny of every required code is returned in
     * {@link ConsentProvisions#virtualDenies()}, over the level 1 period or, if that is missing or not day precise,
     * from {@code Consent.dateTime} with an open end ({@link de.medizininformatikinitiative.torch.model.consent.Period#OPEN_END}).
     * Whether they apply depends on the other consents of the same signing event (see {@link ConsentCalculator}).</p>
     *
     * @param consent       the FHIR domain resource containing consent consentPeriods
     * @param requiredCodes a set of valid codes to filter the consent consentPeriods
     * @return a {@code Map} where each key is a valid code and the value is a list of {@link Period} objects
     * associated with that code
     */
    public ConsentProvisions extractProvisionsPeriodByCode(Consent consent, Set<TermCode> requiredCodes) throws ConsentViolatedException, PatientIdNotFoundException {
        if (consent.getDateTimeElement().isEmpty()) {
            throw new ConsentViolatedException("Consent resource " + consent.getId() + " has no valid consent date");
        }
        String patientId = patientLinkResolver.patientId(consent);
        if (consent.getProvision().getProvision().isEmpty()) {
            return new ConsentProvisions(consent.getIdPart(), patientId, consent.getDateTimeElement(), List.of(),
                    virtualDenies(consent, requiredCodes));
        }
        List<Provision> provisions = new ArrayList<>();
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
        return new ConsentProvisions(consent.getIdPart(), patientId, consent.getDateTimeElement(), provisions);
    }

    private static List<Provision> virtualDenies(Consent consent, Set<TermCode> requiredCodes) throws ConsentViolatedException {
        de.medizininformatikinitiative.torch.model.consent.Period period = levelOnePeriod(consent);
        return requiredCodes.stream().map(code -> new Provision(code, period, false)).toList();
    }

    private static de.medizininformatikinitiative.torch.model.consent.Period levelOnePeriod(Consent consent) throws ConsentViolatedException {
        Optional<de.medizininformatikinitiative.torch.model.consent.Period> period =
                de.medizininformatikinitiative.torch.model.consent.Period.fromHapi(consent.getProvision().getPeriod());
        if (period.isPresent()) {
            return period.get();
        }
        LocalDate dateTime = de.medizininformatikinitiative.torch.model.consent.Period.fromHapi(consent.getDateTimeElement())
                .orElseThrow(() -> new ConsentViolatedException("Consent resource " + consent.getId()
                        + " has only a level 1 provision without a valid period and no day precise consent date"))
                .start();
        return new de.medizininformatikinitiative.torch.model.consent.Period(dateTime,
                de.medizininformatikinitiative.torch.model.consent.Period.OPEN_END);
    }
}
