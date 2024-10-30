package de.medizininformatikinitiative.torch.model.fhir;

import de.medizininformatikinitiative.torch.config.SpringContext;
import de.medizininformatikinitiative.torch.model.crtdl.AttributeGroup;
import de.medizininformatikinitiative.torch.model.crtdl.Code;
import de.medizininformatikinitiative.torch.model.crtdl.Filter;

import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttributeGroupTest {

    @Mock
    DseMappingTreeBase mappingTreeBase;
    MockedStatic<SpringContext> mockedSpringContext;

    @BeforeEach
    public void setup() {
        mockedSpringContext = Mockito.mockStatic(SpringContext.class);
        mockedSpringContext.when(SpringContext::getDseMappingTreeBase).thenReturn(mappingTreeBase);
    }

    @AfterEach
    public void tearDown() {
        mockedSpringContext.close();
    }

    @Test
    void testQueryParamsWithExpandedTokenFilter() {
        when(mappingTreeBase.expand("system1", "code1")).thenReturn(Stream.of("code1", "code1-child1", "code1-child2"));
        Code code = new Code("system1", "code1");
        Filter tokenFilter = new Filter("token", "testToken", List.of(code), null, null);
        Filter dateFilter = new Filter("date", "testDate", null, "2023-01-01", "2023-12-31");

        AttributeGroup attributeGroup = new AttributeGroup("groupRef", List.of(), List.of(dateFilter, tokenFilter), UUID.randomUUID());

        List<QueryParams> result = attributeGroup.queryParams();

        assertEquals(3, result.size(), "Expected one QueryParams object for each token filter");
        assertTrue(result.get(0).toString().equals("testToken=system1|code1&testDate=ge2023-01-01&testDate=le2023-12-31"));
        assertTrue(result.get(1).toString().equals("testToken=system1|code1-child1&testDate=ge2023-01-01&testDate=le2023-12-31"));
        assertTrue(result.get(2).toString().equals("testToken=system1|code1-child2&testDate=ge2023-01-01&testDate=le2023-12-31"));
    }

    @Test
    void testDuplicateDateFiltersThrowsException() {
        Filter dateFilter1 = new Filter("date", "testDateField1", null, "2023-01-01", "2025-01-01");
        Filter dateFilter2 = new Filter("date", "testDateField2", null, "2024-01-01", "2024-11-11");

        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                new AttributeGroup("groupRef", List.of(), List.of(dateFilter1, dateFilter2), UUID.randomUUID())
        );

        assertEquals("Duplicate date type filter found", exception.getMessage());
    }

    @Test
    void testHasFilterWithFilters() {
        Filter tokenFilter = new Filter("token", "testToken", List.of(), null, null);
        AttributeGroup attributeGroup = new AttributeGroup("groupRef", List.of(), List.of(tokenFilter), UUID.randomUUID());

        assertTrue(attributeGroup.hasFilter(), "Expected hasFilter() to return true when filters are present");
    }

    @Test
    void testHasFilterWithoutFilters() {
        AttributeGroup attributeGroup = new AttributeGroup("groupRef", List.of(), List.of(), UUID.randomUUID());

        assertFalse(attributeGroup.hasFilter(), "Expected hasFilter() to return false when no filters are present");
    }

    @Test
    void testGroupReferenceURL() {
        String specialGroupReference = "group reference with spaces & special chars!";
        AttributeGroup attributeGroup = new AttributeGroup(specialGroupReference, List.of(), List.of(), UUID.randomUUID());

        String expectedUrl = URLEncoder.encode(specialGroupReference, StandardCharsets.UTF_8);
        assertEquals(expectedUrl, attributeGroup.groupReferenceURL(), "Expected URL-encoded group reference");
    }




}
