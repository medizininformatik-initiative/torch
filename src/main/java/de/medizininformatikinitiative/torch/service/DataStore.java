package de.medizininformatikinitiative.torch.service;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.flare.Util;
import de.medizininformatikinitiative.flare.model.Population;
import de.medizininformatikinitiative.flare.model.fhir.Query;
import de.medizininformatikinitiative.flare.model.fhir.QueryParams;
import org.hl7.fhir.r4.model.Bundle;
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

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static de.medizininformatikinitiative.flare.model.fhir.QueryParams.stringValue;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;

@Component
public class DataStore {
    private static final Logger logger = LoggerFactory.getLogger(DataStore.class);

    private final WebClient client;
    private final FhirContext fhirContext;
    private final Clock clock;
    private final int pageCount;

    @Autowired
    public DataStore(@Qualifier("fhirClient") WebClient client, FhirContext fhirContext,  @Qualifier("systemDefaultZone") Clock clock,
                     @Value("${torch.fhir.pageCount}") int pageCount) {
        this.client = client;
        this.fhirContext = fhirContext;
        this.clock = clock;
        this.pageCount = pageCount;
    }

    /**
     * Executes {@code FHIRSearchQuery} and returns all resources found with that query.
     *
     * @param parameters the fhir search query parameters defining the patient resources to be fetched
     * @return the resources found with the {@param FHIRSearchQuery}
     */
    public Flux<Resource> getResources(String resourceType,String parameters) {
        //logger.debug("Search Parameters{}", parameters);


        return client.post()
                .uri("/"+resourceType+"/_search")
                .bodyValue(parameters)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(response -> logger.debug("getResources Response: {}", response))
                .flatMap(response -> Mono.just(fhirContext.newJsonParser().parseResource(Bundle.class, response)))
                .expand(bundle -> Optional.ofNullable(bundle.getLink("next"))
                        .map(link -> fetchPage(client, link.getUrl()))
                        .orElse(Mono.empty()))
                .flatMap(bundle -> Flux.fromStream(bundle.getEntry().stream().map(Bundle.BundleEntryComponent::getResource)));
    }

    private Mono<Bundle> fetchPage(WebClient client, String url) {
        //logger.debug("Fetch Page {}", url);
        return client.get()
                .uri(URI.create(url))
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> fhirContext.newJsonParser().parseResource(Bundle.class, response));
    }

    private Mono<de.medizininformatikinitiative.flare.model.fhir.Bundle> fetchPageCompressed(String url) {
        logger.trace("fetch page {}", url);
        return client.get()
                .uri(url)
                .retrieve()
                .bodyToMono(de.medizininformatikinitiative.flare.model.fhir.Bundle.class);
    }

    public Mono<List<String>> executeCollectPatientIds(Query query) {
        var startNanoTime = System.nanoTime();
        logger.debug("Execute query: {}", query);
        return client.post()
                .uri("/{type}/_search", query.type())
                .contentType(APPLICATION_FORM_URLENCODED)
                .bodyValue(query.params().appendParams(extraQueryParams(query.type())).toString())
                .retrieve()
                .bodyToFlux(de.medizininformatikinitiative.flare.model.fhir.Bundle.class)
                .expand(bundle -> bundle.linkWithRel("next")
                        .map(link -> fetchPageCompressed(link.url()))
                        .orElse(Mono.empty()))
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                        .filter(e -> e instanceof WebClientResponseException &&
                                shouldRetry(((WebClientResponseException) e).getStatusCode())))
                .flatMap(bundle -> Flux.fromStream(bundle.entry().stream().flatMap(e -> e.resource().patientId().stream())))
                .collectList()
                .doOnNext(p -> logger.debug("Finished query `{}` returning {} patients in {} seconds.", query, p.size(),
                        "%.1f".formatted(Util.durationSecondsSince(startNanoTime))))
                .doOnError(e -> logger.error("Error while executing query `{}`: {}", query, e.getMessage()));
    }

    private QueryParams extraQueryParams(String type) {
        return QueryParams.of("_elements", stringValue(queryElements(type)))
                .appendParam("_count", stringValue(Integer.toString(pageCount)));
    }

    private static String queryElements(String type) {
        return switch (type) {
            case "Patient" -> "id";
            case "Immunization" -> "patient";
            case "Consent" -> "patient";
            default -> "subject";
        };
    }

    private static boolean shouldRetry(HttpStatusCode code) {
        return code.is5xxServerError() || code.value() == 404;
    }


}
