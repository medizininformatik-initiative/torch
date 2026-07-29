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

    @Schema(description = "FHIR NDJSON Bundle files produced by the job (one per patient batch plus core.ndjson)")
    public List<OutputEntrySchema> output;

    @Schema(description = "TORCH-specific extensions providing job metadata, diagnostics, and issues")
    public List<ExtensionSchema> extension;

    @Schema(
            name = "JobManifestOutput",
            description = "FHIR NDJSON Bundle file produced by the extraction job"
    )
    public static class OutputEntrySchema {

        @Schema(
                description = "Download URL of the NDJSON file",
                example = "https://fileserver/jobs/<jobId>/<batchId>.ndjson"
        )
        public String url;

        @Schema(
                description = "Always 'NDJSON Bundle' — each file contains FHIR transaction Bundles",
                example = "NDJSON Bundle"
        )
        public String type;
    }

    @Schema(
            name = "JobManifestExtension",
            description = """
                    TORCH-specific extension. Known URLs:
                    - https://torch.mii.de/fhir/StructureDefinition/torch-job — full job state (valueObject)
                    - torch-job-diagnostics-summary — per-kind exclusion counts, cohort and final patient totals (valueObject, omitted when no diagnostics)
                    - torch-resource-exclusions — download URL of the resource-exclusions.csv report (valueUrl, omitted when no diagnostics)
                    - torch-patient-exclusions — download URL of the patient-exclusions.csv report (valueUrl, omitted when no diagnostics)
                    - torch-job-issues — list of warnings/errors recorded during processing (valueObject, omitted when empty)
                    """
    )
    public static class ExtensionSchema {

        @Schema(example = "https://torch.mii.de/fhir/StructureDefinition/torch-job")
        public String url;

        @Schema(description = "Content depends on the extension URL; see description above")
        public Object valueObject;

        @Schema(
                description = "Download URL; set instead of valueObject for URL-only extensions",
                example = "https://fileserver/jobs/<jobId>/reports/resource-exclusions.csv"
        )
        public String valueUrl;
    }
}
