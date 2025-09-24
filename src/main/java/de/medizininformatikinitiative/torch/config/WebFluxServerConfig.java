package de.medizininformatikinitiative.torch.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration
public class WebFluxServerConfig implements WebFluxConfigurer {

    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        // Increase the max buffer size for incoming requests to 2 MB
        configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024);
    }
}

