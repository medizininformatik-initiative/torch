package de.medizininformatikinitiative.flare.model.sq.expanded;

import de.medizininformatikinitiative.flare.model.fhir.QueryParams;
import de.medizininformatikinitiative.flare.model.sq.Comparator;
import de.medizininformatikinitiative.flare.model.sq.Quantity;

import static de.medizininformatikinitiative.flare.model.fhir.QueryParams.quantityValue;
import static java.util.Objects.requireNonNull;

public record ExpandedQuantityComparatorFilter(String searchParameter, Comparator comparator, Quantity value)
        implements ExpandedFilter {

    public ExpandedQuantityComparatorFilter {
        requireNonNull(searchParameter);
        requireNonNull(comparator);
        requireNonNull(value);
    }

    @Override
    public QueryParams toParams() {
        return QueryParams.EMPTY.appendParam(searchParameter, quantityValue(comparator, value));
    }
}
