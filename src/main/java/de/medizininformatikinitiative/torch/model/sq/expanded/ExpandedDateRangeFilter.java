package de.medizininformatikinitiative.torch.model.sq.expanded;

import de.medizininformatikinitiative.torch.model.fhir.QueryParams;

import java.time.LocalDate;

import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.dateValue;
import static de.medizininformatikinitiative.torch.model.sq.Comparator.GREATER_EQUAL;
import static de.medizininformatikinitiative.torch.model.sq.Comparator.LESS_EQUAL;
import static java.util.Objects.requireNonNull;

public record ExpandedDateRangeFilter(String searchParameter, LocalDate lowerBound, LocalDate upperBound)
        implements ExpandedFilter {

    public ExpandedDateRangeFilter {
        requireNonNull(searchParameter);
        requireNonNull(lowerBound);
        requireNonNull(upperBound);
    }

    @Override
    public QueryParams toParams() {
        return QueryParams.EMPTY
                .appendParam(searchParameter, dateValue(GREATER_EQUAL, lowerBound))
                .appendParam(searchParameter, dateValue(LESS_EQUAL, upperBound));
    }
}
