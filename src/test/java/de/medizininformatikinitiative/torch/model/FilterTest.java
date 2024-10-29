package de.medizininformatikinitiative.torch.model;

import de.medizininformatikinitiative.torch.config.SpringContext;
import de.medizininformatikinitiative.torch.model.crtdl.Code;
import de.medizininformatikinitiative.torch.model.crtdl.Filter;
import de.medizininformatikinitiative.torch.model.fhir.QueryParams;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;
import de.medizininformatikinitiative.torch.util.DiscriminatorResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class FilterTest {

    private static final Logger logger = LoggerFactory.getLogger(FilterTest.class);

    static final String FILTER_TYPE_TOKEN = "token";
    static final String NAME = "name-164612";
    static final String SYSTEM_A = "system-a";
    static final String SYSTEM_B = "system-b";
    static final String CODE_A_NO_CHILDREN = "code-no-children-a";
    static final String CODE_B_NO_CHILDREN = "code-no-children-b";
    static final String CODE_A_TWO_CHILDREN = "code-a-two-children";
    static final String CODE_A_CHILD_1 = "code-a-child-1";
    static final String CODE_A_CHILD_2 = "code-a-child-2";

    @Mock
    DseMappingTreeBase mappingTreeBase;
    MockedStatic<SpringContext> mockedSpringContext;

    @BeforeEach
    public void setup() {
        mockedSpringContext = Mockito.mockStatic(SpringContext.class);
        mockedSpringContext.when(SpringContext::getDseMappingTreeBase).thenReturn(mappingTreeBase);
    }

    @AfterEach
    public void close() {
        mockedSpringContext.close();
    }


    @Test
    void testDateFilterWithStartAndEnd() {
        // Both start and end dates are provided
        Filter filter = new Filter("date", "testDateField", null, "2023-01-01", "2023-12-31");
        QueryParams queryParams = filter.dateFilter();

        assertEquals(2, queryParams.params().size(), "Expected 2 params for both start and end dates");
        assertTrue(queryParams.toString().contains("testDateField=ge2023-01-01"));
        assertTrue(queryParams.toString().contains("testDateField=le2023-12-31"));
    }

    @Test
    void testDateFilterWithOnlyStart() {
        // Only start date is provided
        Filter filter = new Filter("date", "testDateField", null, "2023-01-01", null);
        QueryParams queryParams = filter.dateFilter();

        assertEquals(1, queryParams.params().size(), "Expected 1 param for only start date");
        assertEquals("testDateField=ge2023-01-01", queryParams.toString());
    }

    @Test
    void testDateFilterWithOnlyEnd() {
        // Only end date is provided
        Filter filter = new Filter("date", "testDateField", null, null, "2023-12-31");
        QueryParams queryParams = filter.dateFilter();

        assertEquals(1, queryParams.params().size(), "Expected 1 param for only end date");
        assertEquals("testDateField=le2023-12-31", queryParams.toString());
    }

    @Test
    void testOneCodeNoChildren() {
        when(mappingTreeBase.expand(SYSTEM_A, CODE_A_NO_CHILDREN)).thenReturn(Stream.of(CODE_A_NO_CHILDREN));

        Code code = new Code(SYSTEM_A, CODE_A_NO_CHILDREN);
        Filter filter = new Filter(FILTER_TYPE_TOKEN, NAME, List.of(code),null,null);
        logger.debug("Filter Code Size {} ",filter.codes().size());
        var queryParams = filter.codeFilter();

        assertEquals(1, queryParams.params().size(), "Expected 1 param ");
        assertEquals("name-164612=system-a|code-no-children-a", queryParams.toString());
    }

    @Test
    void testTwoCodesNoChildren() {
        when(mappingTreeBase.expand(SYSTEM_A, CODE_A_NO_CHILDREN)).thenReturn(Stream.of(CODE_A_NO_CHILDREN));
        when(mappingTreeBase.expand(SYSTEM_B, CODE_B_NO_CHILDREN)).thenReturn(Stream.of(CODE_B_NO_CHILDREN));

        Code codeA = new Code(SYSTEM_A, CODE_A_NO_CHILDREN);
        Code codeB = new Code(SYSTEM_B, CODE_B_NO_CHILDREN);
        Filter filter = new Filter(FILTER_TYPE_TOKEN, NAME, List.of(codeA, codeB),null,null);

        var queryParams = filter.codeFilter();

        assertEquals(2, queryParams.params().size(), "Expected 2 param ");
        assertEquals("name-164612=system-a|code-no-children-a&name-164612=system-b|code-no-children-b", queryParams.toString());


    }
    @Test
    void testOneCodeTwoChildren() {
        when(mappingTreeBase.expand(SYSTEM_A, CODE_A_TWO_CHILDREN)).thenReturn(Stream.of(CODE_A_TWO_CHILDREN, CODE_A_CHILD_1, CODE_A_CHILD_2));

        Code code = new Code(SYSTEM_A, CODE_A_TWO_CHILDREN);
        Filter filter = new Filter(FILTER_TYPE_TOKEN, NAME, List.of(code),null,null);


        var queryParams = filter.codeFilter();

        assertEquals(3, queryParams.params().size(), "Expected 3 params, one parent + 2 children ");
        assertEquals("name-164612=system-a|code-a-two-children&name-164612=system-a|code-a-child-1&name-164612=system-a|code-a-child-2", queryParams.toString());

    }
}
