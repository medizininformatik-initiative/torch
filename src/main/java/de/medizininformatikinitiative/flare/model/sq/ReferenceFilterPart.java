package de.medizininformatikinitiative.flare.model.sq;

import de.medizininformatikinitiative.flare.Either;
import de.medizininformatikinitiative.flare.Util;
import de.medizininformatikinitiative.flare.model.mapping.FilterMapping;
import de.medizininformatikinitiative.flare.model.mapping.MappingContext;
import de.medizininformatikinitiative.flare.model.sq.expanded.ExpandedFilter;

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
    public Either<Exception, List<ExpandedFilter>> expand(MappingContext mappingContext, FilterMapping filterMapping) {
        return criteria.stream().map(criterion -> filterMapping.expandReference(mappingContext, criterion))
                .reduce(Either.right(List.of()), Either.liftBinOp(Util::concat));
    }
}
