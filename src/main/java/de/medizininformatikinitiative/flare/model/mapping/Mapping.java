package de.medizininformatikinitiative.flare.model.mapping;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.medizininformatikinitiative.flare.Either;
import de.medizininformatikinitiative.flare.model.sq.ContextualTermCode;
import de.medizininformatikinitiative.flare.model.sq.Criterion;
import de.medizininformatikinitiative.flare.model.sq.TermCode;
import de.medizininformatikinitiative.flare.model.sq.expanded.ExpandedFilter;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class Mapping {


    private final ContextualTermCode key;
    private final String resourceType;
    private final String termCodeSearchParameter;
    private final ValueFilterMapping valueFilterMapping;
    private final List<FixedCriterion> fixedCriteria;
    private final Map<TermCode, AttributeMapping> attributeMappings;
    private final String timeRestrictionParameter;

    private Mapping(ContextualTermCode key, String resourceType, String termCodeSearchParameter,
                    ValueFilterMapping valueFilterMapping, List<FixedCriterion> fixedCriteria,
                    Map<TermCode, AttributeMapping> attributeMappings, String timeRestrictionParameter) {
        this.key = requireNonNull(key);
        this.resourceType = requireNonNull(resourceType);
        this.termCodeSearchParameter = termCodeSearchParameter;
        this.valueFilterMapping = valueFilterMapping;
        this.fixedCriteria = List.copyOf(fixedCriteria);
        this.attributeMappings = Map.copyOf(attributeMappings);
        this.timeRestrictionParameter = timeRestrictionParameter;
    }

    public static Mapping of(ContextualTermCode contextualTermCode, String resourceType) {
        return new Mapping(contextualTermCode, resourceType, null, null, List.of(), Map.of(), null);
    }

    public static Mapping of(ContextualTermCode contextualTermCode, String resourceType, String termCodeSearchParameter) {
        return new Mapping(contextualTermCode, resourceType, requireNonNull(termCodeSearchParameter), null, List.of(),
                Map.of(), null);
    }

    @JsonCreator
    public static Mapping of(@JsonProperty("context") TermCode context,
                             @JsonProperty("key") TermCode key,
                             @JsonProperty("fhirResourceType") String resourceType,
                             @JsonProperty("termCodeSearchParameter") String termCodeSearchParameter,
                             @JsonProperty("valueSearchParameter") String valueSearchParameter,
                             @JsonProperty("valueType") FilterMappingType valueType,
                             @JsonProperty("fixedCriteria") List<FixedCriterion> fixedCriteria,
                             @JsonProperty("attributeSearchParameters") List<AttributeMapping> attributeMappings,
                             @JsonProperty("timeRestrictionParameter") String timeRestrictionParameter) {
        return new Mapping(ContextualTermCode.of(requireNonNull(context, "missing JSON property: context"),
                requireNonNull(key, "missing JSON property: key")), resourceType, termCodeSearchParameter,
                valueSearchParameter == null
                        ? null
                        : new ValueFilterMapping(valueType, valueSearchParameter),
                fixedCriteria == null ? List.of() : List.copyOf(fixedCriteria),
                attributeMappings == null
                        ? Map.of()
                        : attributeMappings.stream().collect(Collectors.toMap(AttributeMapping::key, Function.identity())),
                timeRestrictionParameter);
    }

    public Mapping withValueFilterMapping(FilterMappingType type, String searchParameter) {
        return new Mapping(key, resourceType, termCodeSearchParameter,
                new ValueFilterMapping(type, searchParameter),
                fixedCriteria, attributeMappings, timeRestrictionParameter);
    }

    public Mapping withFixedCriteria(FixedCriterion fixedCriterion) {
        var fixedCriteria = new LinkedList<>(this.fixedCriteria);
        fixedCriteria.add(fixedCriterion);
        return new Mapping(key, resourceType, termCodeSearchParameter, valueFilterMapping,
                fixedCriteria, attributeMappings, timeRestrictionParameter);
    }

    public Mapping appendAttributeMapping(AttributeMapping attributeMapping) {
        var attributeMappings = new HashMap<>(this.attributeMappings);
        attributeMappings.put(attributeMapping.key(), attributeMapping);
        return new Mapping(key, resourceType, termCodeSearchParameter, valueFilterMapping,
                fixedCriteria, attributeMappings, timeRestrictionParameter);
    }

    public Mapping withTimeRestrictionParameter(String timeRestrictionParameter) {
        return new Mapping(key, resourceType, termCodeSearchParameter, valueFilterMapping,
                fixedCriteria, attributeMappings, requireNonNull(timeRestrictionParameter));
    }

    public ContextualTermCode key() {
        return key;
    }

    public String resourceType() {
        return resourceType;
    }

    public String termCodeSearchParameter() {
        return termCodeSearchParameter;
    }

    public Optional<FilterMapping> valueFilterMapping() {
        return Optional.ofNullable(valueFilterMapping);
    }

    public List<FixedCriterion> fixedCriteria() {
        return fixedCriteria;
    }

    public Either<Exception, AttributeMapping> findAttributeMapping(TermCode code) {
        AttributeMapping mapping = attributeMappings.get(code);
        return mapping == null ? Either.left(new AttributeMappingNotFoundException(key, code)) : Either.right(mapping);
    }

    public String timeRestrictionParameter() {
        return timeRestrictionParameter;
    }

    private record ValueFilterMapping(FilterMappingType type, String searchParameter)
            implements FilterMapping {

        private ValueFilterMapping {
            requireNonNull(type);
            requireNonNull(searchParameter);
        }

        @Override
        public Optional<TermCode> compositeCode() {
            return Optional.empty();
        }

        @Override
        public Either<Exception, List<ExpandedFilter>> expandReference(MappingContext mappingContext,
                                                                       Criterion criterion) {
            return Either.left(new Exception("There are no reference value filters."));
        }
    }
}
