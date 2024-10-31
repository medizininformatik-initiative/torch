package de.medizininformatikinitiative.torch.util;

import de.medizininformatikinitiative.torch.model.AttributeGroup;
import de.medizininformatikinitiative.torch.model.Code;
import de.medizininformatikinitiative.torch.model.Filter;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FhirSearchBuilderTest {

    @Mock
    private DseMappingTreeBase mappingTreeBase;

    @InjectMocks
    private FhirSearchBuilder fhirSearchBuilder;

    @Test
    void testGetSearchParamWithPatientGroupAndFilter() {
        when(mappingTreeBase.expand("", "active")).thenReturn(Stream.of("active"));
        AttributeGroup mockGroup = new AttributeGroup("http://example.com/fhir/Group/patient", List.of(),
                List.of(new Filter("token", "status", List.of(new Code("", "active")), "", "")),
                UUID.fromString("9a7653db-cd1a-4a4c-8b91-117bd94c82ff"));
        List<String> batch = Arrays.asList("123", "456");

        String result = fhirSearchBuilder.getSearchParam(mockGroup, batch);

        assertEquals("_id=123,456&_profile=http%3A%2F%2Fexample.com%2Ffhir%2FGroup%2Fpatient&status=%7Cactive", result);
    }

    @Test
    void testGetSearchParamWithPatientGroupWithoutFilter() {
        AttributeGroup mockGroup = new AttributeGroup("http://example.com/fhir/Group/patient", List.of(),
                List.of(), UUID.fromString("9a7653db-cd1a-4a4c-8b91-117bd94c82ff"));
        List<String> batch = Arrays.asList("789", "101");

        String result = fhirSearchBuilder.getSearchParam(mockGroup, batch);

        assertEquals("_id=789,101&_profile=http%3A%2F%2Fexample.com%2Ffhir%2FGroup%2Fpatient", result);
    }

    @Test
    void testGetSearchParamWithNonPatientGroup() {
        AttributeGroup mockGroup = new AttributeGroup("http://example.com/fhir/Group/other", List.of(),
                List.of(), UUID.fromString("9a7653db-cd1a-4a4c-8b91-117bd94c82ff"));
        List<String> batch = Arrays.asList("102", "103");

        String result = fhirSearchBuilder.getSearchParam(mockGroup, batch);

        assertEquals("subject=102,103&_profile=http%3A%2F%2Fexample.com%2Ffhir%2FGroup%2Fother", result);
    }

}
