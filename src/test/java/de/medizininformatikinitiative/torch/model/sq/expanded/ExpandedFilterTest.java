package de.medizininformatikinitiative.torch.model.sq.expanded;

import de.medizininformatikinitiative.torch.model.fhir.QueryParams;
import de.medizininformatikinitiative.torch.model.sq.TermCode;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.conceptValue;
import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.stringValue;
import static de.medizininformatikinitiative.torch.model.sq.expanded.ExpandedFilter.EMPTY;
import static org.assertj.core.api.Assertions.assertThat;

class ExpandedFilterTest {

    static final String SEARCH_PARAMETER = "search-parameter-165816";
    static final String OTHER_SEARCH_PARAMETER = "other-search-parameter-183329";
    static final String VALUE = "value-165841";
    static final ExpandedFilter CODE_FILTER = new ExpandedCodeFilter(SEARCH_PARAMETER, VALUE);
    static final TermCode TERM_CODE = TermCode.of("system-170523", "code-170528", "display-170533");
    static final ExpandedFilter CONCEPT_FILTER = new ExpandedConceptFilter(SEARCH_PARAMETER, TERM_CODE);
    static final ExpandedFilter FILTER_GROUP = ExpandedFilterGroup.of(CODE_FILTER, CONCEPT_FILTER);
    static final ExpandedFilter CHAINED_FILTER = CODE_FILTER.chain(OTHER_SEARCH_PARAMETER);

    @Nested
    class Empty {

        @ParameterizedTest
        @MethodSource("de.medizininformatikinitiative.torch.model.sq.expanded.ExpandedFilterTest#filterProvider")
        void append(ExpandedFilter filter) {
            assertThat(EMPTY.append(filter)).isEqualTo(filter);
        }

        @Test
        void chain() {
            assertThat(EMPTY.chain(SEARCH_PARAMETER)).isEqualTo(EMPTY);
        }

        @Test
        void toParams() {
            assertThat(EMPTY.toParams()).isEqualTo(QueryParams.EMPTY);
        }
    }

    @Nested
    class Append {

        @ParameterizedTest
        @MethodSource("de.medizininformatikinitiative.torch.model.sq.expanded.ExpandedFilterTest#filterProvider")
        void empty(ExpandedFilter filter) {
            assertThat(filter.append(EMPTY)).isEqualTo(filter);
        }

        @Test
        void group() {
            assertThat(CHAINED_FILTER.append(FILTER_GROUP))
                    .isEqualTo(ExpandedFilterGroup.of(CHAINED_FILTER, CODE_FILTER, CONCEPT_FILTER));
        }
    }

    @ParameterizedTest
    @MethodSource("nonEmptyFilterProvider")
    void chain(ExpandedFilter filter) {
        assertThat(filter.chain(OTHER_SEARCH_PARAMETER)).isEqualTo(new ChainedFilter(OTHER_SEARCH_PARAMETER, filter));
    }

    @Nested
    class ToParams {

        @Test
        void codeFilter() {
            assertThat(CODE_FILTER.toParams()).isEqualTo(QueryParams.of(SEARCH_PARAMETER, stringValue(VALUE)));
        }

        @Test
        void conceptFilter() {
            assertThat(CONCEPT_FILTER.toParams()).isEqualTo(QueryParams.of(SEARCH_PARAMETER, conceptValue(TERM_CODE)));
        }

        @Test
        void filterGroup() {
            assertThat(FILTER_GROUP.toParams())
                    .isEqualTo(QueryParams.of(SEARCH_PARAMETER, stringValue(VALUE))
                            .appendParam(SEARCH_PARAMETER, conceptValue(TERM_CODE)));
        }

        @Test
        void chainedFilter() {
            assertThat(CHAINED_FILTER.toParams())
                    .isEqualTo(QueryParams.of(OTHER_SEARCH_PARAMETER + "." + SEARCH_PARAMETER, stringValue(VALUE)));
        }
    }

    private static Stream<ExpandedFilter> filterProvider() {
        return Stream.concat(Stream.of(EMPTY), nonEmptyFilterProvider());
    }

    private static Stream<ExpandedFilter> nonEmptyFilterProvider() {
        return Stream.of(CODE_FILTER, CONCEPT_FILTER, FILTER_GROUP);
    }
}
