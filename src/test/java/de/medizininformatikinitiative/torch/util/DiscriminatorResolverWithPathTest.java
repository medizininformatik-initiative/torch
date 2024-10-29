package de.medizininformatikinitiative.torch.util;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.ElementDefinition.DiscriminatorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the DiscriminatorResolver class with specific discriminator paths.
 */
@ExtendWith(MockitoExtension.class)
class DiscriminatorResolverWithPathTest {

    @Mock
    ElementDefinition.ElementDefinitionSlicingDiscriminatorComponent discriminatorMock;

    @Mock
    StructureDefinition.StructureDefinitionSnapshotComponent snapshotMock;

    /**
     * Helper method to create an ElementDefinition with a fixed value at a specific path.
     *
     * @param id          The ID of the ElementDefinition.
     * @param path        The path of the ElementDefinition.
     * @param fixedValue  The fixed value to set.
     * @return A configured ElementDefinition instance.
     */
    private ElementDefinition createElementWithFixedValue(String id, String path, String fixedValue) {
        ElementDefinition element = new ElementDefinition();
        element.setId(id);
        element.setPath(path);
        element.setFixed(new StringType(fixedValue));
        return element;
    }

    /**
     * Helper method to create an ElementDefinition with a TypeRefComponent at a specific path.
     *
     * @param id    The ID of the ElementDefinition.
     * @param path  The path of the ElementDefinition.
     * @param type  The type code to set.
     * @return A configured ElementDefinition instance.
     */
    private ElementDefinition createElementWithType(String id, String path, String type) {
        ElementDefinition element = new ElementDefinition();
        element.setId(id);
        element.setPath(path);
        ElementDefinition.TypeRefComponent typeRef = new ElementDefinition.TypeRefComponent();
        typeRef.setCode(type);
        element.addType(typeRef);
        return element;
    }

    @Test
    void testResolveDiscriminator_TypeValue_ResolvePatternTrue_WithPatientResource() throws FHIRException {
        // Arrange
        when(discriminatorMock.getType()).thenReturn(DiscriminatorType.VALUE);
        when(discriminatorMock.getPath()).thenReturn("name.family"); // Specific path to navigate in the Patient resource

        // Create an ElementDefinition for the slice with a fixed value for "family" name
        ElementDefinition slice = new ElementDefinition();
        slice.setId("Patient.name");  // Ensures path matches how it's constructed in resolveSlicePath
        slice.setPath("family");

        // Set the fixed value for "family" name in the slice
        StringType fixedFamilyName = new StringType("Doe");
        slice.setFixed(fixedFamilyName);

        // Mock the snapshot to return the slice for the path "name.family"
        when(snapshotMock.getElementByPath("Patient.name.family")).thenReturn(slice);

        // Create a Patient resource with a matching family name
        Patient basePatient = new Patient();
        HumanName patientName = new HumanName();
        patientName.setFamily("Doe"); // Family name matches the fixed value
        basePatient.addName(patientName);

        // Act
        Boolean result = DiscriminatorResolver.resolveDiscriminator(basePatient, slice, discriminatorMock, snapshotMock);

        // Assert
        assertTrue(result, "Should return true when discriminator type is 'value' and family name matches the fixed value");
    }




    /**
     * Test when discriminator type is 'VALUE' but the fixed value does not match at a specific path.
     * TODO
     */
    @Test
    void testResolveDiscriminator_TypeValue_ResolvePatternFalse_WithSpecificPath() {

    }

    /**
     * Test when discriminator type is 'TYPE' and the types match at a specific path.
     * TODO
     */
    @Test
    void testResolveDiscriminator_TypeType_ResolveTypeTrue_WithSpecificPath() {
    }

    /**
     * Test when discriminator type is 'TYPE' and the types do not match at a specific path.
    TODO
     */

    @Test
    void testResolveDiscriminator_TypeType_ResolveTypeFalse_WithPatientResource() throws FHIRException {

    }


