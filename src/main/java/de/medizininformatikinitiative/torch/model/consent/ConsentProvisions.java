package de.medizininformatikinitiative.torch.model.consent;

import de.medizininformatikinitiative.torch.model.management.TermCode;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Encounter;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record ConsentProvisions(String patientId, DateTimeType dateTime, List<Provision> provisions) {

    /**
     * Adjusts the start date of provisions whose code is in {@code adjustableCodes} based on patient encounters.
     * <p>
     * Validity-gate codes (e.g. {@code ...3.8}) are excluded from adjustment — only data-period codes
     * (e.g. {@code ...3.6}) should have their collection window shifted by encounter timestamps.
     *
     * @param encounters      the patient's encounters
     * @param adjustableCodes codes whose provision start may be shifted (typically non-gate codes)
     */
    public ConsentProvisions updateByEncounters(Collection<Encounter> encounters, Set<TermCode> adjustableCodes) {
        List<Period> encounterPeriods = encounters.stream()
                .map(e -> Period.fromHapi(e.getPeriod()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        return new ConsentProvisions(
                patientId,
                dateTime,
                provisions.stream().map(provisionsPeriod -> {
                    if (!adjustableCodes.contains(provisionsPeriod.code())) {
                        return provisionsPeriod;
                    }
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
