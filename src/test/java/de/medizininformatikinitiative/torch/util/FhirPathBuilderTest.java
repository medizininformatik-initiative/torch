package de.medizininformatikinitiative.torch.util;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Factory;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the FhirPathBuilder class.
 */
@ExtendWith(MockitoExtension.class)
class FhirPathBuilderTest {


    @Mock
    private Slicing slicing;

    @Mock
    private Factory factory;

    @Mock
    private StructureDefinition.StructureDefinitionSnapshotComponent snapshot;

    @Mock
    private Base baseElement;

    private FhirPathBuilder fhirPathBuilder;

    @BeforeEach
    void setUp() {
        // Initialize the FhirPathBuilder with the mocked handler
        fhirPathBuilder = new FhirPathBuilder(slicing);
    }

    // --- handleSlicingForTerser Tests ---

    @Test
    void testHandleSlicingForTerser_NoSlicing() {
        String input = "Observation.identifier.type.coding";
        String expected = "Observation.identifier.type.coding";

        String result = fhirPathBuilder.handleSlicingForFhirPath(input, snapshot)[1];
        ;

        assertEquals(expected, result, "The FHIRPath should remain unchanged when no slicing is present.");
    }

    @Test
    void testHandleSlicingForTerser_SingleSlicing() {
        String input = "Observation.identifier:slice.type.coding";
        String expected = "Observation.identifier.type.coding";

        String result = fhirPathBuilder.handleSlicingForFhirPath(input, snapshot)[1];

        assertEquals(expected, result, "The slicing should be removed from the FHIRPath.");
    }

    @Test
    void testHandleSlicingForTerser_MultipleSlicing() {
        String input = "Patient.contact:home.telecom:phone.value";
        String expected = "Patient.contact.telecom.value";

        String result = fhirPathBuilder.handleSlicingForFhirPath(input, snapshot)[1];

        assertEquals(expected, result, "All slicing indicators should be removed from the FHIRPath.");
    }

    @Test
    void testHandleSlicingForTerser_SlicingAtEnd() {
        String input = "Encounter.participant:practitioner";
        String expected = "Encounter.participant";

        String result = fhirPathBuilder.handleSlicingForFhirPath(input, snapshot)[1];
        ;

        assertEquals(expected, result, "Slicing at the end should be removed correctly.");
    }


    @Test
    void testHandleSlicingForTerser_EmptyString() {
        String input = "";
        String expected = "";

        String result = fhirPathBuilder.handleSlicingForFhirPath(input, snapshot)[1];
        ;

        assertEquals(expected, result, "The method should return an isEmpty string when input is isEmpty.");
    }

    @Test
    void testHandleSlicingForTerser_NullInput() {
        String result = fhirPathBuilder.handleSlicingForFhirPath(null, snapshot)[1];

        assertNull(result, "The method should return null when input is null.");
    }


    @Test
    void testBuildConditions_NoConditions() {
        String path = "Patient.name";
        List<String> conditions = Collections.emptyList();
        String expected = "Patient.name";

        String result = fhirPathBuilder.buildConditions(path, conditions);

        assertEquals(expected, result, "When no conditions are provided, the path should remain unchanged.");
    }

    @Test
    void testBuildConditions_SingleCondition() {
        String path = "Observation.value";
        List<String> conditions = Collections.singletonList("value > 5");
        String expected = "Observation.value.where(value > 5)";

        String result = fhirPathBuilder.buildConditions(path, conditions);

        assertEquals(expected, result, "A single condition should be appended correctly.");
    }

    @Test
    void testBuildConditions_MultipleConditions() {
        String path = "Patient.contact";
        List<String> conditions = Arrays.asList("system = 'phone'", "use = 'home'");
        String expected = "Patient.contact.where(system = 'phone' and use = 'home')";

        String result = fhirPathBuilder.buildConditions(path, conditions);

        assertEquals(expected, result, "Multiple conditions should be combined using 'and' and appended correctly.");
    }

    @Test
    void testBuildConditions_EmptyPath_NoConditions() {
        String path = "";
        List<String> conditions = Collections.emptyList();
        String expected = "";

        String result = fhirPathBuilder.buildConditions(path, conditions);

        assertEquals(expected, result, "When both path and conditions are isEmpty, the method should return an isEmpty string.");
    }

    @Test
    void testBuildConditions_NullPath_NullConditions() {
        String result = fhirPathBuilder.buildConditions(null, null);

        assertNull(result, "When both path and conditions are null, the method should return null.");
    }

    @Test
    void testBuildConditions_NullConditions() {
        String path = "Patient.name";

        String expected = "Patient.name";

        String result = fhirPathBuilder.buildConditions(path, null);

        assertEquals(expected, result, "When conditions are null, the path should remain unchanged.");
    }

    @Test
    void testBuildConditions_NullPath_WithConditions() {
        List<String> conditions = Collections.singletonList("use = 'home'");

        String result = fhirPathBuilder.buildConditions(null, conditions);

        assertNull(result, "When path is null, the method should return null regardless of conditions.");
    }

    // --- handleSlicingForFhirPath Tests ---

