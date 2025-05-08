package de.medizininformatikinitiative.torch.setup;


import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.time.Duration;


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
                .withScaledService("blaze", 1)
                .withScaledService("flare", 1)
                .withExposedService("blaze", 8080, Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(5)))
                .withLogConsumer("blaze", new Slf4jLogConsumer(logger).withPrefix("blaze"))
                .withExposedService("flare", 8080, Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(5)))
                .withLogConsumer("flare", new Slf4jLogConsumer(logger).withPrefix("flare"));
    }

    public ContainerManager() {
        startContainers();
        this.blazeHost = environment.getServiceHost("blaze", 8080);
        this.blazePort = environment.getServicePort("blaze", 8080);
        this.flareHost = environment.getServiceHost("flare", 8080);
        this.flarePort = environment.getServicePort("flare", 8080);
        logger.info("Blaze available at {}:{} and Flare available at {}:{}", blazeHost, blazePort, flareHost, flarePort);
    }

    // Method to start the containers
    public void startContainers() {
        environment.start();
        logger.info("Containers started successfully.");
    }

    @PreDestroy
    public void cleanup() {
        stopContainers();
    }

    // Method to stop the containers
    public void stopContainers() {
        environment.stop();
        logger.info("Containers stopped successfully.");
    }


    public String getBlazeBaseUrl() {
        return String.format("http://%s:%d/fhir", blazeHost, blazePort);
    }

    public String getFlareBaseUrl() {
        return String.format("http://%s:%d", flareHost, flarePort);
    }

}
