package de.medizininformatikinitiative.torch.taskhandling;

import de.medizininformatikinitiative.torch.config.TorchProperties;
import de.medizininformatikinitiative.torch.diagnostics.BatchProgressRegistry;
import de.medizininformatikinitiative.torch.jobhandling.BatchState;
import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.JobPriority;
import de.medizininformatikinitiative.torch.jobhandling.JobStatus;
import de.medizininformatikinitiative.torch.jobhandling.workunit.WorkUnitStatus;
import de.medizininformatikinitiative.torch.util.ResultFileManager;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Task;
import org.hl7.fhir.r4.model.UrlType;
import org.springframework.stereotype.Component;

import java.util.Date;

import static java.util.Objects.requireNonNull;

@Component
public class JobTaskMapper {

    private static final String TORCH_STATUS_SYSTEM =
            "https://medizininformatik-initiative.de/torch/job-status";

    private static final String TORCH_JOB_PROGRESS_EXTENSION =
            "https://torch.mii.de/fhir/torch-job-progress";

    private final ResultFileManager resultFileManager;
    private final BatchProgressRegistry batchProgressRegistry;
    private final String fileServerName;
    private final int batchSize;

    /**
     * @param resultFileManager     used to check which batches have a consent audit trail on disk
     * @param batchProgressRegistry provides the current pipeline stage of in-progress batches
     * @param properties            provides the file server base URL for building consent audit download links,
     *                              and the configured batch size for the progress extension
     */
    public JobTaskMapper(ResultFileManager resultFileManager, BatchProgressRegistry batchProgressRegistry, TorchProperties properties) {
        this.resultFileManager = requireNonNull(resultFileManager);
        this.batchProgressRegistry = requireNonNull(batchProgressRegistry);
        this.fileServerName = properties.output().file().server().url();
        this.batchSize = properties.batchsize();
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

        if (job.cohortState().status() == WorkUnitStatus.FINISHED) {
            addProgressExtension(task, job);
        }

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

    /**
     * Adds a {@code torch-job-progress} extension carrying the cohort size, the configured
     * batch size, the number of batches known/completed, and the current pipeline stage of
     * each in-progress batch, once the cohort query has finished.
     *
     * <p>A batch's stage is omitted if the in-memory {@link BatchProgressRegistry} has no entry
     * for it, e.g. right after a restart before the batch has resumed processing.
     */
    private void addProgressExtension(Task task, Job job) {
        long completedBatches = job.batches().values().stream()
                .filter(bs -> bs.status().isDone())
                .count();

        Extension progress = task.addExtension();
        progress.setUrl(TORCH_JOB_PROGRESS_EXTENSION);
        progress.addExtension("cohortSize", new IntegerType(job.cohortSize()));
        progress.addExtension("batchSize", new IntegerType(batchSize));
        progress.addExtension("batchesTotal", new IntegerType(job.batches().size()));
        progress.addExtension("batchesCompleted", new IntegerType((int) completedBatches));

        for (BatchState bs : job.batches().values()) {
            if (bs.status() != WorkUnitStatus.IN_PROGRESS) {
                continue;
            }
            batchProgressRegistry.currentStage(bs.batchId()).ifPresent(stage -> {
                Extension activeBatch = progress.addExtension();
                activeBatch.setUrl("activeBatch");
                activeBatch.addExtension("batchId", new StringType(bs.batchId().toString()));
                activeBatch.addExtension("stage", new StringType(stage.name()));
            });
        }
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
