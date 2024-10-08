package de.medizininformatikinitiative.torch;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Optional;

@Component
public class DataStore {
    private static final Logger logger = LoggerFactory.getLogger(DataStore.class);

    private final WebClient client;
    private final FhirContext fhirContext;

    @Autowired
    public DataStore(@Qualifier("fhirClient") WebClient client, FhirContext fhirContext) {
        this.client = client;
        this.fhirContext = fhirContext;
    }

    /**
     * Executes {@code FHIRSearchQuery} and returns all resources found with that query.
     *
     * @param parameters the fhir search query parameters defining the patient resources to be fetched
     * @return the resources found with the {@param FHIRSearchQuery}
     */
    public Flux<Resource> getResources(String resourceType, String parameters) {
        logger.debug("Executing FHIR search for resourceType: {} with parameters: {}", resourceType, parameters);

        return client.post()
                .uri("/" + resourceType + "/_search")
                .bodyValue(parameters)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(response -> logger.debug("Received initial response from FHIR server for resourceType: {}", resourceType))
                .flatMap(response -> {
                    logger.debug("Parsing FHIR response for resourceType: {}", resourceType);
                    return Mono.just(fhirContext.newJsonParser().parseResource(Bundle.class, response));
                })
                .expand(bundle -> Optional.ofNullable(bundle.getLink("next"))
                        .map(link -> {
                            logger.debug("Fetching next page of results for resourceType: {}", resourceType);
                            return fetchPage(client, link.getUrl());
                        })
                        .orElse(Mono.empty()))
                .flatMap(bundle -> Flux.fromStream(bundle.getEntry().stream().map(Bundle.BundleEntryComponent::getResource)))
                .collectList()  // Collect the resources into a List to log the size
                .doOnSuccess(resources -> logger.debug("Successfully fetched {} resources for resourceType: {}", resources.size(), resourceType))
                .flatMapMany(Flux::fromIterable)  // Convert the List back into a Flux
                .doOnNext(resource -> logger.debug("Fetched resource: {}", resource.getIdElement().getIdPart()))  // Log each resource fetched
                .doOnError(error -> logger.error("Error occurred while fetching resources for resourceType: {}: {}", resourceType, error.getMessage()));
    }



    private Mono<Bundle> fetchPage(WebClient client, String url) {
        //logger.debug("Fetch Page {}", url);
        return client.get()
                .uri(URI.create(url))
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> fhirContext.newJsonParser().parseResource(Bundle.class, response));
    }

}
