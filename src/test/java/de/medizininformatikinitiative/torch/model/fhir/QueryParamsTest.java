package de.medizininformatikinitiative.torch.model.fhir;

import de.medizininformatikinitiative.torch.model.crtdl.Code;

import de.medizininformatikinitiative.torch.model.sq.Comparator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QueryParamsTest {

    @Test
    void testOfCreatesQueryParamsWithSingleParam() {
        QueryParams.Value value = QueryParams.stringValue("testValue");
        QueryParams queryParams = QueryParams.of("name", value);

        assertEquals("name=testValue", queryParams.toString());
    }

    @Test
    void testStringValueCreatesStringValue() {
        QueryParams.Value value = QueryParams.stringValue("testValue");
        assertEquals("testValue", value.toString());
    }

    @Test
    void testDateValueCreatesDateValueLe() {
        LocalDate date = LocalDate.parse("2023-01-01");
        QueryParams.Value value = QueryParams.dateValue(Comparator.LESS_EQUAL, date);

        assertEquals("le2023-01-01", value.toString());
    }

    @Test
    void testDateValueCreatesDateValueGe() {
        LocalDate date = LocalDate.of(2023, 10, 29);
        QueryParams.Value value = QueryParams.dateValue(Comparator.GREATER_EQUAL, date);

        assertEquals("ge2023-10-29", value.toString());
    }


    @Test
    void testCodeValueCreatesCodeValue() {
        Code code = mock(Code.class);
        when(code.searchParamValue()).thenReturn("codeValue");

        QueryParams.Value value = QueryParams.codeValue(code);
        assertEquals("codeValue", value.toString());
    }

    @Test
    void testAppendParamAddsNewParam() {
        QueryParams initialParams = QueryParams.EMPTY;
        QueryParams.Value value = QueryParams.stringValue("newValue");

        QueryParams resultParams = initialParams.appendParam("newName", value);

        assertEquals("newName=newValue", resultParams.toString());
    }

    @Test
    void testAppendParamsCombinesParamsFromBothQueryParams() {
        QueryParams params1 = QueryParams.of("name1", QueryParams.stringValue("value1"));
        QueryParams params2 = QueryParams.of("name2", QueryParams.stringValue("value2"));

        QueryParams combinedParams = params1.appendParams(params2);

        assertEquals("name1=value1&name2=value2", combinedParams.toString());
    }

    @Test
    void testPrefixNameAddsPrefixToAllParamNames() {
        QueryParams params = QueryParams.of("name", QueryParams.stringValue("value"));
        QueryParams prefixedParams = params.prefixName("prefix");

        assertEquals("prefix.name=value", prefixedParams.toString());
    }

    @Test
    void testToStringGeneratesQueryString() {
        QueryParams params = QueryParams.EMPTY
                .appendParam("name1", QueryParams.stringValue("value1"))
                .appendParam("name2", QueryParams.stringValue("value2"));

        String result = params.toString();

        assertEquals("name1=value1&name2=value2", result);
    }

    @Test
    void testEmptyQueryParams() {
        assertTrue(QueryParams.EMPTY.params().isEmpty(), "Expected EMPTY QueryParams to have no params");
    }
}
