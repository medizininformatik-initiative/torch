package de.medizininformatikinitiative.torch.model.sq;

import de.medizininformatikinitiative.torch.model.mapping.FilterMapping;
import de.medizininformatikinitiative.torch.model.mapping.MappingContext;
import de.medizininformatikinitiative.torch.model.mapping.MappingException;
import de.medizininformatikinitiative.torch.model.sq.expanded.ExpandedFilter;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public record ReferenceFilterPart(List<Criterion> criteria) implements FilterPart {

    public ReferenceFilterPart {
        criteria = List.copyOf(criteria);
    }

    /**
     * Creates a reference attribute filter part.
     *
     * @param criterion the first selected criterion
     * @return the criterion attribute filter part
     * @throws NullPointerException if {@code criterion} is {@code null}
     */
    static ReferenceFilterPart of(Criterion criterion) {
        return new ReferenceFilterPart(List.of(criterion));
    }

    ReferenceFilterPart appendCriterion(Criterion criterion) {
        var criteria = new LinkedList<>(this.criteria);
        criteria.add(criterion);
        return new ReferenceFilterPart(criteria);
    }

    @Override
    public List<ExpandedFilter> expand(MappingContext mappingContext, FilterMapping filterMapping) throws MappingException {
        var filters = new ArrayList<ExpandedFilter>();
        for (Criterion criterion : criteria) {
            filters.addAll(filterMapping.expandReference(mappingContext, criterion));
        }
        return List.copyOf(filters);
    }
}
