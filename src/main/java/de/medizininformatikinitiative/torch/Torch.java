package de.medizininformatikinitiative.torch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;


import static org.springframework.security.oauth2.core.AuthorizationGrantType.CLIENT_CREDENTIALS;


@SpringBootApplication
@ComponentScan(basePackages = {
        "de.medizininformatikinitiative.torch.config",
        "de.medizininformatikinitiative.torch.rest"
})
public class Torch {

    public static void main(String[] args) {
        SpringApplication.run(Torch.class, args);
    }
}
