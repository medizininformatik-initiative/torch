package de.medizininformatikinitiative.torch.model.consent;

import de.medizininformatikinitiative.torch.model.management.TermCode;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Encounter;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public record ConsentProvisions(String id, String patientId, DateTimeType dateTime, List<Provision> provisions) {

    /**
     * Adjusts the start date of permitted provisions whose code is in {@code adjustableCodes} based on patient
     * encounters.
     * <p>
     * Validity-gate codes (e.g. {@code ...3.8}) are excluded from adjustment — only data-period codes
     * (e.g. {@code ...3.6}) should have their collection window shifted by encounter timestamps. Deny
     * provisions are never shifted, regardless of their code. A shifted provision carries an
     * {@link EncounterShift} with its original start and the encounter it was shifted to.
     * <p>
     * If the earliest overlapping encounter starts more than {@code maxShiftDays} days before the provision start,
     * the provision is left unshifted rather than shifted to a later-starting encounter.
     *
     * @param encounters      the patient's encounters
     * @param adjustableCodes codes whose provision start may be shifted (typically non-gate codes)
     * @param maxShiftDays    the maximum number of days a provision start may be moved back
     */
    public ConsentProvisions updateByEncounters(Collection<Encounter> encounters, Set<TermCode> adjustableCodes,
                                                int maxShiftDays) {
        List<EncounterPeriod> encounterPeriods = encounters.stream()
                .flatMap(e -> Period.fromHapi(e.getPeriod()).map(period -> new EncounterPeriod(e.getIdPart(), period)).stream())
                .toList();

        return new ConsentProvisions(
                id,
                patientId,
                dateTime,
                provisions.stream().map(provisionsPeriod -> {
                    if (!provisionsPeriod.permit() || !adjustableCodes.contains(provisionsPeriod.code())) {
                        return provisionsPeriod;
                    }
                    // earliest encounter.start where provision.start lies within encounter period; ties broken by ID
                    // so the recorded encounter does not depend on the server's result order
                    Optional<EncounterPeriod> earliest = encounterPeriods.stream()
                            .filter(encounter -> provisionsPeriod.period().isStartBetween(encounter.period()))
                            .min(Comparator.comparing((EncounterPeriod encounter) -> encounter.period().start())
                                    .thenComparing(EncounterPeriod::encounterId));

                    return earliest
                            .filter(encounter -> !encounter.period().start()
                                    .isBefore(provisionsPeriod.period().start().minusDays(maxShiftDays)))
                            .map(encounter -> new Provision(provisionsPeriod.code(),
                                    new Period(encounter.period().start(), provisionsPeriod.period().end()),
                                    provisionsPeriod.permit(),
                                    new EncounterShift(provisionsPeriod.period().start(), encounter.encounterId())))
                            .orElse(provisionsPeriod);
                }).toList()
        );
    }

    private record EncounterPeriod(String encounterId, Period period) {
    }
}
