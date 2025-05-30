package de.medizininformatikinitiative.torch.consent;

import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import de.medizininformatikinitiative.torch.model.mapping.ConsentKey;
import de.medizininformatikinitiative.torch.service.DataStore;
import de.medizininformatikinitiative.torch.util.ResourceUtils;
import org.hl7.fhir.r4.model.Encounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.util.Collection;
import java.util.Map;

import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.stringValue;
import static java.util.Objects.requireNonNull;

/**
 * The {@code ConsentHandler} class is responsible for building patient consents
 * within the Torch application and adjusting the consent periods  by encounter period.
 *
 * @see DataStore
 * @see ConsentCodeMapper
 * @see ConsentProcessor
 */
public class ConsentHandler {

    private static final Logger logger = LoggerFactory.getLogger(ConsentHandler.class);
    private static final String CDS_ENCOUNTER_PROFILE_URL = "https://www.medizininformatik-initiative.de/fhir/core/modul-fall/StructureDefinition/KontaktGesundheitseinrichtung";

    private final DataStore dataStore;
    private final ConsentFetcher consentFetcher;

    /**
     * Constructs a new {@code ConsentHandler} with the specified dependencies.
     *
     * @param dataStore      The {@link DataStore} service for Server Calls.
     * @param consentFetcher The {@link ConsentFetcher} for fetching and building Consent provisions
     */
    public ConsentHandler(DataStore dataStore, ConsentFetcher consentFetcher) {
        this.dataStore = requireNonNull(dataStore);
        this.consentFetcher = consentFetcher;
    }

    private static Query getEncounterQuery(PatientBatch batch) {
        return Query.of("Encounter", batch.compartmentSearchParam("Encounter").appendParam("_profile:below", stringValue(CDS_ENCOUNTER_PROFILE_URL)));
    }

    private static Mono<Map<String, Collection<Encounter>>> groupEncounterByPatient(Flux<Encounter> encounters) {
        return encounters
                .flatMap(encounter -> {
                    try {
                        String patientId = ResourceUtils.patientId(encounter);
                        return Mono.just(Tuples.of(patientId, encounter));
                    } catch (PatientIdNotFoundException e) {
                        return Mono.error(e);
                    }
                }).collectMultimap(Tuple2::getT1, Tuple2::getT2);
    }

    /**
     * Returns a Mono which will emit a {@code ConsentInfo} for the given consent key and batch.
     * <p>
     * This method starts the fetching and consent building process from Consent resources from a FHIR server and
     * then adjust consent provisions by encounter periods.
     *
     * @param consentKey Consent consentKey for which the ConsentInfo should be built.
     * @param batch      Batch of patient IDs.
     * @return {@link Mono<PatientBatchWithConsent>} containing all required provisions by patient with valid times.
     */
    public Mono<PatientBatchWithConsent> fetchAndBuildConsentInfo(ConsentKey consentKey, PatientBatch batch) {
        return consentFetcher.buildConsentInfo(consentKey, batch)
                .flatMap(this::adjustConsentPeriodsByPatientEncounters);
    }

    /**
     * Adjusts consent periods start in a batch of patient resource bundles using their associated encounters.
     *
     * <p>Only patients with existing consent provisions are processed. Encounters are fetched and used
     * to update the consent periods for each relevant patient.</p>
     *
     * @param batch The batch containing patients and their consent data
     * @return A {@link Mono} emitting the updated batch with consent periods adjusted based on encounters
     */
    private Mono<PatientBatchWithConsent> adjustConsentPeriodsByPatientEncounters(PatientBatchWithConsent batch) {

        Flux<Encounter> encounters = dataStore.search(getEncounterQuery(batch.patientBatch()), Encounter.class)
                .doOnSubscribe(s -> logger.trace("Fetching encounters for batch: {}", batch.patientIds()));

        return groupEncounterByPatient(encounters).map(batch::adjustConsentPeriodsByPatientEncounters);
    }
}
