package de.medizininformatikinitiative.torch.taskhandling;

import de.medizininformatikinitiative.torch.TestUtils;
import de.medizininformatikinitiative.torch.config.TorchProperties;
import de.medizininformatikinitiative.torch.jobhandling.BatchState;
import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.JobPriority;
import de.medizininformatikinitiative.torch.jobhandling.JobStatus;
import de.medizininformatikinitiative.torch.jobhandling.workunit.WorkUnitState;
import de.medizininformatikinitiative.torch.jobhandling.workunit.WorkUnitStatus;
import de.medizininformatikinitiative.torch.util.ResultFileManager;
import org.hl7.fhir.r4.model.Task;
import org.hl7.fhir.r4.model.UrlType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Date;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.of;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JobTaskMapperTest {

    private static final String SYSTEM =
            "https://medizininformatik-initiative.de/torch/job-status";

    private static final TorchProperties PROPERTIES = new TorchProperties(
            new TorchProperties.Base("http://base-url"),
            new TorchProperties.Output(new TorchProperties.Output.File(new TorchProperties.Output.File.Server("http://server-url"))),
            new TorchProperties.Profile("/profile-dir"),
            new TorchProperties.Mapping("typeToConsent"),
            new TorchProperties.Flare(null, null),
            new TorchProperties.Results("BASE_DIR"),
            10, 5, 100,
            "mappingsFile", "conceptTreeFile", "dseMappingTreeFile",
            "search-parameters.json",
            true,
            false
    );

    private final ResultFileManager resultFileManager = mock(ResultFileManager.class);
    private final JobTaskMapper mapper = new JobTaskMapper(resultFileManager, PROPERTIES);

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

    @Test
    void addsConsentAuditOutput_whenJobCompletedAndAuditFileExists() {
        UUID id = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        Job job = job(id, JobStatus.COMPLETED, JobPriority.NORMAL, 1)
                .withBatchState(new BatchState(batchId, WorkUnitState.initNow().finishNow(WorkUnitStatus.FINISHED)));

        when(resultFileManager.consentAuditExists(id.toString(), batchId)).thenReturn(true);

        Task t = mapper.toFhirTask(job);

        assertThat(t.getOutput()).singleElement().satisfies(output -> {
            assertThat(output.getType().getText()).isEqualTo("Consent audit NDJSON");
            assertThat(output.getValue()).isInstanceOf(UrlType.class);
            assertThat(((UrlType) output.getValue()).getValue())
                    .isEqualTo("http://server-url/" + id + "/" + batchId + "_consent.ndjson");
        });
    }

    @Test
    void omitsConsentAuditOutput_whenAuditFileMissing() {
        UUID id = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        Job job = job(id, JobStatus.COMPLETED, JobPriority.NORMAL, 1)
                .withBatchState(new BatchState(batchId, WorkUnitState.initNow().finishNow(WorkUnitStatus.SKIPPED)));

        when(resultFileManager.consentAuditExists(id.toString(), batchId)).thenReturn(false);

        Task t = mapper.toFhirTask(job);

        assertThat(t.getOutput()).isEmpty();
    }

    @Test
    void omitsConsentAuditOutput_whenJobNotCompleted() {
        UUID id = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        Job job = job(id, JobStatus.RUNNING_PROCESS_BATCH, JobPriority.NORMAL, 1)
                .withBatchState(new BatchState(batchId, WorkUnitState.initNow().finishNow(WorkUnitStatus.FINISHED)));

        Task t = mapper.toFhirTask(job);

        assertThat(t.getOutput()).isEmpty();
        verifyNoInteractions(resultFileManager);
    }
}
