package de.medizininformatikinitiative.torch.model.consent;

import org.hl7.fhir.r4.model.Encounter;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public record Provisions(Map<String, NonContinuousPeriod> periods) {

    public Provisions {
        periods = Map.copyOf(periods);
    }

    public static Provisions of() {
        return new Provisions(Map.of());
    }

    /**
     * Helper method to update consent provisions for a patient based on their encounters.
     *
     * <p>This method adjusts the start dates of consent provisions to align with the start dates of the patient's
     * encounters, ensuring that consents are valid during the provisions of active encounters.*
     *
     * @param encounters A list of {@link Encounter} resources associated with the patient.
     * @return ConsentInfo with updated period info
     */
    public Provisions updateConsentPeriodsByPatientEncounters(Collection<Encounter> encounters) {
        Objects.requireNonNull(encounters, "Encounters list cannot be null");

        // If the encounter list is empty, return the same Provisions object without any changes.
        if (encounters.isEmpty()) {
            return this;
        }

        // Create a new map for updated periods, starting with the original periods
        Map<String, NonContinuousPeriod> newPeriods = new HashMap<>(periods);

        // Process each encounter
        for (Encounter encounter : encounters) {
            Period encounterPeriod = Period.fromHapi(encounter.getPeriod());


            // Update consent periods based on the encounter
            for (Map.Entry<String, NonContinuousPeriod> entry : newPeriods.entrySet()) {
                NonContinuousPeriod consentPeriods = entry.getValue();

                // Ensure the update is done correctly by calling the update method
                NonContinuousPeriod updatedConsentPeriod = consentPeriods.update(encounterPeriod);


                // Store the updated period in the map for that specific consent key
                entry.setValue(updatedConsentPeriod);
            }
        }

        // Return the new Provisions instance with the updated periods
        return new Provisions(newPeriods);
    }

    public static Provisions merge(Collection<Provisions> provisions) {
        return new Provisions(provisions.stream().flatMap(map -> map.periods.entrySet().stream()).collect(
                Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        NonContinuousPeriod::merge
                )
        ));
    }

    public boolean isEmpty() {
        return periods.isEmpty();
    }
}
