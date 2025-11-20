package de.medizininformatikinitiative.torch.consent;

import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.model.consent.ConsentProvisions;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import de.medizininformatikinitiative.torch.service.DataStore;
import de.medizininformatikinitiative.torch.util.ResourceUtils;
import org.hl7.fhir.r4.model.Encounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.stringValue;
import static java.util.Objects.requireNonNull;


/**
 * Service responsible for adjusting consent provisions based on associated patient encounters.
 * <p>
 * This class fetches patient encounters from a FHIR server and updates the start times of
 * {@link ConsentProvisions} if the provision start falls within an encounter period.
 * </p>
 */
@Component
public class ConsentAdjuster {

    private static final Logger logger = LoggerFactory.getLogger(ConsentAdjuster.class);
    private static final String CDS_ENCOUNTER_PROFILE_URL = "https://www.medizininformatik-initiative.de/fhir/core/modul-fall/StructureDefinition/KontaktGesundheitseinrichtung";

    private final DataStore dataStore;


    public ConsentAdjuster(DataStore dataStore) {
        this.dataStore = requireNonNull(dataStore);
    }


    /**
     * Fetches encounters for all patients in the given batch and adjusts the provided
     * consent provisions accordingly.
     * <p>
     * This method first retrieves encounters grouped by patient, then updates each
     * {@link ConsentProvisions} entry based on the periods of those encounters.
     * </p>
     *
     * @param batch      the {@link PatientBatch} containing patient IDs whose encounters should be fetched
     * @param provisions the list of consent provisions to be adjusted
     * @return a {@link Mono} emitting a map from patient ID to the list of adjusted provisions
     */
    public Mono<Map<String, List<ConsentProvisions>>> fetchEncounterAndAdjustByEncounter(PatientBatch batch, Map<String, List<ConsentProvisions>> provisions) {
        return fetchAndGroupEncounterByPatient(batch)
                .map(encountersByPatient ->
                        adjustProvisionsByEncounters(provisions, encountersByPatient)
                );
    }

    /**
     * Builds a FHIR Search {@code Query} to fetch all Encounters for a given patient batch
     * that conform to the CDS Encounter profile.
     *
     * @param batch The patient batch for which to fetch encounters.
     * @return A {@link Query} configured for the batch.
     */
    private static Query getEncounterQuery(PatientBatch batch) {
        return Query.of("Encounter", batch.compartmentSearchParam("Encounter").appendParam("_profile:below", stringValue(CDS_ENCOUNTER_PROFILE_URL)));
    }

    /**
     * Fetches all encounters for the patients in the batch and groups them by patient ID.
     *
     * @param batch The patient batch containing the patient IDs.
     * @return A {@link Mono} emitting a map of patient ID to their associated encounters.
     */
    private Mono<Map<String, Collection<Encounter>>> fetchAndGroupEncounterByPatient(PatientBatch batch) {
        return dataStore.search(getEncounterQuery(batch), Encounter.class)
                .doOnSubscribe(s -> logger.trace("Fetching encounters for batch: {}", batch.ids()))
                .flatMap(encounter -> {
                    try {
                        String patientId = ResourceUtils.patientId(encounter);
                        return Mono.just(Tuples.of(patientId, encounter));
                    } catch (PatientIdNotFoundException e) {
                        logger.warn("Skipping encounter without patient ID: {}", encounter.getId());
                        return Mono.empty();
                    }
                }).collectMultimap(Tuple2::getT1, Tuple2::getT2);
    }


    /**
     * Pure function that adjusts a list of {@link ConsentProvisions} based on a map of patient encounters.
     * <p>
     * For each provision, if its start date falls within any of the patient's encounter periods,
     * the provision start is shifted to the earliest overlapping encounter start.
     *
     * @param provisions          The list of consent provisions to adjust.
     * @param encountersByPatient A map from patient ID to their associated encounters.
     * @return A map from patient ID to the list of adjusted {@link ConsentProvisions}.
     */
    public Map<String, List<ConsentProvisions>> adjustProvisionsByEncounters(
            Map<String, List<ConsentProvisions>> provisions,
            Map<String, Collection<Encounter>> encountersByPatient
    ) {
        return provisions
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .map(cp -> cp.updateByEncounters(
                                        encountersByPatient.getOrDefault(entry.getKey(), List.of())))
                                .toList()
                ));
    }
}
