package de.medizininformatikinitiative.torch.rest.schema;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(
        name = "ExtractDataKickoffRequest",
        description = "Kick-off payload containing base64-encoded CRTDL JSON (UTF-8 JSON -> base64)."
)
public class ExtractDataKickoffRequestSchema {

    @Schema(
            description = "Base64-encoded UTF-8 JSON representation of the CRTDL",
            type = "string",
            format = "byte",
            example = "<BASE64_OF_UTF8_JSON_CRTDL>"
    )
    public String crtdlBase64;

    @Schema(
            description = "Optional list of patient references / ids that TORCH should process (if supported by your parser).",
            example = "[\"Patient/123\", \"Patient/456\"]"
    )
    public List<String> patientIds;
}
