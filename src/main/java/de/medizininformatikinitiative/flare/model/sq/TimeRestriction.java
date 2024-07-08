package de.medizininformatikinitiative.flare.model.sq;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.medizininformatikinitiative.flare.Either;
import de.medizininformatikinitiative.flare.model.mapping.Mapping;
import de.medizininformatikinitiative.flare.model.mapping.MappingContext;
import de.medizininformatikinitiative.flare.model.sq.expanded.ExpandedDateComparatorFilter;
import de.medizininformatikinitiative.flare.model.sq.expanded.ExpandedDateRangeFilter;
import de.medizininformatikinitiative.flare.model.sq.expanded.ExpandedFilter;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

import static de.medizininformatikinitiative.flare.model.sq.Comparator.GREATER_EQUAL;
import static de.medizininformatikinitiative.flare.model.sq.Comparator.LESS_EQUAL;
import static java.util.Objects.requireNonNull;

public interface TimeRestriction extends Filter {

    @JsonCreator
    static TimeRestriction fromJson(@JsonProperty("afterDate") String afterDate,
                                    @JsonProperty("beforeDate") String beforeDate) {
        if (afterDate == null && beforeDate == null) {
            throw new IllegalArgumentException("Missing properties expect at least one of `afterDate` or `beforeDate`.");
        } else if (afterDate == null) {
            return new OpenStart(parseLocalDate("beforeDate", beforeDate));
        } else if (beforeDate == null) {
            return new OpenEnd(parseLocalDate("afterDate", afterDate));
        } else {
            return new Interval(parseLocalDate("afterDate", afterDate), parseLocalDate("beforeDate", beforeDate));
        }
    }

    private static LocalDate parseLocalDate(String name, String s) {
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid value `%s` in time restriction property `%s`.".formatted(s, name));
        }
    }

    record OpenStart(LocalDate end) implements TimeRestriction {

        public OpenStart {
            requireNonNull(end);
        }

        @Override
        public Either<Exception, List<ExpandedFilter>> expand(MappingContext mappingContext, Mapping mapping) {
            return Either.right(List.of(new ExpandedDateComparatorFilter(mapping.timeRestrictionParameter(), LESS_EQUAL, end)));
        }
    }

    record OpenEnd(LocalDate start) implements TimeRestriction {

        public OpenEnd {
            requireNonNull(start);
        }

        @Override
        public Either<Exception, List<ExpandedFilter>> expand(MappingContext mappingContext, Mapping mapping) {
            return Either.right(List.of(new ExpandedDateComparatorFilter(mapping.timeRestrictionParameter(), GREATER_EQUAL, start)));
        }
    }

    record Interval(LocalDate start, LocalDate end) implements TimeRestriction {

        public Interval {
            requireNonNull(start);
            requireNonNull(end);
        }

        @Override
        public Either<Exception, List<ExpandedFilter>> expand(MappingContext mappingContext, Mapping mapping) {
            return Either.right(List.of(new ExpandedDateRangeFilter(mapping.timeRestrictionParameter(), start, end)));
        }
    }
}
