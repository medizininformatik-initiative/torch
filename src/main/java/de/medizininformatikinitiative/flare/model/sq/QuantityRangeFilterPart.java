package de.medizininformatikinitiative.flare.model.sq;

import de.medizininformatikinitiative.flare.Either;
import de.medizininformatikinitiative.flare.model.mapping.FilterMapping;
import de.medizininformatikinitiative.flare.model.mapping.MappingContext;
import de.medizininformatikinitiative.flare.model.sq.expanded.ExpandedFilter;

import java.util.List;

import static java.util.Objects.requireNonNull;

public record QuantityRangeFilterPart(Quantity lowerBound, Quantity upperBound) implements FilterPart {

    public QuantityRangeFilterPart {
        requireNonNull(lowerBound);
        requireNonNull(upperBound);
    }

    public static QuantityRangeFilterPart of(Quantity lowerBound, Quantity upperBound) {
        return new QuantityRangeFilterPart(lowerBound, upperBound);
    }

    @Override
    public Either<Exception, List<ExpandedFilter>> expand(MappingContext mappingContext, FilterMapping filterMapping) {
        return filterMapping.expandRangeFilterPart(mappingContext, lowerBound, upperBound);
    }
}
