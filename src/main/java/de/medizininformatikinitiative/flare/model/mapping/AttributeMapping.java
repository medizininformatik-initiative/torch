package de.medizininformatikinitiative.flare.model.mapping;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import de.medizininformatikinitiative.flare.Either;
import de.medizininformatikinitiative.flare.model.sq.ConceptFilterTypeNotExpandableException;
import de.medizininformatikinitiative.flare.model.sq.Criterion;
import de.medizininformatikinitiative.flare.model.sq.TermCode;
import de.medizininformatikinitiative.flare.model.sq.expanded.ExpandedCriterion;
import de.medizininformatikinitiative.flare.model.sq.expanded.ExpandedFilter;

import java.util.List;
import java.util.Optional;

import static de.medizininformatikinitiative.flare.model.mapping.FilterMappingType.*;
import static java.util.Objects.requireNonNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AttributeMapping(FilterMappingType type, TermCode key, String searchParameter,
                               Optional<TermCode> compositeCode)
        implements FilterMapping {

    public AttributeMapping {
        requireNonNull(type);
        requireNonNull(key);
        requireNonNull(searchParameter);
        requireNonNull(compositeCode);
    }

    @JsonCreator
    static AttributeMapping fromJson(@JsonProperty("attributeType") FilterMappingType type,
                                     @JsonProperty("attributeKey") JsonNode key,
                                     @JsonProperty("attributeSearchParameter") String searchParameter,
                                     @JsonProperty("compositeCode") JsonNode compositeCode)
            throws CompositeCodeNotFoundException {
        requireNonNull(type, "missing JSON property: attributeType");
        requireNonNull(key, "missing JSON property: attributeKey");
        requireNonNull(searchParameter, "missing JSON property: attributeSearchParameter");
        if (type.isCompositeType()) {
            if (compositeCode == null) {
                throw new CompositeCodeNotFoundException(type);
            }
            return new AttributeMapping(type, TermCode.fromJsonNode(key), searchParameter,
                    Optional.of(TermCode.fromJsonNode(compositeCode)));
        }
        return new AttributeMapping(type, TermCode.fromJsonNode(key), searchParameter, Optional.empty());
    }

    public static AttributeMapping code(TermCode key, String searchParameter) {
        return new AttributeMapping(CODE, key, searchParameter, Optional.empty());
    }

    public static AttributeMapping coding(TermCode key, String searchParameter) {
        return new AttributeMapping(CONCEPT, key, searchParameter, Optional.empty());
    }

    public static AttributeMapping compositeComparator(TermCode key, String searchParameter, TermCode compositeCode) {
        return new AttributeMapping(COMPOSITE_QUANTITY, key, searchParameter, Optional.of(compositeCode));
    }

    public static AttributeMapping compositeRange(TermCode key, String searchParameter, TermCode compositeCode) {
        return new AttributeMapping(COMPOSITE_QUANTITY, key, searchParameter, Optional.of(compositeCode));
    }

    public static AttributeMapping compositeConcept(TermCode key, String searchParameter, TermCode compositeCode) {
        return new AttributeMapping(COMPOSITE_CONCEPT, key, searchParameter, Optional.of(compositeCode));
    }

    public static AttributeMapping reference(TermCode key, String searchParameter) {
        return new AttributeMapping(REFERENCE, key, searchParameter, Optional.empty());
    }

    @Override
    public Either<Exception, List<ExpandedFilter>> expandReference(MappingContext mappingContext, Criterion criterion) {
        return type == REFERENCE
                ? criterion.expand(mappingContext)
                .map(expandedCriteria -> expandedCriteria.stream()
                        .map(ExpandedCriterion::filter)
                        .map(filter -> filter.chain(searchParameter))
                        .toList())
                : Either.left(new ConceptFilterTypeNotExpandableException(type));
    }
}
