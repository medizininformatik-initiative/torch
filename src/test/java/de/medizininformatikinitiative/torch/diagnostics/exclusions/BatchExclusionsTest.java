package de.medizininformatikinitiative.torch.diagnostics.exclusions;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BatchExclusionsTest {

    @Test
    void isEmpty_trueWhenNoExclusionsRecorded() {
        assertThat(BatchExclusions.empty().isEmpty()).isTrue();
    }

    @Test
    void isEmpty_falseAfterResourceExclusion() {
        BatchExclusions batchExclusions = BatchExclusions.empty();
        batchExclusions.addMustHaveExclusionCore("grp", "Observation/1", "Obs.code");

        assertThat(batchExclusions.isEmpty()).isFalse();
    }

    @Test
    void isEmpty_falseAfterPatientExclusion() {
        BatchExclusions batchExclusions = BatchExclusions.empty();
        batchExclusions.addPatientExclusion(PatientExclusionStage.CONSENT, "pat1");

        assertThat(batchExclusions.isEmpty()).isFalse();
    }

    @Test
    void addReferenceInvalidExclusionCore_addsResourceExclusion() {
        BatchExclusions batchExclusions = BatchExclusions.empty();
        batchExclusions.addReferenceInvalidExclusionCore("grp", "Observation/1", "Obs.ref");

        assertThat(batchExclusions.getResourceExclusions())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.reason()).isEqualTo(ResourceExclusionReason.REFERENCE_INVALID);
                    assertThat(event.groupId()).isEqualTo("grp");
                    assertThat(event.resourceId()).isEqualTo("Observation/1");
                    assertThat(event.attributeRef()).isEqualTo("Obs.ref");
                });
    }
}
