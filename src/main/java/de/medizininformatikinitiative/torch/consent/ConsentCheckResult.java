package de.medizininformatikinitiative.torch.consent;

import java.time.LocalDate;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Result of {@link ConsentValidator#checkConsent(org.hl7.fhir.r4.model.DomainResource, de.medizininformatikinitiative.torch.model.management.PatientResourceBundle)}.
 *
 * @param outcome         the reason for the consent decision
 * @param consideredDate  the resource date checked against the consent periods, present only for
 *                        {@link ConsentCheckOutcome#IN_PERIOD} and {@link ConsentCheckOutcome#OUTSIDE_PERIODS}.
 *                        If the configured FHIRPath yields several dates, only the first is kept for
 *                        {@code OUTSIDE_PERIODS}; all current mappings in {@code type_to_consent.json}
 *                        yield at most one
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
