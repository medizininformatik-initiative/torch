package de.medizininformatikinitiative.torch.model.fhir;

import de.medizininformatikinitiative.torch.model.crtdl.Code;
import de.medizininformatikinitiative.torch.model.sq.Comparator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QueryParamsTest {

    @Test
    void testOfCreatesQueryParamsWithSingleParam() {
        QueryParams.Value value = QueryParams.stringValue("testValue");

        QueryParams queryParams = QueryParams.of("name", value);

        assertThat(queryParams)
                .hasToString("name=" + value);
        assertThat(value).hasToString("testValue");
    }

    @Test
    void testDateValueCreatesDateValueLe() {
        LocalDate date = LocalDate.parse("2023-01-01");
        QueryParams.Value value = QueryParams.dateValue(Comparator.LESS_EQUAL, date);

        QueryParams queryParams = QueryParams.of("date", value);

        assertThat(queryParams)
                .hasToString("date=" + value);
        assertThat(value).hasToString("le2023-01-01");
    }

    @Test
    void testDateValueCreatesDateValueGe() {
        LocalDate date = LocalDate.of(2023, 1, 1);
        QueryParams.Value value = QueryParams.dateValue(Comparator.GREATER_EQUAL, date);

        QueryParams queryParams = QueryParams.of("date", value);

        assertThat(queryParams)
                .hasToString("date=" + value);
        assertThat(value).hasToString("ge2023-01-01");
    }


    @Test
    void testCodeValueCreatesCodeValue() {
        Code code = mock(Code.class);
        when(code.toString()).thenReturn("codeValue");

        QueryParams.Value value = QueryParams.codeValue(code);
        assertThat(value).hasToString(code.toString());
    }

    @Test
    void testAppendParamAddsNewParam() {
        QueryParams initialParams = QueryParams.EMPTY;
        QueryParams.Value value = QueryParams.stringValue("newValue");

        assertThat(initialParams).hasToString("");

        QueryParams resultParams = initialParams.appendParam("newName", value);

        assertThat(resultParams).hasToString("newName=newValue");

    }

    @Test
    void testAppendParamsCombinesParamsFromBothQueryParams() {
        QueryParams params1 = QueryParams.of("name1", QueryParams.stringValue("value1"));
        QueryParams params2 = QueryParams.of("name2", QueryParams.stringValue("value2"));

        QueryParams combinedParams = params1.appendParams(params2);

        assertEquals("name1=value1&name2=value2", combinedParams.toString());
    }
    
    @Test
    void testEmptyQueryParams() {
        assertThat(QueryParams.EMPTY.params().isEmpty()).as("Expected EMPTY QueryParams to have no params");
    }
}
