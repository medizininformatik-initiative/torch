package de.medizininformatikinitiative.torch.model.consent;

import org.hl7.fhir.r4.model.DateTimeType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

public record Period(
        LocalDate start,
        LocalDate end
) {
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

    public static Period fromHapi(org.hl7.fhir.r4.model.Period hapiPeriod) {

        return new Period(toLocalDate(hapiPeriod.getStartElement()), toLocalDate(hapiPeriod.getEndElement()));
    }

    public static Period fromHapi(org.hl7.fhir.r4.model.DateTimeType hapiValue) {
        return new Period(toLocalDate(hapiValue), toLocalDate(hapiValue));
    }

    public boolean isStartBetween(Period period) {
        return period.start().isBefore(start) &&
                period.end().isAfter(start);
    }

    private static LocalDate toLocalDate(DateTimeType value) {
        return LocalDate.of(value.getYear(), value.getMonth() + 1, value.getDay());
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
