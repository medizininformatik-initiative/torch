package de.medizininformatikinitiative.torch.rest.schema;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TorchIssue", description = "Issue attached to a job/work unit")
public class IssueSchema {

    @Schema(description = "Issue severity (TORCH)", example = "ERROR")
    public String severity;

    @Schema(description = "Issue code", example = "invalid")
    public String code;

    @Schema(description = "Human readable diagnostics", example = "Some explanation...")
    public String diagnostics;
}
