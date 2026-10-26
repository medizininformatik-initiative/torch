package de.medizininformatikinitiative.torch.model.sq;

import de.medizininformatikinitiative.torch.model.mapping.Mapping;
import de.medizininformatikinitiative.torch.model.mapping.MappingContext;
import de.medizininformatikinitiative.torch.model.mapping.MappingException;
import de.medizininformatikinitiative.torch.model.sq.expanded.ExpandedFilter;

import java.util.List;

public interface Filter {

    /**
     * Expands this filter into a list {@link ExpandedFilter expanded filters} that should be combined with logical
     * {@literal OR}.
     *
     * @param mappingContext the context inside which the expansion should happen
     * @param mapping        the mapping to use for expansion
     * @return a list of expanded filters
     * @throws MappingException if the filter cannot be expanded
     */
    List<ExpandedFilter> expand(MappingContext mappingContext, Mapping mapping) throws MappingException ;
}
