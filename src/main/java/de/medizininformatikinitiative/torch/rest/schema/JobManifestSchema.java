package de.medizininformatikinitiative.torch.rest.schema;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "JobManifest", description = "FHIR Async Bulk Data manifest (TORCH variant)")
public class JobManifestSchema {
    @Schema(example = "2026-03-10T18:22:11Z")
    public String transactionTime;

    @Schema(example = "https://torch.example.org/fhir/$extract-data")
    public String request;

    @Schema(example = "false")
    public boolean requiresAccessToken;

    @Schema(description = "NDJSON data artifacts produced by the job")
    public List<OutputEntrySchema> output;

    @Schema(description = "Additional TORCH extensions")
    public List<ExtensionSchema> extension;

    @Schema(
            name = "JobManifestOutput",
            description = "NDJSON data artifact produced by the extraction job"
    )
    public static class OutputEntrySchema {

        @Schema(
                description = "Download URL of the NDJSON artifact",
                example = "https://fileserver/jobs/<jobId>/<batchId>.ndjson"
        )
        public String url;

        @Schema(
                description = "Type of NDJSON artifact. Either 'NDJSON Bundle (patient batch bundle)' or 'NDJSON Bundle (core bundle)'.",
                example = "NDJSON Bundle"
        )
        public String type;
    }

    @Schema(
            name = "JobManifestExtension",
            description = """
                    TORCH-specific extension. Known URLs:
                    - https://torch.mii.de/fhir/StructureDefinition/torch-job — job state (valueObject)
                    - torch-job-diagnostics-summary — inline exclusion summary (valueObject)
                    - torch-job-diagnostics-detail — OperationOutcome report URL (valueUri)
                    """
    )
    public static class ExtensionSchema {

        @Schema(example = "https://torch.mii.de/fhir/StructureDefinition/torch-job")
        public String url;

        @Schema(description = "Populated for torch-job and torch-job-diagnostics-summary extensions")
        public Object valueObject;

        @Schema(
                description = "Populated for torch-job-diagnostics-detail — URL of the OperationOutcome report file",
                example = "https://fileserver/jobs/<jobId>/reports/job-summary.json"
        )
        public String valueUri;
    }
}
