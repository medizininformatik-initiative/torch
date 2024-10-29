package de.medizininformatikinitiative.torch.util;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class CopyUtilsTest {

    // Sample class to test reflectListSetter
    public static class SampleClass {
        private List<String> items;

        public void setItems(List<String> items) {
            this.items = items;
        }

        // Additional setter to test absence
        public void setNames(List<String> names) {
            this.items = names;
        }
    }

    // Tests for getElementName method

    @Test
    @DisplayName("Should return last element of a dotted path")
    void testGetElementName_ValidPath() {
        String path = "com.example.project.module.ClassName";
        String expected = "ClassName";
        String actual = CopyUtils.getElementName(path);
        assertEquals(expected, actual, "The last element should be 'ClassName'");
    }

    @Test
    @DisplayName("Should return the element itself if no dots are present")
    void testGetElementName_SingleElement() {
        String path = "ElementName";
        String expected = "ElementName";
        String actual = CopyUtils.getElementName(path);
        assertEquals(expected, actual, "Should return the element itself when no dots are present");
    }

    @Test
    @DisplayName("Should handle empty string")
    void testGetElementName_EmptyString() {
        String path = "";
        String expected = "";
        String actual = CopyUtils.getElementName(path);
        assertEquals(expected, actual, "Should return empty string when input is empty");
    }

    @Test
    @DisplayName("Should handle trailing dots")
    void testGetElementName_TrailingDots() {
        String path = "com.example.";
        String expected = "example";
        String actual = CopyUtils.getElementName(path);
        assertEquals(expected, actual, "Should return empty string when path ends with a dot");
    }

    @Test
    @DisplayName("Should handle multiple consecutive dots")
    void testGetElementName_MultipleDots() {
        String path = "com..example...module.ClassName";
        String expected = "ClassName";
        String actual = CopyUtils.getElementName(path);
        assertEquals(expected, actual, "Should return the last valid element despite multiple dots");
    }

    // Tests for reflectListSetter method

    @Test
    @DisplayName("Should return the correct setter method when it exists")
    void testReflectListSetter_MethodExists() {
        Method method = CopyUtils.reflectListSetter(SampleClass.class, "items");
        assertNotNull(method, "Setter method for 'items' should not be null");
        assertEquals("setItems", method.getName(), "Method name should be 'setItems'");
        assertEquals(List.class, method.getParameterTypes()[0], "Parameter type should be List");
    }

    @Test
    @DisplayName("Should return null when setter method does not exist")
    void testReflectListSetter_MethodDoesNotExist() {
        Method method = CopyUtils.reflectListSetter(SampleClass.class, "nonExistentField");
        assertNull(method, "Setter method for 'nonExistentField' should be null");
    }

    @Test
    @DisplayName("Should return null when setter exists but parameter type is incorrect")
    void testReflectListSetter_WrongParameterType() {
        Method method = CopyUtils.reflectListSetter(SampleClass.class, "count");
        assertNull(method, "Setter method for 'count' with List parameter should not exist");
    }

    @Test
    @DisplayName("Should handle null field name gracefully")
    void testReflectListSetter_NullFieldName() {
        Method method = CopyUtils.reflectListSetter(SampleClass.class, null);
        assertNull(method, "Setter method should be null when field name is null");
    }

    @Test
    @DisplayName("Should handle empty field name gracefully")
    void testReflectListSetter_EmptyFieldName() {
        Method method = CopyUtils.reflectListSetter(SampleClass.class, "");
        assertNull(method, "Setter method should be null when field name is empty");
    }

    // Tests for capitalizeFirstLetter method

    @Test
    @DisplayName("Should capitalize the first letter of a lowercase string")
    void testCapitalizeFirstLetter_Lowercase() {
        String input = "hello";
        String expected = "Hello";
        String actual = CopyUtils.capitalizeFirstLetter(input);
        assertEquals(expected, actual, "First letter should be capitalized");
    }

    @Test
    @DisplayName("Should leave the string unchanged if first letter is already uppercase")
    void testCapitalizeFirstLetter_AlreadyCapitalized() {
        String input = "Hello";
        String expected = "Hello";
        String actual = CopyUtils.capitalizeFirstLetter(input);
        assertEquals(expected, actual, "String should remain unchanged if first letter is uppercase");
    }

    @Test
    @DisplayName("Should handle single character strings")
    void testCapitalizeFirstLetter_SingleCharacter() {
        String input = "h";
        String expected = "H";
        String actual = CopyUtils.capitalizeFirstLetter(input);
        assertEquals(expected, actual, "Single character should be capitalized");
    }

    @Test
    @DisplayName("Should handle empty string")
    void testCapitalizeFirstLetter_EmptyString() {
        String input = "";
        String expected = "";
        String actual = CopyUtils.capitalizeFirstLetter(input);
        assertEquals(expected, actual, "Empty string should remain unchanged");
    }

    @Test
    @DisplayName("Should handle null input")
    void testCapitalizeFirstLetter_Null() {
        String input = null;
        String actual = CopyUtils.capitalizeFirstLetter(input);
        assertNull(actual, "Null input should return null");
    }

    @Test
    @DisplayName("Should capitalize first letter and leave the rest unchanged")
    void testCapitalizeFirstLetter_MixedCase() {
        String input = "hELLO";
        String expected = "HELLO";
        String actual = CopyUtils.capitalizeFirstLetter(input);
        assertEquals(expected, actual, "Only the first letter should be capitalized");
    }

    @Test
    @DisplayName("Should handle strings starting with non-letter characters")
    void testCapitalizeFirstLetter_NonLetterStart() {
        String input = "1hello";
        String expected = "1hello";
        String actual = CopyUtils.capitalizeFirstLetter(input);
        assertEquals(expected, actual, "Non-letter first character should remain unchanged");
    }
}
