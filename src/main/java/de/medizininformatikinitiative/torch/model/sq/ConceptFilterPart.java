package de.medizininformatikinitiative.torch.model.sq;

import de.medizininformatikinitiative.torch.model.mapping.FilterMapping;
import de.medizininformatikinitiative.torch.model.mapping.MappingContext;
import de.medizininformatikinitiative.torch.model.mapping.MappingException;
import de.medizininformatikinitiative.torch.model.sq.expanded.ExpandedFilter;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Contains a list of selected concepts.
 *
 * @param concepts selected concepts
 */
public record ConceptFilterPart(List<TermCode> concepts) implements FilterPart {

    public ConceptFilterPart {
        concepts = List.copyOf(concepts);
    }

    /**
     * Creates a concept attribute filter part.
     *
     * @param concept the first selected concept
     * @return the concept attribute filter part
     * @throws NullPointerException if {@code concept} is {@code null}
     */
    static ConceptFilterPart of(TermCode concept) {
        return new ConceptFilterPart(List.of(concept));
    }

    ConceptFilterPart appendConcept(TermCode concept) {
        var concepts = new LinkedList<>(this.concepts);
        concepts.add(concept);
        return new ConceptFilterPart(concepts);
    }

    @Override
    public List<ExpandedFilter> expand(MappingContext mappingContext, FilterMapping filterMapping) throws MappingException {
        var filters = new ArrayList<ExpandedFilter>();
        for (TermCode concept : concepts) {
            filters.add(filterMapping.expandConcept(concept));
        }
        return List.copyOf(filters);
    }
}