    @Test
    void testHandleSlicingForFhirPath_NoSlicing() {
        String input = "Patient.name.family";
        String expected = "Patient.name.family";

        String result = fhirPathBuilder.handleSlicingForFhirPath(input, snapshot)[0];

        assertEquals(expected, result, "When no slicing is present, the input should remain unchanged.");

    }

    @Test
    void testHandleSlicingForFhirPath_SlicingWithKnownSlice() throws FHIRException {
        String input = "Observation.value[x]:valueQuantity.code";
        String expected = "Observation.value.ofType(Quantity).code";


        String result = fhirPathBuilder.handleSlicingForFhirPath(input, snapshot)[0];

        assertEquals(expected, result, "The slicing should be handled correctly with known slice and conditions appended.");

    }

    @Test
    void testHandleSlicingForFhirPath_SlicingWithUnknownSlice() throws FHIRException {
        String input = "Observation.value[x]:unknownSlice.code";

        FHIRException exception = assertThrows(FHIRException.class, () -> {
            fhirPathBuilder.handleSlicingForFhirPath(input, snapshot);
        }, "An FHIRException should be thrown for unsupported slicing.");

        assertEquals("Unsupported Choice Slicing unknownSlice", exception.getMessage(),
                "The exception message should match the expected message.");


    }

    @Test
    void testHandleSlicingForFhirPath_HandlingChoiceElements() throws FHIRException {
        String input = "Observation.value[x]:valueString.code";
        String expected = "Observation.value.ofType(string).code";

        String result = fhirPathBuilder.handleSlicingForFhirPath(input, snapshot)[0];

        assertEquals(expected, result, "Choice elements should be handled correctly with conditions appended.");

    }

    @Test
    void testHandleSlicingForFhirPath_MultipleSlicingIndicators() throws FHIRException {
        String input = "Patient.contact:home.telecom:phone";


        // Mock slicing.generateConditionsForFHIRPath for each slicing indicator
        List<String> mockConditions1 = List.of("use = 'home'", "system = 'test'");
        List<String> mockConditions2 = List.of("system = 'phone'");

        // When slicing.generateConditionsForFHIRPath is called with different paths
        when(slicing.generateConditionsForFHIRPath("Patient.contact:home", snapshot)).thenReturn(mockConditions1);
        when(slicing.generateConditionsForFHIRPath("Patient.contact:home.telecom:phone", snapshot)).thenReturn(mockConditions2);

        String result = fhirPathBuilder.handleSlicingForFhirPath(input, snapshot)[0];

        // The buildConditions method combines conditions with 'and'
        String expectedCombined = "Patient.contact.where(use = 'home' and system = 'test').telecom.where(system = 'phone')";

        assertEquals(expectedCombined, result, "Multiple slicing indicators should result in combined conditions.");

    }


    @Test
    void testHandleSlicingForFhirPath_ExtensionWithUrlCondition() throws FHIRException {
        // Input FHIRPath with slicing on 'coding' and 'extension'
        String input = "Condition.code.coding:icd10-gm.extension:Mehrfachcodierungs-Kennzeichen";

        // Expected FHIRPath after handling slicing and extension
        String expected = "Condition.code.coding.where(system = 'icd10-gm').extension.where(url = 'http://example.org/fhir/StructureDefinition/Mehrfachcodierungs-Kennzeichen')";


        // Mocking conditions for slicing 'coding:icd10-gm'
        List<String> mockConditionsCoding = Collections.singletonList("system = 'icd10-gm'");
        when(slicing.generateConditionsForFHIRPath("Condition.code.coding:icd10-gm", snapshot)).thenReturn(mockConditionsCoding);

        // Mocking conditions for slicing 'extension:Mehrfachcodierungs-Kennzeichen'
        List<String> mockConditionsExtension = Collections.singletonList("url = 'http://example.org/fhir/StructureDefinition/Mehrfachcodierungs-Kennzeichen'");
        when(slicing.generateConditionsForFHIRPath("Condition.code.coding:icd10-gm.extension:Mehrfachcodierungs-Kennzeichen", snapshot)).thenReturn(mockConditionsExtension);


        // Call the method under test
        String result = fhirPathBuilder.handleSlicingForFhirPath(input, snapshot)[0];

        // Assert the final result
        assertEquals(expected, result, "The FHIRPath should correctly handle slicing on 'coding' and 'extension' with URL conditions.");

        // Verify no interactions with factory
        verifyNoInteractions(factory);
    }


    @Test
    void testHandleSlicingForFhirPath_EmptyString() {
        String input = "";
        String expected = "";

        String result = fhirPathBuilder.handleSlicingForFhirPath(input, snapshot)[0];

        assertEquals(expected, result, "The method should return an isEmpty string when input is isEmpty.");

        // Verify that slicing and factory are not interacted with
        verifyNoInteractions(slicing, factory);
    }

    @Test
    void testHandleSlicingForFhirPath_NullInput() {
        String result = fhirPathBuilder.handleSlicingForFhirPath(null, snapshot)[0];

        assertNull(result, "The method should return null when input is null.");

        // Verify that slicing and factory are not interacted with
        verifyNoInteractions(slicing, factory);
    }
}
