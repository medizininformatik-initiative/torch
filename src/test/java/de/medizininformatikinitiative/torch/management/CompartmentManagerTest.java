package de.medizininformatikinitiative.torch.management;

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


    @Test
    public void getCompartmentTest() {
        assertThat(manager.getCompartment()).hasSize(66);
    }
}