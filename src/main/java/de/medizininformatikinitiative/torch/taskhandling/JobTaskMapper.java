package de.medizininformatikinitiative.torch.taskhandling;

import de.medizininformatikinitiative.torch.config.TorchProperties;
import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.JobPriority;
import de.medizininformatikinitiative.torch.jobhandling.JobStatus;
import de.medizininformatikinitiative.torch.util.ResultFileManager;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Task;
import org.hl7.fhir.r4.model.UrlType;
import org.springframework.stereotype.Component;

import java.util.Date;

import static java.util.Objects.requireNonNull;

@Component
public class JobTaskMapper {

    private static final String TORCH_STATUS_SYSTEM =
            "https://medizininformatik-initiative.de/torch/job-status";

    private final ResultFileManager resultFileManager;
    private final String fileServerName;

    /**
     * @param resultFileManager used to check which batches have a consent audit trail on disk
     * @param properties        provides the file server base URL for building consent audit download links
     */
    public JobTaskMapper(ResultFileManager resultFileManager, TorchProperties properties) {
        this.resultFileManager = requireNonNull(resultFileManager);
        this.fileServerName = properties.output().file().server().url();
    }

    public Task toFhirTask(Job job) {
        Task task = new Task();

        task.setId(job.id().toString());

        task.getMeta()
                .setVersionId(Long.toString(job.version()))
                .setLastUpdated(Date.from(job.updatedAt()));

        task.setStatus(mapToFhirStatus(job.status()));
        task.setIntent(Task.TaskIntent.ORDER);

        task.setBusinessStatus(
                new CodeableConcept().addCoding(
                        new Coding(
                                TORCH_STATUS_SYSTEM,
                                job.status().name(),
                                job.status().display()
                        )
                )
        );

        task.setPriority(mapToFhirPriority(job.priority()));
        task.setAuthoredOn(Date.from(job.startedAt()));

        Period period = new Period();
        period.setStart(Date.from(job.startedAt()));
        job.finishedAt().ifPresent(end -> period.setEnd(Date.from(end)));
        task.setExecutionPeriod(period);

        task.setDescription("TORCH Job " + job.id());

        if (job.status() == JobStatus.COMPLETED) {
            job.batches().keySet().forEach(batchId -> {
                if (resultFileManager.consentAuditExists(job.id().toString(), batchId)) {
                    Task.TaskOutputComponent output = new Task.TaskOutputComponent();
                    output.setType(new CodeableConcept().setText("Consent audit NDJSON"));
                    output.setValue(new UrlType(fileServerName + "/" + job.id() + "/" + batchId + ResultFileManager.CONSENT_NDJSON));
                    task.addOutput(output);
                }
            });
        }

        return task;
    }

    private Task.TaskStatus mapToFhirStatus(JobStatus status) {
        return switch (status) {
            case PENDING -> Task.TaskStatus.REQUESTED;
            case PAUSED, TEMP_FAILED -> Task.TaskStatus.ONHOLD;
            case RUNNING_GET_COHORT,
                 RUNNING_PROCESS_BATCH,
                 RUNNING_PROCESS_CORE -> Task.TaskStatus.INPROGRESS;
            case COMPLETED -> Task.TaskStatus.COMPLETED;
            case FAILED -> Task.TaskStatus.FAILED;
            case CANCELLED, DELETED -> Task.TaskStatus.CANCELLED;
        };
    }

    private Task.TaskPriority mapToFhirPriority(JobPriority p) {
        return switch (p) {
            case HIGH -> Task.TaskPriority.ASAP;
            case NORMAL -> Task.TaskPriority.ROUTINE;
        };
    }
}
