package de.medizininformatikinitiative.torch;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@EnableAutoConfiguration
class PrometheusEndpointIT {

    @Autowired
    WebTestClient webTestClient;

    @Test
    void prometheusEndpointIsAccessible() {
        webTestClient.get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk();
    }
}
