package de.medizininformatikinitiative.torch.diagnostics;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.PatientExclusionStage;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.ResourceExclusionReason;
import de.medizininformatikinitiative.torch.jobhandling.DefaultFileIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiagnosticsStoreTest {

    private final DefaultFileIO io = new DefaultFileIO();
    private final ObjectMapper mapper = new ObjectMapper();
    private final DiagnosticsStore store = new DiagnosticsStore(io, mapper);

    @TempDir
    Path jobDir;

    @BeforeEach
    void setUp() throws IOException {
        store.ensureDirectoryStructure(jobDir);
    }

    @Nested
    class BatchRoundTrip {

        @Test
        void writeThenLoadAllDiagnostics_reconstructsExclusionsAndDetails() throws IOException {
            BatchDiagnostics diagnostics = BatchDiagnostics.empty()
                    .setNumCohortPatients(3)
                    .setFinalPatientCount(2);
            diagnostics.batchExclusions().addMustHaveExclusion("grp", "Observation/1", "Obs.code", "pat1");
            diagnostics.batchExclusions().addPatientExclusion(PatientExclusionStage.CONSENT, "pat2");

            store.writeDiagnostics(diagnostics, jobDir, "batch1");

            Map<String, BatchDiagnostics> loaded = store.loadAllDiagnostics(jobDir);

            assertThat(loaded).containsOnlyKeys("batch1");
            BatchDiagnostics reloaded = loaded.get("batch1");
            assertThat(reloaded.batchDetails().numCohortPatients()).isEqualTo(3);
            assertThat(reloaded.batchDetails().numFinalPatients()).isEqualTo(2);

            assertThat(reloaded.batchExclusions().getResourceExclusions()).hasSize(1);
            var resourceEvent = reloaded.batchExclusions().getResourceExclusions().getFirst();
            assertThat(resourceEvent.reason()).isEqualTo(ResourceExclusionReason.MUST_HAVE);
            assertThat(resourceEvent.groupId()).isEqualTo("grp");
            assertThat(resourceEvent.resourceId()).isEqualTo("Observation/1");
            assertThat(resourceEvent.attributeRef()).isEqualTo("Obs.code");
            assertThat(resourceEvent.patientId()).isEqualTo("pat1");

            assertThat(reloaded.batchExclusions().getPatientExclusions()).hasSize(1);
            var patientEvent = reloaded.batchExclusions().getPatientExclusions().getFirst();
            assertThat(patientEvent.stage()).isEqualTo(PatientExclusionStage.CONSENT);
            assertThat(patientEvent.patientId()).isEqualTo("pat2");
        }

        @Test
        void writeMergedExclusionsThenSummary_roundTripsViaJson() throws IOException {
            BatchDiagnostics diagnostics = BatchDiagnostics.empty();
            diagnostics.batchExclusions().addReferenceNotFoundExclusion("grp", "Observation/1", "pat1");

            store.writeMergedExclusions(Map.of("batch1", diagnostics), jobDir);

            assertThat(store.resourceExclusionsExists(jobDir)).isTrue();
            assertThat(store.patientExclusionsExists(jobDir)).isTrue();

            JobDiagnosticSummary summary = JobDiagnosticSummary.empty().initFromBatches(List.of(diagnostics));
            store.writeSummary(summary, jobDir);

            assertThat(store.jobSummaryExists(jobDir)).isTrue();
            JobDiagnosticSummary reloaded = store.readSummary(jobDir);
            assertThat(reloaded).isEqualTo(summary);
        }

        @Test
        void deleteBatchDiagnostics_removesPerBatchReportDirectory() throws IOException {
            store.writeDiagnostics(BatchDiagnostics.empty(), jobDir, "batch1");
            Path batchDir = jobDir.resolve("reports").resolve("batch1");
            assertThat(io.exists(batchDir)).isTrue();

            store.deleteBatchDiagnostics(Set.of("batch1"), jobDir);

            assertThat(io.exists(batchDir)).isFalse();
        }
    }

    @Nested
    class ErrorHandling {

        @Test
        void loadAllDiagnostics_wrapsIOExceptionInRuntimeException() throws IOException {
            // Missing details.json (never written) triggers a FileNotFoundException while loading.
            Path batchDir = jobDir.resolve("reports").resolve("batch1");
            io.createDirectories(batchDir);
            io.newBufferedWriter(batchDir.resolve("resource-exclusions.csv")).close();
            io.newBufferedWriter(batchDir.resolve("patient-exclusions.csv")).close();

            assertThatThrownBy(() -> store.loadAllDiagnostics(jobDir))
                    .isInstanceOf(RuntimeException.class)
                    .hasCauseInstanceOf(IOException.class);
        }

        @Test
        void writeMergedExclusions_wrapsIOExceptionInRuntimeException() {
            Path nonExistentJobDir = jobDir.resolve("does-not-exist");
            BatchDiagnostics diagnostics = BatchDiagnostics.empty();

            assertThatThrownBy(() -> store.writeMergedExclusions(Map.of("batch1", diagnostics), nonExistentJobDir))
                    .isInstanceOf(RuntimeException.class)
                    .hasCauseInstanceOf(IOException.class);
        }
    }
}
