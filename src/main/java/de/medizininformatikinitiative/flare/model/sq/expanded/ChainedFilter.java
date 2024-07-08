package de.medizininformatikinitiative.flare.model.sq.expanded;

import de.medizininformatikinitiative.flare.model.fhir.QueryParams;

import static java.util.Objects.requireNonNull;

public record ChainedFilter(String searchParameter, ExpandedFilter filter) implements ExpandedFilter {

    public ChainedFilter {
        requireNonNull(searchParameter);
        requireNonNull(filter);
    }

    @Override
    public QueryParams toParams() {
        return filter.toParams().prefixName(searchParameter);
    }
}
