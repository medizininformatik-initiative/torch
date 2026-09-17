package de.medizininformatikinitiative.torch.consent;

import java.time.LocalDate;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Result of {@link ConsentValidator#checkConsent(org.hl7.fhir.r4.model.DomainResource, de.medizininformatikinitiative.torch.model.management.PatientResourceBundle)}.
 *
 * @param outcome         the reason for the consent decision
 * @param consideredDate  the date value evaluated against the consent periods, present only for
 *                        {@link ConsentCheckOutcome#IN_PERIOD} and {@link ConsentCheckOutcome#OUTSIDE_PERIODS}.
 *                        {@code checkConsent} evaluates the configured FHIRPath as a list and checks every
 *                        value against the consent periods, but only the first value's date is kept here for
 *                        {@code OUTSIDE_PERIODS} — every {@code resourceToField} mapping in
 *                        {@code mappings/type_to_consent.json} currently resolves to a path whose segments are
 *                        all 0..1/1..1 in base FHIR (a profile cannot widen that), so this loses nothing today;
 *                        revisit if a multi-valued mapping is ever added
 */
public record ConsentCheckResult(ConsentCheckOutcome outcome, Optional<LocalDate> consideredDate) {

    public ConsentCheckResult {
        requireNonNull(outcome);
        requireNonNull(consideredDate);
    }

    public static ConsentCheckResult of(ConsentCheckOutcome outcome) {
        return new ConsentCheckResult(outcome, Optional.empty());
    }

    public static ConsentCheckResult of(ConsentCheckOutcome outcome, LocalDate consideredDate) {
        return new ConsentCheckResult(outcome, Optional.of(consideredDate));
    }

    public boolean included() {
        return outcome.included();
    }
}
