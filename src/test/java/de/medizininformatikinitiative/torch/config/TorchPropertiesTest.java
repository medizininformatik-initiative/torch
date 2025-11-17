package de.medizininformatikinitiative.torch.config;


import org.junit.experimental.runners.Enclosed;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RunWith(Enclosed.class)
public class TorchPropertiesTest {

    @Nested
    class TestEmptyFields {

        @Test
        void testIsNotSet_withNull() {
            String property = null;

            assertThat(TorchProperties.isNotSet(property)).isTrue();
        }

        @Test
        void testIsNotSet_withBlankString() {
            String property = "";

            assertThat(TorchProperties.isNotSet(property)).isTrue();
        }

        @Test
        void testIsNotSet_withLiteralQuotes() {
            String property = "\"\"";

            assertThat(TorchProperties.isNotSet(property)).isTrue();
        }

        @Test
        void testIsNotSet_withCharSequence() {
            String property = "test";

            assertThat(TorchProperties.isNotSet(property)).isFalse();
        }
    }

    @Nested
    class FlareValidation {

        @Test
        void flareNullShouldThrow() {
            assertThatThrownBy(() -> new TorchProperties(
                    new TorchProperties.Base("http://base-url"),
                    new TorchProperties.Output(new TorchProperties.Output.File(new TorchProperties.Output.File.Server("http://server-url"))),
                    new TorchProperties.Profile("/profile-dir"),
                    new TorchProperties.Mapping("consent", "typeToConsent"),
                    null,
                    new TorchProperties.Results("/dir", "persistence"),
                    10,
                    5,
                    100,
                    "mappingsFile",
                    "conceptTreeFile",
                    "dseMappingTreeFile",
                    "search-parameters.json",
                    false // useCql=false
            )).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("When useCql is false, flare.url must be a non-empty string");
        }

        @Test
        void flareURLNullShouldThrow() {
            var flare = new TorchProperties.Flare(null);

            assertThatThrownBy(() -> new TorchProperties(
                    new TorchProperties.Base("http://base-url"),
                    new TorchProperties.Output(new TorchProperties.Output.File(new TorchProperties.Output.File.Server("http://server-url"))),
                    new TorchProperties.Profile("/profile-dir"),
                    new TorchProperties.Mapping("consent", "typeToConsent"),
                    flare,
                    new TorchProperties.Results("/dir", "persistence"),
                    10,
                    5,
                    100,
                    "mappingsFile",
                    "conceptTreeFile",
                    "dseMappingTreeFile",
                    "search-parameters.json",
                    false
            )).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("When useCql is false, flare.url must be a non-empty string");
        }

        @Test
        void flareURLBlankShouldThrow() {
            var flare = new TorchProperties.Flare("  ");

            assertThatThrownBy(() -> new TorchProperties(
                    new TorchProperties.Base("http://base-url"),
                    new TorchProperties.Output(new TorchProperties.Output.File(new TorchProperties.Output.File.Server("http://server-url"))),
                    new TorchProperties.Profile("/profile-dir"),
                    new TorchProperties.Mapping("consent", "typeToConsent"),
                    flare,
                    new TorchProperties.Results("/dir", "persistence"),
                    10,
                    5,
                    100,
                    "mappingsFile",
                    "conceptTreeFile",
                    "dseMappingTreeFile",
                    "search-parameters.json",
                    false
            )).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("When useCql is false, flare.url must be a non-empty string");
        }

        @Test
        void flareURLSetPasses() {
            var flare = new TorchProperties.Flare("http://flare-url");

            assertThatCode(() -> new TorchProperties(
                    new TorchProperties.Base("http://base-url"),
                    new TorchProperties.Output(new TorchProperties.Output.File(new TorchProperties.Output.File.Server("http://server-url"))),
                    new TorchProperties.Profile("/profile-dir"),
                    new TorchProperties.Mapping("consent", "typeToConsent"),
                    flare,
                    new TorchProperties.Results("/dir", "persistence"),
                    10,
                    5,
                    100,
                    "mappingsFile",
                    "conceptTreeFile",
                    "dseMappingTreeFile",
                    "search-parameters.json",
                    false
            )).doesNotThrowAnyException();
        }

