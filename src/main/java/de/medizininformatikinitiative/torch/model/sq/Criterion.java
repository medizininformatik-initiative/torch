package de.medizininformatikinitiative.torch.model.sq;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.medizininformatikinitiative.torch.model.mapping.FixedCriterion;
import de.medizininformatikinitiative.torch.model.mapping.Mapping;
import de.medizininformatikinitiative.torch.model.mapping.MappingContext;
import de.medizininformatikinitiative.torch.model.mapping.MappingException;
import de.medizininformatikinitiative.torch.model.sq.expanded.ExpandedCriterion;
import de.medizininformatikinitiative.torch.model.sq.expanded.ExpandedFilter;
import de.medizininformatikinitiative.torch.util.ListUtils;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * A single, atomic criterion of a structured query (CCDL).
 *
 * @param concept the concept the criterion is about
 * @param filters the filters applied to entities of {@code concept}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Criterion(ContextualConcept concept, List<Filter> filters) {

    /**
     * The maximum number of expanded criteria, and so of FHIR searches, a single criterion may expand into.
     */
    public static final int MAX_EXPANDED_CRITERIA = 1000;

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
     * Expands this criterion into {@link ExpandedCriterion expanded criteria} that should be combined with logical
     * {@literal OR}.
     *
     * @param mappingContext the context inside which the expansion should happen
     * @return the expanded criteria
     * @throws MappingException if the concept, a mapping or a filter cannot be expanded, or if the criterion expands
     *                          into more than {@link #MAX_EXPANDED_CRITERIA} expanded criteria
     */
    public List<ExpandedCriterion> expand(MappingContext mappingContext) throws MappingException {
        var expandedCriteria = new ArrayList<ExpandedCriterion>();
        for (ContextualTermCode contextualTermCode : mappingContext.expandConcept(concept)) {
            expandedCriteria.addAll(expandContextualTermCode(mappingContext, contextualTermCode));
            if (expandedCriteria.size() > MAX_EXPANDED_CRITERIA) {
                throw new ExpansionLimitExceededException(concept, MAX_EXPANDED_CRITERIA);
            }
        }
        return List.copyOf(expandedCriteria);
    }

    private List<ExpandedCriterion> expandContextualTermCode(MappingContext mappingContext,
                                                             ContextualTermCode contextualTermCode)
            throws MappingException {
        var mapping = mappingContext.findMapping(contextualTermCode);
        var expandedFilterMatrix = ListUtils.cartesianProduct(expandFilters(mappingContext, mapping));
        return expandedFilterMatrix.isEmpty()
                ? List.of(expandedCriterion(mapping, contextualTermCode.termCode()))
                : expandedFilterMatrix.stream()
                .map(expandedFilters -> expandedCriterion(mapping, contextualTermCode.termCode())
                        .appendFilters(expandedFilters))
                .toList();
    }

    private List<List<ExpandedFilter>> expandFilters(MappingContext mappingContext, Mapping mapping)
            throws MappingException {
        var expandedFilters = new ArrayList<>(fixedCriterionFilters(mapping));
        for (Filter filter : filters) {
            expandedFilters.add(filter.expand(mappingContext, mapping));
        }
        return expandedFilters;
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
