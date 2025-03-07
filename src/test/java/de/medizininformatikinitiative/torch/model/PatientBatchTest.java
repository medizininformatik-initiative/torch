package de.medizininformatikinitiative.torch.model;

import de.medizininformatikinitiative.torch.model.fhir.QueryParams;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.multiStringValue;
import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.of;
import static org.assertj.core.api.Assertions.assertThat;

class PatientBatchTest {

    @Nested
    class CompartmentSearchParam {

        @Test
        void typePatient() {
            PatientBatch batch = PatientBatch.of("A");

            QueryParams result = batch.compartmentSearchParam("Patient");

            assertThat(result).isEqualTo(of("_id", multiStringValue("A")));
        }

        @Test
        void typeObservation() {
            PatientBatch batch = PatientBatch.of("A");

            QueryParams result = batch.compartmentSearchParam("Observation");

            assertThat(result).isEqualTo(of("patient", multiStringValue("Patient/A")));
        }

        @Test
        void multiPatientTypeObservation() {
            PatientBatch batch = PatientBatch.of("A", "B");

            QueryParams result = batch.compartmentSearchParam("Observation");

            assertThat(result).isEqualTo(of("patient", multiStringValue("Patient/A", "Patient/B")));
        }
    }

    @Nested
    class Split {

        @Test
        void emptyBatch() {
            PatientBatch originalBatch = PatientBatch.of();

            List<PatientBatch> batches = originalBatch.split(5);

            assertThat(batches).isEmpty();
        }

        @Test
        void sizeLessThanBatchSize() {
            PatientBatch originalBatch = PatientBatch.of("A", "B", "C");

            List<PatientBatch> batches = originalBatch.split(5);

            assertThat(batches).containsExactly(originalBatch);
        }

        @Test
        void sizeEqualBatchSize() {
            PatientBatch originalBatch = PatientBatch.of("A", "B", "C", "D", "E");

            List<PatientBatch> batches = originalBatch.split(5);

            assertThat(batches).containsExactly(originalBatch);
        }

        @Test
        void sizeGreaterThanBatchSize() {
            PatientBatch originalBatch = PatientBatch.of("A", "B", "C", "D", "E");

            List<PatientBatch> batches = originalBatch.split(3);

            assertThat(batches).containsExactly(
                    PatientBatch.of("A", "B", "C"), PatientBatch.of("D", "E")
            );
        }
    }
}