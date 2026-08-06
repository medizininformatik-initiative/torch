package de.medizininformatikinitiative.torch.util;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.ElementDefinition.DiscriminatorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

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
     * @param id         The ID of the ElementDefinition.
     * @param path       The path of the ElementDefinition.
     * @param fixedValue The fixed value to set.
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
     * @param id   The ID of the ElementDefinition.
     * @param path The path of the ElementDefinition.
     * @param type The type code to set.
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
        when(discriminatorMock.getType()).thenReturn(DiscriminatorType.VALUE);
        when(discriminatorMock.getPath()).thenReturn("name.family"); // Specific path to navigate in the Patient resource

        ElementDefinition slice = new ElementDefinition();
        slice.setId("Patient.name.family");  // Ensures path matches how it's constructed in resolveSlicePath
        slice.setPath("Patient.name.family");
        StringType fixedFamilyName = new StringType("Doe");
        slice.setFixed(fixedFamilyName);
        when(snapshotMock.getElementById("Patient.name.family")).thenReturn(slice);
        ElementDefinition baseElement = new ElementDefinition();
        baseElement.setId("Patient");  // Ensures path matches how it's constructed in resolveSlicePath
        baseElement.setPath("Patient");

        Patient basePatient = new Patient();
        HumanName patientName = new HumanName();
        patientName.setFamily("Doe"); // Family name matches the fixed value
        basePatient.addName(patientName);

        Boolean result = DiscriminatorResolver.resolveDiscriminator(basePatient, baseElement, discriminatorMock, snapshotMock);

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
     * Test when discriminator path does not exist in the snapshot.
     */
    @Test
    void testResolveDiscriminator_PathDoesNotExist() {
        when(discriminatorMock.getType()).thenReturn(DiscriminatorType.VALUE);
        when(discriminatorMock.getPath()).thenReturn("nonexistent.path"); // Path does not exist
        ElementDefinition slice = new ElementDefinition();
        slice.setId("sliceId.slicePath");
        slice.setPath("nonexistent.path"); // Set the path that does not exist
        when(snapshotMock.getElementById("sliceId.slicePath.nonexistent.path")).thenReturn(null);
        Patient basePatient = new Patient();

        Boolean result = DiscriminatorResolver.resolveDiscriminator(basePatient, slice, discriminatorMock, snapshotMock);

        assertFalse(result, "Should return false when discriminator path does not exist in the snapshot");
    }

    /**
     * Test when discriminator type is 'TYPE' and its path does not resolve to an element in the snapshot, as
     * happens for reference-resolving paths like {@code $this.resolve()} used to slice by the referenced
     * resource's type. Must return false rather than throw, like the 'VALUE'/'PATTERN' case above already does.
     */
    @Test
    void testResolveDiscriminator_TypeType_PathDoesNotExist() {
        when(discriminatorMock.getType()).thenReturn(DiscriminatorType.TYPE);
        when(discriminatorMock.getPath()).thenReturn("$this.resolve()");
        ElementDefinition slice = new ElementDefinition();
        slice.setId("Observation.derivedFrom:attached-image");
        when(snapshotMock.getElementById("Observation.derivedFrom:attached-image.$this.resolve()")).thenReturn(null);

        Reference reference = new Reference("DocumentReference/123");

        Boolean result = DiscriminatorResolver.resolveDiscriminator(reference, slice, discriminatorMock, snapshotMock);

        assertFalse(result, "Should return false, not throw, when a type discriminator's path does not resolve in the snapshot");
    }


    /**
     * A repeating element (e.g. {@code CodeableConcept.coding}) must match a pattern discriminator if
     * <i>any</i> of its values satisfies the pattern, not only its first value.
     */
    @Test
    void testResolveDiscriminator_TypeValue_PatternMatchesCodingNotFirstInArray() {
        when(discriminatorMock.getType()).thenReturn(DiscriminatorType.VALUE);
        when(discriminatorMock.getPath()).thenReturn("code");

        ElementDefinition slice = new ElementDefinition();
        slice.setId("Observation.component:SystolicBP");

        ElementDefinition codeElement = new ElementDefinition();
        CodeableConcept pattern = new CodeableConcept();
        pattern.addCoding(new Coding("http://loinc.org", "8480-6", null));
        codeElement.setPattern(pattern);
        when(snapshotMock.getElementById("Observation.component:SystolicBP.code")).thenReturn(codeElement);

        Observation.ObservationComponentComponent component = new Observation.ObservationComponentComponent();
        CodeableConcept code = new CodeableConcept();
        code.addCoding(new Coding("http://snomed.info/sct", "271649006", "Systolic blood pressure (observable entity)"));
        code.addCoding(new Coding("http://loinc.org", "8480-6", "Blood pressure panel with all children optional"));
        code.addCoding(new Coding("urn:iso:std:iso:11073:10101", "150017", "Systolic blood pressure"));
        component.setCode(code);

        Boolean result = DiscriminatorResolver.resolveDiscriminator(component, slice, discriminatorMock, snapshotMock);

        assertTrue(result, "Should match when any coding in the array matches the pattern, not just the first");
    }

    /**
     * Test when discriminator type is 'VALUE' but the slice does not have a fixed value at the specified path.
     */
    @Test
    void testResolveDiscriminator_TypeValue_NoFixedValueAtPath() {
        when(discriminatorMock.getType()).thenReturn(ElementDefinition.DiscriminatorType.VALUE);
        when(discriminatorMock.getPath()).thenReturn("name.code"); // Setting specific path to navigate
        ElementDefinition slice = new ElementDefinition();
        slice.setId("Patient"); // Setting the ID
        slice.setPath("name.code");    // Ensure the path is set properly
        ElementDefinition childElement = new ElementDefinition();
        childElement.setId("Patient.name.code");
        childElement.setPath("name.code");
        when(snapshotMock.getElementById("Patient.name.code")).thenReturn(childElement);
        Patient basePatient = new Patient();
        Extension parentExtension = new Extension("name", new Extension("code", new StringType("someValue")));
        basePatient.addExtension(parentExtension);

        Boolean result = DiscriminatorResolver.resolveDiscriminator(basePatient, slice, discriminatorMock, snapshotMock);

        assertFalse(result, "Should return false when discriminator type is 'value' but no fixed value is set at the specified path");
    }
}
