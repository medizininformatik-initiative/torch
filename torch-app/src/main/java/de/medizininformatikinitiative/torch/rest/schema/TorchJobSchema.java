package de.medizininformatikinitiative.torch.rest.schema;

import de.medizininformatikinitiative.torch.jobhandling.JobStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(name = "TorchJob", description = "TORCH job state embedded into the manifest extension.valueObject")
public class TorchJobSchema {

    @Schema(type = "string", format = "uuid", example = "550e8400-e29b-41d4-a716-446655440000")
    public String id;

    @Schema(
            description = """
                    Overall job status.
                    
                    Final states: COMPLETED, FAILED, CANCELLED.
                    Non-final states: PENDING, RUNNING_GET_COHORT, RUNNING_PROCESS_BATCH, RUNNING_PROCESS_CORE, PAUSED, TEMP_FAILED.
                    """,
            implementation = JobStatus.class
    )
    public JobStatus status;

    @Schema(description = "Batch work units keyed by batch name (e.g., 'batch-1', etc.)")
    public Map<String, WorkUnitStateSchema> batches;

    @Schema(description = "Core work unit state")
    public WorkUnitStateSchema coreState;
}
