package de.medizininformatikinitiative.torch.util;

import de.medizininformatikinitiative.torch.model.crtdl.AttributeGroup;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FhirSearchBuilderTest {


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
