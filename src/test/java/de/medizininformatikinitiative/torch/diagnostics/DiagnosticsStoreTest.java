package de.medizininformatikinitiative.torch.diagnostics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.exceptions.CsvValidationException;
import de.medizininformatikinitiative.torch.diagnostics.consent.ConsentConsideredResourceEvent;
import de.medizininformatikinitiative.torch.diagnostics.consent.ConsentDiagnostics;
import de.medizininformatikinitiative.torch.diagnostics.consent.FinalPeriodEvent;
import de.medizininformatikinitiative.torch.diagnostics.consent.RawProvisionEvent;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.BatchExclusions;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.PatientExclusionStage;
import de.medizininformatikinitiative.torch.jobhandling.DefaultFileIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static de.medizininformatikinitiative.torch.TestUtils.concat;
import static de.medizininformatikinitiative.torch.TestUtils.readMergedDiagnostics;
import static de.medizininformatikinitiative.torch.diagnostics.DiagnosticsStore.CONSENT_CONSIDERED_RESOURCES_FILE;
import static de.medizininformatikinitiative.torch.diagnostics.DiagnosticsStore.FINAL_PERIODS_FILE;
import static de.medizininformatikinitiative.torch.diagnostics.DiagnosticsStore.PATIENT_EXCLUSIONS_FILE;
import static de.medizininformatikinitiative.torch.diagnostics.DiagnosticsStore.RAW_PROVISIONS_FILE;
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
                COPY_REDACT, 1096L), 5426, 7316, Map.of(GROUP_1, 12));
        var batchExclusions_1 = BatchExclusions.empty();
        batchExclusions_1.addMustHaveExclusionCore(GROUP_1, RESOURCE_1, ATTRIBUTE_1);
        batchExclusions_1.addReferenceNotFoundExclusionCore(GROUP_1, RESOURCE_1);
        batchExclusions_1.addPatientExclusion(PatientExclusionStage.DIRECT_LOAD, PATIENT_1);
        return new BatchDiagnostics(batchExclusions_1, details_1, ConsentAudit.empty(), ConsentDiagnostics.disabled());
    }

    static BatchDiagnostics createDiagnostics_2() {
        var details_2 = new BatchDetails(Map.of(
                DIRECT_LOAD, 4887L,
                REFERENCE_RESOLVE, 3503L,
                CASCADING_DELETE, 6772L,
                COPY_REDACT, 4847L), 8860, 9659, Map.of(GROUP_2, 34));
        var batchExclusions_2 = BatchExclusions.empty();
        batchExclusions_2.addMustHaveExclusionCore(GROUP_2, RESOURCE_2, ATTRIBUTE_2);
        batchExclusions_2.addReferenceNotFoundExclusionCore(GROUP_2, RESOURCE_2);
        batchExclusions_2.addPatientExclusion(PatientExclusionStage.DIRECT_LOAD, PATIENT_2);
        return new BatchDiagnostics(batchExclusions_2, details_2, ConsentAudit.empty(), ConsentDiagnostics.disabled());
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
    void testWriteReadConsentDiagnosticsPerBatch() throws IOException, CsvValidationException {
        var details = new BatchDetails(Map.of(), 1, 1, Map.of());
        var batchExclusions = BatchExclusions.empty();
        batchExclusions.addConsentExclusion(GROUP_1, RESOURCE_1, PATIENT_1, "OUTSIDE_PERIODS");
        var consentDiagnostics = ConsentDiagnostics.create(true);
        consentDiagnostics.addRawProvision(new RawProvisionEvent(PATIENT_1, "consent-1", "code-1", true,
                LocalDate.of(2021, 1, 1), LocalDate.of(2025, 12, 31)));
        consentDiagnostics.addFinalPeriod(new FinalPeriodEvent(PATIENT_1, LocalDate.of(2021, 1, 1), LocalDate.of(2025, 12, 31)));
        consentDiagnostics.addConsideredResource(new ConsentConsideredResourceEvent(
                PATIENT_1, RESOURCE_1, false, "2019-01-01"));
        var diagnostics = new BatchDiagnostics(batchExclusions, details, ConsentAudit.empty(), consentDiagnostics);

        diagnosticsStore.writeDiagnostics(diagnostics, jobDirectory, BATCH_1);
        var result = diagnosticsStore.loadAllDiagnostics(jobDirectory);

        assertThat(result).isEqualTo(Map.of(BATCH_1, diagnostics));
        assertThat(result.get(BATCH_1).batchExclusions().getResourceExclusions())
                .extracting("detail").containsExactly("OUTSIDE_PERIODS");
    }

    @Test
    void testMergedConsentDiagnosticsAreSortedByPatientAndSurviveIntermediateCleanup() throws IOException, CsvValidationException {
        var details = new BatchDetails(Map.of(), 1, 1, Map.of());

        // rows of both patients interleaved within one batch, as recorded concurrently during resource loading
        var consentDiagnostics1 = ConsentDiagnostics.create(true);
        consentDiagnostics1.addRawProvision(new RawProvisionEvent(PATIENT_2, "consent-2", "code-1", true,
                LocalDate.of(2020, 1, 1), LocalDate.of(2024, 12, 31)));
        consentDiagnostics1.addConsideredResource(new ConsentConsideredResourceEvent(PATIENT_2, RESOURCE_2, true, "2022-04-20"));
        consentDiagnostics1.addConsideredResource(new ConsentConsideredResourceEvent(PATIENT_1, RESOURCE_1, false, "2019-01-01"));
        consentDiagnostics1.addConsideredResource(new ConsentConsideredResourceEvent(PATIENT_2, RESOURCE_1, true, ""));
        var diagnostics1 = new BatchDiagnostics(BatchExclusions.empty(), details, ConsentAudit.empty(), consentDiagnostics1);

        var consentDiagnostics2 = ConsentDiagnostics.create(true);
        consentDiagnostics2.addRawProvision(new RawProvisionEvent(PATIENT_1, "consent-1", "code-1", true,
                LocalDate.of(2021, 1, 1), LocalDate.of(2025, 12, 31)));
        consentDiagnostics2.addFinalPeriod(new FinalPeriodEvent(PATIENT_1, LocalDate.of(2021, 1, 1), LocalDate.of(2025, 12, 31)));
        var diagnostics2 = new BatchDiagnostics(BatchExclusions.empty(), details, ConsentAudit.empty(), consentDiagnostics2);

        diagnosticsStore.writeDiagnostics(diagnostics1, jobDirectory, BATCH_1);
        diagnosticsStore.writeDiagnostics(diagnostics2, jobDirectory, BATCH_2);
        diagnosticsStore.writeMergedExclusions(diagnosticsStore.loadAllDiagnostics(jobDirectory), jobDirectory);
        diagnosticsStore.deleteIntermediateDiagnostics(jobDirectory);

        Path reportDir = jobDirectory.resolve(REPORTS_DIRECTORY);
        assertThat(Files.readAllLines(reportDir.resolve(RAW_PROVISIONS_FILE))).containsExactly(
                "\"Batch-ID\",\"Patient-ID\",\"Consent-ID\",\"Code\",\"Permit\",\"Period-Start\",\"Period-End\"",
                "\"batch-id-2\",\"pat-id-1\",\"consent-1\",\"code-1\",\"true\",\"2021-01-01\",\"2025-12-31\"",
                "\"batch-id-1\",\"pat-id-2\",\"consent-2\",\"code-1\",\"true\",\"2020-01-01\",\"2024-12-31\"");
        assertThat(Files.readAllLines(reportDir.resolve(FINAL_PERIODS_FILE))).containsExactly(
                "\"Batch-ID\",\"Patient-ID\",\"Period-Start\",\"Period-End\"",
                "\"batch-id-2\",\"pat-id-1\",\"2021-01-01\",\"2025-12-31\"");
        assertThat(Files.readAllLines(reportDir.resolve(CONSENT_CONSIDERED_RESOURCES_FILE))).containsExactly(
                "\"Batch-ID\",\"Patient-ID\",\"Resource-ID\",\"Included\",\"Date\"",
                "\"batch-id-1\",\"pat-id-1\",\"resource-id-1\",\"false\",\"2019-01-01\"",
                "\"batch-id-1\",\"pat-id-2\",\"resource-id-2\",\"true\",\"2022-04-20\"",
                "\"batch-id-1\",\"pat-id-2\",\"resource-id-1\",\"true\",\"\"");

        assertThat(reportDir.resolve(BATCH_1)).doesNotExist();
        assertThat(reportDir.resolve(BATCH_2)).doesNotExist();
        assertThat(diagnosticsStore.rawProvisionsExists(jobDirectory)).isTrue();
        assertThat(diagnosticsStore.finalPeriodsExists(jobDirectory)).isTrue();
        assertThat(diagnosticsStore.consentConsideredResourcesExists(jobDirectory)).isTrue();
    }

    @Test
    void testMergedConsentDiagnosticsNotWrittenWithoutRows() throws IOException, CsvValidationException {
        diagnosticsStore.writeDiagnostics(createDiagnostics_1(), jobDirectory, BATCH_1);
        diagnosticsStore.writeMergedExclusions(diagnosticsStore.loadAllDiagnostics(jobDirectory), jobDirectory);

        assertThat(diagnosticsStore.resourceExclusionsExists(jobDirectory)).isTrue();
        assertThat(diagnosticsStore.rawProvisionsExists(jobDirectory)).isFalse();
        assertThat(diagnosticsStore.finalPeriodsExists(jobDirectory)).isFalse();
        assertThat(diagnosticsStore.consentConsideredResourcesExists(jobDirectory)).isFalse();
    }

    @Test
    void testLoadAllDiagnosticsToleratesOldFormatBatchDirectory() throws IOException, CsvValidationException {
        String oldBatch = "batch-id-old";
        Path oldBatchDir = jobDirectory.resolve(REPORTS_DIRECTORY).resolve(oldBatch);
        java.nio.file.Files.createDirectories(oldBatchDir);

        // resource-exclusions.csv written before the Detail column existed — 6 columns, not 7
        java.nio.file.Files.writeString(oldBatchDir.resolve(RESOURCE_EXCLUSIONS_FILE),
                "\"Batch-ID\",\"Reason\",\"Group\",\"Attribute\",\"Resource-ID\",\"Patient-ID\"\n"
                        + "\"" + oldBatch + "\",\"CONSENT\",\"" + GROUP_1 + "\",\"\",\"" + RESOURCE_1 + "\",\"" + PATIENT_1 + "\"\n");
        // patient-exclusions.csv format is unchanged by this PR
        java.nio.file.Files.writeString(oldBatchDir.resolve(PATIENT_EXCLUSIONS_FILE),
                "\"Batch-ID\",\"Stage\",\"Patient-ID\"\n"
                        + "\"" + oldBatch + "\",\"DIRECT_LOAD\",\"" + PATIENT_1 + "\"\n");
        new ObjectMapper().writeValue(oldBatchDir.resolve("details.json").toFile(),
                new BatchDetails(Map.of(), 1, 1, Map.of()));
        // raw-provisions.csv / final-periods.csv / consent-considered-resources.csv deliberately absent —
        // this batch directory predates this PR

        var newDiagnostics = createDiagnostics_2();
        diagnosticsStore.writeDiagnostics(newDiagnostics, jobDirectory, BATCH_2);

        var loaded = diagnosticsStore.loadAllDiagnostics(jobDirectory);

        assertThat(loaded).containsKey(oldBatch);
        var oldDiagnostics = loaded.get(oldBatch);
        assertThat(oldDiagnostics.batchExclusions().getResourceExclusions()).singleElement()
                .extracting("detail").isEqualTo("");
        assertThat(oldDiagnostics.consentDiagnostics().isEmpty()).isTrue();

        // merging must not fail just because one batch predates the consent-diagnostics files/column
        diagnosticsStore.writeMergedExclusions(loaded, jobDirectory);

        var mergedExclusions = readMergedDiagnostics(
                jobDirectory.resolve(REPORTS_DIRECTORY).resolve(RESOURCE_EXCLUSIONS_FILE).toFile(),
                jobDirectory.resolve(REPORTS_DIRECTORY).resolve(PATIENT_EXCLUSIONS_FILE).toFile());
        assertThat(mergedExclusions.getResourceExclusions())
                .containsExactlyInAnyOrderElementsOf(
                        concat(oldDiagnostics.batchExclusions().getResourceExclusions(),
                                newDiagnostics.batchExclusions().getResourceExclusions()));

    }

    @Test
    void writeReadJobSummary() throws IOException {
        var summary = JobDiagnosticSummary.initFromBatches(List.of(createDiagnostics_1(), createDiagnostics_2()));

        diagnosticsStore.writeSummary(summary, jobDirectory);
        var result = diagnosticsStore.readSummary(jobDirectory);

        assertThat(result).isEqualTo(summary);
    }

}