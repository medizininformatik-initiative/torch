package de.medizininformatikinitiative.torch.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.util.TimeUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.MeasureReport;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.stringValue;
import static java.util.Objects.requireNonNull;
import static org.springframework.http.HttpStatus.ACCEPTED;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;

@Component
public class DataStore {

    private static final Logger logger = LoggerFactory.getLogger(DataStore.class);

    private static final Duration ASYNC_POLL_DELAY = Duration.ofSeconds(2);
    private static final RetryBackoffSpec ASYNC_POLL_RETRY_SPEC = Retry.fixedDelay(1000, ASYNC_POLL_DELAY)
            .filter(AsyncRetryException.class::isInstance);
    private static final RetryBackoffSpec RETRY_SPEC = Retry.backoff(5, Duration.ofSeconds(1))
            .filter(e -> e instanceof WebClientResponseException e1 &&
                    shouldRetry((e1).getStatusCode()));
    public static final String APPLICATION_FHIR_JSON = "application/fhir+json";
    public static final String CONTENT_TYPE = "Content-Type";

    private final WebClient client;
    private final FhirContext fhirContext;
    private final int pageCount;
    private final Consumer<HttpHeaders> preferHeaderSetter;

    @Autowired
    public DataStore(@Qualifier("fhirClient") WebClient client, FhirContext fhirContext,
                     @Value("${torch.fhir.page.count}") int pageCount,
                     @Value("${torch.fhir.disable.async}") boolean disableAsync) {
        logger.info("Init DataStore with pageCount = {}, disableAsync = {}", pageCount, disableAsync);
        this.client = requireNonNull(client);
        this.fhirContext = requireNonNull(fhirContext);
        this.pageCount = pageCount;
        preferHeaderSetter = disableAsync ? headers -> {
        } : headers -> headers.add("Prefer", "respond-async,return=representation");
    }

    private static boolean shouldRetry(HttpStatusCode code) {
        return code.is5xxServerError() || code.value() == 404;
    }

    private static Exception handleAcceptedResponse(ClientResponse response) {
        List<String> locations = response.headers().header("Content-Location");
        return locations.isEmpty() ? new MissingContentLocationException() : new AsyncException(locations.getFirst());
    }

    /**
     * Builds a search batch bundle for all resources specified in {@code idsByType}.
     * <p>Creates for every resource type a searchstring with comma concatenated ids.
     *
     * @param idsByType A map from resource types to sets of ids
     * @return search batch bundle
     */
    public Bundle createBatchBundleForReferences(Map<String, Set<String>> idsByType) {
        // Build the batch bundle
        Bundle batchBundle = new Bundle();
        batchBundle.setType(Bundle.BundleType.BATCH);
        batchBundle.getMeta().setLastUpdated(new Date());


        idsByType.forEach((type, ids) -> {
            String joinedIds = String.join(",", ids);
            Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();
            entry.setRequest(new Bundle.BundleEntryRequestComponent()
                    .setMethod(Bundle.HTTPVerb.GET)
                    .setUrl(type + "?_id=" + joinedIds));
            batchBundle.addEntry(entry);
        });

        return batchBundle;

    }


    /**
     * Loads a batch of resources.
     *
     * @param idsByType A map from resource types to sets of ids
     * @return the resources found
     */
    public Mono<List<Resource>> executeSearchBatch(Map<String, Set<String>> idsByType) {
        if (idsByType == null || idsByType.isEmpty()) {
            return Mono.empty(); // No search to execute
        }

        var start = System.nanoTime();
        logger.debug("Execute batch with {} request(s)", idsByType.size());

        return client.post()
                .uri("") // Target endpoint already set up in WebClient
                .header(HttpHeaders.CONTENT_TYPE, APPLICATION_FHIR_JSON)
                .bodyValue(serializeBatchBundle(idsByType))
                .retrieve()
                .bodyToMono(String.class)
                .retryWhen(RETRY_SPEC)
                .map(body -> fhirContext.newJsonParser().parseResource(Bundle.class, body))
                .map(this::extractResourcesFromBundle)
                .flatMap(resources -> resources.isEmpty() ? Mono.empty() : Mono.just(resources))
                .doOnSuccess(resources -> logger.debug(
                        "Finished reference Batch Bundle in {} seconds.",
                        "%.1f".formatted(TimeUtils.durationSecondsSince(start))))
                .doOnError(e -> logger.error(
                        "Error while executing batch reference query: {}", e.getMessage()));
    }

