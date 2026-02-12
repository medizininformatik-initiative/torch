package de.medizininformatikinitiative.torch.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.List;

public interface FhirApi {

    @Operation(
            summary = "Bulk Data Kick-off",
            responses = {
                    @ApiResponse(responseCode = "202", description = "Accepted",
                            content = @Content(schema = @Schema(hidden = true)),
                            headers = @Header(name = "Content-Location",
                                    description = "URL for checking job status",
                                    schema = @Schema(type = "string", example = "http://localhost:8080/fhir/__status/550e8400-e29b-41d4-a716-446655440000"))),
                    @ApiResponse(responseCode = "400", description = "Bad Request (e.g. invalid CRTDL)",
                            content = @Content(mediaType = "application/fhir+json",
                                    schema = @Schema(implementation = OperationOutcomeSchema.class),
                                    examples = @ExampleObject(name = "Invalid Request",
                                            value = """
                                                    {
                                                      "resourceType": "OperationOutcome",
                                                      "id": "request-outcome",
                                                      "issue": [{
                                                        "severity": "error",
                                                        "code": "invalid",
                                                        "diagnostics": "Empty request body"
                                                      }]
                                                    }
                                                    """))),
                    @ApiResponse(responseCode = "500", description = "Internal Server Error",
                            content = @Content(mediaType = "application/fhir+json",
                                    schema = @Schema(implementation = OperationOutcomeSchema.class)))
            }
    )
    Mono<ServerResponse> handleExtractData(ServerRequest request);

    @Operation(
            summary = "Bulk Data Status Request",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Job Completed - Manifest is ready",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = JobManifestSchema.class),
                                    examples = @ExampleObject(name = "Completed Manifest",
                                            value = """
                                                    {
                                                      "transactionTime": "2026-02-10T12:00:00Z",
                                                      "request": "http://torch-server/fhir/$extract-data",
                                                      "requiresAccessToken": false,
                                                      "output": [
                                                        { "url": "http://fileserver/550e/batch-1.ndjson", "type": "NDJSON Bundle" },
                                                        { "url": "http://fileserver/550e/core.ndjson", "type": "NDJSON Bundle" }
                                                      ],
                                                      "extension": [{
                                                        "url": "https://torch.mii.de/fhir/StructureDefinition/torch-job",
                                                        "valueObject": { "id": "550e8400-e29b-41d4-a716-446655440000", "status": "COMPLETED" }
                                                      }]
                                                    }
                                                    """))),
                    @ApiResponse(responseCode = "202", description = "Job In-Progress",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = OperationOutcomeSchema.class),
                                    examples = @ExampleObject(name = "Job Progress",
                                            value = """
                                                    {
                                                      "resourceType": "OperationOutcome",
                                                      "id": "job-550e-outcome",
                                                      "issue": [{
                                                        "severity": "information",
                                                        "code": "informational",
                                                        "diagnostics": "Job 550e8400...\\n status: RUNNING_PROCESS_BATCH\\n batch progress: 45%\\n cohort Size: 1200"
                                                      }]
                                                    }
                                                    """))),
                    @ApiResponse(responseCode = "404", description = "Job Not Found",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = OperationOutcomeSchema.class),
                                    examples = @ExampleObject(name = "NotFound",
                                            value = "{\"resourceType\":\"OperationOutcome\",\"id\":\"request-outcome\",\"issue\":[{\"severity\":\"error\",\"code\":\"not-found\",\"diagnostics\":\"No job with id ... exists\"}]}"))),
                    @ApiResponse(responseCode = "410", description = "Job Cancelled or Expired",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = OperationOutcomeSchema.class))),
                    @ApiResponse(responseCode = "500", description = "Job Failed",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = OperationOutcomeSchema.class))),
                    @ApiResponse(responseCode = "503", description = "Service Unavailable (Transient Failure)",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = OperationOutcomeSchema.class)))
            }
    )
    Mono<ServerResponse> checkStatus(ServerRequest request);

    @Schema(name = "JobManifest", description = "The Bulk Data Access manifest JSON")
    class JobManifestSchema {
        public String transactionTime;
        public String request;
        public boolean requiresAccessToken;
        public List<OutputEntrySchema> output;
        public List<ExtensionSchema> extension;

        @Schema(name = "JobManifestOutput")
        static class OutputEntrySchema {
            public String url;
            public String type;
        }

        @Schema(name = "JobManifestExtension")
        static class ExtensionSchema {
            public String url;
            public Object valueObject;
        }
    }

    @Schema(name = "OperationOutcome", description = "Reduced FHIR OperationOutcome")
    class OperationOutcomeSchema {
        public String id;
        public String resourceType = "OperationOutcome";
        public MetaSchema meta;
        public List<IssueComponentSchema> issue;

        @Schema(name = "OperationOutcomeMeta")
        static class MetaSchema {
            public String lastUpdated;
        }

        @Schema(name = "OperationOutcomeIssue")
        static class IssueComponentSchema {
            @Schema(allowableValues = {"fatal", "error", "warning", "information"})
            public String severity;
            public String code;
            public String diagnostics;
        }
    }
}
