package de.medizininformatikinitiative.flare.model.sq;

import de.medizininformatikinitiative.flare.Either;
import de.medizininformatikinitiative.flare.model.mapping.Mapping;
import de.medizininformatikinitiative.flare.model.mapping.MappingContext;
import de.medizininformatikinitiative.flare.model.sq.expanded.ExpandedFilter;

import java.util.List;

public interface Filter {

    /**
     * Expands this filter into a list {@link ExpandedFilter expanded filters} that should be combined with logical
     * {@literal OR}.
     *
     * @param mappingContext the context inside which the expansion should happen
     * @param mapping        the mapping to use for expansion
     * @return either an error or a list of expanded filters
     */
    Either<Exception, List<ExpandedFilter>> expand(MappingContext mappingContext, Mapping mapping);
}
