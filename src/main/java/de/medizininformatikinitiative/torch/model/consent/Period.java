package de.medizininformatikinitiative.torch.model.consent;

import org.hl7.fhir.r4.model.DateTimeType;

import java.time.LocalDate;

import static java.util.Objects.requireNonNull;

public record Period(
        LocalDate start,
        LocalDate end
) {
    public Period {
        requireNonNull(start);
        requireNonNull(end);
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


}
