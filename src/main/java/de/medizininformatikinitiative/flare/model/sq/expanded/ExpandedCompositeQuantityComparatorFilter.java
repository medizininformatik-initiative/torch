package de.medizininformatikinitiative.flare.model.sq.expanded;

import de.medizininformatikinitiative.flare.model.fhir.QueryParams;
import de.medizininformatikinitiative.flare.model.sq.Comparator;
import de.medizininformatikinitiative.flare.model.sq.Quantity;
import de.medizininformatikinitiative.flare.model.sq.TermCode;

import static de.medizininformatikinitiative.flare.model.fhir.QueryParams.compositeQuantityValue;
import static java.util.Objects.requireNonNull;

public record ExpandedCompositeQuantityComparatorFilter(String searchParameter, TermCode compositeCode,
                                                        Comparator comparator, Quantity value)
        implements ExpandedFilter {

    public ExpandedCompositeQuantityComparatorFilter {
        requireNonNull(searchParameter);
        requireNonNull(compositeCode);
        requireNonNull(comparator);
        requireNonNull(value);
    }

    @Override
    public QueryParams toParams() {
        return QueryParams.EMPTY.appendParam(searchParameter, compositeQuantityValue(compositeCode,
                comparator, value));
    }
}
