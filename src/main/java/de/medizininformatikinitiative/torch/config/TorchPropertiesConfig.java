package de.medizininformatikinitiative.torch.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        TorchProperties.class,
        FhirProperties.class
})
public class TorchPropertiesConfig {
}
