package de.medizininformatikinitiative.torch.setup;

import jakarta.annotation.PostConstruct;
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

    private final ComposeContainer environment;
    private String blazeHost;
    private int blazePort;
    private String flareHost;
    private int flarePort;


    public ContainerManager() {
        environment = new ComposeContainer(new File("src/test/resources/docker-compose.yml"))
                .withScaledService("blaze", 1).withScaledService("flare", 1)
                .withExposedService("blaze", 8080, Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(5)))
                .withLogConsumer("blaze", new Slf4jLogConsumer(logger).withPrefix("blaze"))
                .withExposedService("flare", 8080, Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(5)))
                .withLogConsumer("flare", new Slf4jLogConsumer(logger).withPrefix("flare"));

    }


    @PostConstruct
    public void startContainers() {
        environment.start();
        blazeHost = environment.getServiceHost("blaze", 8080);
        blazePort = environment.getServicePort("blaze", 8080);
        flareHost = environment.getServiceHost("flare", 8080);
        flarePort = environment.getServicePort("flare", 8080);
        logger.info("Blaze available at {}:{} and Flare available at {}:{}", blazeHost, blazePort, flareHost, flarePort);
    }

    @PreDestroy
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
