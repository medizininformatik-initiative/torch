package de.medizininformatikinitiative.torch.rest.schema;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "JobManifest", description = "FHIR Async Bulk Data manifest (TORCH variant)")
public class JobManifestSchema {

    public String transactionTime;
    public String request;
    public boolean requiresAccessToken;
    public List<OutputEntrySchema> output;
    public List<ExtensionSchema> extension;

    @Schema(name = "JobManifestOutput")
    public static class OutputEntrySchema {
        public String url;
        public String type;
    }

    @Schema(name = "JobManifestExtension")
    public static class ExtensionSchema {

        @Schema(example = "https://torch.mii.de/fhir/StructureDefinition/torch-job")
        public String url;

        @Schema(
                description = "Serialized TORCH Job state (mapper.valueToTree(job))",
                implementation = TorchJobSchema.class
        )
        public TorchJobSchema valueObject;
    }
}
