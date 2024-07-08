package de.medizininformatikinitiative.flare.model.sq.expanded;

import de.medizininformatikinitiative.flare.model.fhir.QueryParams;
import de.medizininformatikinitiative.flare.model.sq.Quantity;

import static de.medizininformatikinitiative.flare.model.fhir.QueryParams.quantityValue;
import static de.medizininformatikinitiative.flare.model.sq.Comparator.GREATER_EQUAL;
import static de.medizininformatikinitiative.flare.model.sq.Comparator.LESS_EQUAL;
import static java.util.Objects.requireNonNull;

public record ExpandedQuantityRangeFilter(String searchParameter, Quantity lowerBound, Quantity upperBound)
        implements ExpandedFilter {

    public ExpandedQuantityRangeFilter {
        requireNonNull(searchParameter);
        requireNonNull(lowerBound);
        requireNonNull(upperBound);
    }

    @Override
    public QueryParams toParams() {
        return QueryParams.of(searchParameter, quantityValue(GREATER_EQUAL, lowerBound))
                .appendParam(searchParameter, quantityValue(LESS_EQUAL, upperBound));
    }
}
