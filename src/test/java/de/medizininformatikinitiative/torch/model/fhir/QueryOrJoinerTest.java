package de.medizininformatikinitiative.torch.model.fhir;

import de.medizininformatikinitiative.torch.model.sq.Comparator;
import de.medizininformatikinitiative.torch.model.sq.TermCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.conceptValue;
import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.dateValue;
import static org.assertj.core.api.Assertions.assertThat;

class QueryOrJoinerTest {

    static QueryParams.Value concept(String code) {
        return conceptValue(TermCode.of("s", code, code));
    }

    static Query condition(String code) {
        return Query.of("Condition", QueryParams.of("code", concept(code)));
    }

    static Query observation(String code, String value) {
        return Query.of("Observation", QueryParams.of("code", concept(code)).appendParam("value-concept", concept(value)));
    }

    static List<String> joined(Query... queries) {
        return QueryOrJoiner.join(List.of(queries)).stream().map(Query::toString).toList();
    }

    @Test
    void joinsQueriesDifferingInOneTokenValue() {
        assertThat(joined(condition("a"), condition("b"), condition("c")))
                .containsExactly("Condition?code=s|a,s|b,s|c");
    }

    @Test
    void dropsDuplicates() {
        assertThat(joined(condition("a"), condition("a"))).containsExactly("Condition?code=s|a");
    }

    @Test
    void doesNotJoinAcrossResourceTypes() {
        assertThat(joined(condition("a"), Query.of("Procedure", QueryParams.of("code", concept("b")))))
                .containsExactly("Condition?code=s|a", "Procedure?code=s|b");
    }

    @Test
    void joinsACartesianProductIntoOneQuery() {
        assertThat(joined(observation("a", "x"), observation("a", "y"), observation("b", "x"), observation("b", "y")))
                .containsExactly("Observation?code=s|a,s|b&value-concept=s|x,s|y");
    }

    @Test
    void doesNotJoinQueriesDifferingInMoreThanOneParam() {
        assertThat(joined(observation("a", "x"), observation("b", "y")))
                .containsExactly("Observation?code=s|a&value-concept=s|x", "Observation?code=s|b&value-concept=s|y");
    }

    @Test
    void doesNotJoinNonTokenValues() {
        var before = Query.of("Condition", QueryParams.of("code", concept("a"))
                .appendParam("recorded-date", dateValue(Comparator.LESS_EQUAL, LocalDate.parse("2020-01-01"))));
        var after = Query.of("Condition", QueryParams.of("code", concept("a"))
                .appendParam("recorded-date", dateValue(Comparator.GREATER_EQUAL, LocalDate.parse("2021-01-01"))));

        assertThat(joined(before, after))
                .containsExactly("Condition?code=s|a&recorded-date=le2020-01-01", "Condition?code=s|a&recorded-date=ge2021-01-01");
    }

    @Test
    void joinsOnlyQueriesWithEqualOtherParams() {
        var restricted = Query.of("Condition", QueryParams.of("code", concept("b"))
                .appendParam("recorded-date", dateValue(Comparator.GREATER_EQUAL, LocalDate.parse("2021-01-01"))));

        assertThat(joined(condition("a"), restricted))
                .containsExactly("Condition?code=s|a", "Condition?code=s|b&recorded-date=ge2021-01-01");
    }
}
