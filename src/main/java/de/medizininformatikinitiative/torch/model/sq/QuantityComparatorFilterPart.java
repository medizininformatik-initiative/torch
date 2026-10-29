package de.medizininformatikinitiative.torch.model.sq;

import de.medizininformatikinitiative.torch.model.mapping.FilterMapping;
import de.medizininformatikinitiative.torch.model.mapping.MappingContext;
import de.medizininformatikinitiative.torch.model.mapping.MappingException;
import de.medizininformatikinitiative.torch.model.sq.expanded.ExpandedFilter;

import java.util.List;

import static java.util.Objects.requireNonNull;

public record QuantityComparatorFilterPart(Comparator comparator, Quantity value) implements FilterPart {

    public QuantityComparatorFilterPart {
        requireNonNull(comparator);
        requireNonNull(value);
    }

    /**
     * Returns a comparator filter part.
     *
     * @param comparator the comparator that should be used in the value comparison
     * @param value      the value that should be used in the value comparison
     * @return the comparator filter part
     */
    public static QuantityComparatorFilterPart of(Comparator comparator, Quantity value) {
        return new QuantityComparatorFilterPart(comparator, value);
    }

    @Override
    public List<ExpandedFilter> expand(MappingContext mappingContext, FilterMapping filterMapping) throws MappingException {
        return filterMapping.expandComparatorFilterPart(mappingContext, comparator, value);
    }
}
