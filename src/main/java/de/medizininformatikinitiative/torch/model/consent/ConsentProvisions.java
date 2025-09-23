package de.medizininformatikinitiative.torch.model.consent;

import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Encounter;

import java.time.LocalDate;
import java.util.*;

public record ConsentProvisions(String patientId, DateTimeType dateTime, List<Provision> provisions) {

    public ConsentProvisions updateByEncounters(Collection<Encounter> encounters) {
        List<Period> encounterPeriods = encounters.stream()
                .map(e -> Period.fromHapi(e.getPeriod()))
                .toList();

        return new ConsentProvisions(
                patientId,
                dateTime,
                provisions.stream().map(provisionsPeriod -> {
                    // earliest encounter.start where provision.start lies within encounter period
                    Optional<LocalDate> earliestStart = encounterPeriods.stream()
                            .filter(encounterPeriod -> provisionsPeriod.period().isStartBetween(encounterPeriod))
                            .map(Period::start)
                            .filter(Objects::nonNull)
                            .min(Comparator.naturalOrder());

                    return earliestStart
                            .map(start -> new Provision(provisionsPeriod.code(), new Period(start, provisionsPeriod.period().end()), provisionsPeriod.permit()))
                            .orElse(provisionsPeriod);
                }).toList()
        );
    }


}
