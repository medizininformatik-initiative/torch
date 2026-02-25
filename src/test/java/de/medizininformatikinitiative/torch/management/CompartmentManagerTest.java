package de.medizininformatikinitiative.torch.management;

import de.medizininformatikinitiative.torch.model.extraction.ExtractionId;
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
            assertThat(manager.isInCompartment(new ExtractionId("Patient", "123"))).isTrue();
        }

        @Test
        public void notContained() {
            assertThat(manager.isInCompartment(new ExtractionId("Medication", "123"))).isFalse();
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
            assertThat(manager.isInCompartment(new ResourceGroup(new ExtractionId("Patient", "123"), "Test"))).isTrue();
        }

        @Test
        public void notContained() {
            assertThat(manager.isInCompartment(new ResourceGroup(new ExtractionId("Medication", "123"), "Test"))).isFalse();
        }

    }

    @Test
    public void getCompartmentTest() {
        assertThat(manager.getCompartment()).hasSize(66);
    }
}
