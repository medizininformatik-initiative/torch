package de.medizininformatikinitiative.flare.model.sq.expanded;

import de.medizininformatikinitiative.flare.model.fhir.QueryParams;
import de.medizininformatikinitiative.flare.model.sq.TermCode;

import static de.medizininformatikinitiative.flare.model.fhir.QueryParams.conceptValue;
import static java.util.Objects.requireNonNull;

/**
 * A value filter using a concept as value.
 *
 * @param searchParameter the FHIR search parameter code to use for the value
 * @param value           the concept to search for
 */
public record ExpandedConceptFilter(String searchParameter, TermCode value) implements ExpandedFilter {

    public ExpandedConceptFilter {
        requireNonNull(searchParameter);
        requireNonNull(value);
    }

    @Override
    public QueryParams toParams() {
        return QueryParams.of(searchParameter, conceptValue(value));
    }
}
