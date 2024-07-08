package de.medizininformatikinitiative.flare.model.sq;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.medizininformatikinitiative.flare.Either;
import de.medizininformatikinitiative.flare.Util;
import de.medizininformatikinitiative.flare.model.mapping.FixedCriterion;
import de.medizininformatikinitiative.flare.model.mapping.Mapping;
import de.medizininformatikinitiative.flare.model.mapping.MappingContext;
import de.medizininformatikinitiative.flare.model.sq.expanded.ExpandedCriterion;
import de.medizininformatikinitiative.flare.model.sq.expanded.ExpandedFilter;
import reactor.core.publisher.Mono;

import java.util.LinkedList;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * A single, atomic criterion in a {@link StructuredQuery structured query}.
 *
 * @param concept the concept the criterion is about
 * @param filters the filters applied to entities of {@code concept}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Criterion(ContextualConcept concept, List<Filter> filters) {

    public Criterion {
        requireNonNull(concept);
        filters = List.copyOf(filters);
    }

    public static Criterion of(ContextualConcept concept) {
        return new Criterion(concept, List.of());
    }

    public static Criterion of(ContextualConcept concept, ValueFilter valueFilter) {
        return new Criterion(concept, List.of(valueFilter));
    }

    @JsonCreator
    static Criterion fromJson(@JsonProperty("context") TermCode context,
                              @JsonProperty("termCodes") List<TermCode> termCodes,
                              @JsonProperty("valueFilter") ObjectNode valueFilter,
                              @JsonProperty("attributeFilters") List<ObjectNode> attributeFilters,
                              @JsonProperty("timeRestriction") TimeRestriction timeRestriction) {
        var concept = ContextualConcept.of(requireNonNull(context, "missing JSON property: context"),
                Concept.of(requireNonNull(termCodes, "missing JSON property: termCodes")));

        var filters = new LinkedList<Filter>();
        if (valueFilter != null) {
            filters.add(ValueFilter.fromJsonNode(valueFilter));
        }
        for (ObjectNode attributeFilter : (attributeFilters == null ? List.<ObjectNode>of() : attributeFilters)) {
            filters.add(AttributeFilter.fromJsonNode(attributeFilter));
        }
        if (timeRestriction != null) {
            filters.add(timeRestriction);
        }

        return new Criterion(concept, filters);
    }

    static Criterion fromJsonNode(JsonNode node) {
        try {
            return new ObjectMapper().treeToValue(node, Criterion.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    Criterion appendAttributeFilter(AttributeFilter attributeFilter) {
        var filters = new LinkedList<>(this.filters);
        filters.add(attributeFilter);
        return new Criterion(concept, filters);
    }

    Criterion appendTimeRestrictionFilter(TimeRestriction timeRestrictionFilter) {
        var filters = new LinkedList<>(this.filters);
        filters.add(timeRestrictionFilter);
        return new Criterion(concept, filters);
    }

    /**
     * Expands this criterion into a {@link Mono mono} of {@link ExpandedCriterion expanded criteria}.
     *
     * @param mappingContext the context inside which the expansion should happen
     * @return a mono of expanded criteria
     */
    public Either<Exception, List<ExpandedCriterion>> expand(MappingContext mappingContext) {
        return mappingContext.expandConcept(concept)
                .flatMap(contextualTermCodes -> contextualTermCodes.stream()
                        .map(contextualTermCode -> expandContextualTermCode(mappingContext, contextualTermCode))
                        .reduce(Either.right(List.of()), Either.liftBinOp(Util::concat)));
    }

    private Either<Exception, List<ExpandedCriterion>> expandContextualTermCode(MappingContext mappingContext,
                                                                                ContextualTermCode contextualTermCode) {
        return mappingContext.findMapping(contextualTermCode)
                .flatMap(mapping -> expandFilters(mappingContext, mapping)
                        .map(Util::cartesianProduct)
                        .map(expandedFilterMatrix -> expandedFilterMatrix.isEmpty()
                                ? List.of(expandedCriterion(mapping, contextualTermCode.termCode()))
                                : expandedFilterMatrix.stream()
                                .map(expandedFilters -> expandedCriterion(mapping, contextualTermCode.termCode())
                                        .appendFilters(expandedFilters))
                                .toList()));
    }

    private Either<Exception, List<List<ExpandedFilter>>> expandFilters(MappingContext mappingContext, Mapping mapping) {
        return filters.stream()
                .map(filter -> filter.expand(mappingContext, mapping))
                .reduce(Either.right(fixedCriterionFilters(mapping)), Either.lift2(Util::add), Either.liftBinOp(Util::concat));
    }

    private static List<List<ExpandedFilter>> fixedCriterionFilters(Mapping mapping) {
        return mapping.fixedCriteria().stream().map(FixedCriterion::expand).toList();
    }

    private static ExpandedCriterion expandedCriterion(Mapping mapping, TermCode termCode) {
        return mapping.termCodeSearchParameter() == null
                ? ExpandedCriterion.of(mapping.resourceType())
                : ExpandedCriterion.of(mapping.resourceType(), mapping.termCodeSearchParameter(), termCode);
    }
}
