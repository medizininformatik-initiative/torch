package de.medizininformatikinitiative.torch;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;


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

    @Test
    void testJobStatusCounts() {
        var response = webTestClient.get()
                .uri("/actuator/prometheus")
                .exchange()
                .returnResult().getResponseBodyContent();

        assertThat(response).isNotNull();
        assertThat(new String(response)).contains("jobs_status_count");
        assertThat(new String(response)).contains("jobs_completed_durations");
    }

    @Test
    void testJobDurations() {
        var response = webTestClient.get()
                .uri("/actuator/prometheus")
                .exchange()
                .returnResult().getResponseBodyContent();

        assertThat(response).isNotNull();
        assertThat(new String(response)).contains("jobs_completed_durations");
    }

}
