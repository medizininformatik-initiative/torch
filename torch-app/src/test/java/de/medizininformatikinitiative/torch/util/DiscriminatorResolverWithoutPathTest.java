package de.medizininformatikinitiative.torch.util;

import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.ElementDefinition.DiscriminatorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the DiscriminatorResolver class.
 */
@ExtendWith(MockitoExtension.class)
class DiscriminatorResolverWithoutPathTest {

    @Mock
    ElementDefinition.ElementDefinitionSlicingDiscriminatorComponent discriminatorMock;

    @Mock
    StructureDefinition.StructureDefinitionSnapshotComponent snapshotMock;

    /**
     * Helper method to create an ElementDefinition with a fixed value.
     *
     * @param id          The ID of the ElementDefinition.
     * @param fixedValue  The fixed value to set.
     * @return A configured ElementDefinition instance.
     */
    private ElementDefinition createElementWithFixedValue(String id, String fixedValue) {
        ElementDefinition element = new ElementDefinition();
        element.setId(id);
        element.setFixed(new StringType(fixedValue));
        return element;
    }

    /**
     * Helper method to create an ElementDefinition with a TypeRefComponent.
     *
     * @param id    The ID of the ElementDefinition.
     * @param type  The type code to set.
     * @return A configured ElementDefinition instance.
     */
    private ElementDefinition createElementWithType(String id, String type) {
        ElementDefinition element = new ElementDefinition();
        element.setId(id);
        ElementDefinition.TypeRefComponent typeRef = new ElementDefinition.TypeRefComponent();
        typeRef.setCode(type);
        element.addType(typeRef);
        return element;
    }

    /**
     * Test when discriminator type is 'VALUE' and the fixed value matches.
     */
    @Test
    void testResolveDiscriminator_TypeValue_ResolvePatternTrue_UsingThisPath() {
        // Arrange
        when(discriminatorMock.getType()).thenReturn(DiscriminatorType.VALUE);
        when(discriminatorMock.getPath()).thenReturn("$this"); // Using "$this" simplifies the path resolution

        // Create a fixed value
        StringType fixedValue = new StringType("fixedStringValue");

        // Create a real ElementDefinition instance for the slice with fixed value
        ElementDefinition slice = new ElementDefinition();
        slice.setId("sliceId.slicePath");
        slice.setFixed(fixedValue);

        // Create a base element with matching value
        StringType baseElement = new StringType("fixedStringValue");
        // No need to mock baseElement's fhirType; real instance returns "string"

        // Act
        Boolean result = DiscriminatorResolver.resolveDiscriminator(baseElement, slice, discriminatorMock, snapshotMock);

        // Assert
        assertTrue(result, "Should return true when discriminator type is 'value' and value matches");
    }

    /**
     * Test when discriminator type is 'VALUE' but the fixed value does not match.
     */
    @Test
    void testResolveDiscriminator_TypeValue_ResolvePatternFalse_UsingThisPath() {
        // Arrange
        when(discriminatorMock.getType()).thenReturn(DiscriminatorType.VALUE);
        when(discriminatorMock.getPath()).thenReturn("$this"); // Using "$this" simplifies the path resolution

        // Create a fixed value
        StringType fixedValue = new StringType("fixedStringValue");

        // Create a real ElementDefinition instance for the slice with fixed value
        ElementDefinition slice = new ElementDefinition();
        slice.setId("sliceId.slicePath");
        slice.setFixed(fixedValue);



        // Create a base element with different value
        StringType baseElement = new StringType("differentStringValue");
        // No need to mock baseElement's fhirType; real instance returns "string"

        // Act
        Boolean result = DiscriminatorResolver.resolveDiscriminator(baseElement, slice, discriminatorMock, snapshotMock);

        // Assert
        assertFalse(result, "Should return false when discriminator type is 'value' but value does not match");
    }

    /**
     * Test when discriminator type is 'TYPE' and the types match.
     */
    @Test
    void testResolveDiscriminator_TypeType_ResolveTypeTrue() {
        // Arrange
        when(discriminatorMock.getType()).thenReturn(DiscriminatorType.TYPE);
        when(discriminatorMock.getPath()).thenReturn("$this"); // Using "$this" simplifies the path resolution

        // Create a real ElementDefinition instance for the slice with type "string"
        ElementDefinition slice = new ElementDefinition();
        slice.setId("sliceId.slicePath");
        ElementDefinition.TypeRefComponent typeRef = new ElementDefinition.TypeRefComponent();
        typeRef.setCode("string");
        slice.addType(typeRef);



        // Create a base element with matching type
        StringType baseElement = new StringType("anyValue");
        // No need to mock baseElement's fhirType; real instance returns "string"

        // Act
        Boolean result = DiscriminatorResolver.resolveDiscriminator(baseElement, slice, discriminatorMock, snapshotMock);

        // Assert
        assertTrue(result, "Should return true when discriminator type is 'type' and types match");
    }

