package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.TargetClassCreationException;
import de.medizininformatikinitiative.torch.exceptions.RedactionException;
import de.medizininformatikinitiative.torch.model.management.ExtractionRedactionWrapper;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.util.ElementCopier;
import de.medizininformatikinitiative.torch.util.Redaction;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class BatchCopierRedacterTest {

    @Mock
    private ElementCopier copier;

    @Mock
    private Redaction redaction;

    @InjectMocks
    private BatchCopierRedacter transformer; // created automatically

    @Mock
    private PatientResourceBundle patientResourceBundle;

    private Resource resource;
    private ResourceBundle resourceBundle;

    static Stream<Class<? extends Exception>> easyExceptionProvider() {
        return Stream.of(
                RedactionException.class,
                ReflectiveOperationException.class
        );
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Wrap the @InjectMocks instance with a spy to override methods
        transformer = spy(transformer);

        resourceBundle = new ResourceBundle();
        resource = new Patient();
        resource.setId("dummy");
        resourceBundle.put(resource);
        when(patientResourceBundle.bundle()).thenReturn(resourceBundle);
    }

    @ParameterizedTest
    @MethodSource("easyExceptionProvider")
    void transform_Bundle_removesResourceOnEasyException(Class<? extends Exception> exceptionClass) throws Exception {
        Exception exceptionInstance = exceptionClass.getConstructor(String.class).newInstance("fail");

        doThrow(exceptionInstance)
                .when(transformer)
                .transformResource(any(ExtractionRedactionWrapper.class));

        doReturn(mock(ExtractionRedactionWrapper.class))
                .when(transformer)
                .createExtractionWrapper(any(), eq(resource), any(), any());

        transformer.transformBundle(patientResourceBundle, Map.of());

        assertThat(resourceBundle.contains(resource.getId())).isFalse();
    }

    @Test
    void transform_Bundle_removesResourceOnTargetClassCreationException() throws Exception {
        TargetClassCreationException exceptionInstance = new TargetClassCreationException(ExtractionRedactionWrapper.class);

        doThrow(exceptionInstance)
                .when(transformer)
                .transformResource(any(ExtractionRedactionWrapper.class));

        doReturn(mock(ExtractionRedactionWrapper.class))
                .when(transformer)
                .createExtractionWrapper(any(), eq(resource), any(), any());

        transformer.transformBundle(patientResourceBundle, Map.of());

        assertThat(resourceBundle.contains(resource.getId())).isFalse();
    }
}
