package de.medizininformatikinitiative.flare.model.sq.expanded;

import de.medizininformatikinitiative.flare.model.fhir.QueryParams;
import de.medizininformatikinitiative.flare.model.sq.TermCode;

import static de.medizininformatikinitiative.flare.model.fhir.QueryParams.compositeConceptValue;
import static java.util.Objects.requireNonNull;

/**
 * A value filter with composite code, using a concept as value.
 *
 * @param searchParameter the FHIR search parameter code to use for the value
 * @param compositeCode   the code to prepend before the {@code value}
 * @param value           the code to search for
 */
public record ExpandedCompositeConceptFilter(String searchParameter, TermCode compositeCode, TermCode value)
        implements ExpandedFilter {

    public ExpandedCompositeConceptFilter {
        requireNonNull(searchParameter);
        requireNonNull(compositeCode);
        requireNonNull(value);
    }

    @Override
    public QueryParams toParams() {
        return QueryParams.of(searchParameter, compositeConceptValue(compositeCode, value));
    }
}
