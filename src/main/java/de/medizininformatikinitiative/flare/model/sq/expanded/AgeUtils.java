package de.medizininformatikinitiative.flare.model.sq.expanded;

import de.medizininformatikinitiative.flare.Either;
import de.medizininformatikinitiative.flare.model.sq.CalculationException;
import de.medizininformatikinitiative.flare.model.sq.Comparator;
import de.medizininformatikinitiative.flare.model.sq.Quantity;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class AgeUtils {

    /**
     * Calculates the birth date of a patient from the given age.
     *
     * @param today the day to base the calculation on
     * @param age   the value of the age
     * @return a {@link Mono} containing either the birth date or an {@link CalculationException} if the unit of age
     * is unknown
     */
    private static Either<Exception, LocalDate> birthDate(LocalDate today, Quantity.WithUnit age) {
        return switch (age.unit().code()) {
            case "a" -> Either.right(today.minusYears(age.value().intValue()));
            case "mo" -> Either.right(today.minusMonths(age.value().intValue()));
            case "wk" -> Either.right(today.minusWeeks(age.value().intValue()));
            default -> Either.left(new CalculationException("Unknown age unit `%s`.".formatted(age.unit().code())));
        };
    }

    private static Either<Exception, LocalDate> equalCaseLowerDate(LocalDate today, Quantity.WithUnit age) {
        return birthDate(today, age.add(Quantity.of(BigDecimal.ONE, age.unit()))).map(date -> date.plusDays(1));
    }

    private static Either<Exception, LocalDate> equalCaseUpperDate(LocalDate today, Quantity.WithUnit age) {
        return birthDate(today, age);
    }

    public static Either<Exception, List<ExpandedFilter>> expandedAgeFilterFromComparator(LocalDate today,
                                                                                          Comparator comparator,
                                                                                          Quantity.WithUnit age) {
        if (comparator.equals(Comparator.EQUAL)) {
            return equalCaseLowerDate(today, age)
                    .flatMap(lowerBound -> equalCaseUpperDate(today, age)
                            .map(upperBound -> List.of(new ExpandedDateRangeFilter("birthdate", lowerBound, upperBound))));
        } else {
            return birthDate(today, age).map(birthDate ->
                    List.of(new ExpandedDateComparatorFilter("birthdate", comparator.reverse(), birthDate)));
        }
    }

    public static Either<Exception, List<ExpandedFilter>> expandedAgeFilterFromRange(LocalDate today,
                                                                                     Quantity.WithUnit ageLowerBound,
                                                                                     Quantity.WithUnit ageUpperBound) {
        return birthDate(today, ageLowerBound)
                .flatMap(upperBoundDate -> birthDate(today, ageUpperBound)
                        .map(lowerBoundDate -> List.of(new ExpandedDateRangeFilter("birthdate", lowerBoundDate,
                                upperBoundDate))));
    }
}
