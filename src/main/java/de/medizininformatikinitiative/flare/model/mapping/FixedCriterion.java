package de.medizininformatikinitiative.flare.model.mapping;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import de.medizininformatikinitiative.flare.model.sq.TermCode;
import de.medizininformatikinitiative.flare.model.sq.expanded.ExpandedCodeFilter;
import de.medizininformatikinitiative.flare.model.sq.expanded.ExpandedCompositeConceptFilter;
import de.medizininformatikinitiative.flare.model.sq.expanded.ExpandedConceptFilter;
import de.medizininformatikinitiative.flare.model.sq.expanded.ExpandedFilter;

import java.util.List;

import static java.util.Objects.requireNonNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FixedCriterion(FixedCriterionType type, String searchParameter, List<TermCode> concepts,
                             TermCode compositeCode) {

    public FixedCriterion {
        requireNonNull(type);
        requireNonNull(searchParameter);
        concepts = List.copyOf(concepts);
    }

    @JsonCreator
    static FixedCriterion fromJson(@JsonProperty("type") FixedCriterionType type,
                                   @JsonProperty("searchParameter") String searchParameter,
                                   @JsonProperty("value") List<TermCode> concepts,
                                   @JsonProperty("compositeCode") JsonNode compositeCode) throws MappingException {
        TermCode compCode = compositeCode == null ? null : TermCode.fromJsonNode(compositeCode);
        if (type.isCompositeType() && compCode == null) {
            throw new CompositeCodeNotFoundException(type);
        }
        return new FixedCriterion(type, searchParameter, concepts, compCode);
    }

    public List<ExpandedFilter> expand() {
        return concepts.stream().map(concept -> switch (type) {
            case CODING -> (ExpandedFilter) new ExpandedConceptFilter(searchParameter, concept);
            case CODE -> new ExpandedCodeFilter(searchParameter, concept.code());
            case COMPOSITE_CONCEPT -> new ExpandedCompositeConceptFilter(searchParameter, compositeCode, concept);
        }).toList();
    }
}
