package de.medizininformatikinitiative.torch.consent.mii;

import de.medizininformatikinitiative.torch.consent.ConsentDataClient;
import de.medizininformatikinitiative.torch.consent.PatientResource;
import de.medizininformatikinitiative.torch.consent.mii.model.ConsentProvisions;
import de.medizininformatikinitiative.torch.consent.mii.model.TermCode;
import org.hl7.fhir.r4.model.Encounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Service responsible for adjusting consent provisions based on associated patient encounters.
 * <p>
 * This class fetches patient encounters from a FHIR server and updates the start times of
 * {@link ConsentProvisions} if the provision start falls within an encounter period.
 * </p>
 */
public class ConsentAdjuster {

    private static final Logger logger = LoggerFactory.getLogger(ConsentAdjuster.class);
    private static final String CDS_ENCOUNTER_PROFILE_URL = "https://www.medizininformatik-initiative.de/fhir/core/modul-fall/StructureDefinition/KontaktGesundheitseinrichtung";

    private final ConsentDataClient consentDataClient;

    public ConsentAdjuster(ConsentDataClient consentDataClient) {
        this.consentDataClient = requireNonNull(consentDataClient);
    }

    /**
     * Fetches encounters for all given patients and adjusts the provided consent provisions accordingly.
     * <p>
     * This method first retrieves encounters grouped by patient, then updates each
     * {@link ConsentProvisions} entry based on the periods of those encounters.
     * </p>
     *
     * @param patientIds      the patient IDs whose encounters should be fetched
     * @param provisions      a map from patient ID to their list of consent provisions to be adjusted
     * @param adjustableCodes the provision codes eligible for encounter-based start adjustment
     * @return a {@link Mono} emitting a map from patient ID to the list of adjusted provisions
     */
    public Mono<Map<String, List<ConsentProvisions>>> fetchEncounterAndAdjustByEncounter(
            List<String> patientIds,
            Map<String, List<ConsentProvisions>> provisions,
            Set<TermCode> adjustableCodes) {
        return fetchAndGroupEncounterByPatient(patientIds)
                .map(encountersByPatient ->
                        adjustProvisionsByEncounters(provisions, encountersByPatient, adjustableCodes)
                );
    }

    /**
     * Fetches all encounters for the given patients and groups them by patient ID.
     *
     * @param patientIds the patient IDs to fetch encounters for
     * @return a {@link Mono} emitting a map of patient ID to their associated encounters
     */
    private Mono<Map<String, Collection<Encounter>>> fetchAndGroupEncounterByPatient(List<String> patientIds) {
        return consentDataClient.searchEncountersByProfile(patientIds, CDS_ENCOUNTER_PROFILE_URL)
                .doOnSubscribe(s -> logger.trace("Fetching encounters for batch: {}", patientIds))
                .collectMultimap(PatientResource::patientId, PatientResource::resource);
    }


    /**
     * Pure function that adjusts a map of {@link ConsentProvisions} based on a map of patient encounters.
     * <p>
     * For each provision, if its start date falls within any of the patient's encounter periods,
     * the provision start is shifted to the earliest overlapping encounter start.
     *
     * @param provisions          a map from patient ID to their list of consent provisions to adjust
     * @param encountersByPatient a map from patient ID to their associated encounters
     * @param adjustableCodes     the provision codes eligible for encounter-based start adjustment
     * @return a map from patient ID to the list of adjusted {@link ConsentProvisions}
     */
    public Map<String, List<ConsentProvisions>> adjustProvisionsByEncounters(
            Map<String, List<ConsentProvisions>> provisions,
            Map<String, Collection<Encounter>> encountersByPatient,
            Set<TermCode> adjustableCodes
    ) {
        return provisions
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .map(cp -> cp.updateByEncounters(
                                        encountersByPatient.getOrDefault(entry.getKey(), List.of()),
                                        adjustableCodes))
                                .toList()
                ));
    }
}
