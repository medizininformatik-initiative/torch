package de.medizininformatikinitiative.torch.model.crtdl;

import de.medizininformatikinitiative.torch.model.fhir.QueryParams;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;
import de.medizininformatikinitiative.torch.model.sq.Comparator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FilterTest {

    private static final Logger logger = LoggerFactory.getLogger(FilterTest.class);

    static final String FILTER_TYPE_TOKEN = "token";
    static final String NAME = "name";
    static final String SYSTEM_A = "system-a";
    static final String SYSTEM_B = "system-b";
    static final String CODE_A_NO_CHILDREN = "code-no-children-a";
    static final String CODE_B_NO_CHILDREN = "code-no-children-b";
    static final String CODE_A_1_CHILD = "code-a-two-children";
    static final String CODE_A_CHILD_1 = "code-a-child-1";
    static final String CODE_A_CHILD_2 = "code-a-child-2";

    static final LocalDate START_DATE = LocalDate.of(2023, 1, 1);
    static final LocalDate END_DATE = LocalDate.of(2023, 12, 31);

    @Mock
    DseMappingTreeBase mappingTreeBase;


    @Test
    void testDateFilterWithStartAndEnd() {
        // Both start and end dates are provided
        Filter filter = new Filter("date", "date", null, START_DATE, END_DATE);

        QueryParams result = filter.dateFilter();

        assertThat(result).isEqualTo(QueryParams.EMPTY.appendParam("date", QueryParams.dateValue(Comparator.GREATER_EQUAL, START_DATE)).appendParam("date", QueryParams.dateValue(Comparator.LESS_EQUAL, END_DATE)));
        assertThat(result.params().size()).isEqualTo(2);
    }

    @Test
    void testDateFilterWithOnlyStart() {
        // Only start date is provided
        Filter filter = new Filter("date", "date", null, START_DATE, null);

        QueryParams result = filter.dateFilter();

        assertThat(result).isEqualTo(QueryParams.EMPTY.appendParam("date", QueryParams.dateValue(Comparator.GREATER_EQUAL, START_DATE)));
        assertThat(result.params().size()).isEqualTo(1);
    }

    @Test
    void testDateFilterWithOnlyEnd() {
        // Only end date is provided
        Filter filter = new Filter("date", "date", null, null, LocalDate.parse("2023-12-31"));

        QueryParams result = filter.dateFilter();

        assertThat(result).isEqualTo(QueryParams.EMPTY.appendParam("date", QueryParams.dateValue(Comparator.LESS_EQUAL, END_DATE)));
        assertThat(result.params().size()).isEqualTo(1);
    }

    @Test
    void testOneCodeNoChildren() {
        when(mappingTreeBase.expand(SYSTEM_A, CODE_A_NO_CHILDREN)).thenReturn(Stream.of(CODE_A_NO_CHILDREN));

        Code code = new Code(SYSTEM_A, CODE_A_NO_CHILDREN);
        Filter filter = new Filter(FILTER_TYPE_TOKEN, NAME, List.of(code), null, null);
        logger.debug("Filter Code Size {} ", filter.codes().size());

        var result = filter.codeFilter(mappingTreeBase);

        assertThat(result).isEqualTo(QueryParams.EMPTY.appendParam("name", QueryParams.codeValue(code)));
        assertThat(result.params().size()).isEqualTo(1);
    }

    @Test
    void testTwoCodesNoChildren() {
        when(mappingTreeBase.expand(SYSTEM_A, CODE_A_NO_CHILDREN)).thenReturn(Stream.of(CODE_A_NO_CHILDREN));
        when(mappingTreeBase.expand(SYSTEM_B, CODE_B_NO_CHILDREN)).thenReturn(Stream.of(CODE_B_NO_CHILDREN));

        Code codeA = new Code(SYSTEM_A, CODE_A_NO_CHILDREN);
        Code codeB = new Code(SYSTEM_B, CODE_B_NO_CHILDREN);
        Filter filter = new Filter(FILTER_TYPE_TOKEN, NAME, List.of(codeA, codeB), null, null);

        QueryParams result = filter.codeFilter(mappingTreeBase);

        assertThat(result).isEqualTo(QueryParams.EMPTY.appendParam("name", QueryParams.codeValue(codeA)).appendParam("name", QueryParams.codeValue(codeB)));
        assertThat(result.params().size()).isEqualTo(2);
    }

    @Test
    void testOneCodeTwoChildren() {
        when(mappingTreeBase.expand(SYSTEM_A, CODE_A_1_CHILD)).thenReturn(Stream.of(CODE_A_1_CHILD, CODE_A_CHILD_1));

        Code code = new Code(SYSTEM_A, CODE_A_1_CHILD);
        Code code_child = new Code(SYSTEM_A, CODE_A_CHILD_1);
        Filter filter = new Filter(FILTER_TYPE_TOKEN, NAME, List.of(code), null, null);


        var result = filter.codeFilter(mappingTreeBase);

        assertThat(result).isEqualTo(QueryParams.EMPTY.appendParam("name", QueryParams.codeValue(code)).appendParam("name", QueryParams.codeValue(code_child)));
        assertThat(result.params().size()).isEqualTo(2);

    }
}
