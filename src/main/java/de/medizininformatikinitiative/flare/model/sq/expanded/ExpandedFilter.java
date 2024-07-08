package de.medizininformatikinitiative.flare.model.sq.expanded;

import de.medizininformatikinitiative.flare.model.fhir.QueryParams;
import de.medizininformatikinitiative.flare.model.mapping.Mapping;

/**
 * A filter that already contains {@link Mapping mapping information} like the search parameter.
 * <p>
 * Filters can already contain multiple filters that will result in multiple {@link QueryParams query params} of one
 * query but not in multiple queries. So a group of filters that should be combined by logical {@literal AND} can be
 * represented by one single filter.
 */
public interface ExpandedFilter {

    /**
     * The empty filter.
     * <p>
     * The empty filter is needed as identity element of the {@link #append(ExpandedFilter) append operation}.
     */
    ExpandedFilter EMPTY = new ExpandedFilter() {

        @Override
        public ExpandedFilter append(ExpandedFilter filter) {
            return filter;
        }

        @Override
        public ExpandedFilter chain(String searchParameter) {
            return EMPTY;
        }

        @Override
        public QueryParams toParams() {
            return QueryParams.EMPTY;
        }
    };

    /**
     * Appends {@code filter} to this filter.
     * <p>
     * This means that all individual filters of this filter will prepend all individual filters of {@code filter}.
     *
     * @param filter the filter with potentially multiple individual filters to append
     * @return a filter containing all individual filters by this filter and {@code filter}
     */
    default ExpandedFilter append(ExpandedFilter filter) {
        if (filter == ExpandedFilter.EMPTY) {
            return this;
        }
        return ExpandedFilterGroup.of(this).append(filter);
    }

    /**
     * Returns a new filter with {@code searchParameter} prepended to the chain of search parameters of this filter.
     *
     * @param searchParameter the search parameter to prepend
     * @return a new filter
     */
    default ExpandedFilter chain(String searchParameter) {
        return new ChainedFilter(searchParameter, this);
    }

    /**
     * Transforms this filter part into {@link QueryParams query params}.
     *
     * @return the query params of this filter part
     */
    QueryParams toParams();
}
