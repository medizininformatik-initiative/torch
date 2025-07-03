package de.medizininformatikinitiative.torch.util;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.Factory;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for the FhirPathBuilder class.
 */
@ExtendWith(MockitoExtension.class)
class FhirPathBuilderTest {


    @Mock
    private Slicing slicing;

    @Mock
    private Factory factory;


    private static CompiledStructureDefinition definition;

    @BeforeAll
    static void setup() {
        StructureDefinition structureDefinition = new StructureDefinition();
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot = structureDefinition.getSnapshot();
        ElementDefinition elementDefinition = new ElementDefinition(new StringType("Patient.contact"));
        elementDefinition.setId("Patient.contact");
        snapshot.addElement(elementDefinition);
        definition = CompiledStructureDefinition.fromStructureDefinition(structureDefinition);

    }

    @Test
    void testNoSlicing() {
        String input = "Observation.identifier.type.coding";
        String expected = "Observation.identifier.type.coding";

        String[] result = FhirPathBuilder.handleSlicingForFhirPath(input, definition);

        assertThat(result).contains(expected, expected);
    }


    @Test
    void testEmptyString() {
        assertThatThrownBy(() -> FhirPathBuilder.handleSlicingForFhirPath("", definition)).isInstanceOf(IllegalArgumentException.class);

    }

    @Test
    void testNullInput() {
        assertThatThrownBy(() -> FhirPathBuilder.handleSlicingForFhirPath(null, definition)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testSlicingWithChoiceElements() throws FHIRException {
        String input = "Observation.value[x]:valueQuantity.code";
        String expected = "Observation.value.ofType(Quantity).code";


        String[] result = FhirPathBuilder.handleSlicingForFhirPath(input, definition);

        assertThat(result).contains(expected, expected);

    }

    @Test
    void testSlicingWithUnknownSlice() throws FHIRException {
        String input = "Observation.value[x]:unknownSlice.code";

        assertThatThrownBy(() -> FhirPathBuilder.handleSlicingForFhirPath(input, definition)).isInstanceOf(FHIRException.class);


    }

    
    @Test
    void testBuildConditions_NoConditions() {
        String path = "Patient.name";
        List<String> conditions = Collections.emptyList();
        String expected = "Patient.name";

        String result = FhirPathBuilder.buildConditions(path, conditions);

        assertEquals(expected, result, "When no conditions are provided, the path should remain unchanged.");
    }

    @Test
    void testBuildConditions_SingleCondition() {
        String path = "Observation.value";
        List<String> conditions = Collections.singletonList("value > 5");
        String expected = "Observation.value.where(value > 5)";

        String result = FhirPathBuilder.buildConditions(path, conditions);

        assertEquals(expected, result, "A single condition should be appended correctly.");
    }

    @Test
    void testBuildConditions_MultipleConditions() {
        String path = "Patient.contact";
        List<String> conditions = Arrays.asList("system = 'phone'", "use = 'home'");
        String expected = "Patient.contact.where(system = 'phone' and use = 'home')";

        String result = FhirPathBuilder.buildConditions(path, conditions);

        assertEquals(expected, result, "Multiple conditions should be combined using 'and' and appended correctly.");
    }

    @Test
    void testBuildConditions_EmptyPath_NoConditions() {
        String path = "";
        List<String> conditions = Collections.emptyList();
        String expected = "";

        String result = FhirPathBuilder.buildConditions(path, conditions);

        assertEquals(expected, result, "When both path and conditions are isEmpty, the method should return an isEmpty string.");
    }

    @Test
    void testBuildConditions_NullPath_NullConditions() {
        String result = FhirPathBuilder.buildConditions(null, null);

        assertNull(result, "When both path and conditions are null, the method should return null.");
    }

    @Test
    void testBuildConditions_NullConditions() {
        String path = "Patient.name";

        String expected = "Patient.name";

        String result = FhirPathBuilder.buildConditions(path, null);

        assertEquals(expected, result, "When conditions are null, the path should remain unchanged.");
    }

    @Test
    void testBuildConditions_NullPath_WithConditions() {
        List<String> conditions = Collections.singletonList("use = 'home'");

        String result = FhirPathBuilder.buildConditions(null, conditions);

        assertNull(result, "When path is null, the method should return null regardless of conditions.");
    }
}
