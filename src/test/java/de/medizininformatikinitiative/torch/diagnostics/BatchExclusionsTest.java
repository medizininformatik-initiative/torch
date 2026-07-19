package de.medizininformatikinitiative.torch.diagnostics;

import de.medizininformatikinitiative.torch.diagnostics.exclusions.BatchExclusions;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.PatientExclusionEvent;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.ResourceExclusionEvent;
import org.junit.jupiter.api.Test;

import static de.medizininformatikinitiative.torch.diagnostics.exclusions.PatientExclusionStage.DIRECT_LOAD;
import static de.medizininformatikinitiative.torch.diagnostics.exclusions.ResourceExclusionReason.MUST_HAVE;
import static org.assertj.core.api.Assertions.assertThat;

public class BatchExclusionsTest {
    private static final ResourceExclusionEvent RESOURCE_EXCLUSION_1 =
            new ResourceExclusionEvent(MUST_HAVE, "group-1", "resource-1", "", "");
    private static final ResourceExclusionEvent RESOURCE_EXCLUSION_2 =
            new ResourceExclusionEvent(MUST_HAVE, "group-2", "resource-2", "", "");

    private static final PatientExclusionEvent PATIENT_EXCLUSION_1 = new PatientExclusionEvent(DIRECT_LOAD, "pat-1");
    private static final PatientExclusionEvent PATIENT_EXCLUSION_2 = new PatientExclusionEvent(DIRECT_LOAD, "pat-2");

    @Test
    void testAddResourceExclusion() {
        var batchExclusions = BatchExclusions.empty();

        batchExclusions.addResourceExclusion(RESOURCE_EXCLUSION_1);
        batchExclusions.addResourceExclusion(RESOURCE_EXCLUSION_2);

        assertThat(batchExclusions.getResourceExclusions()).containsExactlyInAnyOrder(RESOURCE_EXCLUSION_1, RESOURCE_EXCLUSION_2);
    }

    @Test
    void testAddPatientExclusion() {
        var batchExclusions = BatchExclusions.empty();

        batchExclusions.addPatientExclusion(PATIENT_EXCLUSION_1);
        batchExclusions.addPatientExclusion(PATIENT_EXCLUSION_2);

        assertThat(batchExclusions.getPatientExclusions()).containsExactlyInAnyOrder(PATIENT_EXCLUSION_1, PATIENT_EXCLUSION_2);
    }

    @Test
    void testEquals_differentReferencesSameExclusions() {
        var A = BatchExclusions.empty();
        var B = BatchExclusions.empty();
        A.addResourceExclusion(RESOURCE_EXCLUSION_1);
        A.addResourceExclusion(RESOURCE_EXCLUSION_2);
        A.addPatientExclusion(PATIENT_EXCLUSION_1);
        A.addPatientExclusion(PATIENT_EXCLUSION_2);
        B.addResourceExclusion(RESOURCE_EXCLUSION_1);
        B.addResourceExclusion(RESOURCE_EXCLUSION_2);
        B.addPatientExclusion(PATIENT_EXCLUSION_1);
        B.addPatientExclusion(PATIENT_EXCLUSION_2);

        assertThat(A).isEqualTo(B);
    }

    @Test
    void testEquals_differentPatientExclusions() {
        var A = BatchExclusions.empty();
        var B = BatchExclusions.empty();
        A.addPatientExclusion(PATIENT_EXCLUSION_1);
        B.addPatientExclusion(PATIENT_EXCLUSION_2);

        assertThat(A).isNotEqualTo(B);
    }

    @Test
    void testEquals_differentResourceExclusions() {
        var A = BatchExclusions.empty();
        var B = BatchExclusions.empty();
        A.addResourceExclusion(RESOURCE_EXCLUSION_1);
        B.addResourceExclusion(RESOURCE_EXCLUSION_2);

        assertThat(A).isNotEqualTo(B);
    }

    @Test
    void testEquals_sameReference() {
        var A = BatchExclusions.empty();
        A.addResourceExclusion(RESOURCE_EXCLUSION_1);
        A.addResourceExclusion(RESOURCE_EXCLUSION_2);
        A.addPatientExclusion(PATIENT_EXCLUSION_1);
        A.addPatientExclusion(PATIENT_EXCLUSION_2);

        assertThat(A).isEqualTo(A);
    }



    @Test
    void testNotEquals_otherType() {
        var A = BatchExclusions.empty();
        var B = "some-string";

        assertThat(A).isNotEqualTo(B);
    }

    @Test
    void testHashCode() {
        var A = BatchExclusions.empty();
        var B = BatchExclusions.empty();
        A.addResourceExclusion(RESOURCE_EXCLUSION_1);
        A.addResourceExclusion(RESOURCE_EXCLUSION_2);
        A.addPatientExclusion(PATIENT_EXCLUSION_1);
        A.addPatientExclusion(PATIENT_EXCLUSION_2);
        B.addResourceExclusion(RESOURCE_EXCLUSION_1);
        B.addResourceExclusion(RESOURCE_EXCLUSION_2);
        B.addPatientExclusion(PATIENT_EXCLUSION_1);
        B.addPatientExclusion(PATIENT_EXCLUSION_2);

        assertThat(A.hashCode()).isEqualTo(B.hashCode());
    }

    @Test
    void testIsEmpty() {
        var A = BatchExclusions.empty();

        assertThat(A.isEmpty()).isTrue();
    }
}
