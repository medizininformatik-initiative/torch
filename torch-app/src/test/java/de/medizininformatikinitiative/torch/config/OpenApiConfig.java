package de.medizininformatikinitiative.torch.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI torchOpenAPI() {
        // 1. Define the 'output' file schema
        Schema<?> fileOutputSchema = new ObjectSchema()
                .addProperty("type", new StringSchema().example("NDJSON Bundle").description("FHIR resource type"))
                .addProperty("url", new StringSchema().example("https://files/job/1.ndjson"));

        // 2. Define the 'extension' (Job Metadata) schema
        Schema<?> extensionSchema = new ObjectSchema()
                .addProperty("url", new StringSchema().example("https://torch.mii.de/fhir/StructureDefinition/torch-job"))
                .addProperty("valueObject", new ObjectSchema().description("The internal Job state node"));

        // 3. Assemble the Bulk Data Manifest
        Schema<?> manifestSchema = new ObjectSchema()
                .description("FHIR Bulk Data Output Manifest (Job Result)")
                .addProperty("transactionTime", new StringSchema().example("2026-02-10T09:00:00Z"))
                .addProperty("request", new StringSchema().example("https://torch/fhir/$extract-data"))
                .addProperty("requiresAccessToken", new BooleanSchema()._default(false))
                .addProperty("output", new ArraySchema().items(fileOutputSchema))
                .addProperty("extension", new ArraySchema().items(extensionSchema));

        return new OpenAPI()
                .info(new Info()
                        .title("TORCH FHIR API")
                        .description("Data Extraction compliant with [HL7 FHIR Bulk Data Access IG](https://hl7.org/fhir/uv/bulkdata/export.html)\n" +
                                "**Note:** The server URLs provided below (e.g., localhost) are for demonstration purposes only.")
                        .version("1.0.0"))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Development Server")))
                .components(new Components()
                        .addSchemas("BulkDataManifest", manifestSchema));
    }
}
