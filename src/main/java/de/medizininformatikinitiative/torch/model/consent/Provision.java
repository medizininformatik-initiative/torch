package de.medizininformatikinitiative.torch.model.consent;

import org.hl7.fhir.r4.model.Consent;

import static java.util.Objects.requireNonNull;

public record Provision(String code, Period period, boolean permit) {
    public Provision {
        requireNonNull(code);
        requireNonNull(period);
    }

    public static Provision fromHapi(Consent.ProvisionComponent provision) {
        boolean permit = provision.getType().equals(Consent.ConsentProvisionType.PERMIT);
        return new Provision(provision.getCode().getFirst().getCoding().getFirst().getCode(), Period.fromHapi(provision.getPeriod()), permit);
    }
}
