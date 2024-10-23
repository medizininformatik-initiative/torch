package de.medizininformatikinitiative.torch.util;

import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class CompareBaseToFixedOrPatternTest {

    /**
     * Helper method to invoke the private static method compareBaseToFixedOrPattern.
     */
    private boolean invokeCompareBaseToFixedOrPattern(Base resolvedBase, Base fixedOrPatternValue) {
        try {
            Method method = DiscriminatorResolver.class.getDeclaredMethod("compareBaseToFixedOrPattern", Base.class, Base.class);
            method.setAccessible(true);
            return (Boolean) method.invoke(null, resolvedBase, fixedOrPatternValue);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to invoke compareBaseToFixedOrPattern", e);
        }
    }

    /**
     * Test when both Base elements are primitive types with matching values.
     */
    @Test
    void testCompareBaseToFixedOrPattern_PrimitiveMatch_ReturnsTrue() {
        // Arrange
        StringType resolvedBase = new StringType("testValue");
        StringType fixedOrPatternValue = new StringType("testValue");

        // Act
        boolean result = invokeCompareBaseToFixedOrPattern(resolvedBase, fixedOrPatternValue);

        // Assert
        assertTrue(result, "Primitive types with same value should return true");
    }

    /**
     * Test when both Base elements are primitive types with different values.
     */
    @Test
    void testCompareBaseToFixedOrPattern_PrimitiveMismatch_ReturnsFalse() {
        // Arrange
        StringType resolvedBase = new StringType("testValue1");
        StringType fixedOrPatternValue = new StringType("testValue2");

        // Act
        boolean result = invokeCompareBaseToFixedOrPattern(resolvedBase, fixedOrPatternValue);

        // Assert
        assertFalse(result, "Primitive types with different values should return false");
    }

    /**
     * Test when Base elements are of different FHIR types.
     */
    @Test
    void testCompareBaseToFixedOrPattern_DifferentTypes_ReturnsFalse() {
        // Arrange
        StringType resolvedBase = new StringType("testValue");
        IntegerType fixedOrPatternValue = new IntegerType(123);

        // Act
        boolean result = invokeCompareBaseToFixedOrPattern(resolvedBase, fixedOrPatternValue);

        // Assert
        assertFalse(result, "Different fhirTypes should return false");
    }

    /**
     * Test when both Base elements are complex types with matching nested properties.
     */
    @Test
    void testCompareBaseToFixedOrPattern_ComplexMatch_ReturnsTrue() {
        // Arrange
        // Create a complex resolvedBase
        Observation.ObservationComponentComponent resolvedBase = new Observation.ObservationComponentComponent();
        CodeableConcept code = new CodeableConcept();
        code.addCoding().setCode("code1");
        resolvedBase.setCode(code);

        // Create a complex fixedOrPatternValue
        Observation.ObservationComponentComponent fixedOrPatternValue = new Observation.ObservationComponentComponent();
        CodeableConcept fixedCode = new CodeableConcept();
        fixedCode.addCoding().setCode("code1");
        fixedOrPatternValue.setCode(fixedCode);

        // Act
        boolean result = invokeCompareBaseToFixedOrPattern(resolvedBase, fixedOrPatternValue);

        // Assert
        assertTrue(result, "Complex types with matching child values should return true");
    }

    /**
     * Test when both Base elements are complex types with differing nested properties.
     */
    @Test
    void testCompareBaseToFixedOrPattern_ComplexMismatch_ReturnsFalse() {
        // Arrange
        // Create a complex resolvedBase
        Observation.ObservationComponentComponent resolvedBase = new Observation.ObservationComponentComponent();
        CodeableConcept code = new CodeableConcept();
        code.addCoding().setCode("code1");
        resolvedBase.setCode(code);
        resolvedBase.setValue(new Quantity().setValue(5).setUnit("mg"));

        // Create a complex fixedOrPatternValue with only one child
        Observation.ObservationComponentComponent fixedOrPatternValue = new Observation.ObservationComponentComponent();
        CodeableConcept fixedCode = new CodeableConcept();
        fixedCode.addCoding().setCode("code1");
        fixedOrPatternValue.setCode(fixedCode);
        // Missing the 'value' child

        // Act
        boolean result = invokeCompareBaseToFixedOrPattern(resolvedBase, fixedOrPatternValue);

        // Assert
        assertFalse(result, "Complex types with missing child should return false");
    }

    /**
     * Test when both Base elements are complex types with matching multiple nested properties.
     */
    @Test
    void testCompareBaseToFixedOrPattern_ComplexWithMultipleChildren_ReturnsTrue() {
        // Arrange
        // Create a complex resolvedBase
        Observation.ObservationComponentComponent resolvedBase = new Observation.ObservationComponentComponent();
        CodeableConcept code = new CodeableConcept();
        code.addCoding().setCode("code1");
        resolvedBase.setCode(code);
        resolvedBase.setValue(new Quantity().setValue(5).setUnit("mg"));

        // Create a complex fixedOrPatternValue with matching children
        Observation.ObservationComponentComponent fixedOrPatternValue = new Observation.ObservationComponentComponent();
        CodeableConcept fixedCode = new CodeableConcept();
        fixedCode.addCoding().setCode("code1");
        fixedOrPatternValue.setCode(fixedCode);
        fixedOrPatternValue.setValue(new Quantity().setValue(5).setUnit("mg"));

        // Act
        boolean result = invokeCompareBaseToFixedOrPattern(resolvedBase, fixedOrPatternValue);

        // Assert
        assertTrue(result, "Complex types with matching multiple child values should return true");
    }

    /**
     * Test when resolvedBase has a child property missing in fixedOrPatternValue.
     */
    @Test
    void testCompareBaseToFixedOrPattern_ComplexWithMissingChild_ReturnsFalse() {
        // Arrange
        // Create a complex resolvedBase with two children
        Observation.ObservationComponentComponent resolvedBase = new Observation.ObservationComponentComponent();
        CodeableConcept code = new CodeableConcept();
        code.addCoding().setCode("code1");
        resolvedBase.setCode(code);
        resolvedBase.setValue(new Quantity().setValue(5).setUnit("mg"));

        // Create a complex fixedOrPatternValue with only one child
        Observation.ObservationComponentComponent fixedOrPatternValue = new Observation.ObservationComponentComponent();
        CodeableConcept fixedCode = new CodeableConcept();
        fixedCode.addCoding().setCode("code1");
        fixedOrPatternValue.setCode(fixedCode);
        // Missing the 'value' child

        // Act
        boolean result = invokeCompareBaseToFixedOrPattern(resolvedBase, fixedOrPatternValue);

        // Assert
        assertFalse(result, "Complex types with missing child should return false");
    }

    /**
     * Test when fixedOrPatternValue has an extra child property not present in resolvedBase.
     */
    @Test
    void testCompareBaseToFixedOrPattern_ComplexWithExtraChild_ReturnsFalse() {
        // Arrange
        // Create a complex resolvedBase with one child
        Observation.ObservationComponentComponent resolvedBase = new Observation.ObservationComponentComponent();
        CodeableConcept code = new CodeableConcept();
        code.addCoding().setCode("code1");
        resolvedBase.setCode(code);

        // Create a complex fixedOrPatternValue with two children
        Observation.ObservationComponentComponent fixedOrPatternValue = new Observation.ObservationComponentComponent();
        CodeableConcept fixedCode = new CodeableConcept();
        fixedCode.addCoding().setCode("code1");
        fixedOrPatternValue.setCode(fixedCode);
        fixedOrPatternValue.setValue(new Quantity().setValue(5).setUnit("mg")); // Extra child

        // Act
        boolean result = invokeCompareBaseToFixedOrPattern(resolvedBase, fixedOrPatternValue);

        // Assert
        assertFalse(result, "Complex types with extra child should return false");
    }

    /**
     * Test when resolvedBase and fixedOrPatternValue are both null.
     * Now expecting false instead of throwing NullPointerException.
     */
    @Test
    void testCompareBaseToFixedOrPattern_BothNull_ReturnsFalse() {
        // Arrange
        Base resolvedBase = null;
        Base fixedOrPatternValue = null;

        // Act
        boolean result = invokeCompareBaseToFixedOrPattern(resolvedBase, fixedOrPatternValue);

        // Assert
        assertFalse(result, "Passing both null should return false");
    }

    /**
     * Test when resolvedBase is null and fixedOrPatternValue is not.
     */
    @Test
    void testCompareBaseToFixedOrPattern_ResolvedBaseNull_ReturnsFalse() {
        // Arrange
        Base resolvedBase = null;
        Base fixedOrPatternValue = new StringType("testValue");

        // Act
        boolean result = invokeCompareBaseToFixedOrPattern(resolvedBase, fixedOrPatternValue);

        // Assert
        assertFalse(result, "Passing null resolvedBase should return false");
    }

    /**
     * Test when fixedOrPatternValue is null and resolvedBase is not.
     */
    @Test
    void testCompareBaseToFixedOrPattern_FixedOrPatternValueNull_ReturnsFalse() {
        // Arrange
        Base resolvedBase = new StringType("testValue");
        Base fixedOrPatternValue = null;

        // Act
        boolean result = invokeCompareBaseToFixedOrPattern(resolvedBase, fixedOrPatternValue);

        // Assert
        assertFalse(result, "Passing null fixedOrPatternValue should return false");
    }

    /**
     * Test comparing complex types with multiple nested properties where one child does not match.
     */
    @Test
    void testCompareBaseToFixedOrPattern_ComplexWithPartialMismatch_ReturnsFalse() {
        // Arrange
        // Create a complex resolvedBase
        Observation.ObservationComponentComponent resolvedBase = new Observation.ObservationComponentComponent();
        CodeableConcept code = new CodeableConcept();
        code.addCoding().setCode("code1");
        resolvedBase.setCode(code);
        resolvedBase.setValue(new Quantity().setValue(5).setUnit("mg"));

        // Create a complex fixedOrPatternValue with matching 'code' but different 'value'
        Observation.ObservationComponentComponent fixedOrPatternValue = new Observation.ObservationComponentComponent();
        CodeableConcept fixedCode = new CodeableConcept();
        fixedCode.addCoding().setCode("code1");
        fixedOrPatternValue.setCode(fixedCode);
        fixedOrPatternValue.setValue(new Quantity().setValue(10).setUnit("mg")); // Different value

        // Act
        boolean result = invokeCompareBaseToFixedOrPattern(resolvedBase, fixedOrPatternValue);

        // Assert
        assertFalse(result, "Complex types with one differing child should return false");
    }
}
