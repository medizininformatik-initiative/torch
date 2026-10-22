package de.medizininformatikinitiative.torch.model.mapping;

import de.medizininformatikinitiative.torch.model.sq.*;
import de.medizininformatikinitiative.torch.model.sq.expanded.*;

import java.util.List;
import java.util.Optional;

import static de.medizininformatikinitiative.torch.model.mapping.FilterMappingType.COMPOSITE_CONCEPT;

public interface FilterMapping {

    String searchParameter();

    FilterMappingType type();

    /**
     * Returns the optional composite code of this filter mapping.
     *
     * @return the composite code or {@link Optional#empty() empty}
     */
    Optional<TermCode> compositeCode();

    default List<ExpandedFilter> expandComparatorFilterPart(MappingContext mappingContext, Comparator comparator,
                                                            Quantity value) throws CalculationException {
        if (type() == FilterMappingType.AGE) {
            if (value instanceof Quantity.WithUnit age) {
                return AgeUtils.expandedAgeFilterFromComparator(mappingContext.today(), comparator, age);
            }
            throw new CalculationException("Missing unit in age calculation.");
        }
        return List.of(compositeCode()
                .map(compositeCode -> (ExpandedFilter) new ExpandedCompositeQuantityComparatorFilter(
                        searchParameter(), compositeCode, comparator, value))
                .orElse(new ExpandedQuantityComparatorFilter(searchParameter(), comparator, value)));
    }

    default List<ExpandedFilter> expandRangeFilterPart(MappingContext mappingContext, Quantity lowerBound,
                                                       Quantity upperBound) throws CalculationException {
        if (type() == FilterMappingType.AGE) {
            if (lowerBound instanceof Quantity.WithUnit lower && upperBound instanceof Quantity.WithUnit upper) {
                return AgeUtils.expandedAgeFilterFromRange(mappingContext.today(), lower, upper);
            }
            throw new CalculationException("Missing unit in age calculation.");
        }
        return List.of(compositeCode()
                .map(compositeCode -> (ExpandedFilter) new ExpandedCompositeQuantityRangeFilter(
                        searchParameter(), compositeCode, lowerBound, upperBound))
                .orElse(new ExpandedQuantityRangeFilter(searchParameter(), lowerBound, upperBound)));
    }

    default ExpandedFilter expandConcept(TermCode concept) throws ConceptFilterTypeNotExpandableException {
        return switch (type()) {
            case CODE -> new ExpandedCodeFilter(searchParameter(), concept.code());
            case CONCEPT -> new ExpandedConceptFilter(searchParameter(), concept);
            case COMPOSITE_CONCEPT -> new ExpandedCompositeConceptFilter(searchParameter(),
                    compositeCode().orElseThrow(() -> new ConceptFilterTypeNotExpandableException(COMPOSITE_CONCEPT)),
                    concept);
            default -> throw new ConceptFilterTypeNotExpandableException(type());
        };
    }

    /**
     * Expands the {@code criterion} into a list of {@code ExpandedFilter expanded filters} that should be combined with
     * logical {@literal OR}.
     *
     * @param mappingContext the context inside which the expansion should happen
     * @param criterion      the criterion to expand
     * @return a list of {@code ExpandedFilter expanded filters}
     * @throws MappingException if the criterion cannot be expanded
     */
    List<ExpandedFilter> expandReference(MappingContext mappingContext, Criterion criterion) throws MappingException;
}
