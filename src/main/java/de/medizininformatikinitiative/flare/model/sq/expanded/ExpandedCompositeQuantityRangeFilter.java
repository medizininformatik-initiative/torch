package de.medizininformatikinitiative.flare.model.sq.expanded;

import de.medizininformatikinitiative.flare.model.fhir.QueryParams;
import de.medizininformatikinitiative.flare.model.sq.Quantity;
import de.medizininformatikinitiative.flare.model.sq.TermCode;

import static de.medizininformatikinitiative.flare.model.fhir.QueryParams.compositeQuantityValue;
import static de.medizininformatikinitiative.flare.model.sq.Comparator.GREATER_EQUAL;
import static de.medizininformatikinitiative.flare.model.sq.Comparator.LESS_EQUAL;
import static java.util.Objects.requireNonNull;

public record ExpandedCompositeQuantityRangeFilter(String searchParameter, TermCode compositeCode,
                                                   Quantity lowerBound, Quantity upperBound)
        implements ExpandedFilter {

    public ExpandedCompositeQuantityRangeFilter {
        requireNonNull(searchParameter);
        requireNonNull(compositeCode);
        requireNonNull(lowerBound);
        requireNonNull(upperBound);
    }

    @Override
    public QueryParams toParams() {
        return QueryParams.of(searchParameter, compositeQuantityValue(compositeCode, GREATER_EQUAL, lowerBound))
                .appendParam(searchParameter, compositeQuantityValue(compositeCode, LESS_EQUAL, upperBound));
    }
}
