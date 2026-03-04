package de.medizininformatikinitiative.torch;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public class FileServerClient {

    private final WebClient webClient;
    private final FhirContext context = FhirContext.forR4();

    public FileServerClient(WebClient webClient) {
        this.webClient = requireNonNull(webClient);
    }

    public Stream<Bundle> fetchBundles(URI url) {
        if (!isBundleArtifact(url)) {
            return Stream.empty();
        }

        // only use the path part of the URL here, because in the test setup the port of the file server isn't right
        var response = webClient.get().uri(url.getPath()).retrieve().bodyToMono(String.class).block();
        if (response == null) {
            throw new RuntimeException("Error while fetching NDJSON from " + url);
        }

        return Stream.of(response.split("\n"))
                .filter(line -> line != null && !line.isBlank())
                .map(line -> context.newJsonParser().parseResource(Bundle.class, line));
    }

    private boolean isBundleArtifact(URI url) {
        String path = url.getPath();
        return path != null
                && path.endsWith(".ndjson")
                && !path.endsWith("/reports/job-summary.json");
    }
}
