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

    public static final TorchProperties.Fhir FHIR = new TorchProperties.Fhir(
            "http://fhir-url",
            new TorchProperties.Max(5),
            new TorchProperties.Fhir.Page(10),
            null,
            new TorchProperties.Fhir.Disable(true),
            "user",
            "password"
    );

    @Nested
    class FlareValidation {

        @Test
        void flareNullShouldThrow() {
            var fhir = new TorchProperties.Fhir(
                    "http://fhir-url",
                    new TorchProperties.Max(5),
                    new TorchProperties.Fhir.Page(10),
                    null,
                    new TorchProperties.Fhir.Disable(true),
                    "user",
                    "password"
            );

            assertThatThrownBy(() -> new TorchProperties(
                    new TorchProperties.Base("http://base-url"),
                    new TorchProperties.Output(new TorchProperties.Output.File(new TorchProperties.Output.File.Server("http://server-url"))),
                    new TorchProperties.Profile("/profile-dir"),
                    new TorchProperties.Mapping("consent", "typeToConsent"),
                    fhir,
                    null,
                    new TorchProperties.Results("/dir", "persistence"),
                    10,
                    5,
                    100,
                    "mappingsFile",
                    "conceptTreeFile",
                    "dseMappingTreeFile",
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
                    FHIR,
                    flare,
                    new TorchProperties.Results("/dir", "persistence"),
                    10,
                    5,
                    100,
                    "mappingsFile",
                    "conceptTreeFile",
                    "dseMappingTreeFile",
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
                    FHIR,
                    flare,
                    new TorchProperties.Results("/dir", "persistence"),
                    10,
                    5,
                    100,
                    "mappingsFile",
                    "conceptTreeFile",
                    "dseMappingTreeFile",
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
                    FHIR,
                    flare,
                    new TorchProperties.Results("/dir", "persistence"),
                    10,
                    5,
                    100,
                    "mappingsFile",
                    "conceptTreeFile",
                    "dseMappingTreeFile",
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
                    FHIR,
                    flare,
                    new TorchProperties.Results("/dir", "persistence"),
                    10,
                    5,
                    100,
                    "mappingsFile",
                    "conceptTreeFile",
                    "dseMappingTreeFile",
                    true
            )).doesNotThrowAnyException();
        }

    }

    @Nested
    class FhirURLValidation {

        @Test
        void fhirNullShouldThrow() {
            var flare = new TorchProperties.Flare("http://flare-url");

            assertThatThrownBy(() -> new TorchProperties(
                    new TorchProperties.Base("http://base-url"),
                    new TorchProperties.Output(new TorchProperties.Output.File(new TorchProperties.Output.File.Server("http://server-url"))),
                    new TorchProperties.Profile("/profile-dir"),
                    new TorchProperties.Mapping("consent", "typeToConsent"),
                    null,
                    flare,
                    new TorchProperties.Results("/dir", "persistence"),
                    10,
                    5,
                    100,
                    "mappingsFile",
                    "conceptTreeFile",
                    "dseMappingTreeFile",
                    true
            )).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("FHIR URL must not be null or empty");
        }

        @Test
        void fhirUrlNullShouldThrow() {
            var fhir = new TorchProperties.Fhir(
                    null,
                    new TorchProperties.Max(5),
                    new TorchProperties.Fhir.Page(10),
                    null,
                    new TorchProperties.Fhir.Disable(true),
                    null,
                    null
            );

            var flare = new TorchProperties.Flare("http://flare-url");

            assertThatThrownBy(() -> new TorchProperties(
                    new TorchProperties.Base("http://base-url"),
                    new TorchProperties.Output(new TorchProperties.Output.File(new TorchProperties.Output.File.Server("http://server-url"))),
                    new TorchProperties.Profile("/profile-dir"),
                    new TorchProperties.Mapping("consent", "typeToConsent"),
                    fhir,
                    flare,
                    new TorchProperties.Results("/dir", "persistence"),
                    10,
                    5,
                    100,
                    "mappingsFile",
                    "conceptTreeFile",
                    "dseMappingTreeFile",
                    true
            )).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("FHIR URL must not be null or empty");
        }

        @Test
        void emptyFhirUrlThrows() {
            var fhir = new TorchProperties.Fhir(
                    " ",
                    new TorchProperties.Max(5),
                    new TorchProperties.Fhir.Page(10),
                    null,
                    new TorchProperties.Fhir.Disable(true),
                    null,
                    null
            );

            var flare = new TorchProperties.Flare("http://flare-url");

            assertThatThrownBy(() -> new TorchProperties(
                    new TorchProperties.Base("http://base-url"),
                    new TorchProperties.Output(new TorchProperties.Output.File(new TorchProperties.Output.File.Server("http://server-url"))),
                    new TorchProperties.Profile("/profile-dir"),
                    new TorchProperties.Mapping("consent", "typeToConsent"),
                    fhir,
                    flare,
                    new TorchProperties.Results("/dir", "persistence"),
                    10,
                    5,
                    100,
                    "mappingsFile",
                    "conceptTreeFile",
                    "dseMappingTreeFile",
                    true
            )).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("FHIR URL must not be null or empty");
        }

        @Test
        void validFlareAndFhirUrlsShouldPass() {
            var flare = new TorchProperties.Flare("http://flare-url");
            var fhir = new TorchProperties.Fhir(
                    "http://fhir-url",
                    new TorchProperties.Max(5),
                    new TorchProperties.Fhir.Page(10),
                    null,
                    new TorchProperties.Fhir.Disable(true),
                    "user",
                    "password"
            );

            assertThatCode(() -> new TorchProperties(
                    new TorchProperties.Base("http://base-url"),
                    new TorchProperties.Output(new TorchProperties.Output.File(new TorchProperties.Output.File.Server("http://server-url"))),
                    new TorchProperties.Profile("/profile-dir"),
                    new TorchProperties.Mapping("consent", "typeToConsent"),
                    fhir,
                    flare,
                    new TorchProperties.Results("/dir", "persistence"),
                    10,
                    5,
                    100,
                    "mappingsFile",
                    "conceptTreeFile",
                    "dseMappingTreeFile",
                    false
            )).doesNotThrowAnyException();
        }

    }

    @Nested
    class OauthNullSetToEmptyString {
        @Test
        void defaultsWhenUserPasswordAndOauthNull() {
            TorchProperties.Fhir fhir = new TorchProperties.Fhir(
                    "http://fhir-url",
                    new TorchProperties.Max(5),
                    new TorchProperties.Fhir.Page(10),
                    null, // oauth is null
                    new TorchProperties.Fhir.Disable(true),
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
            TorchProperties.Fhir.Oauth oauth = new TorchProperties.Fhir.Oauth(null, null);
            assertThat(oauth.issuer().uri()).isEmpty();
            assertThat(oauth.client().id()).isEmpty();
            assertThat(oauth.client().secret()).isEmpty();
        }

        @Test
        void issuerDefaultsWhenUriNull() {
            TorchProperties.Fhir.Oauth.Issuer issuer = new TorchProperties.Fhir.Oauth.Issuer(null);
            assertThat(issuer.uri()).isEmpty();
        }

        @Test
        void clientDefaultsWhenIdAndSecretNull() {
            TorchProperties.Fhir.Oauth.Client client = new TorchProperties.Fhir.Oauth.Client(null, null);
            assertThat(client.id()).isEmpty();
            assertThat(client.secret()).isEmpty();
        }

        @Test
        void defaultOauthAndCredentials() {
            var fhir = new TorchProperties.Fhir(
                    "http://fhir-url",
                    new TorchProperties.Max(3),
                    new TorchProperties.Fhir.Page(5),
                    null,
                    new TorchProperties.Fhir.Disable(false),
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
            TorchProperties.Fhir.Oauth oauthWithNulls = new TorchProperties.Fhir.Oauth(null, null);
            assertThat(oauthWithNulls.issuer()).isNotNull();
            assertThat(oauthWithNulls.issuer().uri()).isEmpty();
            assertThat(oauthWithNulls.client()).isNotNull();
            assertThat(oauthWithNulls.client().id()).isEmpty();
            assertThat(oauthWithNulls.client().secret()).isEmpty();

            // Case 3: Issuer with null URI
            TorchProperties.Fhir.Oauth.Issuer issuerWithNull = new TorchProperties.Fhir.Oauth.Issuer(null);
            assertThat(issuerWithNull.uri()).isEmpty();

            // Case 4 & 5: Client with null id and secret
            TorchProperties.Fhir.Oauth.Client clientWithNulls = new TorchProperties.Fhir.Oauth.Client(null, null);
            assertThat(clientWithNulls.id()).isEmpty();
            assertThat(clientWithNulls.secret()).isEmpty();
        }
    }

}
