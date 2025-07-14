package de.medizininformatikinitiative.torch.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.consent.ConsentCodeMapper;
import de.medizininformatikinitiative.torch.exceptions.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ConsentCodeMapperTest {

    @Mock
    private ObjectMapper objectMapperMock;


    @Test
    @DisplayName("Test IOException is thrown during file reading")
    public void testIOExceptionThrown() throws IOException {
        when(objectMapperMock.readTree(any(File.class))).thenThrow(new IOException("Test exception"));

        assertThatExceptionOfType(IOException.class).isThrownBy(() -> new ConsentCodeMapper("/invalid/path", objectMapperMock));
    }

    @Test
    @DisplayName("Test invalid JSON structure throws IllegalStateException")
    public void testInvalidJsonStructure() throws IOException {
        JsonNode invalidNode = mock(JsonNode.class);
        when(objectMapperMock.readTree(any(File.class))).thenReturn(invalidNode);
        when(invalidNode.iterator()).thenReturn(Collections.emptyIterator());

        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() -> new ConsentCodeMapper("/dummy/path", objectMapperMock))
                .withMessageContaining("Consent map size does not match");
    }

}
