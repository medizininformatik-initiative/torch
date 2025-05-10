package de.medizininformatikinitiative.torch;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.Parameters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

import static java.util.Objects.requireNonNull;
import static org.springframework.http.HttpStatus.ACCEPTED;

public class TorchClient {

    private static final int POLL_MAX_RETRIES = 1000;
    private static final Duration POLL_RETRY_DELAY = Duration.ofSeconds(2);

    private final WebClient webClient;
    private final FhirContext context = FhirContext.forR4();

    public TorchClient(WebClient webClient) {
        this.webClient = requireNonNull(webClient);
    }

    private static boolean shouldRetry(Throwable e) {
        return e instanceof WebClientResponseException e1 && e1.getStatusCode().isSameCodeAs(ACCEPTED);
    }

    /**
     * Executes the $extract-data operation.
     *
     * @param parameters the operation parameters
     * @return the status URL
     */
    public Mono<String> executeExtractData(Parameters parameters) {
        return webClient.post()
                .uri("/$extract-data")
                .header("Content-Type", "application/fhir+json")
                .bodyValue(context.newJsonParser().encodeResourceToString(parameters))
                .retrieve()
                .toBodilessEntity()
                .flatMap(e -> Mono.justOrEmpty(e.getHeaders().get("Content-Location")))
                .flatMap(headers -> Mono.justOrEmpty(headers.getFirst()));
    }

    public Mono<StatusResponse> pollStatus(String statusUrl) {
        return webClient.get().uri(statusUrl)
                .header("Accept", "application/json")
                .retrieve()
                .onStatus(status -> status.isSameCodeAs(ACCEPTED), ClientResponse::createException)
                .bodyToMono(StatusResponse.class)
                .retryWhen(Retry.fixedDelay(POLL_MAX_RETRIES, POLL_RETRY_DELAY).filter(TorchClient::shouldRetry));
    }
}
