package de.medizininformatikinitiative.torch;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "springdoc.override-with-generic-response=false",
                "springdoc.api-docs.enabled=true"
        })
@Import({
        org.springdoc.webflux.ui.SwaggerConfig.class,
        org.springdoc.core.configuration.SpringDocConfiguration.class,
        org.springdoc.core.properties.SpringDocConfigProperties.class,
        de.medizininformatikinitiative.torch.config.TestConfig.class // Your config
})
@AutoConfigureWebTestClient
class OpenApiGeneratorTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void generateOpenApiSpec() throws IOException {
        byte[] responseBody = webTestClient.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult()
                .getResponseBody();

        if (responseBody != null) {
            Path path = Paths.get("docs/public/openapi.json");
            Files.createDirectories(path.getParent());
            Files.write(path, responseBody);
            System.out.println("✅ OpenAPI spec generated at: " + path.toAbsolutePath());
        }
    }
}
