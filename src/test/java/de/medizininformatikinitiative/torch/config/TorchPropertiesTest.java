package de.medizininformatikinitiative.torch.config;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    public void whenUseCqlFalseAndFlareNull_shouldThrow() {
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
    public void whenFhirNull_shouldThrow() {
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
    public void flareURLNotRequiredWithCQLToggled() {
        var flare = new TorchProperties.Flare(null);
        var torchProperties = new TorchProperties(
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
        );

        assertThat(torchProperties.fhir().url()).isEqualTo("http://fhir-url");
        assertThat(torchProperties.useCql()).isTrue();
    }

    @Test
    public void flareURLSetWithFlareUseToggled() {
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
    public void testInvalidTorchProperties_withUseCqlFalse_flareUrlBlank_shouldThrow() {
        var flare = new TorchProperties.Flare(" "); // blank url

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
    public void noFHIRUrlThrowsIllegalArgumentException() {
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
    public void emptyFHIRUrlThrowsIllegalArgumentException() {
        var fhir = new TorchProperties.Fhir(
                "",
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
    public void defaultOauthAndCredentials() {
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

    // NEW: ensures both flare URL and FHIR URL checks pass with useCql=false
    @Test
    public void validFlareAndFhirUrlsWithUseCqlFalseShouldPass() {
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
                false // useCql=false
        )).doesNotThrowAnyException();
    }
}
