package de.medizininformatikinitiative.torch.jobhandling.workunit;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.JobExecutionContext;
import de.medizininformatikinitiative.torch.jobhandling.JobParameters;
import de.medizininformatikinitiative.torch.jobhandling.JobPriority;
import de.medizininformatikinitiative.torch.jobhandling.JobStatus;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedCrtdl;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedDataExtraction;
import de.medizininformatikinitiative.torch.service.CohortQueryService;
import de.medizininformatikinitiative.torch.service.ExtractDataService;
import de.medizininformatikinitiative.torch.service.JobPersistenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetCohortWorkUnitTest {

    @Mock
    JobPersistenceService persistence;
    @Mock
    ExtractDataService extract; // unused here but required by ctx
    @Mock
    CohortQueryService cohortQueryService;

    private static AnnotatedCrtdl minimalCrtdl() {
        return new AnnotatedCrtdl(
                JsonNodeFactory.instance.objectNode(),
                new AnnotatedDataExtraction(List.of()),
                Optional.empty()
        );
    }

    private static Job jobWithParams(UUID jobId, List<String> paramBatch) {
        JobParameters params = new JobParameters(minimalCrtdl(), paramBatch);

        return new Job(
                jobId,
                JobStatus.PENDING, WorkUnitState.initNow(),     // status doesn't matter for this work unit
                0,
                Map.of(),
                Instant.now(),
                Instant.now(),
                Optional.empty(),
                List.of(),
                params,
                JobPriority.NORMAL,
                WorkUnitState.initNow());
    }

    private JobExecutionContext ctx() {
        return new JobExecutionContext(persistence, extract, cohortQueryService, 100, 3, 1);
    }

    @Test
    void execute_whenParamBatchProvided_skipsCohortQuery_andPersistsCohortSuccess() {
        UUID jobId = UUID.randomUUID();
        List<String> paramBatch = List.of("Patient/A", "Patient/B");
        Job job = jobWithParams(jobId, paramBatch);

        ProcessCohortWorkUnit wu = new ProcessCohortWorkUnit(job);

        assertThatCode(() -> wu.execute(ctx()).block())
                .doesNotThrowAnyException();

        verifyNoInteractions(cohortQueryService);

        verify(persistence).onCohortSuccess(jobId, paramBatch);
        verify(persistence, never()).onJobError(any(), anyList(), any());
    }

    @Test
    void execute_whenParamBatchEmpty_runsCohortQuery_andPersistsCohortSuccess() {
        UUID jobId = UUID.randomUUID();
        Job job = jobWithParams(jobId, List.of());

        ProcessCohortWorkUnit wu = new ProcessCohortWorkUnit(job);

        List<String> ids = List.of("Patient/1", "Patient/2");
        when(cohortQueryService.runCohortQuery(any())).thenReturn(Mono.just(ids));

        assertThatCode(() -> wu.execute(ctx()).block())
                .doesNotThrowAnyException();

        verify(cohortQueryService).runCohortQuery(job.parameters().crtdl());
        verify(persistence).onCohortSuccess(jobId, ids);
        verify(persistence, never()).onJobError(any(), anyList(), any());
    }

    @Test
    void execute_whenCohortQueryFails_recordsJobError_andCompletes() {
        UUID jobId = UUID.randomUUID();
        Job job = jobWithParams(jobId, List.of());

        ProcessCohortWorkUnit wu = new ProcessCohortWorkUnit(job);

        RuntimeException boom = new RuntimeException("boom");
        when(cohortQueryService.runCohortQuery(any())).thenReturn(Mono.error(boom));

        // onErrorResume swallows -> completes
        assertThatCode(() -> wu.execute(ctx()).block())
                .doesNotThrowAnyException();

        verify(persistence, never()).onCohortSuccess(any(), anyList());
        verify(persistence).onCohortError(eq(jobId), eq(List.of()), any(Exception.class));
    }
}
