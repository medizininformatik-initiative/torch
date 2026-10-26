package de.medizininformatikinitiative.torch.model.sq.expanded;

import de.medizininformatikinitiative.torch.model.sq.CalculationException;
import de.medizininformatikinitiative.torch.model.sq.Comparator;
import de.medizininformatikinitiative.torch.model.sq.Quantity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class AgeUtils {

    /**
     * Calculates the birth date of a patient from the given age.
     *
     * @param today the day to base the calculation on
     * @param age   the value of the age
     * @return the birth date
     * @throws CalculationException if the unit of age is unknown
     */
    private static LocalDate birthDate(LocalDate today, Quantity.WithUnit age) throws CalculationException {
        return switch (age.unit().code()) {
            case "a" -> today.minusYears(age.value().intValue());
            case "mo" -> today.minusMonths(age.value().intValue());
            case "wk" -> today.minusWeeks(age.value().intValue());
            default -> throw new CalculationException("Unknown age unit `%s`.".formatted(age.unit().code()));
        };
    }

    private static LocalDate equalCaseLowerDate(LocalDate today, Quantity.WithUnit age) throws CalculationException {
        return birthDate(today, age.add(Quantity.of(BigDecimal.ONE, age.unit()))).plusDays(1);
    }

    private static LocalDate equalCaseUpperDate(LocalDate today, Quantity.WithUnit age) throws CalculationException {
        return birthDate(today, age);
    }

    public static List<ExpandedFilter> expandedAgeFilterFromComparator(LocalDate today, Comparator comparator,
                                                                       Quantity.WithUnit age)
            throws CalculationException {
        if (comparator.equals(Comparator.EQUAL)) {
            return List.of(new ExpandedDateRangeFilter("birthdate", equalCaseLowerDate(today, age),
                    equalCaseUpperDate(today, age)));
        } else {
            return List.of(new ExpandedDateComparatorFilter("birthdate", comparator.reverse(), birthDate(today, age)));
        }
    }

    public static List<ExpandedFilter> expandedAgeFilterFromRange(LocalDate today, Quantity.WithUnit ageLowerBound,
                                                                  Quantity.WithUnit ageUpperBound)
            throws CalculationException {
        var upperBoundDate = birthDate(today, ageLowerBound);
        var lowerBoundDate = birthDate(today, ageUpperBound);
        return List.of(new ExpandedDateRangeFilter("birthdate", lowerBoundDate, upperBoundDate));
    }
}
