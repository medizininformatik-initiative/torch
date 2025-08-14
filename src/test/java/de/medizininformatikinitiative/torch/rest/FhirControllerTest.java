package de.medizininformatikinitiative.torch.rest;


import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.config.TorchProperties;
import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;
import de.medizininformatikinitiative.torch.model.crtdl.DecodedCRTDLContent;
import de.medizininformatikinitiative.torch.service.ExtractDataService;
import de.medizininformatikinitiative.torch.util.CrtdlParser;
import de.medizininformatikinitiative.torch.util.ResultFileManager;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FhirControllerTest {

    @Test
    void handleExtractDatasetsCorrectContentLocation() {
        String requestBody = "fake-crtdl-body";
        String baseUrl = "http://example.com/";

        // Mock dependencies
        CrtdlParser parser = mock(CrtdlParser.class);
        ExtractDataService service = mock(ExtractDataService.class);
        FhirContext fhirContext = mock(FhirContext.class);
        ResultFileManager resultFileManager = mock(ResultFileManager.class);

        // Mock TorchProperties and nested base() method
        TorchProperties torchProperties = mock(TorchProperties.class);
        TorchProperties.Base base = mock(TorchProperties.Base.class);
        when(torchProperties.base()).thenReturn(base);
        when(base.url()).thenReturn(baseUrl);

        // Create controller with mocks and injected baseUrl
        FhirController controller = new FhirController(
                fhirContext,
                resultFileManager,
                parser,
                service,
                torchProperties
        );

        // Prepare Crtdl and patientIds mocks/values
        Crtdl crtdl = mock(Crtdl.class);
        List<String> fakePatientIds = List.of("p1", "p2");

        // When parseCrtdl called, return decoded content
        when(parser.parseCrtdl(anyString()))
                .thenReturn(Mono.just(new DecodedCRTDLContent(crtdl, fakePatientIds)));

        // When startJob called, return fixedJobId
        when(service.startJob(eq(crtdl), eq(fakePatientIds), anyString()))
                .thenReturn(Mono.empty());

        // Build a mock ServerRequest with body
        ServerRequest request = MockServerRequest.builder()
                .method(HttpMethod.POST)
                .uri(URI.create(baseUrl + "/fhir/$extract-data"))
                .body(Mono.just(requestBody));

        // Act
        Mono<ServerResponse> responseMono = controller.handleExtractData(request);

        // Assert
        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    String location = response.headers().getFirst("Content-Location");
                    assertThat(location).startsWith(baseUrl + "/fhir/__status/");
                })
                .verifyComplete();
    }
}
