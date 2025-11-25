package de.medizininformatikinitiative.torch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.medizininformatikinitiative.torch.config.TorchProperties;
import de.medizininformatikinitiative.torch.jobhandling.BatchState;
import de.medizininformatikinitiative.torch.jobhandling.DefaultFileIO;
import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.JobParameters;
import de.medizininformatikinitiative.torch.jobhandling.JobPriority;
import de.medizininformatikinitiative.torch.jobhandling.JobStatus;
import de.medizininformatikinitiative.torch.jobhandling.WorkUnitStatus;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedCrtdl;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedDataExtraction;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionResourceBundle;
import de.medizininformatikinitiative.torch.model.extraction.ResourceExtractionInfo;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class JobPersistenceServiceTest {

    static final Path BASE_DIR = Path.of("JobPersistenceTestDir");

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new Jdk8Module())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    JobParameters emptyParameters =
            new JobParameters(new AnnotatedCrtdl(JsonNodeFactory.instance.objectNode(), new AnnotatedDataExtraction(List.of()), Optional.empty()),
                    List.of());

    TorchProperties properties = new TorchProperties(
            new TorchProperties.Base("http://base-url"),
            new TorchProperties.Output(new TorchProperties.Output.File(new TorchProperties.Output.File.Server("http://server-url"))),
            new TorchProperties.Profile("/profile-dir"),
            new TorchProperties.Mapping("consent", "typeToConsent"),
            new TorchProperties.Flare(null),
            new TorchProperties.Results(BASE_DIR.toString(), "persistence"),
            10,
            5,
            100,
            "mappingsFile",
            "conceptTreeFile",
            "dseMappingTreeFile",
            "search-parameters.json",
            true
    );


    private Job createJob(UUID jobId) {
        BatchState s1 = new BatchState(UUID.randomUUID(), WorkUnitStatus.INIT,
                Optional.empty(), Optional.empty());
        BatchState s2 = new BatchState(UUID.randomUUID(), WorkUnitStatus.INIT,
                Optional.empty(), Optional.empty());
        return new Job(jobId, JobStatus.RUNNING_CREATE_BATCHES, Map.of(s1.batchId(), s1, s2.batchId(), s2),
                Instant.now(), Instant.now(), Optional.empty(), List.of(), emptyParameters, JobPriority.NORMAL, 0);
    }

    JobPersistenceService persistenceService;

    @BeforeEach
    void setUp() throws IOException {
        if (Files.exists(BASE_DIR)) {
            FileSystemUtils.deleteRecursively(BASE_DIR);
        }
        Files.createDirectories(BASE_DIR);
        persistenceService = new JobPersistenceService(new DefaultFileIO(), MAPPER, properties);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (Files.exists(BASE_DIR)) {
            FileSystemUtils.deleteRecursively(BASE_DIR);
        }
    }

    @Test
    void testSaveAndLoadJob() throws IOException {
        UUID jobId = UUID.randomUUID();
        Job job = createJob(jobId);

        persistenceService.initJob(job);

        assertThat(persistenceService.loadJob(jobId)).isEqualTo(job);
    }

    @Test
    void testSaveAndLoadUnfinishedBatches() throws IOException {
        UUID jobId = UUID.randomUUID();

        UUID batch1 = UUID.randomUUID();
        UUID batch2 = UUID.randomUUID();

        PatientBatch pb1 = new PatientBatch(List.of("A", "B"), batch1);
        PatientBatch pb2 = new PatientBatch(List.of("X", "Y"), batch2);


        persistenceService.saveBatch(pb1, jobId);
        persistenceService.saveBatch(pb2, jobId);

        assertThat(persistenceService.loadBatchForProcessing(jobId, batch2)).isEqualTo(pb2);

    }

    @Test
    void loadAllJobs_shouldLoadAllJobJsons() throws IOException {
        // Setup two job directories
        UUID j1 = UUID.randomUUID();
        UUID j2 = UUID.randomUUID();

        Path dir1 = BASE_DIR.toAbsolutePath().resolve(j1.toString());
        Path dir2 = BASE_DIR.toAbsolutePath().resolve(j2.toString());

        Files.createDirectories(dir1);
        Files.createDirectories(dir2);

        Job job1 = createJob(j1);
        Job job2 = createJob(j2);

        Files.writeString(dir1.resolve("job.json"), MAPPER.writeValueAsString(job1));
        Files.writeString(dir2.resolve("job.json"), MAPPER.writeValueAsString(job2));

        assertThat(persistenceService.loadAllJobs()).extracting(Job::id)
                .containsExactlyInAnyOrder(j1, j2);
    }

    @Test
    void saveCoreBatch_and_loadCoreInfo_shouldPersistAndReloadSingleCoreBundle() throws IOException {
        UUID jobId = UUID.randomUUID();
        Job job = createJob(jobId);
        persistenceService.initJob(job);

        // Prepare core bundle
        ResourceExtractionInfo rei =
                new ResourceExtractionInfo(
                        Set.of("G1"),
                        Map.of("Patient.name", Set.of("rid-1"))
                );

        ExtractionResourceBundle cb = new ExtractionResourceBundle(
                new ConcurrentHashMap<>(Map.of("rid-1", rei)),
                new ConcurrentHashMap<>()
        );

        // Save as core batch
        UUID batchId = UUID.randomUUID();
        persistenceService.saveCoreBatch(jobId, batchId, cb);

        // Load merged core info
        ExtractionResourceBundle merged = persistenceService.loadCoreInfo(jobId);

        assertThat(merged.extractionInfoMap())
                .containsOnlyKeys("rid-1");

        assertThat(merged.extractionInfoMap().get("rid-1").groups())
                .containsExactly("G1");
    }
}
