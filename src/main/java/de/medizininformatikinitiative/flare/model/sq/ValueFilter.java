package de.medizininformatikinitiative.flare.model.sq;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import de.medizininformatikinitiative.flare.Either;
import de.medizininformatikinitiative.flare.model.mapping.Mapping;
import de.medizininformatikinitiative.flare.model.mapping.MappingContext;
import de.medizininformatikinitiative.flare.model.mapping.ValueMappingNotFoundException;
import de.medizininformatikinitiative.flare.model.sq.expanded.ExpandedFilter;

import java.util.List;

import static java.util.Objects.requireNonNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ValueFilter(FilterPart filterPart) implements Filter {

    public ValueFilter {
        requireNonNull(filterPart);
    }

    public static ValueFilter ofConcept(TermCode firstConcept, TermCode... otherConcepts) {
        var filterPart = ConceptFilterPart.of(firstConcept);
        for (TermCode concept : otherConcepts) {
            filterPart = filterPart.appendConcept(concept);
        }
        return new ValueFilter(filterPart);
    }

    public static ValueFilter ofComparator(Comparator comparator, Quantity value) {
        return new ValueFilter(QuantityComparatorFilterPart.of(comparator, value));
    }

    public static ValueFilter ofRange(Quantity lowerBound, Quantity upperBound) {
        return new ValueFilter(QuantityRangeFilterPart.of(lowerBound, upperBound));
    }

    /**
     * Parses an attribute filter part.
     *
     * @param node the JSON representation of the attribute filter part
     * @return the parsed attribute filter part
     * @throws IllegalArgumentException if {@code node} isn't valid
     */
    public static ValueFilter fromJsonNode(JsonNode node) {
        return new ValueFilter(FilterPart.fromJsonNode(node));
    }

    @Override
    public Either<Exception, List<ExpandedFilter>> expand(MappingContext mappingContext, Mapping mapping) {
        return mapping.valueFilterMapping()
                .map(filterMapping -> filterPart.expand(mappingContext, filterMapping))
                .orElseGet(() -> Either.left(new ValueMappingNotFoundException(mapping.key())));
    }
}
