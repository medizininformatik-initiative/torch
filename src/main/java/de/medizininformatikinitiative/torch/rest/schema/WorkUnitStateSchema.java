package de.medizininformatikinitiative.torch.rest.schema;

import de.medizininformatikinitiative.torch.jobhandling.workunit.WorkUnitStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "WorkUnitState", description = "State of a TORCH work unit")
public class WorkUnitStateSchema {

    @Schema(
            description = """
                    Execution status of this work unit.
                    
                    Terminal states (orchestration considers the unit done):
                    FINISHED, SKIPPED, FAILED.
                    
                    Non-terminal / retry-related states:
                    INIT, IN_PROGRESS, TEMP_FAILED.
                    """,
            implementation = WorkUnitStatus.class
    )
    public WorkUnitStatus status;

    @Schema(description = "Optional issues attached to this work unit (if present in your serialized state)")
    public List<IssueSchema> issues;
}
