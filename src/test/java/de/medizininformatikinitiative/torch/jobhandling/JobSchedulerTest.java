package de.medizininformatikinitiative.torch.jobhandling;

import de.medizininformatikinitiative.torch.config.TorchProperties;
import de.medizininformatikinitiative.torch.jobhandling.workunit.ProcessBatchWorkUnit;
import de.medizininformatikinitiative.torch.jobhandling.workunit.WorkUnit;
import de.medizininformatikinitiative.torch.service.CohortQueryService;
import de.medizininformatikinitiative.torch.service.ExtractDataService;
import de.medizininformatikinitiative.torch.service.JobPersistenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobSchedulerTest {

    @Mock
    TorchProperties properties;
    @Mock
    JobPersistenceService persistence;
    @Mock
    ExtractDataService extract;
    @Mock
    CohortQueryService cohortQueryService;

    private void setRunning(JobScheduler scheduler, boolean value) throws Exception {
        Field field = JobScheduler.class.getDeclaredField("running");
        field.setAccessible(true);
        field.set(scheduler, value);
    }

    private static Method workerLoopMethod() throws Exception {
        Method m = JobScheduler.class.getDeclaredMethod("workerLoop");
        m.setAccessible(true);
        return m;
    }

    private JobExecutionContext newCtx() {
        return new JobExecutionContext(persistence, extract, cohortQueryService, 100, 3, 1);
    }

    private JobScheduler newScheduler(JobExecutionContext ctx) {
        when(properties.maxConcurrency()).thenReturn(1);
        return new JobScheduler(properties, ctx);
    }


    /**
     * Fix 1: Selection Exception.
     * In your new logic, selectNextWorkUnit throwing an exception is FATAL.
     * We run this in a thread because it might trigger System.exit(1).
     */
    @Test
    void workerLoop_terminatesOnSelectionException() throws Exception {
        JobScheduler scheduler = spy(newScheduler(newCtx()));
        setRunning(scheduler, true);

        doNothing().when(scheduler).terminate();

        when(persistence.selectNextWorkUnit()).thenThrow(new RuntimeException("DB Connection Lost"));

        Method workerLoop = JobScheduler.class.getDeclaredMethod("workerLoop");
        workerLoop.setAccessible(true);
        workerLoop.invoke(scheduler);

        verify(scheduler).terminate();
        verify(persistence, times(1)).selectNextWorkUnit();
    }

    @Test
    void workerLoop_coversEmptyBranch() throws Exception {
        JobScheduler scheduler = newScheduler(newCtx());
        setRunning(scheduler, true);

        when(persistence.selectNextWorkUnit())
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty())
                .thenAnswer(inv -> {
                    setRunning(scheduler, false);
                    return Optional.empty();
                });

        workerLoopMethod().invoke(scheduler);

        verify(persistence, atLeast(3)).selectNextWorkUnit();
    }

    @Test
    void workerLoop_coversExecuteBlockingException() throws Exception {
        JobScheduler scheduler = newScheduler(newCtx());
        setRunning(scheduler, true);

        ProcessBatchWorkUnit mockWu = mock(ProcessBatchWorkUnit.class);
        when(persistence.selectNextWorkUnit())
                .thenReturn(Optional.of(mockWu))
                .thenAnswer(inv -> {
                    setRunning(scheduler, false);
                    return Optional.empty();
                });

        when(mockWu.execute(any())).thenReturn(Mono.error(new RuntimeException("Job Logic Error")));

        Job mockJob = mock(Job.class);
        when(mockWu.job()).thenReturn(mockJob);
        when(mockJob.id()).thenReturn(UUID.randomUUID());

        workerLoopMethod().invoke(scheduler);

        verify(persistence, atLeast(2)).selectNextWorkUnit();
    }

    @Test
    void executeBlocking_coversOnErrorResume() throws Exception {
        JobScheduler scheduler = newScheduler(newCtx());
        ProcessBatchWorkUnit mockWu = mock(ProcessBatchWorkUnit.class);
        Job mockJob = mock(Job.class);
        UUID jobId = UUID.randomUUID();
        Exception testError = new RuntimeException("Execution failed");

        when(mockJob.id()).thenReturn(jobId);
        when(mockWu.job()).thenReturn(mockJob);
        when(mockWu.execute(any())).thenReturn(Mono.error(testError));

        Method executeBlocking = JobScheduler.class.getDeclaredMethod("executeBlocking", WorkUnit.class);
        executeBlocking.setAccessible(true);

        executeBlocking.invoke(scheduler, mockWu);

        verify(persistence, timeout(1000)).onJobError(eq(jobId), eq(List.of()), eq(testError));
    }

    /**
     * Fix 4: Infrastructure Failure (Fatal).
     * Tests when the error-reporting mechanism itself fails.
     */
    @Test
    void executeBlocking_crashesThread_whenPersistenceFailsToSaveError() throws Exception {
        JobScheduler scheduler = newScheduler(newCtx());
        ProcessBatchWorkUnit mockWu = mock(ProcessBatchWorkUnit.class);
        Job mockJob = mock(Job.class);

        when(mockWu.job()).thenReturn(mockJob);
        when(mockJob.id()).thenReturn(UUID.randomUUID());

        when(mockWu.execute(any())).thenReturn(Mono.error(new RuntimeException("Job failed")));
        doThrow(new RuntimeException("DB Connection Lost")).when(persistence).onJobError(any(), any(), any());

        Method executeBlocking = JobScheduler.class.getDeclaredMethod("executeBlocking", WorkUnit.class);
        executeBlocking.setAccessible(true);

        try {
            executeBlocking.invoke(scheduler, mockWu);
        } catch (InvocationTargetException e) {
            assertThat(e.getCause()).isInstanceOf(RuntimeException.class);
            assertThat(e.getCause().getMessage()).contains("DB Connection Lost");
        }
    }

    @Test
    void workerLoop_whenErrorOccurs_propagatesWithoutCatching() throws Exception {
        JobScheduler scheduler = newScheduler(newCtx());

        Method workerLoop = workerLoopMethod();

        Thread worker = new Thread(() -> {
            try {
                workerLoop.invoke(scheduler);
            } catch (Exception e) {
                assertThat(e.getCause()).isInstanceOf(OutOfMemoryError.class);
            }
        });

        worker.start();
        worker.join(1_000);

        assertThat(worker.isAlive()).isFalse();
    }

    @Test
    void shutdown_stopsWorkersAndShutsDownExecutor() throws Exception {
        JobScheduler scheduler = newScheduler(newCtx());
        when(persistence.selectNextWorkUnit()).thenReturn(Optional.empty());
        Method init = JobScheduler.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(scheduler);
        Thread.sleep(100);
        Method shutdown = JobScheduler.class.getDeclaredMethod("shutdown");
        shutdown.setAccessible(true);
        shutdown.invoke(scheduler);
        Field executorField = JobScheduler.class.getDeclaredField("executor");
        executorField.setAccessible(true);
        ExecutorService executor = (ExecutorService) executorField.get(scheduler);

        assertThat(executor.isShutdown()).isTrue();
        assertThat(executor.isTerminated()).isTrue();
    }

    @Test
    void shutdown_whenWorkersDoNotTerminateInTime_forcesShutdown() throws Exception {
        JobScheduler scheduler = newScheduler(newCtx());

        // Make workers block indefinitely by having selectNextWorkUnit sleep
        when(persistence.selectNextWorkUnit()).thenAnswer(inv -> {
            Thread.sleep(60_000); // Sleep longer than shutdown timeout
            return Optional.empty();
        });

        // Start the scheduler
        Method init = JobScheduler.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(scheduler);

        Thread.sleep(100);

        // Call shutdown - it should timeout and force shutdown
        Method shutdown = JobScheduler.class.getDeclaredMethod("shutdown");
        shutdown.setAccessible(true);

        long start = System.currentTimeMillis();
        shutdown.invoke(scheduler);
        long duration = System.currentTimeMillis() - start;

        // Should complete in around 30 seconds (the timeout), not 60
        assertThat(duration).isLessThan(35_000);

        Field executorField = JobScheduler.class.getDeclaredField("executor");
        executorField.setAccessible(true);
        ExecutorService executor = (ExecutorService) executorField.get(scheduler);

        assertThat(executor.isShutdown()).isTrue();
    }

    @Test
    void workerLoop_retriesOnRetryableException_fromOnJobError() throws Exception {
        JobScheduler scheduler = spy(newScheduler(newCtx()));
        setRunning(scheduler, true);

        ProcessBatchWorkUnit wu = mock(ProcessBatchWorkUnit.class);
        Job job = mock(Job.class);
        UUID jobId = UUID.randomUUID();

        when(wu.job()).thenReturn(job);
        when(job.id()).thenReturn(jobId);

        // WorkUnit fails -> triggers onErrorResume
        when(wu.execute(any())).thenReturn(Mono.error(new RuntimeException("boom")));

        // Make onJobError fail with a RETRYABLE cause (IOException)
        doThrow(new RuntimeException("wrapped", new IOException("connection reset")))
                .when(persistence).onJobError(eq(jobId), eq(List.of()), any(Throwable.class));

        // One unit, then stop loop after first retry sleep
        when(persistence.selectNextWorkUnit())
                .thenReturn(Optional.of(wu))
                .thenAnswer(inv -> {
                    setRunning(scheduler, false);
                    return Optional.empty();
                });

        long start = System.currentTimeMillis();
        workerLoopMethod().invoke(scheduler);
        long elapsed = System.currentTimeMillis() - start;

        // Proves retry path slept (RETRY_SLEEP_MS=1000)
        assertThat(elapsed).isGreaterThanOrEqualTo(800);

        // Should not terminate on first retryable failure
        verify(scheduler, times(0)).terminate();

        // Error path was entered
        verify(persistence, atLeast(1)).onJobError(eq(jobId), eq(List.of()), any(Throwable.class));
    }

    @Test
    void workerLoop_terminatesOnNonRetryableException() throws Exception {
        JobScheduler scheduler = spy(newScheduler(newCtx()));
        setRunning(scheduler, true);
        doNothing().when(scheduler).terminate();

        ProcessBatchWorkUnit wu = mock(ProcessBatchWorkUnit.class);
        Job job = mock(Job.class);
        UUID jobId = UUID.randomUUID();

        when(wu.job()).thenReturn(job);
        when(job.id()).thenReturn(jobId);

        // WorkUnit fails -> triggers onErrorResume -> onJobError
        when(wu.execute(any())).thenReturn(Mono.error(new RuntimeException("boom")));

        // Non-retryable failure in error reporting
        doThrow(new IllegalArgumentException("bad"))
                .when(persistence).onJobError(eq(jobId), eq(List.of()), any(Throwable.class));

        when(persistence.selectNextWorkUnit()).thenReturn(Optional.of(wu));

        // Will terminate due to non-retryable branch -> RuntimeException -> outer catch -> terminate()
        workerLoopMethod().invoke(scheduler);

        verify(scheduler).terminate();
    }

    @Test
    void workerLoop_terminatesWhenRetryableFailuresExhausted() throws Exception {
        long oldSleep = JobScheduler.RETRY_SLEEP_MS;
        int oldMax = JobScheduler.MAX_RETRYABLE_FAILURES;
        try {
            JobScheduler.RETRY_SLEEP_MS = 0;      // no delay
            JobScheduler.MAX_RETRYABLE_FAILURES = 1; // exhaust immediately

            JobScheduler scheduler = spy(newScheduler(newCtx()));
            setRunning(scheduler, true);
            doNothing().when(scheduler).terminate();

            ProcessBatchWorkUnit wu = mock(ProcessBatchWorkUnit.class);
            Job job = mock(Job.class);
            UUID jobId = UUID.randomUUID();

            when(wu.job()).thenReturn(job);
            when(job.id()).thenReturn(jobId);

            when(wu.execute(any())).thenReturn(Mono.error(new RuntimeException("boom")));

            // Retryable: wrapped IOException
            doThrow(new RuntimeException("wrapped", new IOException("connection reset")))
                    .when(persistence).onJobError(eq(jobId), eq(List.of()), any(Throwable.class));

            when(persistence.selectNextWorkUnit()).thenReturn(Optional.of(wu));

            workerLoopMethod().invoke(scheduler);

            verify(scheduler).terminate();
        } finally {
            JobScheduler.RETRY_SLEEP_MS = oldSleep;
            JobScheduler.MAX_RETRYABLE_FAILURES = oldMax;
        }
    }
}
