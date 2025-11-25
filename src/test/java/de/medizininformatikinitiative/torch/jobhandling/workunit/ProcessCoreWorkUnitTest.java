package de.medizininformatikinitiative.torch.jobhandling.workunit;

import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.JobExecutionContext;
import de.medizininformatikinitiative.torch.jobhandling.result.CoreResult;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionResourceBundle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessCoreWorkUnitTest {

    @Mock
    JobExecutionContext ctx;
    @Mock
    de.medizininformatikinitiative.torch.service.JobPersistenceService persistence;
    @Mock
    de.medizininformatikinitiative.torch.service.ExtractDataService extract; // adjust type


    @Test
    void execute_runsCoreAndMarksSuccessWhenAcquired() throws Exception {
        UUID jobId = UUID.randomUUID();
        Job job = mock(Job.class);
        when(job.id()).thenReturn(jobId);

        ExtractionResourceBundle coreInfo = new ExtractionResourceBundle();
        CoreResult result = mock(CoreResult.class);

        when(ctx.persistence()).thenReturn(persistence);
        when(ctx.extract()).thenReturn(extract);
        when(persistence.loadCoreInfo(jobId)).thenReturn(coreInfo);
        when(extract.processCore(job, coreInfo)).thenReturn(Mono.just(result));

        ProcessCoreWorkUnit wu = new ProcessCoreWorkUnit(job);

        StepVerifier.create(wu.execute(ctx)).verifyComplete();

        InOrder inOrder = inOrder(persistence, extract);
        inOrder.verify(persistence).loadCoreInfo(jobId);
        inOrder.verify(extract).processCore(job, coreInfo);
        inOrder.verify(persistence).onCoreSuccess(result);

        verify(persistence, never()).onJobError(any(), anyList(), any());
    }

    @Test
    void execute_callsOnJobErrorWhenProcessCoreFails() throws Exception {
        UUID jobId = UUID.randomUUID();
        Job job = mock(Job.class);
        when(job.id()).thenReturn(jobId);

        ExtractionResourceBundle coreInfo = new ExtractionResourceBundle();
        IOException boom = new IOException("boom");

        when(ctx.persistence()).thenReturn(persistence);
        when(ctx.extract()).thenReturn(extract);
        when(persistence.loadCoreInfo(jobId)).thenReturn(coreInfo);
        when(extract.processCore(job, coreInfo)).thenReturn(Mono.error(boom));

        ProcessCoreWorkUnit wu = new ProcessCoreWorkUnit(job);

        StepVerifier.create(wu.execute(ctx)).verifyComplete();

        verify(persistence, never()).onCoreSuccess(any());
        verify(persistence).onCoreError(eq(jobId), eq(List.of()), any(Exception.class));
    }
}
