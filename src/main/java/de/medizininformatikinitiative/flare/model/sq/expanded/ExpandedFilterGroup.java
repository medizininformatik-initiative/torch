package de.medizininformatikinitiative.flare.model.sq.expanded;

import de.medizininformatikinitiative.flare.Util;
import de.medizininformatikinitiative.flare.model.fhir.QueryParams;

import java.util.List;

public record ExpandedFilterGroup(List<ExpandedFilter> filters) implements ExpandedFilter {

    public ExpandedFilterGroup {
        filters = List.copyOf(filters);
    }

    public static ExpandedFilterGroup of(ExpandedFilter filter) {
        return new ExpandedFilterGroup(List.of(filter));
    }

    public static ExpandedFilterGroup of(ExpandedFilter f1, ExpandedFilter f2) {
        return new ExpandedFilterGroup(List.of(f1, f2));
    }

    public static ExpandedFilterGroup of(ExpandedFilter f1, ExpandedFilter f2, ExpandedFilter f3) {
        return new ExpandedFilterGroup(List.of(f1, f2, f3));
    }

    @Override
    public ExpandedFilter append(ExpandedFilter filter) {
        if (filter == ExpandedFilter.EMPTY) {
            return this;
        }
        return new ExpandedFilterGroup(Util.concat(filters, filter instanceof ExpandedFilterGroup
                ? ((ExpandedFilterGroup) filter).filters : List.of(filter)));
    }

    @Override
    public QueryParams toParams() {
        return filters.stream().map(ExpandedFilter::toParams).reduce(QueryParams.EMPTY, QueryParams::appendParams);
    }
}
