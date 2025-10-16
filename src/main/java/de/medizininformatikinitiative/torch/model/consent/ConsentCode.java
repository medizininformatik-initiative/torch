package de.medizininformatikinitiative.torch.model.consent;

public record ConsentCode(String system, String code) {
    public ConsentCode {
        if (system == null || system.isBlank()) {
            throw new IllegalArgumentException("system must not be null or blank");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be null or blank");
        }
    }
}
