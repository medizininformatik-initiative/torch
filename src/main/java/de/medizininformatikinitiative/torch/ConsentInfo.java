package de.medizininformatikinitiative.torch;

import com.google.common.collect.Streams;
import org.hl7.fhir.r4.model.Encounter;

import java.time.LocalDate;
import java.util.*;

import static java.util.Objects.requireNonNull;

/**
 * @param patientId Patient ID
 * @param periods   Map of required Provision Codes with their valid Periods
 */
public record ConsentInfo(
        String patientId,
        Map<String, NonContinousPeriod> periods
) {
    public ConsentInfo {
        requireNonNull(patientId);
        periods = Map.copyOf(periods);
    }

    /**
     * Helper method to update consent periods for a patient based on their encounters.
     *
     * <p>This method adjusts the start dates of consent periods to align with the start dates of the patient's
     * encounters, ensuring that consents are valid during the periods of active encounters.*
     *
     * @param encounters A list of {@link Encounter} resources associated with the patient.
     * @return ConsentInfo with updated period info
     */
    public ConsentInfo updateConsentPeriodsByPatientEncounters(Collection<Encounter> encounters) {
        Objects.requireNonNull(encounters, "Encounters list cannot be null");
        Map<String, NonContinousPeriod> newPeriods = new HashMap<>(periods);
        for (Encounter encounter : encounters) {
            Period encounterPeriod = Period.fromHapi(encounter.getPeriod());

            for (Map.Entry<String, NonContinousPeriod> entry : periods.entrySet()) {
                NonContinousPeriod consentPeriods = entry.getValue();
                newPeriods.put(entry.getKey(), consentPeriods.update(encounterPeriod));
            }

        }
        return new ConsentInfo(patientId, newPeriods);
    }

    public record NonContinousPeriod(
            List<Period> periods
    ) {
        public NonContinousPeriod {
            periods = List.copyOf(periods);
        }

        public NonContinousPeriod merge(NonContinousPeriod other) {
            return new NonContinousPeriod(Streams.concat(periods.stream(), other.periods.stream()).toList());
        }

        public NonContinousPeriod update(Period encounterPeriod) {
            return new NonContinousPeriod(
                    periods.stream()
                            .map(consentPeriod -> {
                                if (encounterPeriod.isStartBetween(consentPeriod)) {
                                    return new Period(encounterPeriod.start, consentPeriod.end);
                                }
                                return consentPeriod;
                            }).toList()
            );
        }

        public boolean within(Period resourcePeriod) {
            return periods.stream().anyMatch(period ->
                    resourcePeriod.start.isAfter(period.start) && resourcePeriod.end.isBefore(period.end));
        }
    }

    public record Period(
            LocalDate start,
            LocalDate end
    ) {
        public Period {
            requireNonNull(start);
            requireNonNull(end);
        }

        public static Period fromHapi(org.hl7.fhir.r4.model.Period hapiPeriod) {
            return new Period(LocalDate.parse(hapiPeriod.getStartElement().asStringValue()), LocalDate.parse(hapiPeriod.getEndElement().asStringValue()));
        }

        public static Period fromHapi(org.hl7.fhir.r4.model.DateTimeType hapieValue) {
            return new Period(LocalDate.parse(hapieValue.asStringValue()), LocalDate.parse(hapieValue.asStringValue()));
        }

        public boolean isStartBetween(Period period) {
            return period.start().isBefore(start) &&
                    period.end().isAfter(start);
        }


    }

}
