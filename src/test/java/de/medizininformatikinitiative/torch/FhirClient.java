package de.medizininformatikinitiative.torch;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Set;

import static java.util.Objects.requireNonNull;
import static org.hl7.fhir.r4.model.Bundle.BundleType.TRANSACTION;
import static org.hl7.fhir.r4.model.Bundle.HTTPVerb.DELETE;

public class FhirClient {

    private static final Logger logger = LoggerFactory.getLogger(FhirClient.class);

    private final WebClient webClient;
    private final FhirContext context = FhirContext.forR4();

    public FhirClient(WebClient webClient) {
        this.webClient = requireNonNull(webClient);
    }

    public Mono<ResponseEntity<Void>> transact(String bundle) {
        logger.info("Transact bundle...");
        return webClient.post()
                .header("Content-Type", "application/fhir+json")
                .bodyValue(bundle)
                .retrieve()
                .toBodilessEntity();
    }

    public void deleteResources(Set<String> types) {
        Bundle bundle = new Bundle();
        bundle.setType(TRANSACTION);
        types.forEach(resourceType -> {
            Bundle.BundleEntryRequestComponent request = new Bundle.BundleEntryRequestComponent();
            request.setUrl(resourceType);
            request.setMethod(DELETE);

            Bundle.BundleEntryComponent entryComponent = new Bundle.BundleEntryComponent();
            entryComponent.setRequest(request);

            bundle.addEntry(entryComponent);
        });

        webClient.post()
                .header("Content-Type", "application/fhir+json")
                .bodyValue(context.newJsonParser().encodeToString(bundle))
                .retrieve()
                .toBodilessEntity()
                .block();
    }
}
