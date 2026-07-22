package de.medizininformatikinitiative.torch.config;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatNoException;

class FhirServerConnectivityCheckTest {

    private FhirProperties fhirProperties(String url) {
        return new FhirProperties(
                url,
                new FhirProperties.Max(5, 30),
                new FhirProperties.Page(10),
                new FhirProperties.Oauth(new FhirProperties.Oauth.Issuer(""), new FhirProperties.Oauth.Client("", "")),
                new FhirProperties.Disable(false),
                "",
                ""
        );
    }

    @Test
    void run_logsSuccessOnValidResponse() throws IOException {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
            server.start();

            String baseUrl = server.url("/").toString();
            WebClient client = WebClient.builder().baseUrl(baseUrl).build();
            var check = new FhirServerConnectivityCheck(client, fhirProperties(baseUrl));

            assertThatNoException().isThrownBy(() -> check.run(null));
        }
    }

    @Test
    void run_doesNotThrowOnServerError() throws IOException {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error"));
            server.start();

            String baseUrl = server.url("/").toString();
            WebClient client = WebClient.builder().baseUrl(baseUrl).build();
            var check = new FhirServerConnectivityCheck(client, fhirProperties(baseUrl));

            assertThatNoException().isThrownBy(() -> check.run(null));
        }
    }

    @Test
    void run_doesNotThrowOnConnectionFailure() {
        // port 1 is almost certainly not listening — provokes an immediate connection refused
        WebClient client = WebClient.builder().baseUrl("http://localhost:1").build();
        var check = new FhirServerConnectivityCheck(client, fhirProperties("http://localhost:1"));

        assertThatNoException().isThrownBy(() -> check.run(null));
    }
}
