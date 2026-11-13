package de.medizininformatikinitiative.torch.model.sq;

import de.medizininformatikinitiative.torch.model.mapping.FilterMapping;
import de.medizininformatikinitiative.torch.model.mapping.MappingContext;
import de.medizininformatikinitiative.torch.model.mapping.MappingException;
import de.medizininformatikinitiative.torch.model.sq.expanded.ExpandedFilter;

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
    public List<ExpandedFilter> expand(MappingContext mappingContext, FilterMapping filterMapping) throws MappingException {
        return filterMapping.expandRangeFilterPart(mappingContext, lowerBound, upperBound);
    }
}
