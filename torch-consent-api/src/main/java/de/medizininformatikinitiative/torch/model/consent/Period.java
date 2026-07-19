package de.medizininformatikinitiative.torch.model.consent;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.BaseDateTimeType;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.InstantType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;


public record Period(
        LocalDate start,
        LocalDate end
) {

    static final Logger logger = LoggerFactory.getLogger(Period.class);

    public Period {
        requireNonNull(start);
        requireNonNull(end);
    }

    public static Period of(String start, String end) {
        requireNonNull(start, "start cannot be null");
        requireNonNull(end, "end cannot be null");
        return new Period(LocalDate.parse(start), LocalDate.parse(end));
    }


    public static Period of(LocalDate start, LocalDate end) {
        return new Period(start, end);
    }


    /**
     * Converts to a day precise Period from a HAPI time type.
     *
     * @param value base value to be converted to a period. Requires at least day precision in all values.
     * @return Optional Period (empty if unsupported / insufficient precision)
     */
    public static Optional<Period> fromHapi(Base value) {
        if (value == null) {
            return Optional.empty();
        }

        return switch (value) {
            case org.hl7.fhir.r4.model.Period p -> fromHapiPeriodSafe(p);
            case DateTimeType dt -> fromHapiDateTimeSafe(dt);
            case DateType d -> fromHapiDateSafe(d);
            case InstantType i -> fromHapiInstantSafe(i);
            default -> {
                logger.trace("Ignoring unsupported FHIR time type: {}", value.getClass().getSimpleName());
                yield Optional.empty();
            }
        };
    }

    private static Optional<Period> fromHapiPeriodSafe(
            org.hl7.fhir.r4.model.Period period
    ) {
        if (!period.hasStart() || !period.hasEnd()) {
            logger.trace("Ignoring FHIR Period without start or end");
            return Optional.empty();
        }

        if (isNotDayPrecise(period.getStartElement())
                || isNotDayPrecise(period.getEndElement())) {
            logger.trace("Ignoring FHIR Period with precision < DAY");
            return Optional.empty();
        }

        Optional<LocalDate> start =
                toLocalDateDayPrecise(period.getStartElement());

        Optional<LocalDate> end =
                toLocalDateDayPrecise(period.getEndElement());

        if (start.isEmpty() || end.isEmpty()) {
            return Optional.empty();
        }

        if (end.get().isBefore(start.get())) {
            logger.trace(
                    "Ignoring reversed FHIR Period: start={}, end={}",
                    start.get(), end.get()
            );
            return Optional.empty();
        }

        return Optional.of(new Period(start.get(), end.get()));
    }

    private static Optional<Period> fromHapiDateTimeSafe(DateTimeType dt) {
        if (!dt.hasValue()) {
            logger.trace("Ignoring empty DateTimeType");
            return Optional.empty();
        }
        if (isNotDayPrecise(dt)) return Optional.empty();
        return toLocalDateDayPrecise(dt).map(date -> new Period(date, date));
    }

    private static Optional<Period> fromHapiInstantSafe(InstantType i) {
        if (!i.hasValue()) {
            return Optional.empty();
        }
        return fromHapiDateSafe(new DateType(i.getValue()));
    }

    private static boolean isNotDayPrecise(BaseDateTimeType t) {
        return t.getPrecision().compareTo(TemporalPrecisionEnum.DAY) < 0;
    }

    private static Optional<Period> fromHapiDateSafe(DateType date) {
        if (!date.hasValue() || isNotDayPrecise(date)) {
            return Optional.empty();
        }

        return toLocalDateDayPrecise(date)
                .map(value -> new Period(value, value));
    }

    private static Optional<LocalDate> toLocalDateDayPrecise(BaseDateTimeType value) {
        Integer year = value.getYear();
        Integer month = value.getMonth();
        Integer day = value.getDay();

        if (year == null || month == null || day == null) {
            logger.warn(
                    "Incomplete FHIR date: type={}, value={}, precision={}, year={}, month={}, day={}",
                    value.getClass().getSimpleName(),
                    value.primitiveValue(),
                    value.getPrecision(),
                    year, month, day
            );
            return Optional.empty();
        }

        try {
            return Optional.of(LocalDate.of(year, month + 1, day));
        } catch (DateTimeException e) {
            logger.warn(
                    "Invalid FHIR date components: value={}, precision={}, year={}, month={}, day={}",
                    value.primitiveValue(),
                    value.getPrecision(),
                    year, month, day,
                    e
            );
            return Optional.empty();
        }
    }

    public boolean isStartBetween(Period period) {
        return period.start().isBefore(start) &&
                !period.end().isBefore(start);
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(start) && !date.isAfter(end);
    }

    public Period intersect(Period other) {
        LocalDate maxStart = start.isAfter(other.start) ? start : other.start;
        LocalDate minEnd = end.isBefore(other.end) ? end : other.end;
        if (!maxStart.isAfter(minEnd)) {
            return new Period(maxStart, minEnd);
        }
        return null; // no overlap
    }

    public List<Period> subtract(Period deny) {
        List<Period> result = new ArrayList<>();

        // No overlap → keep original period
        if (this.intersect(deny) == null) {
            result.add(this);
            return result;
        }

        // Left part (before deny)
        if (deny.start().isAfter(this.start)) {
            result.add(new Period(this.start, deny.start().minusDays(1)));
        }

        // Right part (after deny)
        if (deny.end().isBefore(this.end)) {
            result.add(new Period(deny.end().plusDays(1), this.end));
        }

        return result;
    }
}