        @Test
        void flareIgnoredWithCQL() {
            var flare = new TorchProperties.Flare(null);

            assertThatCode(() -> new TorchProperties(
                    new TorchProperties.Base("http://base-url"),
                    new TorchProperties.Output(new TorchProperties.Output.File(new TorchProperties.Output.File.Server("http://server-url"))),
                    new TorchProperties.Profile("/profile-dir"),
                    new TorchProperties.Mapping("consent", "typeToConsent"),
                    flare,
                    new TorchProperties.Results("/dir", "persistence"),
                    10,
                    5,
                    100,
                    "mappingsFile",
                    "conceptTreeFile",
                    "dseMappingTreeFile",
                    "search-parameters.json",
                    true
            )).doesNotThrowAnyException();
        }

    }

    @Nested
    class OauthNullSetToEmptyString {
        @Test
        void defaultsWhenUserPasswordAndOauthNull() {
            FhirProperties fhir = new FhirProperties(
                    "http://fhir-url",
                    new FhirProperties.Max(5),
                    new FhirProperties.Page(10),
                    null, // oauth is null
                    new FhirProperties.Disable(true),
                    null, // user is null
                    null  // password is null
            );

            assertThat(fhir.user()).isEmpty();
            assertThat(fhir.password()).isEmpty();
            assertThat(fhir.oauth()).isNotNull();
            assertThat(fhir.oauth().issuer().uri()).isEmpty();
            assertThat(fhir.oauth().client().id()).isEmpty();
            assertThat(fhir.oauth().client().secret()).isEmpty();
        }

        @Test
        void issuerAndClientNull() {
            FhirProperties.Oauth oauth = new FhirProperties.Oauth(null, null);
            assertThat(oauth.issuer().uri()).isEmpty();
            assertThat(oauth.client().id()).isEmpty();
            assertThat(oauth.client().secret()).isEmpty();
        }

        @Test
        void issuerDefaultsWhenUriNull() {
            FhirProperties.Oauth.Issuer issuer = new FhirProperties.Oauth.Issuer(null);
            assertThat(issuer.uri()).isEmpty();
        }

        @Test
        void clientDefaultsWhenIdAndSecretNull() {
            FhirProperties.Oauth.Client client = new FhirProperties.Oauth.Client(null, null);
            assertThat(client.id()).isEmpty();
            assertThat(client.secret()).isEmpty();
        }

        @Test
        void defaultOauthAndCredentials() {
            var fhir = new FhirProperties(
                    "http://fhir-url",
                    new FhirProperties.Max(3),
                    new FhirProperties.Page(5),
                    null,
                    new FhirProperties.Disable(false),
                    null,
                    null
            );

            assertThat(fhir.user()).isEmpty();
            assertThat(fhir.password()).isEmpty();

            var oauth = fhir.oauth();
            assertThat(oauth).isNotNull();

            assertThat(oauth.issuer()).isNotNull();
            assertThat(oauth.issuer().uri()).isEmpty();

            assertThat(oauth.client()).isNotNull();
            assertThat(oauth.client().id()).isEmpty();
            assertThat(oauth.client().secret()).isEmpty();
        }

        @Test
        void oauthDefaultsWhenIssuerAndClientNullOrWithNullFields() {
            // Case 1 & 2: Oauth constructed with null issuer and null client
            FhirProperties.Oauth oauthWithNulls = new FhirProperties.Oauth(null, null);
            assertThat(oauthWithNulls.issuer()).isNotNull();
            assertThat(oauthWithNulls.issuer().uri()).isEmpty();
            assertThat(oauthWithNulls.client()).isNotNull();
            assertThat(oauthWithNulls.client().id()).isEmpty();
            assertThat(oauthWithNulls.client().secret()).isEmpty();

            // Case 3: Issuer with null URI
            FhirProperties.Oauth.Issuer issuerWithNull = new FhirProperties.Oauth.Issuer(null);
            assertThat(issuerWithNull.uri()).isEmpty();

            // Case 4 & 5: Client with null id and secret
            FhirProperties.Oauth.Client clientWithNulls = new FhirProperties.Oauth.Client(null, null);
            assertThat(clientWithNulls.id()).isEmpty();
            assertThat(clientWithNulls.secret()).isEmpty();
        }
    }

}
