package de.medizininformatikinitiative.torch.jobhandling.workunit;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.JobExecutionContext;
import de.medizininformatikinitiative.torch.jobhandling.JobParameters;
import de.medizininformatikinitiative.torch.jobhandling.JobPriority;
import de.medizininformatikinitiative.torch.jobhandling.JobStatus;
import de.medizininformatikinitiative.torch.jobhandling.result.BatchResult;
import de.medizininformatikinitiative.torch.jobhandling.result.BatchSelection;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedCrtdl;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedDataExtraction;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import de.medizininformatikinitiative.torch.service.CohortQueryService;
import de.medizininformatikinitiative.torch.service.ExtractDataService;
import de.medizininformatikinitiative.torch.service.JobPersistenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessBatchWorkUnitTest {

    static final JobParameters EMPTY_PARAMETERS =
            new JobParameters(
                    new AnnotatedCrtdl(
                            JsonNodeFactory.instance.objectNode(),
                            new AnnotatedDataExtraction(List.of()),
                            Optional.empty()
                    ),
                    List.of()
            );

    @Mock
    JobPersistenceService persistence;
    @Mock
    ExtractDataService extract;
    @Mock
    CohortQueryService cohortQueryService;

    PatientBatch batch = new PatientBatch(List.of("1", "2"));
    @Mock
    BatchResult batchResult;

    private static Job newJob(UUID jobId) {
        Instant now = Instant.now();
        return new Job(
                jobId,
                JobStatus.RUNNING_PROCESS_BATCH,
                WorkUnitState.initNow(),
                0,
                Map.of(),
                now,
                now,
                Optional.empty(),
                List.of(),
                EMPTY_PARAMETERS,
                JobPriority.NORMAL,
                WorkUnitState.initNow()
        );
    }

    private JobExecutionContext ctx() {
        return new JobExecutionContext(
                persistence,
                extract,
                cohortQueryService,
                100,
                3,
                1
        );
    }

    @Test
    void execute_success_loadsBatch_loadsJob_processesBatch_andPersistsSuccess() throws IOException {
        UUID jobId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        Job job = newJob(jobId);

        ProcessBatchWorkUnit wu = new ProcessBatchWorkUnit(job, batchId);

        when(persistence.tryStartBatch(jobId, batchId)).thenReturn(true);
        when(persistence.loadBatch(jobId, batchId)).thenReturn(batch);
        when(persistence.getJob(jobId)).thenReturn(Optional.of(job));
        when(extract.processBatch(any(BatchSelection.class))).thenReturn(Mono.just(batchResult));

        assertThatCode(() -> wu.execute(ctx()).block())
                .doesNotThrowAnyException();

        verify(persistence).loadBatch(jobId, batchId);
        verify(persistence).getJob(jobId);

        ArgumentCaptor<BatchSelection> captor = ArgumentCaptor.forClass(BatchSelection.class);
        verify(extract).processBatch(captor.capture());

        BatchSelection selection = captor.getValue();
        assertThat(selection).isNotNull();
        assertThat(selection.job().id()).isEqualTo(jobId);
        assertThat(selection.batch()).isSameAs(batch);

        verify(persistence).onBatchProcessingSuccess(batchResult);
        verify(persistence, never()).onBatchError(any(), any(), anyList(), any());
        verify(persistence, never()).onJobError(any(), anyList(), any());
    }

    @Test
    void execute_whenExtractFails_recordsBatchError_andCompletes() throws IOException {
        UUID jobId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        Job job = newJob(jobId);

        ProcessBatchWorkUnit wu = new ProcessBatchWorkUnit(job, batchId);

        when(persistence.tryStartBatch(jobId, batchId)).thenReturn(true);
        when(persistence.loadBatch(jobId, batchId)).thenReturn(batch);
        when(persistence.getJob(jobId)).thenReturn(Optional.of(job));

        RuntimeException boom = new RuntimeException("boom");
        when(extract.processBatch(any(BatchSelection.class))).thenReturn(Mono.error(boom));

        // batch error is swallowed -> execute completes
        assertThatCode(() -> wu.execute(ctx()).block())
                .doesNotThrowAnyException();

        verify(persistence, never()).onBatchProcessingSuccess(any());

        verify(persistence).onBatchError(
                eq(jobId),
                eq(batchId),
                eq(List.of()),
                any(Exception.class)
        );

        verify(persistence, never()).onJobError(any(), anyList(), any());
    }

    @Test
    void execute_whenNotClaimed_doesNothing_andCompletes() throws IOException {
        UUID jobId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        Job job = newJob(jobId);

        ProcessBatchWorkUnit wu = new ProcessBatchWorkUnit(job, batchId);

        when(persistence.tryStartBatch(jobId, batchId)).thenReturn(false);

        assertThatCode(() -> wu.execute(ctx()).block()).doesNotThrowAnyException();

        verify(persistence, never()).loadBatch(any(), any());
        verify(persistence, never()).getJob(any());
        verify(extract, never()).processBatch(any());

        verify(persistence, never()).onBatchProcessingSuccess(any());
        verify(persistence, never()).onBatchError(any(), any(), anyList(), any());
        verify(persistence, never()).onJobError(any(), anyList(), any());
    }

    @Test
    void execute_whenExtractReturnsEmpty_doesNotPersistSuccess() throws IOException {
        UUID jobId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        Job job = newJob(jobId);

        ProcessBatchWorkUnit wu = new ProcessBatchWorkUnit(job, batchId);

        when(persistence.tryStartBatch(jobId, batchId)).thenReturn(true);
        when(persistence.loadBatch(jobId, batchId)).thenReturn(batch);
        when(persistence.getJob(jobId)).thenReturn(Optional.of(job));
        when(extract.processBatch(any(BatchSelection.class))).thenReturn(Mono.empty());

        assertThatCode(() -> wu.execute(ctx()).block()).doesNotThrowAnyException();

        verify(persistence, never()).onBatchProcessingSuccess(any());
        verify(persistence, never()).onBatchError(any(), any(), anyList(), any());
        verify(persistence, never()).onJobError(any(), anyList(), any());
    }

    @Test
    void missingJobThrows() throws IOException {
        UUID jobId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        Job job = newJob(jobId);

        ProcessBatchWorkUnit wu = new ProcessBatchWorkUnit(job, batchId);

        when(persistence.tryStartBatch(jobId, batchId)).thenReturn(true);
        when(persistence.getJob(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> wu.execute(ctx()).block())
                .isInstanceOf(RuntimeException.class);

        verify(persistence, never()).loadBatch(any(), any());
        verify(persistence, never()).onJobError(any(), anyList(), any());
        verify(persistence, never()).onBatchProcessingSuccess(any());
        verify(persistence, never()).onBatchError(any(), any(), anyList(), any());
    }

    @Test
    void execute_whenExtractEmitsRuntimeException_recordsBatchError_andCompletes() throws IOException {
        UUID jobId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        Job job = newJob(jobId);

        ProcessBatchWorkUnit wu = new ProcessBatchWorkUnit(job, batchId);

        when(persistence.tryStartBatch(jobId, batchId)).thenReturn(true);
        when(persistence.loadBatch(jobId, batchId)).thenReturn(batch);
        when(persistence.getJob(jobId)).thenReturn(Optional.of(job));

        RuntimeException boom = new RuntimeException("boom");
        when(extract.processBatch(any(BatchSelection.class))).thenReturn(Mono.error(boom));

        assertThatCode(() -> wu.execute(ctx()).block()).doesNotThrowAnyException();

        ArgumentCaptor<Exception> exCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(persistence).onBatchError(eq(jobId), eq(batchId), eq(List.of()), exCaptor.capture());

        assertThat(exCaptor.getValue()).isSameAs(boom);
        verify(persistence, never()).onJobError(any(), anyList(), any());
    }


    @Test
    void persistingSuccessFailsThrows() throws IOException {
        UUID jobId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        Job job = newJob(jobId);

        ProcessBatchWorkUnit wu = new ProcessBatchWorkUnit(job, batchId);

        when(persistence.tryStartBatch(jobId, batchId)).thenReturn(true);
        when(persistence.loadBatch(jobId, batchId)).thenReturn(batch);
        when(persistence.getJob(jobId)).thenReturn(Optional.of(job));
        when(extract.processBatch(any(BatchSelection.class))).thenReturn(Mono.just(batchResult));

        // persistence/orchestration failure in onBatchProcessingSuccess => job error
        RuntimeException boom = new RuntimeException(new IOException("disk full?"));
        doThrow(boom).when(persistence).onBatchProcessingSuccess(batchResult);


        assertThatThrownBy(() -> wu.execute(ctx()).block())
                .isInstanceOf(RuntimeException.class); // will wrap NoSuchElementException

        verify(persistence, never()).onJobError(any(), anyList(), any());
        verify(persistence, never()).onBatchError(any(), any(), anyList(), any());
    }
}
