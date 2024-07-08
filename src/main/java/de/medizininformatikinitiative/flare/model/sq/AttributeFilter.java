package de.medizininformatikinitiative.flare.model.sq;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import de.medizininformatikinitiative.flare.Either;
import de.medizininformatikinitiative.flare.model.mapping.Mapping;
import de.medizininformatikinitiative.flare.model.mapping.MappingContext;
import de.medizininformatikinitiative.flare.model.sq.expanded.ExpandedFilter;

import java.util.List;

import static java.util.Objects.requireNonNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AttributeFilter(TermCode code, FilterPart filterPart) implements Filter {

    public AttributeFilter {
        requireNonNull(code);
        requireNonNull(filterPart);
    }

    public static AttributeFilter ofConcept(TermCode code, TermCode firstConcept, TermCode... otherConcepts) {
        var filterPart = ConceptFilterPart.of(firstConcept);
        for (TermCode concept : otherConcepts) {
            filterPart = filterPart.appendConcept(concept);
        }
        return new AttributeFilter(code, filterPart);
    }

    public static AttributeFilter ofReference(TermCode code, Criterion firstCriterion, Criterion... otherCriteria) {
        var filterPart = ReferenceFilterPart.of(firstCriterion);
        for (Criterion criterion : otherCriteria) {
            filterPart = filterPart.appendCriterion(criterion);
        }
        return new AttributeFilter(code, filterPart);
    }

    /**
     * Parses an attribute filter part.
     *
     * @param node the JSON representation of the attribute filter part
     * @return the parsed attribute filter part
     * @throws IllegalArgumentException if {@code node} isn't valid
     */
    public static AttributeFilter fromJsonNode(JsonNode node) {
        var code = TermCode.fromJsonNode(node.get("attributeCode"));
        return new AttributeFilter(code, FilterPart.fromJsonNode(node));
    }

    @Override
    public Either<Exception, List<ExpandedFilter>> expand(MappingContext mappingContext, Mapping mapping) {
        return mapping.findAttributeMapping(code).flatMap(filterMapping -> filterPart.expand(mappingContext, filterMapping));
    }
}
