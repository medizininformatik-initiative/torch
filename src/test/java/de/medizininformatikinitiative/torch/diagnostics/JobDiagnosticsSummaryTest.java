package de.medizininformatikinitiative.torch.diagnostics;

import de.medizininformatikinitiative.torch.diagnostics.exclusions.PatientExclusionStage;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static de.medizininformatikinitiative.torch.TestUtils.toMillis;
import static de.medizininformatikinitiative.torch.diagnostics.PipelineStage.CASCADING_DELETE;
import static de.medizininformatikinitiative.torch.diagnostics.PipelineStage.DIRECT_LOAD;
import static org.assertj.core.api.Assertions.assertThat;

public class JobDiagnosticsSummaryTest {

    private static final int NUM_1 = 5;
    private static final int NUM_2 = 10;

    @Nested
    class TestNanosMeasurements {

        private static final long NANOS_1 = 10L;
        private static final long NANOS_3 = 30L;
        private static final long NANOS_2 = 20L;
        private static final long NANOS_4 = 40L;
        private static final long NANOS_5 = 50L;
        private static final long NANOS_6 = 60L;

        @Test
        void testAlLStagesSame() {
            var batch1 = fillNanosForAllStages(BatchDiagnostics.empty(), NANOS_1);
            var batch2 = fillNanosForAllStages(BatchDiagnostics.empty(), NANOS_2);
            var batch3 = fillNanosForAllStages(BatchDiagnostics.empty(),  NANOS_3);

            var summary  = JobDiagnosticSummary.initFromBatches(List.of(batch1, batch2, batch3));

            assertThat(summary.durationSummaries().keySet()).containsExactlyInAnyOrder(PipelineStage.values());
            assertThat(summary.durationSummaries().values()).allMatch(durationSummary ->
                    durationSummary.medianMs().equals(toMillis(NANOS_2)) &&
                    durationSummary.averageMs().equals(toMillis((NANOS_1+NANOS_2+NANOS_3)/3)));
        }

        @Test
        void testSameStageDiffers() {
            var batch1 = BatchDiagnostics.empty();
            var batch2 = BatchDiagnostics.empty();
            var batch3 = BatchDiagnostics.empty();
            batch1.batchDetails().nanosElapsed().put(DIRECT_LOAD, NANOS_1);
            batch2.batchDetails().nanosElapsed().put(DIRECT_LOAD, NANOS_2);
            batch3.batchDetails().nanosElapsed().put(DIRECT_LOAD, NANOS_3);
            batch1.batchDetails().nanosElapsed().put(CASCADING_DELETE, NANOS_4);
            batch2.batchDetails().nanosElapsed().put(CASCADING_DELETE, NANOS_5);
            batch3.batchDetails().nanosElapsed().put(CASCADING_DELETE, NANOS_6);

            var summary  = JobDiagnosticSummary.initFromBatches(List.of(batch1, batch2, batch3));

            assertThat(summary.durationSummaries().get(DIRECT_LOAD).averageMs()).isEqualTo(toMillis((NANOS_1+NANOS_2+NANOS_3)/3));
            assertThat(summary.durationSummaries().get(DIRECT_LOAD).medianMs()).isEqualTo(toMillis(NANOS_2));
            assertThat(summary.durationSummaries().get(CASCADING_DELETE).averageMs()).isEqualTo(toMillis((NANOS_4+NANOS_5+NANOS_6)/3));
            assertThat(summary.durationSummaries().get(CASCADING_DELETE).medianMs()).isEqualTo(toMillis(NANOS_5));
        }

        private static BatchDiagnostics fillNanosForAllStages(BatchDiagnostics diagnostics, long nanos) {
            Arrays.stream(PipelineStage.values()).forEach(stage -> {
                diagnostics.batchDetails().nanosElapsed().put(stage, nanos);
            });
            return diagnostics;
        }
    }

    @Nested
    class TestResourceSummaries {
        private static final String GROUP_1 = "group-1";
        private static final String RESOURCE_1 = "resource-1";
        private static final String PATIENT_1 = "patient-1";
        private static final String ATTRIBUTE_1 = "attribute-1";
        private static final String GROUP_2 = "group-2";
        private static final String RESOURCE_2 = "resource-2";
        private static final String PATIENT_2 = "patient-2";
        private static final String ATTRIBUTE_2 = "attribute-2";
        private static final int NUM_3 = 15;
        private static final int NUM_4 = 20;

