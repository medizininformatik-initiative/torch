package de.medizininformatikinitiative.torch.model.crtdl;

import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.fhir.QueryParams;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.*;
import static de.medizininformatikinitiative.torch.model.sq.Comparator.GREATER_EQUAL;
import static de.medizininformatikinitiative.torch.model.sq.Comparator.LESS_EQUAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttributeGroupTest {

    private static final Logger logger = LoggerFactory.getLogger(AttributeGroupTest.class);

    public static final LocalDate DATE_START = LocalDate.parse("2023-01-01");
    public static final LocalDate DATE_END = LocalDate.parse("2023-12-31");
    public static final Code CODE1 = new Code("system1", "code1");
    public static final Code CODE1_CHILD1 = new Code("system1", "code1-child1");
    public static final Code CODE2 = new Code("system2", "code2");

    @Mock
    DseMappingTreeBase mappingTreeBase;


    @Test
    void codeWithExpandedTokenFilter() {
        //Code Handling im DSE Mapping Tree
        when(mappingTreeBase.expand("system1", "code1")).thenReturn(Stream.of("code1", "code1-child1"));
        Filter tokenFilter = new Filter("token", "code", List.of(CODE1), null, null);
        AttributeGroup attributeGroup = new AttributeGroup("groupRef", List.of(), List.of(tokenFilter));

        List<QueryParams> result = attributeGroup.queryParams(mappingTreeBase);

        assertThat(result).containsExactly(
                QueryParams.of("code", codeValue(CODE1)).appendParam("_profile", stringValue("groupRef")),
                QueryParams.of("code", codeValue(CODE1_CHILD1)).appendParam("_profile", stringValue("groupRef"))
        );
    }

    @Test
    void testQueryParamsWithMultiTokenFilter() {
        when(mappingTreeBase.expand("system1", "code1")).thenReturn(Stream.of("code1"));
        when(mappingTreeBase.expand("system2", "code2")).thenReturn(Stream.of("code2"));
        Filter tokenFilter = new Filter("token", "code", List.of(CODE1, CODE2), null, null);
        AttributeGroup attributeGroup = new AttributeGroup("groupRef", List.of(), List.of(tokenFilter));

        List<QueryParams> result = attributeGroup.queryParams(mappingTreeBase);

        assertThat(result).containsExactly(
                QueryParams.of("code", codeValue(CODE1)).appendParam("_profile", stringValue("groupRef")),
                QueryParams.of("code", codeValue(CODE2)).appendParam("_profile", stringValue("groupRef"))
        );
    }

    @Test
    void testQueries() {
        when(mappingTreeBase.expand("system1", "code1")).thenReturn(Stream.of("code1"));
        Code code1 = new Code("system1", "code1");
        when(mappingTreeBase.expand("system2", "code2")).thenReturn(Stream.of("code2"));
        Code code2 = new Code("system2", "code2");
        Filter tokenFilter = new Filter("token", "code", List.of(code1, code2), null, null);
        AttributeGroup attributeGroup = new AttributeGroup("groupRef", List.of(new Attribute("Patient.name", false)), List.of(tokenFilter));

        List<Query> result = attributeGroup.queries(mappingTreeBase);


        assertThat(result).containsExactly(
                new Query("Patient", QueryParams.of("code", codeValue(CODE1)).appendParam("_profile", stringValue("groupRef"))),
                new Query("Patient", QueryParams.of("code", codeValue(CODE2)).appendParam("_profile", stringValue("groupRef")))
        );
    }

    @Test
    void testQueriesWithoutCode() {
        Filter dateFilter = new Filter("date", "date", null, DATE_START, DATE_END);
        AttributeGroup attributeGroup = new AttributeGroup("groupRef", List.of(new Attribute("Patient.name", false)), List.of(dateFilter));

        List<Query> result = attributeGroup.queries(mappingTreeBase);


        assertThat(result).containsExactly(
                new Query("Patient", QueryParams.of("date", dateValue(GREATER_EQUAL, DATE_START)).appendParam("date", dateValue(LESS_EQUAL, DATE_END)).appendParam("_profile", stringValue("groupRef"))));


    }

    @Test
    void testResourceType() {
        Filter tokenFilter = new Filter("token", "code", List.of(CODE1), null, null);
        String expectedResourceType = "Patient";
        AttributeGroup attributeGroup = new AttributeGroup("groupRef", List.of(new Attribute("Patient.name", false)), List.of(tokenFilter));

        String result = attributeGroup.resourceType();

        assertThat(result).isEqualTo(expectedResourceType);
    }


    @Test
    void testDuplicateDateFiltersThrowsException() {
        Filter dateFilter1 = new Filter("date", "dateField1", null, DATE_START, DATE_END);
        Filter dateFilter2 = new Filter("date", "dateField2", null, DATE_START, DATE_END);

        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                new AttributeGroup("groupRef", List.of(), List.of(dateFilter1, dateFilter2))
        );

        assertThat(exception.getMessage()).isEqualTo("Duplicate date type filter found");
    }

    @Test
    void testHasFilterWithFilters() {
        Filter tokenFilter = new Filter("token", "code", List.of(), null, null);
        AttributeGroup attributeGroup = new AttributeGroup("groupRef", List.of(), List.of(tokenFilter));

        assertThat(attributeGroup.hasFilter())
                .as("Expected hasFilter() to return true when filters are present")
                .isTrue();
    }

    @Test
    void testHasFilterWithoutFilters() {
        AttributeGroup attributeGroup = new AttributeGroup("groupRef", List.of(), List.of());

        assertThat(attributeGroup.hasFilter())
                .as("Expected hasFilter() to return false when no filters are present")
                .isFalse();
    }
}
