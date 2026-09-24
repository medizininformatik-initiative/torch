package de.medizininformatikinitiative.torch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.JobStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@EnableAutoConfiguration
class PrometheusEndpointIT {

    static final UUID FINISHED_JOB_ID = UUID.randomUUID();
    static Path resultsDir;

    @Autowired
    WebTestClient webTestClient;

    /**
     * Seeds an already-finished job into a results directory of its own before the context starts, so that
     * {@code jobs_completed_durations} has a row regardless of which other tests ran before. Being final, the
     * job is never picked up by the scheduler, and the dedicated directory keeps this context from being
     * shared with other test classes.
     */
    @DynamicPropertySource
    static void seedFinishedJob(DynamicPropertyRegistry registry) throws IOException {
        resultsDir = Files.createTempDirectory("prometheus-endpoint-it");
        Path jobDir = Files.createDirectories(resultsDir.resolve(FINISHED_JOB_ID.toString()));
        Job finishedJob = Job.init(FINISHED_JOB_ID, TestUtils.emptyJobParams()).withStatus(JobStatus.CANCELLED);
        new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .registerModule(new Jdk8Module())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .writeValue(jobDir.resolve("job.json").toFile(), finishedJob);

        registry.add("torch.results.dir", resultsDir::toString);
    }

    @AfterAll
    static void deleteResultsDir() throws IOException {
        FileSystemUtils.deleteRecursively(resultsDir);
    }

    @Test
    void prometheusEndpointIsAccessible() {
        webTestClient.get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void testJobStatusCounts() {
        assertThat(scrape()).contains("jobs_status_count{status=\"CANCELLED\"} 1.0");
    }

    @Test
    void testJobDurations() throws InterruptedException {
        String expectedRow = "jobs_completed_durations{id=\"" + FINISHED_JOB_ID + "\",status=\"CANCELLED\"}";

        // the duration gauge is refreshed on a schedule (torch.micrometer.schedule, 1s by default)
        String response = scrape();
        for (int attempt = 0; attempt < 50 && !response.contains(expectedRow); attempt++) {
            Thread.sleep(100);
            response = scrape();
        }

        assertThat(response).contains(expectedRow);
    }

    private String scrape() {
        var response = webTestClient.get()
                .uri("/actuator/prometheus")
                .exchange()
                .returnResult().getResponseBodyContent();

        assertThat(response).isNotNull();
        return new String(response);
    }
}
