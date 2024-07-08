package de.medizininformatikinitiative.flare.model.sq.expanded;

import de.medizininformatikinitiative.flare.model.fhir.Query;
import de.medizininformatikinitiative.flare.model.mapping.Mapping;
import de.medizininformatikinitiative.flare.model.sq.Criterion;
import de.medizininformatikinitiative.flare.model.sq.TermCode;

import java.util.Collection;

import static java.util.Objects.requireNonNull;

/**
 * A criterion that is already expanded from a {@link Criterion} of the structured query.
 * <p>
 * Expanded criterion {@link #toQuery() translate} to exactly one {@link Query query} and contain already all
 * {@link Mapping mapping information} needed.
 *
 * @param resourceType the type of the resource like Condition or Observation
 * @param filter       the filter which can be also a {@link ExpandedFilterGroup group of filters}
 */
public record ExpandedCriterion(String resourceType, ExpandedFilter filter) {

    public ExpandedCriterion {
        requireNonNull(resourceType);
        requireNonNull(filter);
    }

    public static ExpandedCriterion of(String resourceType) {
        return new ExpandedCriterion(resourceType, ExpandedFilter.EMPTY);
    }

    public static ExpandedCriterion of(String resourceType, String searchParameter, TermCode termCode) {
        return new ExpandedCriterion(resourceType, new ExpandedConceptFilter(requireNonNull(searchParameter),
                requireNonNull(termCode)));
    }

    public ExpandedCriterion appendFilter(ExpandedFilter filter) {
        return new ExpandedCriterion(resourceType, this.filter.append(filter));
    }

    public ExpandedCriterion appendFilters(Collection<ExpandedFilter> filters) {
        return new ExpandedCriterion(resourceType, filters.stream().reduce(filter, ExpandedFilter::append));
    }

    public Query toQuery() {
        return new Query(resourceType, filter.toParams());
    }
}
