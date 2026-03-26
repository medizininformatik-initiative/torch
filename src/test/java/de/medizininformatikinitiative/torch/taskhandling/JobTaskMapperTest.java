package de.medizininformatikinitiative.torch.taskhandling;

import de.medizininformatikinitiative.torch.TestUtils;
import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.JobPriority;
import de.medizininformatikinitiative.torch.jobhandling.JobStatus;
import org.hl7.fhir.r4.model.Task;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Date;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.of;

class JobTaskMapperTest {

    private static final String SYSTEM =
            "https://medizininformatik-initiative.de/torch/job-status";

    private final JobTaskMapper mapper = new JobTaskMapper();

    private static Stream<org.junit.jupiter.params.provider.Arguments> statusMappings() {
        return Stream.of(
                of(JobStatus.PENDING, Task.TaskStatus.REQUESTED),
                of(JobStatus.PAUSED, Task.TaskStatus.ONHOLD),
                of(JobStatus.TEMP_FAILED, Task.TaskStatus.ONHOLD),
                of(JobStatus.RUNNING_GET_COHORT, Task.TaskStatus.INPROGRESS),
                of(JobStatus.RUNNING_PROCESS_BATCH, Task.TaskStatus.INPROGRESS),
                of(JobStatus.RUNNING_PROCESS_CORE, Task.TaskStatus.INPROGRESS),
                of(JobStatus.COMPLETED, Task.TaskStatus.COMPLETED),
                of(JobStatus.FAILED, Task.TaskStatus.FAILED),
                of(JobStatus.CANCELLED, Task.TaskStatus.CANCELLED)
        );
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> priorityMappings() {
        return Stream.of(
                of(JobPriority.HIGH, Task.TaskPriority.ASAP),
                of(JobPriority.NORMAL, Task.TaskPriority.ROUTINE)
        );
    }

    private Job job(UUID id, JobStatus status, JobPriority priority, long version) {
        Job job = Job.init(id, TestUtils.emptyJobParams())
                .withStatus(status)
                .withPriority(priority);

        while (job.version() < version) {
            job = job.incrementVersion();
        }
        return job;
    }

    @Test
    void mapsCommon() {
        UUID id = UUID.randomUUID();
        Job job = job(id, JobStatus.RUNNING_PROCESS_BATCH, JobPriority.NORMAL, 3);

        Task t = mapper.toFhirTask(job);

        assertThat(t.getIdElement().getIdPart()).isEqualTo(id.toString());
        assertThat(t.getMeta().getVersionId()).isEqualTo("3");
        assertThat(t.getMeta().getLastUpdated()).isEqualTo(Date.from(job.updatedAt()));

        assertThat(t.getStatus()).isEqualTo(Task.TaskStatus.INPROGRESS);
        assertThat(t.getIntent()).isEqualTo(Task.TaskIntent.ORDER);
        assertThat(t.getPriority()).isEqualTo(Task.TaskPriority.ROUTINE);

        assertThat(t.getBusinessStatus().getCodingFirstRep().getSystem()).isEqualTo(SYSTEM);
        assertThat(t.getBusinessStatus().getCodingFirstRep().getCode())
                .isEqualTo(JobStatus.RUNNING_PROCESS_BATCH.name());

        assertThat(t.getAuthoredOn()).isEqualTo(Date.from(job.startedAt()));
        assertThat(t.getExecutionPeriod().getStart()).isEqualTo(Date.from(job.startedAt()));
        assertThat(t.getExecutionPeriod().hasEnd()).isFalse();

        assertThat(t.getDescription()).isEqualTo("TORCH Job " + id);
    }

    @ParameterizedTest
    @MethodSource("statusMappings")
    void mapsStatus(JobStatus status, Task.TaskStatus expected) {
        Job job = job(UUID.randomUUID(), status, JobPriority.NORMAL, 1);

        Task t = mapper.toFhirTask(job);

        assertThat(t.getStatus()).isEqualTo(expected);
        assertThat(t.getBusinessStatus().getCodingFirstRep().getCode()).isEqualTo(status.name());
    }

    @ParameterizedTest
    @MethodSource("priorityMappings")
    void mapsPriority(JobPriority priority, Task.TaskPriority expected) {
        Job job = job(UUID.randomUUID(), JobStatus.PENDING, priority, 1);

        Task t = mapper.toFhirTask(job);

        assertThat(t.getPriority()).isEqualTo(expected);
    }

    @Test
    void setsEnd() {
        UUID id = UUID.randomUUID();
        Job job = job(id, JobStatus.COMPLETED, JobPriority.HIGH, 2);

        Task t = mapper.toFhirTask(job);

        assertThat(t.getExecutionPeriod().getEnd())
                .isEqualTo(Date.from(job.finishedAt().orElseThrow()));
    }
}
