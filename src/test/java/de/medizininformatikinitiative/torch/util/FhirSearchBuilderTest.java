package de.medizininformatikinitiative.torch.util;

import de.medizininformatikinitiative.torch.model.AttributeGroup;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FhirSearchBuilderTest {

    @Test
    void testGetSearchParamWithPatientGroupAndFilter() {
        AttributeGroup mockGroup = mock(AttributeGroup.class);
        when(mockGroup.getGroupReferenceURL()).thenReturn("http://example.com/fhir/Group/patient");
        when(mockGroup.hasFilter()).thenReturn(true);
        when(mockGroup.getFilterString()).thenReturn("status=active");

        List<String> batch = Arrays.asList("123", "456");
        String expected = "identifier=123,456&_profile=http://example.com/fhir/Group/patient&status=active";

        String result = FhirSearchBuilder.getSearchParam(mockGroup, batch);
        assertEquals(expected, result);
    }

    @Test
    void testGetSearchParamWithPatientGroupWithoutFilter() {
        AttributeGroup mockGroup = mock(AttributeGroup.class);
        when(mockGroup.getGroupReferenceURL()).thenReturn("http://example.com/fhir/Group/patient");
        when(mockGroup.hasFilter()).thenReturn(false);

        List<String> batch = Arrays.asList("789", "101");
        String expected = "identifier=789,101&_profile=http://example.com/fhir/Group/patient";

        String result = FhirSearchBuilder.getSearchParam(mockGroup, batch);
        assertEquals(expected, result);
    }

    @Test
    void testGetSearchParamWithNonPatientGroup() {
        AttributeGroup mockGroup = mock(AttributeGroup.class);
        when(mockGroup.getGroupReferenceURL()).thenReturn("http://example.com/fhir/Group/other");
        when(mockGroup.hasFilter()).thenReturn(false);

        List<String> batch = Arrays.asList("102", "103");
        String expected = "patient=102,103&_profile=http://example.com/fhir/Group/other";

        String result = FhirSearchBuilder.getSearchParam(mockGroup, batch);
        assertEquals(expected, result);
    }

    @Test
    void testGetConsent() {
        List<String> batch = Arrays.asList("111", "222");
        String expected = "patient=111,222&_profile=https://www.medizininformatik-initiative.de/fhir/modul-consent/StructureDefinition/mii-pr-consent-einwilligung";
        String result = FhirSearchBuilder.getConsent(batch);
        assertEquals(expected, result);
    }

    @Test
    void testGetEncounter() {
        List<String> batch = Arrays.asList("333", "444");
        String expected = "patient=333,444&_profile=https://www.medizininformatik-initiative.de/fhir/core/modul-fall/StructureDefinition/KontaktGesundheitseinrichtung";
        String result = FhirSearchBuilder.getEncounter(batch);
        assertEquals(expected, result);
    }

}
