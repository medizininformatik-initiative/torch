package de.medizininformatikinitiative.torch.consent.mii.model;

import de.medizininformatikinitiative.torch.model.consent.Period;

import static java.util.Objects.requireNonNull;

public record Provision(TermCode code, Period period, boolean permit, boolean retroExtended) {
    public Provision {
        requireNonNull(code);
        requireNonNull(period);
    }

    public Provision(TermCode code, Period period, boolean permit) {
        this(code, period, permit, false);
    }
}
