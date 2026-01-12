package de.medizininformatikinitiative.torch.model.consent;

import de.medizininformatikinitiative.torch.model.management.TermCode;

import static java.util.Objects.requireNonNull;

public record Provision(TermCode code, Period period, boolean permit) {
    public Provision {
        requireNonNull(code);
        requireNonNull(period);
    }

}
