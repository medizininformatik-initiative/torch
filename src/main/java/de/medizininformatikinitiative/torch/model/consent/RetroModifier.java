package de.medizininformatikinitiative.torch.model.consent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.medizininformatikinitiative.torch.model.management.TermCode;

import java.time.LocalDate;

import static java.util.Objects.requireNonNull;

/**
 * Describes a retrospective modifier code that extends the valid period of a paired prospective consent code
 * backwards in time.
 * <p>
 * When a patient has a permitted provision for this modifier code whose period overlaps a permitted provision
 * of the associated prospective code, the prospective provision's start date is shifted to
 * {@link #lookbackStart(LocalDate)} — effectively granting retroactive access to historical data.
 * <p>
 * The shift is relative to each prospective provision's own start date: a 200-year lookback on a
 * provision starting 2020 yields 1820; a 5-year lookback yields 2015.
 *
 * @param code          the FHIR provision code identifying this retrospective modifier
 * @param lookbackYears number of years to subtract from the prospective provision's own start date when computing the shifted start date
 * @see ProspectiveEntry
 * @see ConsentCodeConfig
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RetroModifier(TermCode code, int lookbackYears) {

    public RetroModifier {
        requireNonNull(code);
        if (lookbackYears <= 0) throw new IllegalArgumentException("lookbackYears must be positive");
    }

    @JsonCreator
    public static RetroModifier fromJson(
            @JsonProperty("system") String system,
            @JsonProperty("code") String code,
            @JsonProperty("lookbackYears") int lookbackYears) {
        return new RetroModifier(new TermCode(system, code), lookbackYears);
    }

    /**
     * Returns the shifted start date for a prospective provision when this modifier applies.
     * <p>
     * Computed as {@code provisionStart − lookbackYears}, so the shift is relative to each
     * provision's own start date. For example, a 200-year lookback on a provision starting 2020
     * yields 1820; a 5-year lookback yields 2015.
     *
     * @param provisionStart the original start date of the prospective provision being modified
     * @return the shifted start date
     */
    public LocalDate lookbackStart(LocalDate provisionStart) {
        return provisionStart.minusYears(lookbackYears);
    }
}
