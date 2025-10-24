package de.medizininformatikinitiative.torch.config;


import de.medizininformatikinitiative.torch.model.consent.ConsentCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Set;

@ConfigurationProperties(prefix = "torch.consent")
@Validated
public record ConsentContextProperties(@NotEmpty Set<@Valid ConsentCode> context) {

    public static ConsentContextProperties of() {
        return new ConsentContextProperties(Set.of());
    }

}
