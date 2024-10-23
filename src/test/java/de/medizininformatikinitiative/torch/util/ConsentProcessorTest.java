package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.IFhirPath;
import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ConsentProcessorTest {

    @Mock
    private FhirContext fhirContextMock;

    @Mock
    private IFhirPath fhirPathMock;

    private ConsentProcessor consentProcessor;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        // Mocking FhirPath within FhirContext
        when(fhirContextMock.newFhirPath()).thenReturn(fhirPathMock);

        // Instantiate ConsentProcessor with the mocked FhirContext
        consentProcessor = new ConsentProcessor(fhirContextMock);
    }

    @Test
    @DisplayName("Test extractConsentProvisions - valid resource")
    public void testExtractConsentProvisionsValid() {
        DomainResource domainResource = mock(DomainResource.class);
        List<Base> mockProvisions = List.of(mock(Consent.provisionComponent.class));

        // Mock the FHIRPath evaluation to return some provisions
        when(fhirPathMock.evaluate(eq(domainResource), eq("Consent.provision.provision"), eq(Base.class)))
                .thenReturn(mockProvisions);

        List<Base> provisions = consentProcessor.extractConsentProvisions(domainResource);

        assertNotNull(provisions);
        assertEquals(mockProvisions, provisions);  // Ensure returned provisions match the mock
    }

    @Test
    @DisplayName("Test extractConsentProvisions - exception scenario")
    public void testExtractConsentProvisionsException() {
        DomainResource domainResource = mock(DomainResource.class);

        // Mock the FHIRPath evaluation to throw an exception
        when(fhirPathMock.evaluate(any(), anyString(), eq(Base.class)))
                .thenThrow(new RuntimeException("FHIRPath evaluation error"));

        List<Base> provisions = consentProcessor.extractConsentProvisions(domainResource);

        assertTrue(provisions.isEmpty(), "Expected an empty list when an exception is thrown");
    }

    @Test
    @DisplayName("Test transformToConsentPeriodByCode - valid consent periods")
    public void testTransformToConsentPeriodByCodeValid() throws ConsentViolatedException {
        DomainResource domainResource = mock(DomainResource.class);
        Set<String> validCodes = Set.of("VALID_CODE");

        // Mock the provision components with periods and codes
        Consent.provisionComponent mockProvision = mock(Consent.provisionComponent.class);
        Period mockPeriod = mock(Period.class);
        Coding mockCoding = new Coding().setCode("VALID_CODE");

        // Setting up mock provision to return the period and code
        when(mockProvision.getPeriod()).thenReturn(mockPeriod);
        when(mockProvision.getCode()).thenReturn(Collections.singletonList(new CodeableConcept().addCoding(mockCoding)));

        // Mock FhirPath to return the provision list
        when(fhirPathMock.evaluate(any(), anyString(), eq(Base.class)))
                .thenReturn(List.of(mockProvision));

        // Set up valid start and end dates
        when(mockPeriod.hasStart()).thenReturn(true);
        when(mockPeriod.hasEnd()).thenReturn(true);
        when(mockPeriod.getStartElement()).thenReturn(new DateTimeType("2021-01-01"));
        when(mockPeriod.getEndElement()).thenReturn(new DateTimeType("2021-12-31"));

        // Run the method
        Map<String, List<Period>> result = consentProcessor.transformToConsentPeriodByCode(domainResource, validCodes);

        // Verify the results
        assertNotNull(result);
        assertTrue(result.containsKey("VALID_CODE"));
        assertEquals(1, result.get("VALID_CODE").size());
        assertEquals(mockPeriod, result.get("VALID_CODE").get(0));
    }

    @Test
    @DisplayName("Test transformToConsentPeriodByCode - missing periods throws ConsentViolatedException")
    public void testTransformToConsentPeriodByCodeNoPeriods() {
        DomainResource domainResource = mock(DomainResource.class);
        Set<String> validCodes = Set.of("VALID_CODE");

        // Mock FhirPath to return an empty provision list
        when(fhirPathMock.evaluate(any(), anyString(), eq(Base.class)))
                .thenReturn(Collections.emptyList());

        ConsentViolatedException exception = assertThrows(ConsentViolatedException.class, () -> {
            consentProcessor.transformToConsentPeriodByCode(domainResource, validCodes);
        });

        assertEquals("No valid start or end dates found for the provided valid codes", exception.getMessage());
    }

    @Test
    @DisplayName("Test transformToConsentPeriodByCode - partial valid periods throws ConsentViolatedException")
    public void testTransformToConsentPeriodByCodePartialValid() throws ConsentViolatedException {
        DomainResource domainResource = mock(DomainResource.class);

        // Assume we are requesting two codes, one valid ("VALID_CODE") and one invalid ("INVALID_CODE")
        Set<String> validCodes = Set.of("VALID_CODE", "INVALID_CODE");

        // Mock provision for VALID_CODE
        Consent.provisionComponent validProvision = mock(Consent.provisionComponent.class);
        Period validPeriod = mock(Period.class);
        when(validProvision.getPeriod()).thenReturn(validPeriod);
        when(validProvision.getCode()).thenReturn(Collections.singletonList(new CodeableConcept().addCoding(new Coding().setCode("VALID_CODE"))));

        // Mock start and end elements for VALID_CODE period
        when(validPeriod.hasStart()).thenReturn(true);
        when(validPeriod.hasEnd()).thenReturn(true);

        // Mock getStartElement() and getEndElement() to return DateTimeType objects
        DateTimeType startDateTime = new DateTimeType("2021-01-01");
        DateTimeType endDateTime = new DateTimeType("2021-12-31");
        when(validPeriod.getStartElement()).thenReturn(startDateTime);
        when(validPeriod.getEndElement()).thenReturn(endDateTime);

        // Mock provision for INVALID_CODE
        Consent.provisionComponent invalidProvision = mock(Consent.provisionComponent.class);
        when(invalidProvision.getCode()).thenReturn(Collections.singletonList(new CodeableConcept().addCoding(new Coding().setCode("INVALID_CODE"))));

        // Mock FhirPath to return both valid and invalid provisions
        when(fhirPathMock.evaluate(any(), anyString(), eq(Base.class)))
                .thenReturn(List.of(validProvision, invalidProvision));

        // We expect an exception because one valid code is missing (INVALID_CODE is invalid)
        ConsentViolatedException exception = assertThrows(ConsentViolatedException.class, () -> {
            consentProcessor.transformToConsentPeriodByCode(domainResource, validCodes);
        });

        assertEquals("Resource does not have valid consents for every requested code", exception.getMessage());
    }





    @Test
    @DisplayName("Test transformToConsentPeriodByCode - some codes are missing and should throw ConsentViolatedException")
    public void testTransformToConsentPeriodByCodePartialValidButOneMissing() throws ConsentViolatedException {
        DomainResource domainResource = mock(DomainResource.class);

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
        when(fhirPathMock.evaluate(any(), anyString(), eq(Base.class)))
                .thenReturn(List.of(validProvision1));  // Only one valid provision

        // Since VALID_CODE_2 is missing, an exception should be thrown
        ConsentViolatedException exception = assertThrows(ConsentViolatedException.class, () -> {
            consentProcessor.transformToConsentPeriodByCode(domainResource, validCodes);
        });

        // The exception should indicate that the resource does not have valid consents for every requested code
        assertEquals("Resource does not have valid consents for every requested code", exception.getMessage());
    }


}
