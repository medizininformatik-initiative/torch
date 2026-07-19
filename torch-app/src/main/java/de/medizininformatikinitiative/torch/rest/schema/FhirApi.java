package de.medizininformatikinitiative.torch.rest.schema;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

public interface FhirApi {

    @Operation(
            summary = "Bulk Data Kick-off",
            description = """
                    Starts a TORCH extraction job following the FHIR async pattern.
                    
                    The request body is a TORCH-specific Parameters-like payload that contains a base64-encoded CRTDL
                    (UTF-8 JSON serialized and then base64 encoded).
                    """,
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            // Your router accepts application/fhir+json, so document that.
                            mediaType = "application/fhir+json",
                            schema = @Schema(implementation = ExtractDataKickoffRequestSchema.class),
                            examples = @ExampleObject(
                                    name = "Kick-off Request",
                                    value = """
                                            {
                                              "crtdlBase64": "<BASE64_OF_UTF8_JSON_CRTDL>",
                                              "patientIds": ["Patient/123", "Patient/456"]
                                            }
                                            """
                            )
                    )
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "202",
                            description = "Accepted - job created",
                            content = @Content(schema = @Schema(hidden = true)),
                            headers = @Header(
                                    name = "Content-Location",
                                    description = "URL to poll job status",
                                    schema = @Schema(
                                            type = "string",
                                            example = "http://localhost:8080/fhir/__status/550e8400-e29b-41d4-a716-446655440000"
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Bad Request (empty body / invalid base64 / invalid CRTDL / invalid parameters)",
                            content = @Content(
                                    mediaType = "application/fhir+json",
                                    schema = @Schema(implementation = OperationOutcomeSchema.class),
                                    examples = @ExampleObject(
                                            name = "Invalid Request",
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
                                                    """
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Internal Server Error",
                            content = @Content(
                                    mediaType = "application/fhir+json",
                                    schema = @Schema(implementation = OperationOutcomeSchema.class)
                            )
                    )
            }
    )
    Mono<ServerResponse> handleExtractData(ServerRequest request);

    @Operation(
            summary = "Bulk Data Status Request",
            parameters = @Parameter(
                    name = "jobId",
                    in = ParameterIn.PATH,
                    required = true,
                    description = "UUID of the job returned by kick-off",
                    schema = @Schema(type = "string", format = "uuid")
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Job completed - manifest available",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = JobManifestSchema.class),
                                    examples = @ExampleObject(
                                            name = "Completed Manifest",
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
                                                        "valueObject": {
                                                          "id": "550e8400-e29b-41d4-a716-446655440000",
                                                          "status": "COMPLETED",
                                                          "batches": {
                                                            "batch-1": { "status": "FINISHED" }
                                                          },
                                                          "coreState": { "status": "FINISHED" }
                                                        }
                                                      }]
                                                    }
                                                    """
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "202",
                            description = "Job in progress",
                            content = @Content(
                                    mediaType = "application/fhir+json",
                                    schema = @Schema(implementation = OperationOutcomeSchema.class)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Job not found",
                            content = @Content(
                                    mediaType = "application/fhir+json",
                                    schema = @Schema(implementation = OperationOutcomeSchema.class)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "410",
                            description = "Job cancelled",
                            content = @Content(
                                    mediaType = "application/fhir+json",
                                    schema = @Schema(implementation = OperationOutcomeSchema.class)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Job failed",
                            content = @Content(
                                    mediaType = "application/fhir+json",
                                    schema = @Schema(implementation = OperationOutcomeSchema.class)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "503",
                            description = "Temporary failure",
                            content = @Content(
                                    mediaType = "application/fhir+json",
                                    schema = @Schema(implementation = OperationOutcomeSchema.class)
                            )
                    )
            }
    )
    Mono<ServerResponse> checkStatus(ServerRequest request);
}
