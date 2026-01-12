package de.medizininformatikinitiative.torch.model.consent;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.BaseDateTimeType;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.InstantType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static Optional<Period> fromHapiPeriodSafe(org.hl7.fhir.r4.model.Period p) {
        if (!p.hasStart() || !p.hasEnd()) {
            logger.trace("Ignoring FHIR Period without start or end");
            return Optional.empty();
        }
        if (isNotDayPrecise(p.getStartElement()) || isNotDayPrecise(p.getEndElement())) {
            logger.trace("Ignoring FHIR Period with precision < DAY");
            return Optional.empty();
        }
        return Optional.of(fromHapiPeriod(p));
    }

    private static Optional<Period> fromHapiDateTimeSafe(DateTimeType dt) {
        if (!dt.hasValue()) {
            logger.trace("Ignoring empty DateTimeType");
            return Optional.empty();
        }
        if (isNotDayPrecise(dt)) return Optional.empty();
        return Optional.of(fromHapiDateTime(dt));
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

    private static Optional<Period> fromHapiDateSafe(DateType d) {
        if (!d.hasValue() || isNotDayPrecise(d)) {
            return Optional.empty();
        }
        LocalDate date = toLocalDateDayPrecise(d);
        return Optional.of(new Period(date, date));
    }

    private static Period fromHapiPeriod(org.hl7.fhir.r4.model.Period hapiPeriod) {
        return new Period(toLocalDateDayPrecise(hapiPeriod.getStartElement()), toLocalDateDayPrecise(hapiPeriod.getEndElement()));
    }

    private static Period fromHapiDateTime(org.hl7.fhir.r4.model.DateTimeType hapiValue) {
        return new Period(toLocalDateDayPrecise(hapiValue), toLocalDateDayPrecise(hapiValue));
    }


    private static LocalDate toLocalDateDayPrecise(BaseDateTimeType value) {
        return LocalDate.of(
                value.getYear(),
                value.getMonth() + 1,
                value.getDay()
        );
    }

    public boolean isStartBetween(Period period) {
        return period.start().isBefore(start) &&
                period.end().isAfter(start);
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