    /**
     * Test when discriminator path does not exist in the snapshot.
     */
    @Test
    void testResolveDiscriminator_PathDoesNotExist() {
        // Arrange
        when(discriminatorMock.getType()).thenReturn(DiscriminatorType.VALUE);
        when(discriminatorMock.getPath()).thenReturn("nonexistent.path"); // Path does not exist

        // Create a real ElementDefinition instance for the slice with a nonexistent path
        ElementDefinition slice = new ElementDefinition();
        slice.setId("sliceId.slicePath");
        slice.setPath("nonexistent.path"); // Set the path that does not exist

        // Mock the snapshot to return null when getElementByPath is called with "sliceId.slicePath.nonexistent.path"
        when(snapshotMock.getElementByPath("sliceId.slicePath.nonexistent.path")).thenReturn(null);

        // Create a base element with any value (irrelevant for this test)
        Patient basePatient = new Patient();

        // Act
        Boolean result = DiscriminatorResolver.resolveDiscriminator(basePatient, slice, discriminatorMock, snapshotMock);

        // Assert
        assertFalse(result, "Should return false when discriminator path does not exist in the snapshot");
    }


    /**
     * Test when discriminator type is 'VALUE' but the slice does not have a fixed value at the specified path.
     */
    @Test
    void testResolveDiscriminator_TypeValue_NoFixedValueAtPath() {
        // Arrange
        when(discriminatorMock.getType()).thenReturn(ElementDefinition.DiscriminatorType.VALUE);
        when(discriminatorMock.getPath()).thenReturn("name.code"); // Setting specific path to navigate

        // Create a real ElementDefinition instance for the slice
        ElementDefinition slice = new ElementDefinition();
        slice.setId("Patient"); // Setting the ID
        slice.setPath("name.code");    // Ensure the path is set properly

        // Create an ElementDefinition at "sliceId.slicePath.parent.child" without setting a fixed value
        ElementDefinition childElement = new ElementDefinition();
        childElement.setId("Patient.name.code");
        childElement.setPath("name.code");
        // Not setting fixed or pattern, this tests the absence of a fixed value

        // Mock the snapshot to return the childElement when getElementByPath is called with "Patient.name.code"
        when(snapshotMock.getElementByPath("Patient.name.code")).thenReturn(childElement);

        // Create a base element with a child "name.code" having any value
        Patient basePatient = new Patient();
        Extension parentExtension = new Extension("name", new Extension("code", new StringType("someValue")));
        basePatient.addExtension(parentExtension);

        // Act
        Boolean result = DiscriminatorResolver.resolveDiscriminator(basePatient, slice, discriminatorMock, snapshotMock);

        // Assert
        assertFalse(result, "Should return false when discriminator type is 'value' but no fixed value is set at the specified path");
    }

    /**
     * Test when discriminator type is 'TYPE' but the slice does not have type information at the specified path.
     */
    @Test
    void testResolveDiscriminator_TypeType_NoTypeAtPath() {
        // Arrange
        when(discriminatorMock.getType()).thenReturn(DiscriminatorType.TYPE);
        when(discriminatorMock.getPath()).thenReturn("parent.child"); // Specific path to navigate

        // Create a real ElementDefinition instance for the slice without type information at "parent.child"
        ElementDefinition slice = new ElementDefinition();
        slice.setId("sliceId.slicePath"); // Set the ID
        slice.setPath("parent"); // Set the parent path

        // Create an ElementDefinition at "sliceId.slicePath.parent.child" without setting a type
        ElementDefinition childElement = new ElementDefinition();
        childElement.setId("sliceId.slicePath.parent.child"); // Set the ID of the child
        childElement.setPath("parent.child"); // Set the path for child

        // Not setting any type references in the childElement to simulate the absence of type information

        // Stubbing both paths, parent and parent.child
        when(snapshotMock.getElementByPath("sliceId.slicePath.parent")).thenReturn(slice);

        // Create a base element with a child "parent.child" having any value
        Patient basePatient = new Patient();
        Extension parentExtension = new Extension("parent", new Extension("child", new StringType("someValue")));
        basePatient.addExtension(parentExtension);

        // Act
        Boolean result = DiscriminatorResolver.resolveDiscriminator(basePatient, slice, discriminatorMock, snapshotMock);

        // Assert
        assertFalse(result, "Should return false when discriminator type is 'type' but no type is set at the specified path");
    }

}
