package de.medizininformatikinitiative.torch.model.fhir;

import de.medizininformatikinitiative.torch.model.crtdl.Attribute;
import de.medizininformatikinitiative.torch.model.crtdl.AttributeGroup;
import de.medizininformatikinitiative.torch.model.crtdl.Code;
import de.medizininformatikinitiative.torch.model.crtdl.Filter;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.codeValue;
import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.stringValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttributeGroupTest {

    private static final Logger logger = LoggerFactory.getLogger(AttributeGroupTest.class);

    @Mock
    private Attribute attribute; // Mock the Attribute class or interface

    public static final LocalDate DATE_START = LocalDate.parse("2023-01-01");
    public static final LocalDate DATE_END = LocalDate.parse("2023-12-31");
    public static final Code CODE1 = new Code("system1", "code1");
    public static final Code CODE1_CHILD1 = new Code("system1", "code1-child1");
    @Mock
    DseMappingTreeBase mappingTreeBase;


    @Test
    void codeWithExpandedTokenFilter() {
        //Code Handling im DSE Mapping Tree
        when(mappingTreeBase.expand("system1", "code1")).thenReturn(Stream.of("code1", "code1-child1"));
        Filter tokenFilter = new Filter("token", "code", List.of(CODE1), null, null);
        AttributeGroup attributeGroup = new AttributeGroup("groupRef", List.of(), List.of(tokenFilter), UUID.randomUUID());

        List<QueryParams> result = attributeGroup.queryParams(mappingTreeBase);

        assertThat(result).containsExactly(
                QueryParams.of("code", codeValue(CODE1)).appendParam("_profile", stringValue("groupRef")),
                QueryParams.of("code", codeValue(CODE1_CHILD1)).appendParam("_profile", stringValue("groupRef"))
        );
    }

    @Test
    void testQueryParamsWithMultiTokenFilter() {
        when(mappingTreeBase.expand("system1", "code1")).thenReturn(Stream.of("code1"));
        Code code1 = new Code("system1", "code1");
        when(mappingTreeBase.expand("system2", "code2")).thenReturn(Stream.of("code2"));
        Code code2 = new Code("system2", "code2");
        Filter tokenFilter = new Filter("token", "code", List.of(code1, code2), null, null);
        Filter dateFilter = new Filter("date", "date", null, LocalDate.parse("2023-01-01"), LocalDate.parse("2023-12-31"));
        AttributeGroup attributeGroup = new AttributeGroup("groupRef", List.of(), List.of(dateFilter, tokenFilter), UUID.randomUUID());

        List<QueryParams> result = attributeGroup.queryParams(mappingTreeBase);

        assertEquals(2, result.size(), "Expected one QueryParams object for each token filter");
        assertEquals("code=system1|code1&date=ge2023-01-01&date=le2023-12-31&_profile=groupRef", result.get(0).toString());
        assertEquals("code=system2|code2&date=ge2023-01-01&date=le2023-12-31&_profile=groupRef", result.get(1).toString());

    }

    @Test
    void testQueries() {
        when(mappingTreeBase.expand("system1", "code1")).thenReturn(Stream.of("code1"));
        Code code1 = new Code("system1", "code1");
        when(mappingTreeBase.expand("system2", "code2")).thenReturn(Stream.of("code2"));
        Code code2 = new Code("system2", "code2");
        Filter tokenFilter = new Filter("token", "code", List.of(code1, code2), null, null);
        Filter dateFilter = new Filter("date", "date", null, LocalDate.parse("2023-01-01"), LocalDate.parse("2023-12-31"));
        AttributeGroup attributeGroup = new AttributeGroup("groupRef", List.of(new Attribute("Patient.name", false)), List.of(dateFilter, tokenFilter), UUID.randomUUID());

        List<Query> result = attributeGroup.queries(mappingTreeBase);


        Assert.assertTrue(result.size() == 2);
        Assert.assertEquals(result.get(0).toString(), "Patient?code=system1|code1&date=ge2023-01-01&date=le2023-12-31&_profile=groupRef");
        Assert.assertEquals(result.get(1).toString(), "Patient?code=system2|code2&date=ge2023-01-01&date=le2023-12-31&_profile=groupRef");

    }

    @Test
    void testQueriesWithoutCode() {
        Filter dateFilter = new Filter("date", "date", null, LocalDate.parse("2023-01-01"), LocalDate.parse("2023-12-31"));
        AttributeGroup attributeGroup = new AttributeGroup("groupRef", List.of(new Attribute("Patient.name", false)), List.of(dateFilter), UUID.randomUUID());

        List<Query> result = attributeGroup.queries(mappingTreeBase);


        Assert.assertTrue(result.size() == 1);
        Assert.assertEquals(result.getFirst().toString(), "Patient?date=ge2023-01-01&date=le2023-12-31&_profile=groupRef");


    }

    @Test
    void testResourceType() {
        Code code1 = new Code("system1", "code1");
        Filter tokenFilter = new Filter("token", "code", List.of(code1), null, null);
        // Arrange
        String expectedResourceType = "Patient";

        AttributeGroup attributeGroup = new AttributeGroup("groupRef", List.of(new Attribute("Patient.name", false)), List.of(tokenFilter), UUID.randomUUID());
        // Act
        String result = attributeGroup.resourceType();
        // Assert
        assertEquals(expectedResourceType, result);
    }


    @Test
    void testDuplicateDateFiltersThrowsException() {
        Filter dateFilter1 = new Filter("date", "dateField1", null, LocalDate.parse("2023-01-01"), LocalDate.parse("2025-01-01"));
        Filter dateFilter2 = new Filter("date", "dateField2", null, LocalDate.parse("2024-01-01"), LocalDate.parse("2024-11-11"));

        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                new AttributeGroup("groupRef", List.of(), List.of(dateFilter1, dateFilter2), UUID.randomUUID())
        );

        assertEquals("Duplicate date type filter found", exception.getMessage());
    }

    @Test
    void testHasFilterWithFilters() {
        Filter tokenFilter = new Filter("token", "code", List.of(), null, null);
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
