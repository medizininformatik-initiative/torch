package de.medizininformatikinitiative.torch.diagnostics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.exceptions.CsvValidationException;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.BatchExclusions;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.PatientExclusionStage;
import de.medizininformatikinitiative.torch.jobhandling.DefaultFileIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static de.medizininformatikinitiative.torch.TestUtils.concat;
import static de.medizininformatikinitiative.torch.TestUtils.readMergedDiagnostics;
import static de.medizininformatikinitiative.torch.diagnostics.DiagnosticsStore.PATIENT_EXCLUSIONS_FILE;
import static de.medizininformatikinitiative.torch.diagnostics.DiagnosticsStore.REPORTS_DIRECTORY;
import static de.medizininformatikinitiative.torch.diagnostics.DiagnosticsStore.RESOURCE_EXCLUSIONS_FILE;
import static de.medizininformatikinitiative.torch.diagnostics.PipelineStage.CASCADING_DELETE;
import static de.medizininformatikinitiative.torch.diagnostics.PipelineStage.CONSENT_FETCH;
import static de.medizininformatikinitiative.torch.diagnostics.PipelineStage.COPY_REDACT;
import static de.medizininformatikinitiative.torch.diagnostics.PipelineStage.DIRECT_LOAD;
import static de.medizininformatikinitiative.torch.diagnostics.PipelineStage.REFERENCE_RESOLVE;
import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticsStoreTest {

    static final String BATCH_1 = "batch-id-1";
    static final String BATCH_2 = "batch-id-2";
    static final String GROUP_1 = "group-id-1";
    static final String RESOURCE_1 = "resource-id-1";
    static final String ATTRIBUTE_1 = "attribute-1";
    static final String PATIENT_1 = "pat-id-1";
    static final String GROUP_2 = "group-id-2";
    static final String RESOURCE_2 = "resource-id-2";
    static final String ATTRIBUTE_2 = "attribute-2";
    static final String PATIENT_2 = "pat-id-2";

    @TempDir
    Path jobDirectory;

    DiagnosticsStore diagnosticsStore;


    @BeforeEach
    void setUp() throws IOException {
        diagnosticsStore = new DiagnosticsStore(new DefaultFileIO(), new ObjectMapper());
        diagnosticsStore.ensureDirectoryStructure(jobDirectory);
    }

    static BatchDiagnostics createDiagnostics_1() {
        var details_1 = new BatchDetails(Map.of(
                CONSENT_FETCH, 3677L,
                DIRECT_LOAD, 7678L,
                REFERENCE_RESOLVE, 1500L,
                CASCADING_DELETE, 3439L,
                COPY_REDACT, 1096L), 5426, 7316);
        var batchExclusions_1 = BatchExclusions.empty();
        batchExclusions_1.addMustHaveExclusionCore(GROUP_1, RESOURCE_1, ATTRIBUTE_1);
        batchExclusions_1.addReferenceNotFoundExclusionCore(GROUP_1, RESOURCE_1);
        batchExclusions_1.addPatientExclusion(PatientExclusionStage.DIRECT_LOAD, PATIENT_1);
        return new BatchDiagnostics(batchExclusions_1, details_1);
    }

    static BatchDiagnostics createDiagnostics_2() {
        var details_2 = new BatchDetails(Map.of(
                DIRECT_LOAD, 4887L,
                REFERENCE_RESOLVE, 3503L,
                CASCADING_DELETE, 6772L,
                COPY_REDACT, 4847L), 8860, 9659);
        var batchExclusions_2 = BatchExclusions.empty();
        batchExclusions_2.addMustHaveExclusionCore(GROUP_2, RESOURCE_2, ATTRIBUTE_2);
        batchExclusions_2.addReferenceNotFoundExclusionCore(GROUP_2, RESOURCE_2);
        batchExclusions_2.addPatientExclusion(PatientExclusionStage.DIRECT_LOAD, PATIENT_2);
        return new BatchDiagnostics(batchExclusions_2, details_2);
    }

    @Test
    void testWriteReadDiagnosticsPerBatch() throws IOException, CsvValidationException {
        var diagnostics_1 = createDiagnostics_1();
        var diagnostics_2 = createDiagnostics_2();

        diagnosticsStore.writeDiagnostics(diagnostics_1, jobDirectory, BATCH_1);
        diagnosticsStore.writeDiagnostics(diagnostics_2, jobDirectory, BATCH_2);
        var result = diagnosticsStore.loadAllDiagnostics(jobDirectory);

        assertThat(result).isEqualTo(Map.of(BATCH_1, diagnostics_1, BATCH_2, diagnostics_2));
    }

    @Test
    void testWriteMergedExclusions() throws IOException, CsvValidationException {
        var diagnostics_1 = createDiagnostics_1();
        var diagnostics_2 = createDiagnostics_2();
        var diagnosticsPerBatch = Map.of(BATCH_1, diagnostics_1, BATCH_2, diagnostics_2);

        diagnosticsStore.writeMergedExclusions(diagnosticsPerBatch, jobDirectory);

        var writtenExclusions = readMergedDiagnostics(
                jobDirectory.resolve(REPORTS_DIRECTORY).resolve(RESOURCE_EXCLUSIONS_FILE).toFile(),
                jobDirectory.resolve(REPORTS_DIRECTORY).resolve(PATIENT_EXCLUSIONS_FILE).toFile());
        assertThat(writtenExclusions.getPatientExclusions())
                .containsExactlyInAnyOrderElementsOf(
                        concat(diagnostics_1.batchExclusions().getPatientExclusions(),
                                diagnostics_2.batchExclusions().getPatientExclusions()));
        assertThat(writtenExclusions.getResourceExclusions())
                .containsExactlyInAnyOrderElementsOf(
                        concat(diagnostics_1.batchExclusions().getResourceExclusions(),
                                diagnostics_2.batchExclusions().getResourceExclusions()));
    }

    @Test
    void writeReadJobSummary() throws IOException {
        var summary = JobDiagnosticSummary.initFromBatches(List.of(createDiagnostics_1(), createDiagnostics_2()));

        diagnosticsStore.writeSummary(summary, jobDirectory);
        var result = diagnosticsStore.readSummary(jobDirectory);

        assertThat(result).isEqualTo(summary);
    }

}