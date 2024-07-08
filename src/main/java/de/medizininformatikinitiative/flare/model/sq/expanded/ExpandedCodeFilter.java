package de.medizininformatikinitiative.flare.model.sq.expanded;

import de.medizininformatikinitiative.flare.model.fhir.QueryParams;

import static de.medizininformatikinitiative.flare.model.fhir.QueryParams.stringValue;
import static java.util.Objects.requireNonNull;

/**
 * A value filter using a single string code as value.
 *
 * @param searchParameter the FHIR search parameter code to use for the value
 * @param value           the code to search for
 */
public record ExpandedCodeFilter(String searchParameter, String value) implements ExpandedFilter {

    public ExpandedCodeFilter {
        requireNonNull(searchParameter);
        requireNonNull(value);
    }

    @Override
    public QueryParams toParams() {
        return QueryParams.of(searchParameter, stringValue(value));
    }
}