    /**
     * Test when discriminator type is 'TYPE' and the types do not match.
     */
    @Test
    void testResolveDiscriminator_TypeType_ResolveTypeFalse() {
        // Arrange
        when(discriminatorMock.getType()).thenReturn(DiscriminatorType.TYPE);
        when(discriminatorMock.getPath()).thenReturn("$this"); // Using "$this" simplifies the path resolution

        // Create a real ElementDefinition instance for the slice with type "string"
        ElementDefinition slice = new ElementDefinition();
        slice.setId("sliceId.slicePath");
        ElementDefinition.TypeRefComponent typeRef = new ElementDefinition.TypeRefComponent();
        typeRef.setCode("string");
        slice.addType(typeRef);


        // Create a base element with different type
        IntegerType baseElement = new IntegerType(123);
        // No need to mock baseElement's fhirType; real instance returns "integer"

        // Act
        Boolean result = DiscriminatorResolver.resolveDiscriminator(baseElement, slice, discriminatorMock, snapshotMock);

        // Assert
        assertFalse(result, "Should return false when discriminator type is 'type' but types do not match");
    }

    /**
     * Test when discriminator type is 'EXISTS'.
     */
    @Test
    void testResolveDiscriminator_TypeExists() {
        // Arrange
        when(discriminatorMock.getType()).thenReturn(DiscriminatorType.EXISTS);
        // The path can be anything since 'EXISTS' should return false immediately

        // Create a real ElementDefinition instance for the slice
        ElementDefinition slice = new ElementDefinition();
        slice.setId("sliceId.slicePath");
        // 'EXISTS' does not utilize fixed or type, so no need to set them

        // Create a base element (value is irrelevant)
        StringType baseElement = new StringType("anyValue");

        // Act
        Boolean result = DiscriminatorResolver.resolveDiscriminator(baseElement, slice, discriminatorMock, snapshotMock);

        // Assert
        assertFalse(result, "Should return false when discriminator type is 'exists'");
    }

    /**
     * Test when discriminator type is 'PROFILE'.
     */
    @Test
    void testResolveDiscriminator_TypeProfile() {
        // Arrange
        when(discriminatorMock.getType()).thenReturn(DiscriminatorType.PROFILE);
        // The path can be anything since 'PROFILE' should return false immediately

        // Create a real ElementDefinition instance for the slice
        ElementDefinition slice = new ElementDefinition();
        slice.setId("sliceId.slicePath");
        // 'PROFILE' does not utilize fixed or type, so no need to set them


        // Create a base element (value is irrelevant)
        StringType baseElement = new StringType("anyValue");

        // Act
        Boolean result = DiscriminatorResolver.resolveDiscriminator(baseElement, slice, discriminatorMock, snapshotMock);

        // Assert
        assertFalse(result, "Should return false when discriminator type is 'profile'");
    }

    /**
     * Test when discriminator type is unknown.
     * Since DiscriminatorType is an enum, simulating an unknown type requires a workaround.
     * Alternatively, ensure that the default case is covered by using a type not handled explicitly.
     */
    @Test
    void testResolveDiscriminator_UnknownType() {
        // Arrange
        // Since DiscriminatorType is an enum with predefined values, we cannot create an unknown type.
        // Instead, we can simulate an unknown type by having a type that the resolver does not handle.

        // For example, let's assume the resolver handles "exists", "value", "pattern", "type", "profile"
        // If we use one of these, it's handled; to simulate unknown, perhaps extend the enum if possible.
        // However, Java enums cannot be extended. Therefore, this test is not feasible.

        // As an alternative, ensure that using an existing type not mapped to true returns false.
        // For demonstration, use 'EXISTS' which should return false as per implementation.

        when(discriminatorMock.getType()).thenReturn(DiscriminatorType.EXISTS);

        // Create a real ElementDefinition instance for the slice
        ElementDefinition slice = new ElementDefinition();
        slice.setId("sliceId.slicePath");


        // Create a base element (value is irrelevant)
        StringType baseElement = new StringType("anyValue");

        // Act
        Boolean result = DiscriminatorResolver.resolveDiscriminator(baseElement, slice, discriminatorMock, snapshotMock);

        // Assert
        assertFalse(result, "Should return false for unknown discriminator type");
    }
}
