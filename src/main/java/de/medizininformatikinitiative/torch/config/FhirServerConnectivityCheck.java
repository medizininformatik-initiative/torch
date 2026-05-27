package de.medizininformatikinitiative.torch.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@Profile("!test")
public class FhirServerConnectivityCheck implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(FhirServerConnectivityCheck.class);

    private final WebClient fhirClient;
    private final FhirProperties fhirProperties;

    public FhirServerConnectivityCheck(@Qualifier("fhirClient") WebClient fhirClient, FhirProperties fhirProperties) {
        this.fhirClient = fhirClient;
        this.fhirProperties = fhirProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        logger.info("Testing FHIR server connectivity: GET {}/metadata ...", fhirProperties.url());
        fhirClient.get()
                .uri("/metadata")
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(body -> logger.info("FHIR server at {} is reachable", fhirProperties.url()))
                .doOnError(e -> logger.warn("FHIR server connectivity check failed for {}: {}", fhirProperties.url(), e.getMessage()))
                .onErrorComplete()
                .block();
    }
}
