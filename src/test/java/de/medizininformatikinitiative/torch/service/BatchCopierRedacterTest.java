package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.TargetClassCreationException;
import de.medizininformatikinitiative.torch.exceptions.RedactionException;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionResourceBundle;
import de.medizininformatikinitiative.torch.model.extraction.ResourceExtractionInfo;
import de.medizininformatikinitiative.torch.model.management.ExtractionRedactionWrapper;
import de.medizininformatikinitiative.torch.util.ElementCopier;
import de.medizininformatikinitiative.torch.util.Redaction;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

class BatchCopierRedacterTest {

    @Mock
    private ElementCopier copier;

    @Mock
    private Redaction redaction;

    @InjectMocks
    private BatchCopierRedacter transformer;

    private ExtractionResourceBundle extractionBundle;
    private Resource resource;

    static Stream<Class<? extends Exception>> easyExceptionProvider() {
        return Stream.of(
                RedactionException.class,
                ReflectiveOperationException.class
        );
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        transformer = spy(transformer);

        resource = new Patient();
        resource.setId("dummy");

        // Set up bundle with exactly one resource and its info
        Map<String, ResourceExtractionInfo> infoMap = Map.of(
                "dummy",
                new ResourceExtractionInfo(
                        Set.of("G1"),
                        Map.of() // no references needed for this test
                )
        );
        ConcurrentHashMap<String, Optional<Resource>> cache = new ConcurrentHashMap<>();
        cache.put("dummy", Optional.of(resource));

        extractionBundle = new ExtractionResourceBundle(new ConcurrentHashMap<>(infoMap), cache);

        // group map stub not needed deeply
        // but createWrapper must not run real logic
        doReturn(mock(ExtractionRedactionWrapper.class))
                .when(transformer)
                .createWrapper(any(), any(), any());
    }

    @ParameterizedTest
    @MethodSource("easyExceptionProvider")
    void transformBundle_removesResourceOnEasyException(Class<? extends Exception> exClass) throws Exception {
        Exception ex = exClass.getConstructor(String.class).newInstance("fail");

        doThrow(ex)
                .when(transformer)
                .transformResource(any());

        transformer.transformBundle(extractionBundle, Map.of());

        assertThat(extractionBundle.getResource("dummy")).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void transformBundle_removesResourceOnTargetClassCreationException() throws Exception {

        TargetClassCreationException ex =
                new TargetClassCreationException(ExtractionRedactionWrapper.class);

        doThrow(ex)
                .when(transformer)
                .transformResource(any());

        transformer.transformBundle(extractionBundle, Map.of());

        assertThat(extractionBundle.getResource("dummy")).isEmpty();
    }
}
