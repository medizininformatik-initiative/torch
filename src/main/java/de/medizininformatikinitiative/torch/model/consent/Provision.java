package de.medizininformatikinitiative.torch.model.consent;

import de.medizininformatikinitiative.torch.model.management.TermCode;

import static java.util.Objects.requireNonNull;

/**
 * @param encounterShift how the encounter shift moved {@code period}'s start, or {@code null} if it was not shifted
 */
public record Provision(TermCode code, Period period, boolean permit, EncounterShift encounterShift) {
    public Provision {
        requireNonNull(code);
        requireNonNull(period);
    }

    public Provision(TermCode code, Period period, boolean permit) {
        this(code, period, permit, null);
    }
}
