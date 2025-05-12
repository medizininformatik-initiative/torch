package de.medizininformatikinitiative.torch.service;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.util.TimeUtils;
import org.hl7.fhir.instance.model.api.IBaseResource;
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
import static java.util.Objects.requireNonNull;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;

@Component
public class DataStore {

    private static final Logger logger = LoggerFactory.getLogger(DataStore.class);

    private final WebClient client;
    private final FhirContext fhirContext;
    private final int pageCount;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public DataStore(@Qualifier("fhirClient") WebClient client, FhirContext fhirContext, @Value("${torch.fhir.pageCount}") int pageCount) {
        this.client = requireNonNull(client);
        this.fhirContext = requireNonNull(fhirContext);
        this.pageCount = pageCount;
    }

    private static boolean shouldRetry(HttpStatusCode code) {
        return code.is5xxServerError() || code.value() == 404;
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
                .retryWhen(Retry.backoff(5, Duration.ofSeconds(1))
                        .filter(e -> e instanceof WebClientResponseException &&
                                shouldRetry(((WebClientResponseException) e).getStatusCode())))
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
     * Executes {@code FHIRSearchQuery} and returns all resources found with that query.
     *
     * @param query the fhir search query defined by the attribute group
     * @return the resources found with the {@code FHIRSearchQuery}
     */
    public Flux<Resource> search(Query query) {
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
                .retryWhen(Retry.backoff(5, Duration.ofSeconds(1))
                        .filter(e -> e instanceof WebClientResponseException &&
                                shouldRetry(((WebClientResponseException) e).getStatusCode())))
                .flatMap(bundle -> Flux.fromStream(bundle.getEntry().stream().map(Bundle.BundleEntryComponent::getResource)))
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
                .retryWhen(Retry.backoff(5, Duration.ofSeconds(1))
                        .filter(e -> e instanceof WebClientResponseException &&
                                shouldRetry(((WebClientResponseException) e).getStatusCode())))
                .doOnSuccess(response -> logger.debug("Successfully executed a transaction."))
                .doOnError(error -> logger.error("Error occurred executing a transaction: {}", error.getMessage()))
                .then();
    }

    /**
     * Get the {@link MeasureReport} for a previously transmitted Measure
     *
     * @param params the Parameters for the evaluation of the Measure
     * @return the retrieved {@link MeasureReport} from the server
     */
    public Mono<MeasureReport> evaluateMeasure(Parameters params) {
        var measureUrn = params.getParameter("measure").getValue().toString();
        logger.debug("Evaluate Measure with URN {}...", measureUrn);

        return client.post()
                .uri("/Measure/$evaluate-measure")
                .header("Content-Type", "application/fhir+json")
                .bodyValue(fhirContext.newJsonParser().encodeResourceToString(params))
                .retrieve()
                .bodyToMono(String.class)
                .retryWhen(Retry.backoff(5, Duration.ofSeconds(1))
                        .filter(e -> e instanceof WebClientResponseException &&
                                shouldRetry(((WebClientResponseException) e).getStatusCode())))
                .map(response -> fhirContext.newJsonParser().parseResource(MeasureReport.class, response))
                .doOnSuccess(measureReport -> logger.debug("Successfully evaluated Measure with URN {}.", measureUrn))
                .doOnError(error -> logger.error("Error occurred while evaluating Measure with URN {}: {}", measureUrn, error.getMessage()));
    }
}
