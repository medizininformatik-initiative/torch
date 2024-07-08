package de.medizininformatikinitiative.flare.model.sq;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import de.medizininformatikinitiative.flare.Either;
import de.medizininformatikinitiative.flare.model.mapping.FilterMapping;
import de.medizininformatikinitiative.flare.model.mapping.MappingContext;
import de.medizininformatikinitiative.flare.model.sq.expanded.ExpandedFilter;

import java.util.List;
import java.util.stream.StreamSupport;

/**
 * The common part of {@link AttributeFilter} and {@link ValueFilter}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public interface FilterPart {

    /**
     * Parses a value filter part.
     *
     * @param node the JSON representation of the value filter part
     * @return the parsed value filter part
     * @throws IllegalArgumentException if {@code node} isn't valid
     */
    static FilterPart fromJsonNode(JsonNode node) {
        var type = node.get("type").asText();

        return switch (type) {
            case "concept" -> {
                var selectedConcepts = node.get("selectedConcepts");
                if (selectedConcepts == null || selectedConcepts.isEmpty()) {
                    throw new IllegalArgumentException("empty selectedConcepts");
                } else {
                    yield new ConceptFilterPart(StreamSupport.stream(selectedConcepts.spliterator(), false)
                            .map(TermCode::fromJsonNode).toList());
                }
            }
            case "quantity-comparator" -> {
                var comparator = Comparator.fromJson(node.get("comparator").asText());
                var value = node.get("value").decimalValue();
                var unit = node.get("unit");
                yield QuantityComparatorFilterPart.of(comparator, unit == null ? Quantity.of(value)
                        : Quantity.of(value, ucumTermCode(unit)));
            }
            case "quantity-range" -> {
                var lowerBound = node.get("minValue").decimalValue();
                var upperBound = node.get("maxValue").decimalValue();
                var unit = node.get("unit");
                yield QuantityRangeFilterPart.of(unit == null ? Quantity.of(lowerBound)
                        : Quantity.of(lowerBound, ucumTermCode(unit)), unit == null ? Quantity.of(upperBound)
                        : Quantity.of(upperBound, ucumTermCode(unit)));
            }
            case "reference" -> {
                var criteria = node.get("criteria");
                if (criteria == null || criteria.isEmpty()) {
                    throw new IllegalArgumentException("empty criteria");
                } else {
                    yield new ReferenceFilterPart(StreamSupport.stream(criteria.spliterator(), false)
                            .map(Criterion::fromJsonNode).toList());
                }
            }
            default -> throw new IllegalArgumentException("unknown filterPart type: " + type);
        };
    }

    private static TermCode ucumTermCode(JsonNode unit) {
        var system = unit.get("system") == null ? "http://unitsofmeasure.org" : unit.get("system").asText();
        return new TermCode(system, unit.get("code").asText(), unit.get("display").asText());
    }

    /**
     * Expands this {@code FilterPart} into a list of {@code ExpandedFilter expanded filters} that should be combined
     * with logical {@literal OR}.
     *
     * @param mappingContext the context inside which the expansion should happen
     * @param filterMapping  the mapping for the filter to expand
     * @return either an error or a list of expanded filters
     */
    Either<Exception, List<ExpandedFilter>> expand(MappingContext mappingContext, FilterMapping filterMapping);
}
