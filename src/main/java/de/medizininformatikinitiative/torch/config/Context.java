package de.medizininformatikinitiative.torch.config;

import jakarta.validation.constraints.NotBlank;

public record Context(
        @NotBlank(message = "Context code is required") String code,
        @NotBlank(message = "Context system is required") String system
) {
}
