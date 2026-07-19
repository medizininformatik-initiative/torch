package de.medizininformatikinitiative.torch.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFilterFunctions;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Clock;

import static org.springframework.security.oauth2.core.AuthorizationGrantType.CLIENT_CREDENTIALS;
import static org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames.REGISTRATION_ID;

@Configuration
@Profile("!test")
public class WebConfig {
    private static final Logger logger = LoggerFactory.getLogger(WebConfig.class);

    @Bean
    public ConnectionProvider flareConnectionProvider() {
        return ConnectionProvider.builder("flare-pool")
                .maxConnections(4)
                .pendingAcquireMaxCount(500)
                .build();
    }

    @Bean
    public ConnectionProvider fhirConnectionProvider(FhirProperties fhirProperties) {
        return ConnectionProvider.builder("fhir-pool")
                .maxConnections(fhirProperties.max().connections())
                .build();
    }

    static boolean oAuthEnabled(String issuerUri, String clientId, String clientSecret) {
        return !issuerUri.isBlank() && !clientId.isBlank() && !clientSecret.isBlank();
    }

    @Bean("flareClient")
    public WebClient flareWebClient(TorchProperties torchProperties, @Qualifier("flareConnectionProvider") ConnectionProvider flareConnectionProvider) {
        logger.info("Initializing Flare WebClient with URL: {}", torchProperties.flare().url());
        HttpClient httpClient = HttpClient.create(flareConnectionProvider);
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(torchProperties.flare().url())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Accept", "application/sq+json");

        return builder.build();
    }

    @Bean
    public Clock systemDefaultZone() {
        return Clock.systemDefaultZone();
    }

    static boolean isBasicAuthConfigured(String user, String password) {
        return !user.isBlank() && !password.isBlank();
    }

    @Bean("fhirClient")
    public WebClient fhirWebClient(TorchProperties torchProperties,
                                   ExchangeFilterFunction oauthExchangeFilterFunction,
                                   FhirProperties fhirProperties, @Qualifier("fhirConnectionProvider") ConnectionProvider fhirConnectionProvider) {
        String user = fhirProperties.user();
        String password = fhirProperties.password();
        int maxConnections = fhirProperties.max().connections();
        String baseUrl = fhirProperties.url();
        logger.info("Initializing FHIR WebClient with URL {} and a maximum number of {} concurrent connections", baseUrl, maxConnections);

        // Configure buffer size to 10MB
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(1024 * 1024 * torchProperties.bufferSize()))
                .build();
        HttpClient httpClient = HttpClient.create(fhirConnectionProvider);
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(baseUrl)
                .exchangeStrategies(strategies)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Accept", "application/fhir+json");

        if (isBasicAuthConfigured(user, password)) {
            builder = builder.filter(ExchangeFilterFunctions.basicAuthentication(user, password));
            logger.info("Added basic authentication for user: {}", user);
            return builder.build();
        } else {
            return builder.filter(oauthExchangeFilterFunction).build();
        }
    }

    @Bean
    ExchangeFilterFunction oauthExchangeFilterFunction(FhirProperties fhirProperties) {
        String issuerUri = fhirProperties.oauth().issuer().uri();
        String clientId = fhirProperties.oauth().client().id();
        String clientSecret = fhirProperties.oauth().client().secret();

        if (oAuthEnabled(issuerUri, clientId, clientSecret)) {
            logger.info("Enabling OAuth2 authentication (issuer uri: '{}', client id: '{}').",
                    issuerUri, clientId);
            var clientRegistration = ClientRegistrations.fromIssuerLocation(issuerUri)
                    .registrationId(REGISTRATION_ID)
                    .clientId(clientId)
                    .clientSecret(clientSecret)
                    .authorizationGrantType(CLIENT_CREDENTIALS)
                    .build();
            var registrations = new InMemoryReactiveClientRegistrationRepository(clientRegistration);
            var clientService = new InMemoryReactiveOAuth2AuthorizedClientService(registrations);
            var authorizedClientManager = new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(
                    registrations, clientService);
            var oAuthExchangeFilterFunction = new ServerOAuth2AuthorizedClientExchangeFilterFunction(
                    authorizedClientManager);
            oAuthExchangeFilterFunction.setDefaultClientRegistrationId(REGISTRATION_ID);

            return oAuthExchangeFilterFunction;
        } else {
            logger.info("Skipping OAuth2 authentication.");
            return (request, next) -> next.exchange(request);
        }
    }
}
