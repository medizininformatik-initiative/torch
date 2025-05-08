package de.medizininformatikinitiative.torch.assertions;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.ResourceType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static de.medizininformatikinitiative.torch.assertions.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

public class BundleAssertTest {

    @Nested
    class TestContainsNEntries {

        @Test
        void testBundleWithoutEntries() {
            Bundle bundle = new Bundle();

            assertThat(bundle).containsNEntries(0);
        }

        @Test
        void testFail() {
            Bundle bundle = new Bundle();

            assertThatThrownBy(() -> assertThat(bundle).containsNEntries(1))
                    .isInstanceOf(AssertionError.class)
                    .hasMessage("Expected bundle to contain 1 entries, but found 0");
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 2, 5, 15})
        void testBundleWithNEntries(int n) {
            Bundle bundle = new Bundle();
            for (int i = 0; i < n; i++) {
                bundle.addEntry();
            }

            assertThat(bundle).containsNEntries(n);
        }
    }

    @Nested
    class TestExtractResources {
        Condition CONDITION = new Condition();
        Observation OBSERVATION = new Observation();

        @Test
        void testExtract_twoResources() {
            Bundle bundle = new Bundle().setEntry(List.of(
                    new Bundle.BundleEntryComponent().setResource(CONDITION),
                    new Bundle.BundleEntryComponent().setResource(OBSERVATION)));

            assertThat(bundle).extractResources().containsExactlyInAnyOrder(CONDITION, OBSERVATION);
        }

        @Test
        void testExtract_zeroResources() {
            Bundle bundle = new Bundle().setEntry(List.of());

            assertThat(bundle).extractResources().isEmpty();
        }

        @Test
        void testExtractByPredicate() {
            Bundle bundle = new Bundle().setEntry(List.of(
                    new Bundle.BundleEntryComponent().setResource(CONDITION),
                    new Bundle.BundleEntryComponent().setResource(OBSERVATION)));

            assertThat(bundle).extractResources(r -> r.getResourceType().equals(ResourceType.Observation)).containsExactlyInAnyOrder(OBSERVATION);
        }

        @Test
        void testExtractByPredicate_zeroResources() {
            Bundle bundle = new Bundle().setEntry(List.of());

            assertThat(bundle).extractResources(r -> r.getResourceType().equals(ResourceType.Observation)).isEmpty();
        }
    }

    @Nested
    class TestExtractResourcesById {
        String CONDITION_ID = "cond-id-123";
        String OBSERVATION_ID = "obs-id-456";
        Condition CONDITION = (Condition) new Condition().setId(CONDITION_ID);
        Observation OBSERVATION = (Observation) new Observation().setId(OBSERVATION_ID);

        @Test
        void testExtract_twoResources() {
            Bundle bundle = new Bundle().setEntry(List.of(
                    new Bundle.BundleEntryComponent().setResource(CONDITION),
                    new Bundle.BundleEntryComponent().setResource(OBSERVATION)));

            assertThat(bundle).extractResourceById("Condition", CONDITION_ID).isEqualTo(CONDITION);
            assertThat(bundle).extractResourceById("Observation", OBSERVATION_ID).isEqualTo(OBSERVATION);
        }

        @Test
        void testExtract_zeroResources() {
            Bundle bundle = new Bundle().setEntry(List.of());

            assertThatThrownBy(() -> assertThat(bundle).extractResourceById("Condition", CONDITION_ID))
                    .hasMessage("Expected bundle to contain resource of type Condition and with id %s, but it could not be found".formatted(CONDITION_ID));
        }

        @Test
        void testExtract_wrongRersourceType() {
            Bundle bundle = new Bundle().setEntry(List.of(new Bundle.BundleEntryComponent().setResource(CONDITION)));

            assertThatThrownBy(() -> assertThat(bundle).extractResourceById("Observation", CONDITION_ID))
                    .hasMessage("Expected bundle to contain resource of type Observation and with id %s, but it could not be found".formatted(CONDITION_ID));
        }

        @Test
        void testExtract_wrongId() {
            Bundle bundle = new Bundle().setEntry(List.of(new Bundle.BundleEntryComponent().setResource(CONDITION)));

            assertThatThrownBy(() -> assertThat(bundle).extractResourceById("Condition", OBSERVATION_ID))
                    .hasMessage("Expected bundle to contain resource of type Condition and with id %s, but it could not be found".formatted(OBSERVATION_ID));
        }
    }

    @Nested
    class TestExtractOnlyPatient {
        Condition CONDITION = new Condition();
        Patient PATIENT = new Patient();

        @Test
        void testExtract_simple() {
            Bundle bundle = new Bundle().setEntry(List.of(
                    new Bundle.BundleEntryComponent().setResource(CONDITION),
                    new Bundle.BundleEntryComponent().setResource(PATIENT)));

            assertThat(bundle).extractOnlyPatient().isNotNull();
        }
    }

    @Nested
    class TestExtractResourcesByType {
        Condition CONDITION = new Condition();
        Observation OBSERVATION = new Observation();

        @Test
        void testExtract_twoResources() {
            Bundle bundle = new Bundle().setEntry(List.of(
                    new Bundle.BundleEntryComponent().setResource(CONDITION),
                    new Bundle.BundleEntryComponent().setResource(OBSERVATION)));

            assertThat(bundle).extractResourcesByType(ResourceType.Observation).hasSize(1).first().isEqualTo(OBSERVATION);
        }

        @Test
        void testExtract_wrongType() {
            Bundle bundle = new Bundle().setEntry(List.of(
                    new Bundle.BundleEntryComponent().setResource(CONDITION)));

            assertThat(bundle).extractResourcesByType(ResourceType.Observation).isEmpty();
        }
    }
 }
