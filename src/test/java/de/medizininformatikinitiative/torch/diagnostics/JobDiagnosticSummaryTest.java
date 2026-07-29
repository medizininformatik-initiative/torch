package de.medizininformatikinitiative.torch.diagnostics;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.diagnostics.PipelineStage;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.PatientExclusionStage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JobDiagnosticSummaryTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void initFromBatches_aggregatesEachResourceExclusionReasonSeparately() throws Exception {
        BatchDiagnostics diagnostics = BatchDiagnostics.empty();
        diagnostics.batchExclusions().addMustHaveExclusion("grp", "Observation/1", "Obs.code", "pat1");
        diagnostics.batchExclusions().addMustHaveExclusion("grp", "Observation/2", "Obs.code", "pat2");
        diagnostics.batchExclusions().addConsentExclusion("grp", "Observation/3", "pat3");
        diagnostics.batchExclusions().addReferenceNotFoundExclusion("grp", "Observation/4", "pat4");
        diagnostics.batchExclusions().addResourceOutsideBatch("grp", "Observation/5");
        diagnostics.batchExclusions().addReferenceInvalidExclusion("grp", "Observation/6", "Obs.ref", "pat6");

        JobDiagnosticSummary summary = JobDiagnosticSummary.empty().initFromBatches(List.of(diagnostics));

        // Serialize to inspect the private GroupSummary record without exposing new production API.
        Map<String, Object> json = mapper.convertValue(summary, Map.class);
        Map<String, Object> resourceExclusions = (Map<String, Object>) json.get("Resource-Exclusions");
        Map<String, Object> group = (Map<String, Object>) resourceExclusions.get("grp");

        assertThat((Map<String, Integer>) group.get("Must-Have")).containsEntry("Obs.code", 2);
        assertThat(group.get("Consent")).isEqualTo(1);
        assertThat(group.get("Reference-Not-Found")).isEqualTo(1);
        assertThat(group.get("Resource-Outside-Batch")).isEqualTo(1);
        assertThat(group.get("Reference-Invalid")).isEqualTo(1);
    }

    @Test
    void initFromBatches_countsPatientExclusionsPerStageAcrossBatches() {
        BatchDiagnostics batch1 = BatchDiagnostics.empty();
        batch1.batchExclusions().addPatientExclusion(PatientExclusionStage.CONSENT, "pat1");
        batch1.batchExclusions().addPatientExclusion(PatientExclusionStage.CASCADING_DELETE, "pat2");
        BatchDiagnostics batch2 = BatchDiagnostics.empty();
        batch2.batchExclusions().addPatientExclusion(PatientExclusionStage.CONSENT, "pat3");

        JobDiagnosticSummary summary = JobDiagnosticSummary.empty().initFromBatches(List.of(batch1, batch2));

        assertThat(summary.patientSummaries())
                .containsEntry(PatientExclusionStage.CONSENT, 2)
                .containsEntry(PatientExclusionStage.CASCADING_DELETE, 1)
                .containsEntry(PatientExclusionStage.DIRECT_LOAD, 0);
    }

    @Test
    void initFromBatches_sumsCohortAndFinalPatientCountsAcrossBatches() {
        BatchDiagnostics batch1 = BatchDiagnostics.empty().setNumCohortPatients(5).setFinalPatientCount(3);
        BatchDiagnostics batch2 = BatchDiagnostics.empty().setNumCohortPatients(2).setFinalPatientCount(2);

        JobDiagnosticSummary summary = JobDiagnosticSummary.empty().initFromBatches(List.of(batch1, batch2));

        assertThat(summary.numCohortPatients()).isEqualTo(7);
        assertThat(summary.numFinalPatients()).isEqualTo(5);
    }

    @Test
    void initFromBatches_computesMedianAndAverageDurationPerStage() {
        BatchDiagnostics batch1 = withNanos(PipelineStage.DIRECT_LOAD, 100L);
        BatchDiagnostics batch2 = withNanos(PipelineStage.DIRECT_LOAD, 200L);
        BatchDiagnostics batch3 = withNanos(PipelineStage.DIRECT_LOAD, 300L);

        JobDiagnosticSummary summary = JobDiagnosticSummary.empty().initFromBatches(List.of(batch1, batch2, batch3));

        assertThat(summary.durationSummaries()).containsKey(PipelineStage.DIRECT_LOAD);
    }

    private static BatchDiagnostics withNanos(PipelineStage stage, long nanos) {
        BatchDetails details = new BatchDetails(Map.of(stage, nanos), 0, 0);
        return BatchDiagnostics.empty().setBatchDetails(details);
    }
}
