package de.medizininformatikinitiative.torch.service;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.fhir.QueryParams;
import de.medizininformatikinitiative.torch.util.TimeUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.MeasureReport;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Optional;

import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.stringValue;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;

@Component
public class DataStore {
    private static final Logger logger = LoggerFactory.getLogger(DataStore.class);

    private final WebClient client;
    private final FhirContext fhirContext;
    private final int pageCount;


    @Autowired
    public DataStore(@Qualifier("fhirClient") WebClient client, FhirContext fhirContext,
                     @Value("${torch.fhir.pageCount}") int pageCount) {
        this.client = client;
        this.fhirContext = fhirContext;
        this.pageCount = pageCount;
    }


    /**
     * Executes {@code FHIRSearchQuery} and returns all resources found with that query.
     *
     * @param query the fhir search query defined by the attribute group
     * @return the resources found with the {@param FHIRSearchQuery}
     */
    public Flux<Resource> search(Query query) {
        var startNanoTime = System.nanoTime();
        logger.debug("Execute resource query: {}", query);

        return client.post()
                .uri("/" + query.type() + "/_search")
                .contentType(APPLICATION_FORM_URLENCODED)
                .bodyValue(query.params().toString())
                .retrieve()
                .bodyToMono(String.class)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                        .filter(e -> e instanceof WebClientResponseException &&
                                shouldRetry(((WebClientResponseException) e).getStatusCode())))
                .map(body -> fhirContext.newJsonParser().parseResource(Bundle.class, body))
                .expand(bundle -> Optional.ofNullable(bundle.getLink("next"))
                        .map(link -> fetchPage(link.getUrl()))
                        .orElse(Mono.empty()))
                .flatMap(bundle -> Flux.fromStream(bundle.getEntry().stream().map(Bundle.BundleEntryComponent::getResource)))
                .doOnComplete(() -> logger.debug("Finished query `{}` in {} seconds.", query,
                        "%.1f".formatted(TimeUtils.durationSecondsSince(startNanoTime))))
                .doOnError(e -> logger.error("Error while executing resource query `{}`: {}", query, e.getMessage()));
    }


    private Mono<Bundle> fetchPage(String url) {
        logger.trace("Fetch Page {}", url);
        return client.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> fhirContext.newJsonParser().parseResource(Bundle.class, response));
    }


    public Flux<String> executeCollectPatientIds(Query query) {
        logger.debug("Execute  fetch query: {}", query);
        return client.post()
                .uri("/{type}/_search", query.type())
                .contentType(APPLICATION_FORM_URLENCODED)
                .bodyValue(query.params().appendParams(extraQueryParams(query.type())).toString())
                .retrieve()
                .bodyToFlux(String.class)
                .map(response -> fhirContext.newJsonParser().parseResource(Bundle.class, response))
                .expand(bundle -> Optional.ofNullable(bundle.getLink("next"))
                        .map(link -> fetchPage(link.getUrl()))
                        .orElse(Mono.empty()))
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                        .filter(e -> e instanceof WebClientResponseException &&
                                shouldRetry(((WebClientResponseException) e).getStatusCode())))
                .flatMap(bundle -> Flux.fromStream(bundle.getEntry().stream().flatMap(e -> Optional.ofNullable(e.getResource().getId()).stream())))
                .doOnError(e -> logger.error("Error while executing query `{}`: {}", query, e.getMessage()));
    }

    private QueryParams extraQueryParams(String type) {
        return QueryParams.of("_elements", stringValue(queryElements(type)))
                .appendParam("_count", stringValue(Integer.toString(pageCount)));
    }

    private static String queryElements(String type) {
        return switch (type) {
            case "Patient" -> "id";
            case "Immunization", "Consent" -> "patient";
            default -> "subject";
        };
    }

    private static boolean shouldRetry(HttpStatusCode code) {
        return code.is5xxServerError() || code.value() == 404;
    }

    public Mono<Void> transact(Bundle bundle) {

        return client.post()
                .uri("")
                .header("Content-Type", "application/fhir+json")
                .bodyValue(fhirContext.newJsonParser().encodeResourceToString(bundle))
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(response -> {
                    try {
                        logger.trace("Received response: {}", response);
                    } catch (Exception e) {
                        throw new RuntimeException("Error processing the response", e);
                    }
                })
                .doOnSuccess(response -> logger.debug("Successfully transmitted Bundle"))
                .doOnError(error -> logger.error("Error occurred while transmitting Bundle: {}", error.getMessage()))
                .then();
    }


    /**
     * Get the {@link MeasureReport} for a previously transmitted Measure
     *
     * @param params the Parameters for the evaluation of the Measure
     * @return the retrieved {@link MeasureReport} from the server
     */
    public Mono<MeasureReport> evaluateMeasure(Parameters params) {
        logger.debug("Evaluating Measure with provided parameters.");
        logger.debug(params.toString());
        return client.post()
                .uri("/Measure/$evaluate-measure")
                .header("Content-Type", "application/fhir+json")
                .bodyValue(fhirContext.newJsonParser().encodeResourceToString(params))
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(response -> {
                    logger.debug("Parsing response into MeasureReport.");
                    return Mono.just(fhirContext.newJsonParser().parseResource(MeasureReport.class, response));
                })
                .doOnSuccess(measureReport -> logger.debug("Successfully evaluated Measure and received MeasureReport."))
                .doOnError(error -> logger.error("Error occurred while evaluating Measure: {}", error.getMessage()));
    }

}