    private List<Resource> extractResourcesFromBundle(Bundle bundle) {
        return bundle.getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResource)
                .flatMap(resource -> {
                    if (resource instanceof Bundle nestedBundle) {
                        return nestedBundle.getEntry().stream()
                                .map(Bundle.BundleEntryComponent::getResource);
                    } else {
                        logger.warn("Found unexpected resource type {} in batch-response",
                                resource.getClass().getSimpleName());
                        return Stream.empty();
                    }
                })
                .toList();
    }

    private String serializeBatchBundle(Map<String, Set<String>> idsByType) {
        Bundle batchBundle = createBatchBundleForReferences(idsByType);
        return fhirContext.newJsonParser().encodeResourceToString(batchBundle);
    }


    /**
     * Executes {@code query} and returns all resources found.
     *
     * <p> All bundles that don't correspond to the given {@code resourceType} are ignored
     * and a warning about that event is logged.
     *
     * @param query        the fhir search query defined by the attribute group
     * @param resourceType the Type of the Bundle entries queried
     * @return the resources found
     */
    public <T extends Resource> Flux<T> search(Query query, Class<T> resourceType) {
        var start = System.nanoTime();
        logger.debug("Execute query: {}", query);

        return client.post()
                .uri("/" + query.type() + "/_search")
                .contentType(APPLICATION_FORM_URLENCODED)
                .bodyValue(query.params().appendParam("_count", stringValue(Integer.toString(pageCount))).toString())
                .retrieve()
                .bodyToMono(String.class)
                .map(body -> fhirContext.newJsonParser().parseResource(Bundle.class, body))
                .expand(bundle -> Optional.ofNullable(bundle.getLink("next"))
                        .map(link -> fetchPage(link.getUrl()))
                        .orElse(Mono.empty()))
                .retryWhen(RETRY_SPEC)
                .flatMap(bundle -> Flux.fromStream(bundle.getEntry().stream().map(Bundle.BundleEntryComponent::getResource)))
                .flatMap(resource -> {
                    if (resourceType.isInstance(resource)) {
                        return Mono.just(resourceType.cast(resource));
                    } else {
                        logger.warn("Found miss match resource type {} querying type {}", resource.getClass().getSimpleName(), query.type());
                        return Mono.empty();
                    }
                })
                .doOnComplete(() -> logger.debug("Finished query `{}` in {} seconds.", query,
                        "%.1f".formatted(TimeUtils.durationSecondsSince(start))))
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

    public Mono<Void> transact(Bundle bundle) {
        logger.debug("Execute transaction...");
        return client.post()
                .uri("")
                .header(CONTENT_TYPE, APPLICATION_FHIR_JSON)
                .bodyValue(fhirContext.newJsonParser().encodeResourceToString(bundle))
                .retrieve()
                .bodyToMono(String.class)
                .retryWhen(RETRY_SPEC)
                .doOnSuccess(response -> logger.debug("Successfully executed a transaction."))
                .doOnError(error -> logger.error("Error occurred executing a transaction: {}", error.getMessage()))
                .then();
    }

    /**
     * Evaluate a previously transmitted {@code Measure}.
     *
     * <p> In case this {@code DataStore} isn't initialized with {@code disableAsync} set to {@code true},
     *
     * @param params the parameters have to contain at least the {@code measure} parameter
     * @return the result of the {@code Measure} evaluation as {@link MeasureReport}
     */
    public Mono<MeasureReport> evaluateMeasure(Parameters params) {
        var start = System.nanoTime();
        var measureUrn = params.getParameter("measure").getValue().toString();
        logger.debug("Evaluate Measure with URN {}...", measureUrn);

        return client.post()
                .uri("/Measure/$evaluate-measure")
                .headers(preferHeaderSetter)
                .header(CONTENT_TYPE, APPLICATION_FHIR_JSON)
                .bodyValue(fhirContext.newJsonParser().encodeResourceToString(params))
                .retrieve()
                .onStatus(status -> status.isSameCodeAs(ACCEPTED), response -> Mono.error(handleAcceptedResponse(response)))
                .bodyToMono(String.class)
                .flatMap(body -> parseResource(MeasureReport.class, body))
                .onErrorResume(AsyncException.class, e -> pollStatus(e.getStatusUrl(), measureUrn, start))
                .doOnSuccess(measureReport -> logger.debug("Successfully evaluated Measure with URN {} in {} seconds.",
                        measureUrn, "%.1f".formatted(TimeUtils.durationSecondsSince(start))))
                .doOnError(error -> logger.error("Error occurred while evaluating Measure with URN {}: {}", measureUrn, error.getMessage()));
    }

    private <T extends Resource> Mono<T> parseResource(Class<T> resourceType, String body) {
        try {
            return Mono.just(fhirContext.newJsonParser().parseResource(resourceType, body));
        } catch (DataFormatException e) {
            return Mono.error(e);
        }
    }

    private Mono<MeasureReport> pollStatus(String statusUrl, String measureUrn, long start) {
        return client.get().uri(statusUrl)
                .retrieve()
                .onStatus(status -> status.isSameCodeAs(ACCEPTED), response -> {
                    logger.trace("Evaluation of measure with URN {} is still in progress for {} seconds. Next try will be in {}.",
                            measureUrn, "%.1f".formatted(TimeUtils.durationSecondsSince(start)), ASYNC_POLL_DELAY);
                    return Mono.error(new AsyncRetryException());
                })
                .bodyToMono(String.class)
                .retryWhen(ASYNC_POLL_RETRY_SPEC)
                .retryWhen(RETRY_SPEC)
                .flatMap(body -> parseResource(Bundle.class, body))
                .flatMap(bundle -> {
                    var entry = bundle.getEntryFirstRep();
                    if (entry.hasResponse()) {
                        var response = entry.getResponse();
                        if ("201".equals(response.getStatus())) {
                            if (entry.hasResource()) {
                                return entry.getResource().getResourceType() == ResourceType.MeasureReport
                                        ? Mono.just((MeasureReport) entry.getResource())
                                        : Mono.error(new RuntimeException("unknown resource type"));
                            } else {
                                return Mono.error(new RuntimeException("missing resource"));
                            }
                        } else {
                            if (response.hasOutcome()) {
                                return response.getOutcome().getResourceType() == ResourceType.OperationOutcome
                                        ? Mono.error(new OutcomeException((OperationOutcome) response.getOutcome()))
                                        : Mono.error(new RuntimeException("unknown resource type"));
                            } else {
                                return Mono.error(new RuntimeException("missing outcome"));
                            }
                        }
                    } else {
                        return Mono.error(new RuntimeException("missing response"));
                    }
                });
    }

    private static class AsyncException extends Exception {

        private final String statusUrl;

        private AsyncException(String statusUrl) {
            this.statusUrl = requireNonNull(statusUrl);
        }

        public String getStatusUrl() {
            return statusUrl;
        }
    }

    private static class MissingContentLocationException extends Exception {

    }

    private static class AsyncRetryException extends Exception {

    }
}
