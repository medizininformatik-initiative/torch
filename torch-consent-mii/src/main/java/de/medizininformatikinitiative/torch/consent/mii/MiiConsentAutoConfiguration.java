package de.medizininformatikinitiative.torch.consent.mii;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.consent.ConsentDataClient;
import de.medizininformatikinitiative.torch.consent.ConsentEvaluator;
import de.medizininformatikinitiative.torch.consent.mii.model.ConsentCodeConfig;
import de.medizininformatikinitiative.torch.consent.mii.model.ProspectiveEntry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Registers the MII default {@link ConsentEvaluator} implementation. Discovered via Spring Boot's
 * auto-configuration SPI ({@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports})
 * rather than component scanning, so a replacement implementation can be dropped onto the classpath
 * (e.g. via an externalized {@code loader.path}) and take over without any change to the host
 * application. See issue #1068.
 */
@AutoConfiguration
public class MiiConsentAutoConfiguration {

    @Bean
    public ConsentCodeConfig consentCodeConfig(
            ObjectMapper objectMapper,
            @Value("${torch.consent.mii.code-config-path:classpath:consent-code-config.json}") Resource resource) throws IOException {
        try (InputStream in = resource.getInputStream()) {
            List<ProspectiveEntry> entries = objectMapper.readValue(in, new TypeReference<>() {
            });
            return new ConsentCodeConfig(entries);
        }
    }

    @Bean
    public ProvisionExtractor provisionExtractor() {
        return new ProvisionExtractor();
    }

    @Bean
    public CrtdlConsentValidator crtdlConsentValidator() {
        return new CrtdlConsentValidator();
    }

    @Bean
    public ConsentFetcher consentFetcher(ConsentDataClient consentDataClient, ProvisionExtractor provisionExtractor) {
        return new ConsentFetcher(consentDataClient, provisionExtractor);
    }

    @Bean
    public ConsentAdjuster consentAdjuster(ConsentDataClient consentDataClient) {
        return new ConsentAdjuster(consentDataClient);
    }

    @Bean
    public ConsentCalculator consentCalculator(ConsentCodeConfig consentCodeConfig) {
        return new ConsentCalculator(consentCodeConfig);
    }

    @Bean
    @ConditionalOnMissingBean(ConsentEvaluator.class)
    public ConsentEvaluator consentEvaluator(CrtdlConsentValidator crtdlConsentValidator,
                                              ConsentCodeConfig consentCodeConfig,
                                              ConsentFetcher consentFetcher,
                                              ConsentAdjuster consentAdjuster,
                                              ConsentCalculator consentCalculator) {
        return new MiiConsentEvaluator(crtdlConsentValidator, consentCodeConfig, consentFetcher, consentAdjuster, consentCalculator);
    }
}