        @Nested
        class TestMustHaveExclusions {
            private void increaseMustHaveBy(int num, BatchDiagnostics diagnostics, String groupId, String resourceId,
                                            String attributeRef, String patientId) {
                for(int i = 0; i < num; i++) {
                    diagnostics.batchExclusions().addMustHaveExclusion(groupId, resourceId, attributeRef, patientId);
                }
            }

            @Test
            void testSameGroupDifferentAttributes() {
                var batch1 = BatchDiagnostics.empty();
                var batch2 = BatchDiagnostics.empty();
                increaseMustHaveBy(NUM_1, batch1, GROUP_1, RESOURCE_1, ATTRIBUTE_1, PATIENT_1);
                increaseMustHaveBy(NUM_2, batch1, GROUP_1, RESOURCE_1, ATTRIBUTE_2, PATIENT_1);
                increaseMustHaveBy(NUM_3, batch2, GROUP_1, RESOURCE_1, ATTRIBUTE_1, PATIENT_1);
                increaseMustHaveBy(NUM_4, batch2, GROUP_1, RESOURCE_1, ATTRIBUTE_2, PATIENT_1);

                var summary  = JobDiagnosticSummary.initFromBatches(List.of(batch1, batch2));

                assertThat(summary.resourceSummaries()).hasSize(1);
                assertThat(summary.resourceSummaries().get(GROUP_1).mustHaveExclusions()).hasSize(2);
                assertThat(summary.resourceSummaries().get(GROUP_1).mustHaveExclusions().get(ATTRIBUTE_1)).isEqualTo(NUM_1+NUM_3);
                assertThat(summary.resourceSummaries().get(GROUP_1).mustHaveExclusions().get(ATTRIBUTE_2)).isEqualTo(NUM_2+NUM_4);
            }

            @Test
            void testDifferentGroupSameAttributes() {
                var batch1 = BatchDiagnostics.empty();
                var batch2 = BatchDiagnostics.empty();
                increaseMustHaveBy(NUM_1, batch1, GROUP_1, RESOURCE_1, ATTRIBUTE_1, PATIENT_1);
                increaseMustHaveBy(NUM_2, batch1, GROUP_2, RESOURCE_1, ATTRIBUTE_1, PATIENT_1);
                increaseMustHaveBy(NUM_3, batch2, GROUP_1, RESOURCE_1, ATTRIBUTE_1, PATIENT_1);
                increaseMustHaveBy(NUM_4, batch2, GROUP_2, RESOURCE_1, ATTRIBUTE_1, PATIENT_1);

                var summary  = JobDiagnosticSummary.initFromBatches(List.of(batch1, batch2));

                assertThat(summary.resourceSummaries()).hasSize(2);
                assertThat(summary.resourceSummaries().get(GROUP_1).mustHaveExclusions()).hasSize(1);
                assertThat(summary.resourceSummaries().get(GROUP_2).mustHaveExclusions()).hasSize(1);
                assertThat(summary.resourceSummaries().get(GROUP_1).mustHaveExclusions().get(ATTRIBUTE_1)).isEqualTo(NUM_1+NUM_3);
                assertThat(summary.resourceSummaries().get(GROUP_2).mustHaveExclusions().get(ATTRIBUTE_1)).isEqualTo(NUM_2+NUM_4);
            }

            @Test
            void testDifferentResourceSameAttribute() {
                var batch1 = BatchDiagnostics.empty();
                var batch2 = BatchDiagnostics.empty();
                increaseMustHaveBy(NUM_1, batch1, GROUP_1, RESOURCE_1, ATTRIBUTE_1, PATIENT_1);
                increaseMustHaveBy(NUM_2, batch1, GROUP_1, RESOURCE_2, ATTRIBUTE_1, PATIENT_1);

                var summary  = JobDiagnosticSummary.initFromBatches(List.of(batch1, batch2));

                assertThat(summary.resourceSummaries()).hasSize(1);
                assertThat(summary.resourceSummaries().get(GROUP_1).mustHaveExclusions()).hasSize(1);
                assertThat(summary.resourceSummaries().get(GROUP_1).mustHaveExclusions().get(ATTRIBUTE_1)).isEqualTo(NUM_1+NUM_2);
            }

