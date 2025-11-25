package de.medizininformatikinitiative.torch.config;

import de.medizininformatikinitiative.torch.setup.ContainerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@Configuration
@Profile("test")
@Import(BaseConfig.class)
@ComponentScan("de.medizininformatikinitiative.torch")
public class TestConfig {
    private static final Logger logger = LoggerFactory.getLogger(TestConfig.class);

    @Bean("fhirClient")
    public WebClient fhirWebClient(ContainerManager containerManager, TorchProperties torchProperties, FhirProperties fhirProperties) {
        String host = containerManager.getBlazeHost();
        String baseUrl = String.format("http://%s/fhir", host);
        logger.info("Initializing FHIR WebClient with URL: {}", baseUrl);

        ConnectionProvider provider = ConnectionProvider.builder("data-store")
                .maxConnections(fhirProperties.max().connections())
                .pendingAcquireMaxCount(500)
                .build();

        HttpClient httpClient = HttpClient.create(provider);


        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(1024 * 1024 * torchProperties.bufferSize()))
                .build();

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .defaultHeader("Accept", "application/fhir+json")
                .defaultHeader("X-Forwarded-Host", host)
                .build();
    }


    // Bean for the Flare WebClient initialized with the dynamically determined URL
    @Bean("flareClient")
    public WebClient flareWebClient(ContainerManager containerManager) {
        String flareBaseUrl = containerManager.getFlareBaseUrl();
        logger.info("Initializing Flare WebClient with URL: {}", flareBaseUrl);

        ConnectionProvider provider = ConnectionProvider.builder("flare-store").maxConnections(4).pendingAcquireMaxCount(500).build();
        HttpClient httpClient = HttpClient.create(provider);
        return WebClient.builder().baseUrl(flareBaseUrl).clientConnector(new ReactorClientHttpConnector(httpClient)).build();
    }

    @Bean
    public ContainerManager containerManager() {
        return new ContainerManager();
    }
}
