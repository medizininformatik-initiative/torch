package de.medizininformatikinitiative.torch;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Parameters;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public class TorchClient {

    private static final int POLL_MAX_RETRIES = 10;
    private static final Duration POLL_RETRY_DELAY = Duration.ofSeconds(2);

    private final WebClient webClient;
    private final FhirContext context = FhirContext.forR4();

    public TorchClient(WebClient webClient) {
        this.webClient = requireNonNull(webClient);
    }

    /**
     * Executes the $extract-data operation.
     *
     * @param parameters the operation parameters
     * @return the status URL
     */
    public String executeExtractData(Parameters parameters) {
        return webClient.post()
                .uri("/$extract-data")
                .header("Content-Type", "application/fhir+json")
                .bodyValue(context.newJsonParser().encodeResourceToString(parameters))
                .retrieve()
                .toEntity(String.class)
                .map(HttpEntity::getHeaders)
                .block()
                .get("Content-Location")
                .getFirst();
    }

    public StatusResponse pollStatus(String statusUrl) {
        return webClient.get().uri(statusUrl)
                .header("Accept", "application/json")
                .retrieve()
                .onStatus(status -> status.isSameCodeAs(HttpStatusCode.valueOf(202)), ClientResponse::createException)
                .bodyToMono(StatusResponse.class)
                .retryWhen(Retry.fixedDelay(POLL_MAX_RETRIES, POLL_RETRY_DELAY))
                .block();
    }
}
