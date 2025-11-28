package de.medizininformatikinitiative.torch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.medizininformatikinitiative.torch.config.TorchProperties;
import de.medizininformatikinitiative.torch.jobhandling.BatchState;
import de.medizininformatikinitiative.torch.jobhandling.FileIO;
import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.JobParameters;
import de.medizininformatikinitiative.torch.jobhandling.JobPriority;
import de.medizininformatikinitiative.torch.jobhandling.JobStatus;
import de.medizininformatikinitiative.torch.jobhandling.WorkUnitStatus;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedCrtdl;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedDataExtraction;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobPersistenceServiceMockedTest {

    static final Path BASE_DIR = Path.of("JobPersistenceTestDir");

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new Jdk8Module())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    JobParameters emptyParameters =
            new JobParameters(
                    new AnnotatedCrtdl(JsonNodeFactory.instance.objectNode(), new AnnotatedDataExtraction(List.of()), Optional.empty()),
                    List.of()
            );

    TorchProperties properties = new TorchProperties(
            new TorchProperties.Base("http://base-url"),
            new TorchProperties.Output(new TorchProperties.Output.File(new TorchProperties.Output.File.Server("http://server-url"))),
            new TorchProperties.Profile("/profile-dir"),
            new TorchProperties.Mapping("consent", "typeToConsent"),
            new TorchProperties.Flare(null),
            new TorchProperties.Results(BASE_DIR.toString(), "persistence"),
            10, 5, 100,
            "mappingsFile", "conceptTreeFile", "dseMappingTreeFile",
            "search-parameters.json",
            true
    );

    @Mock
    FileIO io;

    JobPersistenceService service;

    private Job createJob(UUID jobId) {
        BatchState s1 = new BatchState(UUID.randomUUID(), WorkUnitStatus.INIT, Optional.empty(), Optional.empty());
        BatchState s2 = new BatchState(UUID.randomUUID(), WorkUnitStatus.INIT, Optional.empty(), Optional.empty());

        return new Job(
                jobId,
                JobStatus.RUNNING_CREATE_BATCHES,
                Map.of(s1.batchId(), s1, s2.batchId(), s2),
                Instant.now(), Instant.now(), Optional.empty(),
                List.of(), emptyParameters, JobPriority.NORMAL, 0
        );
    }

    @BeforeEach
    void init() {
        service = new JobPersistenceService(io, MAPPER, properties);
    }

    @Test
    void saveJob_whenWriterFails_shouldEmitError() throws IOException {
        UUID jobId = UUID.randomUUID();
        Job job = createJob(jobId);

        doNothing().when(io).createDirectories(any());
        when(io.newBufferedWriter(any(Path.class))).thenThrow(new IOException("boom"));

        assertThatThrownBy(() -> service.initJob(job))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to initialize job");
    }

    @Test
    void saveJob_atomicMoveFails_shouldEmitError() throws IOException {
        UUID jobId = UUID.randomUUID();
        Job job = createJob(jobId);

        BufferedWriter writer = mock(BufferedWriter.class);

        when(io.newBufferedWriter(any())).thenReturn(writer);
        doThrow(new IOException("atomic move failed"))
                .when(io).move(any(), any(), any(), any());

        assertThatThrownBy(() -> service.initJob(job))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to initialize job");
    }

    @Test
    void loadJob_readerFails_shouldEmitError() throws IOException {
        UUID jobId = UUID.randomUUID();

        when(io.newBufferedReader(any(Path.class)))
                .thenThrow(new IOException("cannot read"));

        assertThatThrownBy(() -> service.loadJob(jobId))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("cannot read");
    }

    @Test
    void saveBatch_writerFails_shouldEmitError() throws IOException {
        UUID jobId = UUID.randomUUID();
        PatientBatch batch = new PatientBatch(List.of("1", "2"), UUID.randomUUID());

        doNothing().when(io).createDirectories(any());
        when(io.newBufferedWriter(any())).thenThrow(new IOException("boom"));

        assertThatThrownBy(() -> service.saveBatch(batch, jobId))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("boom");
    }

    @Test
    void saveJob_whenCreatingDirectoriesFails_shouldEmitError() throws IOException {
        Job job = createJob(UUID.randomUUID());

        doThrow(new IOException("mkdir fail"))
                .when(io).createDirectories(any());

        assertThatThrownBy(() -> service.initJob(job))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to initialize job");
    }
}

