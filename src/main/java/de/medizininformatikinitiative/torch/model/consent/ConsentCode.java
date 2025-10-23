package de.medizininformatikinitiative.torch.model.consent;

import de.medizininformatikinitiative.torch.util.ConfigUtils;

public record ConsentCode(String system, String code) {
    public ConsentCode {
        if (ConfigUtils.isNotSet(system)) {
            throw new IllegalArgumentException("system must not be null or blank");
        }
        if (ConfigUtils.isNotSet(code)) {
            throw new IllegalArgumentException("code must not be null or blank");
        }
    }
}
