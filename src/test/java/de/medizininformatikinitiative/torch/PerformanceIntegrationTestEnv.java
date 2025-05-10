package de.medizininformatikinitiative.torch;

import org.slf4j.Logger;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.time.Duration;

public class PerformanceIntegrationTestEnv {

    private final ComposeContainer environment;

    public PerformanceIntegrationTestEnv(Logger logger) {
        environment = new ComposeContainer(new File("src/test/resources/docker-compose-performance.yml"))
                .withExposedService("torch", 8080, Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(5)))
                .withLogConsumer("torch", new Slf4jLogConsumer(logger).withPrefix("torch"))
                .withExposedService("nginx", 8080, Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(5)))
                .withLogConsumer("nginx", new Slf4jLogConsumer(logger).withPrefix("nginx"));
    }

    public void start() {
        environment.start();
    }

    public void stop() {
        environment.stop();
    }

    public TorchClient torchClient() {
        var host = environment.getServiceHost("torch", 8080);
        var port = environment.getServicePort("torch", 8080);

        // Allow 1 MB payload size
        ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();

        return new TorchClient(WebClient.builder()
                .exchangeStrategies(exchangeStrategies)
                .baseUrl("http://%s:%s/fhir".formatted(host, port))
                .defaultHeader("Accept", "application/fhir+json")
                .build());
    }

    public FileServerClient fileServerClient() {
        var host = environment.getServiceHost("nginx", 8080);
        var port = environment.getServicePort("nginx", 8080);

        // Allow 100 MB payload size
        ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(100 * 1024 * 1024))
                .build();

        return new FileServerClient(WebClient.builder()
                .exchangeStrategies(exchangeStrategies)
                .baseUrl("http://%s:%s".formatted(host, port))
                .build());
    }
}
