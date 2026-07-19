package de.medizininformatikinitiative.torch;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.exceptions.CsvValidationException;
import de.medizininformatikinitiative.torch.diagnostics.BatchDiagnostics;
import de.medizininformatikinitiative.torch.diagnostics.JobDiagnosticSummary;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.BatchExclusions;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.URI;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public class FileServerClient {

    private final WebClient webClient;
    private final FhirContext context = FhirContext.forR4();
    private final ObjectMapper mapper = new ObjectMapper();

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

    public JobDiagnosticSummary fetchJobSummary(URI url) {
        // only use the path part of the URL here, because in the test setup the port of the file server isn't right
        var response = webClient.get().uri(url.getPath()).retrieve().bodyToMono(String.class).block();
        if (response == null) {
            throw new RuntimeException("Error while fetching JSON from " + url);
        }

        try {
            return mapper.readValue(response, JobDiagnosticSummary.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error while parsing job summary: " + response);
        }
    }

    public BatchExclusions fetchExclusions(URI patientExclusionsUrl, URI resourceExclusionsUrl) {
        // only use the path part of the URL here, because in the test setup the port of the file server isn't right
        var patientExclusions = webClient.get().uri(patientExclusionsUrl.getPath()).retrieve().bodyToMono(String.class).block();
        if (patientExclusions == null) {
            throw new RuntimeException("Error while fetching JSON from " + patientExclusionsUrl);
        }

        var resourceExclusions = webClient.get().uri(resourceExclusionsUrl.getPath()).retrieve().bodyToMono(String.class).block();
        if (resourceExclusions == null) {
            throw new RuntimeException("Error while fetching JSON from " + resourceExclusionsUrl);
        }

        try {
            return TestUtils.readMergedDiagnostics(resourceExclusions, patientExclusions);
        } catch (IOException | CsvValidationException e) {
            throw new RuntimeException(e);
        }
    }
}
