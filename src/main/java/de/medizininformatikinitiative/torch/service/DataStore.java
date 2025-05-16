package de.medizininformatikinitiative.torch.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.util.TimeUtils;
import org.hl7.fhir.instance.model.api.IBaseResource;
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
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.stringValue;
import static java.util.Objects.requireNonNull;
import static org.springframework.http.HttpStatus.ACCEPTED;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;

@Component
public class DataStore {

    private static final Logger logger = LoggerFactory.getLogger(DataStore.class);

    private static final Duration ASYNC_POLL_DELAY = Duration.ofSeconds(2);
    private static final RetryBackoffSpec ASYNC_POLL_RETRY_SPEC = Retry.fixedDelay(1000, ASYNC_POLL_DELAY)
            .filter(e -> e instanceof AsyncRetryException);
    private static final RetryBackoffSpec RETRY_SPEC = Retry.backoff(5, Duration.ofSeconds(1))
            .filter(e -> e instanceof WebClientResponseException e1 &&
                    shouldRetry((e1).getStatusCode()));

    private final WebClient client;
    private final FhirContext fhirContext;
    private final int pageCount;
    private final Consumer<HttpHeaders> preferHeaderSetter;

    private final ObjectMapper objectMapper = new ObjectMapper();

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
     * Loads a single resource by id
     *
     * @param reference Reference String of the resource to be loaded
     * @return the resources found in {@param reference}
     */
    public Mono<Resource> fetchResourceByReference(String reference) {
        logger.debug("Fetching resource by reference {}", reference);
        if (reference.startsWith("http") || reference.startsWith("https")) { // Absolute URL
            return Mono.error(new IllegalArgumentException("Absolute reference " + reference + "not supported"));
        } else if (reference.contains("/")) { // Relative reference
            String[] parts = reference.split("/");
            if (parts.length != 2) {
                return Mono.error(new IllegalArgumentException("Unexpected reference format: " + reference));
            }
            String type = parts[0];
            String id = parts[1];

            return fetchAndParseResource(type, id);
        } else {
            return Mono.error(new IllegalArgumentException("Invalid FHIR reference format: " + reference));
        }
    }

    private Mono<Resource> fetchAndParseResource(String type, String id) {
        return client.get()
                .uri("/{type}/{id}", type, id)
                .retrieve()
                .bodyToMono(String.class)
                .retryWhen(RETRY_SPEC)
                .flatMap(this::parseFhirResource)
                .doOnSuccess(resource -> logger.debug("Successfully fetched resource: {}/{}", type, id))
                .doOnError(error -> logger.error("Failed to fetch resource {}/{}: {}", type, id, error.getMessage()));
    }

    private Mono<Resource> parseFhirResource(String jsonBody) {
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonBody);
            if (!jsonNode.has("resourceType")) {
                return Mono.error(new RuntimeException("Missing 'resourceType' field in response"));
            }
            String resourceType = jsonNode.get("resourceType").asText();
            Class<? extends IBaseResource> resourceClass = fhirContext.getResourceDefinition(resourceType).getImplementingClass();
            IBaseResource parsedResource = fhirContext.newJsonParser().parseResource(resourceClass, jsonBody);
            if (parsedResource instanceof Resource resource) {
                return Mono.just(resource);
            } else {
                return Mono.error(new ClassCastException("Parsed object is not a valid FHIR Resource: " + resourceClass.getSimpleName()));
            }
        } catch (Exception e) {
            return Mono.error(new RuntimeException("Failed to parse FHIR resource", e));
        }
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
                .header("Content-Type", "application/fhir+json")
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
                .header("Content-Type", "application/fhir+json")
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
