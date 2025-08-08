package de.medizininformatikinitiative.torch.config;

import ca.uhn.fhir.context.FhirContext;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("active")
class AppConfigTest {


    @Autowired
    ApplicationContext ctx;

    @Test
    void shouldHaveFhirContextBean() {
        assertThat(ctx.getBeanNamesForType(FhirContext.class))
                .isNotEmpty()
                .containsAnyOf("fhirContext");
    }

    private TorchProperties minimalTorchPropertiesWithBasicAuth() {
        var base = new TorchProperties.Base("http://base-url");
        var output = new TorchProperties.Output(new TorchProperties.Output.File(new TorchProperties.Output.File.Server("http://output-server")));
        var profile = new TorchProperties.Profile("profile-dir");
        var mapping = new TorchProperties.Mapping("consent-mapping.json", "type-to-consent.json");
        var page = new TorchProperties.Fhir.Page(10);
        var oauthIssuer = new TorchProperties.Fhir.Oauth.Issuer(""); // empty disables OAuth
        var oauthClient = new TorchProperties.Fhir.Oauth.Client("", "");
        var oauth = new TorchProperties.Fhir.Oauth(oauthIssuer, oauthClient);
        var fhir = new TorchProperties.Fhir(
                "http://localhost/fhir",
                new TorchProperties.Max(5),
                page,
                oauth,
                new TorchProperties.Fhir.Disable(false),
                "user",
                "password"
        );
        var flare = new TorchProperties.Flare("http://flare-url");
        var results = new TorchProperties.Results("results-dir", "persistence");

        return new TorchProperties(
                base,
                output,
                profile,
                mapping,
                fhir,
                flare,
                results,
                1, // batchsize
                1, // maxConcurrency
                10, // bufferSize
                "mappingsFile.json",
                "conceptTree.json",
                "dseMappingTree.json",
                false // useCql
        );
    }

    private TorchProperties minimalTorchPropertiesWithOAuth(String url) {
        var base = new TorchProperties.Base("http://base-url");
        var output = new TorchProperties.Output(new TorchProperties.Output.File(new TorchProperties.Output.File.Server("http://output-server")));
        var profile = new TorchProperties.Profile("profile-dir");
        var mapping = new TorchProperties.Mapping("consent-mapping.json", "type-to-consent.json");

        var page = new TorchProperties.Fhir.Page(10);
        var oauthIssuer = new TorchProperties.Fhir.Oauth.Issuer(url);
        var oauthClient = new TorchProperties.Fhir.Oauth.Client("clientId", "clientSecret");
        var oauth = new TorchProperties.Fhir.Oauth(oauthIssuer, oauthClient);
        var fhir = new TorchProperties.Fhir(
                "http://localhost/fhir",
                new TorchProperties.Max(5),
                page,
                oauth,
                new TorchProperties.Fhir.Disable(false),
                "", // user empty disables basic auth
                ""
        );
        var flare = new TorchProperties.Flare("http://flare-url");
        var results = new TorchProperties.Results("results-dir", "persistence");

        return new TorchProperties(
                base,
                output,
                profile,
                mapping,
                fhir,
                flare,
                results,
                1, // batchsize
                1, // maxConcurrency
                10, // bufferSize
                "mappingsFile.json",
                "conceptTree.json",
                "dseMappingTree.json",
                false // useCql
        );
    }

    @Test
    void testOauthExchangeFilterFunction_noOAuth() {
        var torchProperties = minimalTorchPropertiesWithBasicAuth();
        var appConfig = new AppConfig(torchProperties);

        ExchangeFilterFunction filter = appConfig.oauthExchangeFilterFunction(torchProperties);

        // This should be a pass-through filter (lambda)
        assertThat(filter.getClass().getName()).contains("$$Lambda");
    }

    @Test
    void testOauthExchangeFilterFunction_withOAuth() throws IOException {
        MockWebServer mockWebServer = new MockWebServer();
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
        var torchProperties = minimalTorchPropertiesWithOAuth(mockWebServer.url("/").toString());
        var appConfig = new AppConfig(torchProperties);


        ExchangeFilterFunction filter = appConfig.oauthExchangeFilterFunction(torchProperties);

        assertThat(filter).isInstanceOf(org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction.class);

        mockWebServer.shutdown();
    }

    @Test
    void testFhirWebClient_baseUrl() {
        var torchProperties = minimalTorchPropertiesWithBasicAuth();
        var appConfig = new AppConfig(torchProperties);
        ExchangeFilterFunction oauthFilter = appConfig.oauthExchangeFilterFunction(torchProperties);

        WebClient client = appConfig.fhirWebClient(torchProperties, oauthFilter);

        assertThat(client).isNotNull();

        assertThat(torchProperties.fhir().url()).isEqualTo("http://localhost/fhir");
    }

}

