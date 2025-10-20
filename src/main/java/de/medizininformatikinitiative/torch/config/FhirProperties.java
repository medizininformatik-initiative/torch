package de.medizininformatikinitiative.torch.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import static de.medizininformatikinitiative.torch.config.TorchProperties.isNotSet;

@ConfigurationProperties(prefix = "torch.fhir")
@Validated
public record FhirProperties(
        @NotBlank(message = "FHIR URL is required") String url,
        @Valid Max max,
        @Valid Page page,
        @Valid Oauth oauth,
        @Valid Disable disable,
        String user,
        String password) {

    public FhirProperties {
        if (isNotSet(user)) user = "";
        if (isNotSet(password)) password = "";
        if (oauth == null) {
            oauth = new Oauth(new Oauth.Issuer(""), new Oauth.Client("", ""));
        }
    }


    public record Page(@Min(value = 1, message = "Page count must be at least 1") int count) {
    }

    public record Disable(boolean async) {
    }

    public record Oauth(@Valid Issuer issuer, @Valid Client client) {
        public Oauth {
            if (issuer == null) issuer = new Issuer("");
            if (client == null) client = new Client("", "");
        }

        public record Issuer(String uri) {
            public Issuer {
                if (isNotSet(uri)) uri = "";
            }
        }

        public record Client(String id, String secret) {
            public Client {
                if (isNotSet(id)) id = "";
                if (isNotSet(secret)) secret = "";
            }
        }
    }

    public record Max(@Min(value = 1, message = "Max connections must be at least 1") int connections) {
    }
}
