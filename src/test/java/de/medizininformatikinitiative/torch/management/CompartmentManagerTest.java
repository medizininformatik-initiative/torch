package de.medizininformatikinitiative.torch.management;

import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
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


    @Nested
    class ResourceGroupRelationTest {
        @Test
        public void contained() {
            assertThat(manager.isInCompartment(new ResourceGroup("Observation/123", "Test"))).isTrue();
        }

        @Test
        public void notContained() {
            assertThat(manager.isInCompartment(new ResourceGroup("Medication/123", "Test"))).isFalse();
        }

    }

    @Nested
    class ReferenceStringTest {
        @Test
        public void contained() {
            assertThat(manager.isInCompartment("Observation/123")).isTrue();
        }

        @Test
        public void notContained() {
            assertThat(manager.isInCompartment("Medication/123")).isFalse();
        }

    }


    @Test
    public void getCompartmentTest() {
        assertThat(manager.getCompartment()).hasSize(66);
    }
}
