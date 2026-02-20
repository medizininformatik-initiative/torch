package de.medizininformatikinitiative.torch.rest;

import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.JobPriority;
import de.medizininformatikinitiative.torch.jobhandling.JobStatus;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Task;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class JobTaskMapper {

    private static final String TORCH_STATUS_SYSTEM =
            "https://medizininformatik-initiative.de/torch/job-status";

    // -----------------------------
    // Job → FHIR Task
    // -----------------------------

    public Task toFhirTask(Job job) {
        Task task = new Task();

        // Resource id
        task.setId(job.id().toString());

        // Meta (versioning + lastUpdated)
        task.getMeta()
                .setVersionId(Long.toString(job.version()))
                .setLastUpdated(Date.from(job.updatedAt()));

        // Coarse FHIR status
        task.setStatus(mapToFhirStatus(job.status()));

        // Exact Torch status in businessStatus
        task.setBusinessStatus(
                new CodeableConcept().addCoding(
                        new Coding(
                                TORCH_STATUS_SYSTEM,
                                job.status().name(),
                                job.status().name()
                        )
                )
        );

        // Priority
        task.setPriority(mapToFhirPriority(job.priority()));

        // Execution period
        Period period = new Period();
        period.setStart(Date.from(job.startedAt()));
        job.finishedAt().ifPresent(end ->
                period.setEnd(Date.from(end))
        );
        task.setExecutionPeriod(period);

        // Human readable description
        task.setDescription("TORCH Job " + job.id());

        return task;
    }


    // -----------------------------
    // FHIR Task → Job (representation only)
    // -----------------------------
    // NOTE:
    // This does NOT execute transitions.
    // It only converts a full Task representation back to a Job.
    // Actual state changes must go through JobPersistenceService.
    // -----------------------------

    // -----------------------------
    // Status Mapping
    // -----------------------------

    private Task.TaskStatus mapToFhirStatus(JobStatus status) {
        return switch (status) {
            case PENDING -> Task.TaskStatus.REQUESTED;
            case PAUSED, TEMP_FAILED -> Task.TaskStatus.ONHOLD;
            case RUNNING_GET_COHORT,
                 RUNNING_PROCESS_BATCH,
                 RUNNING_PROCESS_CORE -> Task.TaskStatus.INPROGRESS;
            case COMPLETED -> Task.TaskStatus.COMPLETED;
            case FAILED -> Task.TaskStatus.FAILED;
            case CANCELLED -> Task.TaskStatus.CANCELLED;
        };
    }

    // -----------------------------
    // Priority Mapping
    // -----------------------------

    private Task.TaskPriority mapToFhirPriority(JobPriority p) {
        return switch (p) {
            case HIGH -> Task.TaskPriority.ASAP;
            case NORMAL -> Task.TaskPriority.ROUTINE;
        };
    }

}
