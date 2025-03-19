package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.IFhirPath;
import de.medizininformatikinitiative.torch.consent.ConsentProcessor;
import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.model.consent.Provisions;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ConsentProcessorTest {


    @Mock
    private FhirContext fhirContext;

    @Mock
    private IFhirPath fhirPath;

    @InjectMocks
    private ConsentProcessor consentProcessor;

    @BeforeEach
    public void setUp() {
        when(fhirContext.newFhirPath()).thenReturn(fhirPath);
    }

    @Test
    @DisplayName("Test extractConsentProvisions - valid resource")
    public void testExtractConsentProvisionsValid() {
        Consent consent = mock(Consent.class);
        List<Base> mockProvisions = List.of(mock(Consent.provisionComponent.class));
        when(fhirPath.evaluate(consent, "Consent.provision.provision", Base.class)).thenReturn(mockProvisions);

        List<Base> provisions = consentProcessor.extractConsentProvisions(consent);

        assertNotNull(provisions);
        assertEquals(mockProvisions, provisions);
    }


    @Test
    @DisplayName("Test transformToConsentPeriodByCode - valid consent provisions")
    public void testTransformToConsentPeriodByCodeValid() throws ConsentViolatedException {
        Consent consent = mock(Consent.class);
        Set<String> validCodes = Set.of("VALID_CODE");


        Consent.provisionComponent mockProvision = mock(Consent.provisionComponent.class);
        Period mockPeriod = mock(Period.class);
        Coding mockCoding = new Coding().setCode("VALID_CODE");


        when(mockProvision.getPeriod()).thenReturn(mockPeriod);
        when(mockProvision.getCode()).thenReturn(Collections.singletonList(new CodeableConcept().addCoding(mockCoding)));


        when(fhirPath.evaluate(any(), anyString(), eq(Base.class))).thenReturn(List.of(mockProvision));


        when(mockPeriod.hasStart()).thenReturn(true);
        when(mockPeriod.hasEnd()).thenReturn(true);
        when(mockPeriod.getStartElement()).thenReturn(new DateTimeType("2021-01-01"));
        when(mockPeriod.getEndElement()).thenReturn(new DateTimeType("2021-12-31"));


        Provisions result = consentProcessor.transformToConsentPeriodByCode(consent, validCodes);

        assertNotNull(result);
        assertTrue(result.periods().containsKey("VALID_CODE"));
        assertEquals(1, result.periods().get("VALID_CODE").size());
        assertEquals(de.medizininformatikinitiative.torch.model.consent.Period.fromHapi(mockPeriod), result.periods().get("VALID_CODE").get(0));
    }

    @Test
    @DisplayName("Test transformToConsentPeriodByCode - missing provisions throws ConsentViolatedException")
    public void testTransformToConsentPeriodByCodeNoPeriods() {
        Consent consent = mock(Consent.class);
        Set<String> validCodes = Set.of("VALID_CODE");

        // Mock FhirPath to return an isEmpty provision list
        when(fhirPath.evaluate(any(), anyString(), eq(Base.class))).thenReturn(Collections.emptyList());

        assertThrows(ConsentViolatedException.class, () -> {
            consentProcessor.transformToConsentPeriodByCode(consent, validCodes);
        });
    }


    @Test
    @DisplayName("Test transformToConsentPeriodByCode - some codes are missing and should throw ConsentViolatedException")
    public void testTransformToConsentPeriodByCodePartialValidButOneMissing() throws ConsentViolatedException {
        Consent consent = mock(Consent.class);

        // Assume we are requesting two valid codes, but only one will be found
        Set<String> validCodes = Set.of("VALID_CODE_1", "VALID_CODE_2");

        // Mock provision for VALID_CODE_1
        Consent.provisionComponent validProvision1 = mock(Consent.provisionComponent.class);
        Period validPeriod1 = mock(Period.class);
        when(validProvision1.getPeriod()).thenReturn(validPeriod1);
        when(validProvision1.getCode()).thenReturn(Collections.singletonList(new CodeableConcept().addCoding(new Coding().setCode("VALID_CODE_1"))));

        // Mock start and end elements for VALID_CODE_1 period
        when(validPeriod1.hasStart()).thenReturn(true);
        when(validPeriod1.hasEnd()).thenReturn(true);
        when(validPeriod1.getStartElement()).thenReturn(new DateTimeType("2021-01-01"));
        when(validPeriod1.getEndElement()).thenReturn(new DateTimeType("2021-12-31"));

        // Only VALID_CODE_1 is provided, VALID_CODE_2 is missing
        when(fhirPath.evaluate(any(), anyString(), eq(Base.class))).thenReturn(List.of(validProvision1));  // Only one valid provision

        // Since VALID_CODE_2 is missing, an exception should be thrown
        assertThrows(ConsentViolatedException.class, () -> {
            consentProcessor.transformToConsentPeriodByCode(consent, validCodes);
        });

    }


}
