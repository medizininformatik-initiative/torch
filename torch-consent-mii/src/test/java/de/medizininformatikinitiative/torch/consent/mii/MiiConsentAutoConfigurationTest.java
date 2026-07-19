package de.medizininformatikinitiative.torch.consent.mii;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.consent.ConsentDataClient;
import de.medizininformatikinitiative.torch.consent.ConsentEvaluator;
import de.medizininformatikinitiative.torch.consent.mii.model.ConsentCodeConfig;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MiiConsentAutoConfigurationTest {

    private final MiiConsentAutoConfiguration autoConfiguration = new MiiConsentAutoConfiguration();

    @Test
    void consentCodeConfig_loadsShippedDefaultFromClasspath() throws Exception {
        ConsentCodeConfig config = autoConfiguration.consentCodeConfig(
                new ObjectMapper(), new ClassPathResource("consent-code-config.json"));

        assertThat(config).isNotNull();
    }

    @Test
    void wiresAllBeansFromTheirDeclaredDependencies() throws Exception {
        ConsentDataClient consentDataClient = mock(ConsentDataClient.class);
        ConsentCodeConfig consentCodeConfig = autoConfiguration.consentCodeConfig(
                new ObjectMapper(), new ClassPathResource("consent-code-config.json"));

        ProvisionExtractor provisionExtractor = autoConfiguration.provisionExtractor();
        CrtdlConsentValidator crtdlConsentValidator = autoConfiguration.crtdlConsentValidator();
        ConsentFetcher consentFetcher = autoConfiguration.consentFetcher(consentDataClient, provisionExtractor);
        ConsentAdjuster consentAdjuster = autoConfiguration.consentAdjuster(consentDataClient);
        ConsentCalculator consentCalculator = autoConfiguration.consentCalculator(consentCodeConfig);

        assertThat(provisionExtractor).isNotNull();
        assertThat(crtdlConsentValidator).isNotNull();
        assertThat(consentFetcher).isNotNull();
        assertThat(consentAdjuster).isNotNull();
        assertThat(consentCalculator).isNotNull();

        ConsentEvaluator evaluator = autoConfiguration.consentEvaluator(
                crtdlConsentValidator, consentCodeConfig, consentFetcher, consentAdjuster, consentCalculator);

        assertThat(evaluator).isNotNull();
    }
}
