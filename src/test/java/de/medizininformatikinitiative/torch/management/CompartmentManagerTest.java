package de.medizininformatikinitiative.torch.management;

import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class CompartmentManagerTest {

    CompartmentManager manager = new CompartmentManager("compartmentdefinition-patient.json");

    CompartmentManagerTest() throws IOException {
    }

    @Nested
    class InCompartment {

        @Test
        public void contained() {
            assertThat(manager.isInCompartment("Patient")).isTrue();
        }

        @Test
        public void notContained() {
            assertThat(manager.isInCompartment("Medication")).isFalse();
        }
    }

    @Nested
    class Resource {
        @Test
        public void contained() {
            assertThat(manager.isInCompartment(new Patient())).isTrue();
        }

        @Test
        public void notContained() {
            assertThat(manager.isInCompartment(new Medication())).isFalse();
        }

    }


    @Test
    public void getCompartmentTest() {
        assertThat(manager.getCompartment()).hasSize(66);
    }
}