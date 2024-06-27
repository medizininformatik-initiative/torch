package de.medizininformatikinitiative;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Optional;

@Component
public class DataStore {

    private final WebClient client;
    private final IParser parser;

    public DataStore(WebClient client, FhirContext context) {
        this.client = client;
        this.parser = context.newJsonParser();
    }


    /**
     * Executes {@code FHIRSearchQuery} and returns all resources found with that query.
     *
     * @param FHIRSearchQuery the fhir search query defining the patient resources to be fetched
     * @return the resources found with the {@param FHIRSearchQuery}
     */
    public Flux<Resource> getResources(String FHIRSearchQuery) {
        return client.get()
                .uri(FHIRSearchQuery)
                .retrieve()
                .bodyToFlux(String.class)
                .map(response -> parser.parseResource(Bundle.class, response))
                .expand(bundle -> Optional.ofNullable(bundle.getLink("next"))
                        .map(link -> fetchPage(client, link.getUrl()))
                        .orElse(Mono.empty()))
                .flatMap(bundle -> Flux.fromStream(bundle.getEntry().stream().map(Bundle.BundleEntryComponent::getResource)));
    }

    private Mono<Bundle> fetchPage(WebClient client, String url) {
        return client.get()
                .uri(URI.create(url))
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> parser.parseResource(Bundle.class, response));
    }

}
