package de.medizininformatikinitiative.torch.setup;



import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.time.Duration;

@Component
public class ContainerManager {

    private static final Logger logger = LoggerFactory.getLogger(ContainerManager.class);
    private final String blazeHost;
    private final int blazePort;
    private final String flareHost;
    private final int flarePort;
    private static final ComposeContainer environment;

    // Static initialization of the ComposeContainer
    static {
        environment = new ComposeContainer(new File("src/test/resources/docker-compose.yml"))
                .withExposedService("blaze", 8080, Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(30)))
                .withLogConsumer("blaze", new Slf4jLogConsumer(logger).withPrefix("blaze"))
                .withExposedService("flare", 8080, Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(30)))
                .withLogConsumer("flare", new Slf4jLogConsumer(logger).withPrefix("flare"));
    }

    // Constructor starts containers and performs health checks
    public ContainerManager() {
        startContainers();

        // Retrieve host and port after container start
        this.blazeHost = environment.getServiceHost("blaze", 8080);
        this.blazePort = environment.getServicePort("blaze", 8080);
        checkServiceHealth("blaze", "/health", blazeHost, blazePort);

        this.flareHost = environment.getServiceHost("flare", 8080);
        this.flarePort = environment.getServicePort("flare", 8080);
        checkServiceHealth("flare", "/cache/stats", flareHost, flarePort);

        logger.info("Blaze available at {}:{} and Flare available at {}:{}", blazeHost, blazePort, flareHost, flarePort);
    }

    // Method to start the containers
    public void startContainers() {
        environment.start();
        logger.info("Containers started successfully.");
    }

    // Method to stop the containers
    public void stopContainers() {
        environment.stop();
        logger.info("Containers stopped successfully.");
    }

    // Method for checking service health by calling the provided health endpoint
    private void checkServiceHealth(String service, String healthEndpoint, String host, int port) {
        String url = String.format("http://%s:%d%s", host, port, healthEndpoint);

        WebClient webClient = WebClient.create();
        int attempts = 0;
        int maxAttempts = 10;

        while (attempts < maxAttempts) {
            try {
                Mono<String> responseMono = webClient.get()
                        .uri(url)
                        .retrieve()
                        .bodyToMono(String.class);

                String response = responseMono.block();
                if (response != null) {
                    logger.info("Health check passed for service: {} at {}", service, url);
                    return;
                }
            } catch (Exception e) {
                logger.warn("Health check failed for service: {} at {}. Retrying...", service, url);
            }
            attempts++;
            try {
                Thread.sleep(5000);  // Wait 5 seconds before retrying
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        throw new RuntimeException("Health check failed for service: " + service + " at " + url);
    }


    public String getBlazeBaseUrl() {
        return String.format("http://%s:%d/fhir", blazeHost, blazePort);
    }

    public String getFlareBaseUrl() {
        return String.format("http://%s:%d", flareHost, flarePort);
    }

}