            @Test
            void testDifferentPatientSameAttribute() {
                var batch1 = BatchDiagnostics.empty();
                var batch2 = BatchDiagnostics.empty();
                increaseMustHaveBy(NUM_1, batch1, GROUP_1, RESOURCE_1, ATTRIBUTE_1, PATIENT_1);
                increaseMustHaveBy(NUM_2, batch1, GROUP_1, RESOURCE_2, ATTRIBUTE_1, PATIENT_2);

                var summary  = JobDiagnosticSummary.initFromBatches(List.of(batch1, batch2));

                assertThat(summary.resourceSummaries()).hasSize(1);
                assertThat(summary.resourceSummaries().get(GROUP_1).mustHaveExclusions()).hasSize(1);
                assertThat(summary.resourceSummaries().get(GROUP_1).mustHaveExclusions().get(ATTRIBUTE_1)).isEqualTo(NUM_1+NUM_2);
            }
        }

        @Test
        void TestOtherResourceExclusions() {
            var batch1 = BatchDiagnostics.empty();
            var batch2 = BatchDiagnostics.empty();
            increaseOtherResourceExclusionsBy(NUM_1, batch1, GROUP_1, RESOURCE_1, PATIENT_1);
            increaseOtherResourceExclusionsBy(NUM_2, batch2, GROUP_1, RESOURCE_1, PATIENT_1);

            var summary  = JobDiagnosticSummary.initFromBatches(List.of(batch1, batch2));

            assertThat(summary.resourceSummaries()).hasSize(1);
            assertThat(summary.resourceSummaries().get(GROUP_1).consentExclusions()).isEqualTo(NUM_1+NUM_2);
            assertThat(summary.resourceSummaries().get(GROUP_1).refNotFoundExclusions()).isEqualTo(2*(NUM_1+NUM_2));
            assertThat(summary.resourceSummaries().get(GROUP_1).resOutsideBatchExclusions()).isEqualTo(NUM_1+NUM_2);
        }

        private void increaseOtherResourceExclusionsBy(int num, BatchDiagnostics diagnostics, String groupId, String resourceId,
                                               String patientId) {
            for(int i = 0; i < num; i++) {
                diagnostics.batchExclusions().addConsentExclusion(groupId, resourceId, patientId);
                diagnostics.batchExclusions().addReferenceNotFoundExclusion(groupId, resourceId, patientId);
                diagnostics.batchExclusions().addReferenceNotFoundExclusionCore(groupId, resourceId);
                diagnostics.batchExclusions().addResourceOutsideBatch(groupId, resourceId);
            }
        }
    }

    @Test
    void testResourceSummaries_allStagesTwoPatients() {
        var batch1 = BatchDiagnostics.empty();
        var batch2 = BatchDiagnostics.empty();
        batch1.batchExclusions().addPatientExclusion(PatientExclusionStage.CONSENT_FETCH, "pat-1");
        batch1.batchExclusions().addPatientExclusion(PatientExclusionStage.DIRECT_LOAD,  "pat-2");
        batch1.batchExclusions().addPatientExclusion(PatientExclusionStage.CASCADING_DELETE, "pat-3");
        batch2.batchExclusions().addPatientExclusion(PatientExclusionStage.CONSENT_FETCH, "pat-4");
        batch2.batchExclusions().addPatientExclusion(PatientExclusionStage.DIRECT_LOAD, "pat-5");
        batch2.batchExclusions().addPatientExclusion(PatientExclusionStage.CASCADING_DELETE, "pat-6");

        var summary  = JobDiagnosticSummary.initFromBatches(List.of(batch1, batch2));

        assertThat(summary.patientSummaries().keySet()).containsExactlyInAnyOrder(PatientExclusionStage.values());
        assertThat(summary.patientSummaries().values()).allMatch(count -> count==2);
    }

    @Test
    void testNumCohortPatients() {
        var batch1 = BatchDiagnostics.empty().setNumCohortPatients(NUM_1);
        var batch2 = BatchDiagnostics.empty().setNumCohortPatients(NUM_2);

        var summary = JobDiagnosticSummary.initFromBatches(List.of(batch1, batch2));

        assertThat(summary.numCohortPatients()).isEqualTo(NUM_1+NUM_2);
        assertThat(summary.numFinalPatients()).isEqualTo(0);
    }

    @Test
    void testNumFinalPatients() {
        var batch1 = BatchDiagnostics.empty().setFinalPatientCount(NUM_1);
        var batch2 = BatchDiagnostics.empty().setFinalPatientCount(NUM_2);

        var summary = JobDiagnosticSummary.initFromBatches(List.of(batch1, batch2));

        assertThat(summary.numCohortPatients()).isEqualTo(0);
        assertThat(summary.numFinalPatients()).isEqualTo(NUM_1+NUM_2);
    }


}
