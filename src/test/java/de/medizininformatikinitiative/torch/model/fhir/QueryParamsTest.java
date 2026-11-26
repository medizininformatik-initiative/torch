package de.medizininformatikinitiative.torch.model.fhir;

import de.medizininformatikinitiative.torch.model.crtdl.Code;
import de.medizininformatikinitiative.torch.model.sq.Comparator;
import de.medizininformatikinitiative.torch.model.sq.Quantity;
import de.medizininformatikinitiative.torch.model.sq.TermCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Test
    void multiStringValueEmpty() {
        assertThatThrownBy(() -> QueryParams.multiStringValue(List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void quantityValueWithoutUnit() {
        QueryParams.Value value = QueryParams.quantityValue(Comparator.GREATER_THAN, Quantity.of(BigDecimal.valueOf(5)));

        assertThat(value).hasToString("gt5");
    }

    @Test
    void quantityValueWithUnit() {
        QueryParams.Value value = QueryParams.quantityValue(Comparator.LESS_THAN,
                Quantity.of(BigDecimal.valueOf(5), TermCode.of("http://unitsofmeasure.org", "kg", "kg")));

        assertThat(value).hasToString("lt5|http://unitsofmeasure.org|kg");
    }

    @Test
    void compositeConceptValue() {
        QueryParams.Value value = QueryParams.compositeConceptValue(TermCode.of("sys", "comp", "comp"),
                TermCode.of("sys", "val", "val"));

        assertThat(value).hasToString("sys|comp$sys|val");
    }

    @Test
    void compositeQuantityValue() {
        QueryParams.Value value = QueryParams.compositeQuantityValue(TermCode.of("sys", "comp", "comp"),
                Comparator.GREATER_EQUAL, Quantity.of(BigDecimal.valueOf(5)));

        assertThat(value).hasToString("sys|comp$ge5");
    }

    @Test
    void orValueJoinsAndFlattensTokenValues() {
        var a = QueryParams.conceptValue(TermCode.of("sys", "a", "a"));
        var b = QueryParams.conceptValue(TermCode.of("sys", "b", "b"));
        var c = QueryParams.codeValue(new Code("sys", "c"));

        var value = QueryParams.orValue(List.of(QueryParams.orValue(List.of(a, b)), c));

        assertThat(value).hasToString("sys|a,sys|b,sys|c");
        assertThat(QueryParams.isToken(value)).isTrue();
    }

    @Test
    void orValueRejectsNonTokenAndEmptyValues() {
        var date = QueryParams.dateValue(Comparator.GREATER_EQUAL, LocalDate.parse("2020-01-01"));

        assertThat(QueryParams.isToken(date)).isFalse();
        assertThatThrownBy(() -> QueryParams.orValue(List.of(date))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> QueryParams.orValue(List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
