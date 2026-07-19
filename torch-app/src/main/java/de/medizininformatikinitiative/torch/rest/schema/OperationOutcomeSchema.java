package de.medizininformatikinitiative.torch.rest.schema;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "OperationOutcome", description = "Reduced FHIR OperationOutcome (TORCH responses)")
public class OperationOutcomeSchema {

    @Schema(example = "OperationOutcome")
    public String resourceType = "OperationOutcome";

    public String id;
    public MetaSchema meta;
    public List<IssueComponentSchema> issue;

    @Schema(name = "OperationOutcomeMeta")
    public static class MetaSchema {
        public String lastUpdated;
    }

    @Schema(name = "OperationOutcomeIssue")
    public static class IssueComponentSchema {

        @Schema(allowableValues = {"fatal", "error", "warning", "information"})
        public String severity;

        public String code;
        public String diagnostics;
    }
}
