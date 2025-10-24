package de.medizininformatikinitiative.torch.model.management;

public record TermCode(String system, String code) {
    public TermCode {
        if (system == null || system.isBlank()) {
            throw new IllegalArgumentException("system must not be null or blank");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be null or blank");
        }
    }
}
