package de.medizininformatikinitiative.torch.config;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.Torch;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.resources.ConnectionProvider;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest(classes = Torch.class)
@ActiveProfiles("active")
class WebConfigTest {

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("torch.fhir.url", () -> "http://dummy.fhir.server");
        registry.add("torch.base.url", () -> "http://dummy.torch.server");
    }

    @Autowired
    ApplicationContext ctx;

    @Test
    void shouldHaveFhirContextBean() {
        assertThat(ctx.getBeanNamesForType(FhirContext.class))
                .isNotEmpty()
                .containsAnyOf("fhirContext");
    }

    private FhirProperties minimalfhirPropertiesWithBasicAuth(String url) {
        var page = new FhirProperties.Page(10);
        var oauthIssuer = new FhirProperties.Oauth.Issuer(""); // empty disables OAuth
        var oauthClient = new FhirProperties.Oauth.Client("", "");
        var oauth = new FhirProperties.Oauth(oauthIssuer, oauthClient);
        return new FhirProperties(
                url,
                new FhirProperties.Max(5),
                page,
                oauth,
                new FhirProperties.Disable(false),
                "user",
                "password"
        );
    }


    @Test
    void isBasicAuthConfigured() {
        assertThat(WebConfig.isBasicAuthConfigured("user", "pass")).isTrue();
        assertThat(WebConfig.isBasicAuthConfigured("", "pass")).isFalse();
        assertThat(WebConfig.isBasicAuthConfigured("user", "")).isFalse();
    }

    @Test
    void oAuthEnabled() {
        assertThat(WebConfig.oAuthEnabled("issuer", "id", "secret")).isTrue();
        assertThat(WebConfig.oAuthEnabled("", "id", "secret")).isFalse();
        assertThat(WebConfig.oAuthEnabled("issuer", "", "secret")).isFalse();
        assertThat(WebConfig.oAuthEnabled("issuer", "id", "")).isFalse();
    }

    private TorchProperties torchProperties() {
        var base = new TorchProperties.Base("http://base-url");
        var output = new TorchProperties.Output(new TorchProperties.Output.File(new TorchProperties.Output.File.Server("http://output-server")));
        var profile = new TorchProperties.Profile("profile-dir");
        var mapping = new TorchProperties.Mapping("consent-mapping.json", "type-to-consent.json");


        var flare = new TorchProperties.Flare("http://flare-url");
        var results = new TorchProperties.Results("results-dir", "persistence");

        return new TorchProperties(
                base,
                output,
                profile,
                mapping,
                flare,
                results,
                1, // batchsize
                1, // maxConcurrency
                10, // bufferSize
                "mappingsFile.json",
                "conceptTree.json",
                "dseMappingTree.json",
                "search-parameters.json",
                false // useCql
        );
    }

    private FhirProperties minimalOauth(String url) {
        var page = new FhirProperties.Page(10);
        var oauthIssuer = new FhirProperties.Oauth.Issuer(url);
        var oauthClient = new FhirProperties.Oauth.Client("clientId", "clientSecret");
        var oauth = new FhirProperties.Oauth(oauthIssuer, oauthClient);
        return new FhirProperties(
                "http://localhost/fhir",
                new FhirProperties.Max(5),
                page,
                oauth,
                new FhirProperties.Disable(false),
                "", // user empty disables basic auth
                ""
        );
    }

    @Test
    void testOauthExchangeFilterFunction_BasicAuth() throws IOException, InterruptedException {
        try (MockWebServer mockWebServer = new MockWebServer()) {
            mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("OK"));
            mockWebServer.start();

            var fhirProperties = minimalfhirPropertiesWithBasicAuth(mockWebServer.url("/").toString());
            var appConfig = new WebConfig();
            ExchangeFilterFunction filter = appConfig.oauthExchangeFilterFunction(fhirProperties);
            WebClient client = appConfig.fhirWebClient(torchProperties(), filter, fhirProperties, ConnectionProvider.newConnection());
            // Perform a request
            client.get()
                    .uri("/fhir/Patient")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            okhttp3.mockwebserver.RecordedRequest recordedRequest = mockWebServer.takeRequest();
            String authHeader = recordedRequest.getHeader("Authorization");
            assertThat(authHeader).isNotEmpty();
            String encoded = authHeader.substring("Basic ".length());
            String decoded = new String(java.util.Base64.getDecoder().decode(encoded));

            // This should be a pass-through filter (lambda)
            assertThat(filter.getClass().getName()).contains("$$Lambda");
            // Verify credentials
            assertThat(authHeader)
                    .isNotNull()
                    .startsWith("Basic ");
            assertThat(decoded).isEqualTo("user:password");
        }

    }

    @Test
    void testOauthExchangeFilterFunction_withOAuth() throws IOException {
        try (MockWebServer mockWebServer = new MockWebServer()) {
            mockWebServer.start();

            // Setup mock response for .well-known/openid-configuration
            mockWebServer.enqueue(new MockResponse()
                    .setBody("{\n" +
                            "  \"issuer\": \"" + mockWebServer.url("/") + "\",\n" +
                            "  \"authorization_endpoint\": \"http://localhost:50301/oauth2/authorize\",\n" +
                            "  \"token_endpoint\": \"http://localhost:50301/oauth2/token\",\n" +
                            "  \"jwks_uri\": \"http://localhost:50301/oauth2/jwks\",\n" +
                            "  \"subject_types_supported\": [\"public\"],\n" +
                            "  \"id_token_signing_alg_values_supported\": [\"RS256\"]\n" +
                            "}\n")
                    .setHeader("Content-Type", "application/json"));
            var fhirProperties = minimalOauth(mockWebServer.url("/").toString());
            var appConfig = new WebConfig();


            ExchangeFilterFunction filter = appConfig.oauthExchangeFilterFunction(fhirProperties);

            assertThat(filter).isInstanceOf(org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction.class);

            mockWebServer.shutdown();
        }
    }

    @Test
    void testFhirWebClient_baseUrl() {
        var fhirProperties = minimalfhirPropertiesWithBasicAuth("test-url");
        var appConfig = new WebConfig();
        ExchangeFilterFunction oauthFilter = appConfig.oauthExchangeFilterFunction(fhirProperties);

        WebClient client = appConfig.fhirWebClient(torchProperties(), oauthFilter, fhirProperties, ConnectionProvider.newConnection());
        assertThat(client).isNotNull();
        assertThat(fhirProperties.url()).isEqualTo("test-url");
    }

}

