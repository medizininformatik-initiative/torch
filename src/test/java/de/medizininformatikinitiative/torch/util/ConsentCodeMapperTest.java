package de.medizininformatikinitiative.torch.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ConsentCodeMapperTest {

    private ObjectMapper objectMapperMock;
    private ConsentCodeMapper consentCodeMapper;

    private static final String EXAMPLE_JSON = """
            [
                {
                    "key": { "code": "CONSENT_KEY_1" },
                    "fixedCriteria": [
                        {
                            "value": [
                                { "code": "CODE_1" },
                                { "code": "CODE_2" }
                            ]
                        }
                    ]
                }
            ]
            """;

    @BeforeEach
    public void setUp() throws IOException {


        // Mock ObjectMapper behavior to read JSON string instead of a file
        objectMapperMock = mock(ObjectMapper.class);
        JsonNode rootNode = new ObjectMapper().readTree(EXAMPLE_JSON);
        when(objectMapperMock.readTree(any(File.class))).thenReturn(rootNode);


        consentCodeMapper = new ConsentCodeMapper("/dummy/path", objectMapperMock);
    }

    // Positive Tests
    @Test
    @DisplayName("Test getRelevantCodes returns expected codes for valid key")
    public void testGetRelevantCodes() {
        Set<String> relevantCodes = consentCodeMapper.getRelevantCodes("CONSENT_KEY_1");
        Set<String> expectedCodes = new HashSet<>(List.of("CODE_1", "CODE_2"));

        assertEquals(expectedCodes, relevantCodes);
    }

    // Negative Tests
    @Test
    @DisplayName("Test getRelevantCodes returns isEmpty set for non-existent key")
    public void testGetRelevantCodesForNonExistentKey() {
        Set<String> relevantCodes = consentCodeMapper.getRelevantCodes("NON_EXISTENT_KEY");

        assertTrue(relevantCodes.isEmpty(), "Expected an isEmpty set for a non-existent key");
    }

    @Test
    @DisplayName("Test IOException is thrown during file reading")
    public void testIOExceptionThrown() throws IOException {
        // Simulate an IOException
        when(objectMapperMock.readTree(any(File.class))).thenThrow(new IOException("Test exception"));

        IOException thrown = assertThrows(IOException.class, () -> {
            new ConsentCodeMapper("/invalid/path", objectMapperMock);
        });

        assertEquals("Test exception", thrown.getMessage(), "Expected IOException message to match");
    }

    @Test
    @DisplayName("Test invalid JSON structure returns isEmpty set")
    public void testInvalidJsonStructure() throws IOException {
        // Simulate an invalid JSON by returning an isEmpty root node
        JsonNode invalidNode = mock(JsonNode.class);
        when(objectMapperMock.readTree(any(File.class))).thenReturn(invalidNode);
        when(invalidNode.iterator()).thenReturn(Collections.emptyIterator());

        // Reinitialize with the mocked ObjectMapper
        consentCodeMapper = new ConsentCodeMapper("/dummy/path", objectMapperMock);

        assertTrue(consentCodeMapper.getRelevantCodes("ANY_KEY").isEmpty(), "Expected isEmpty set for invalid JSON structure");
    }
}
