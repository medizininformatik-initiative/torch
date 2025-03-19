package de.medizininformatikinitiative.torch.consent;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.consent.Provisions;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.fhir.QueryParams;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.service.DataStore;
import de.medizininformatikinitiative.torch.util.ResourceUtils;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.Encounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.stringValue;

/**
 * The {@code ConsentHandler} class is responsible for managing and verifying patient consents
 * within the Torch application.
 * <p>Key functionalities include:
 * <ul>
 *     <li>Checking patient consent based on FHIR resources.</li>
 *     <li>Building consent information for a batch of patients.</li>
 *     <li>Updating consent provisions based on patient encounters.</li>
 * </ul>
 *
 * @see DataStore
 * @see ConsentCodeMapper
 * @see ConsentProcessor
 */
public class ConsentHandler {

    private static final Logger logger = LoggerFactory.getLogger(ConsentHandler.class);
    public static final String CDS_CONSENT_PROFILE_URL = "https://www.medizininformatik-initiative.de/fhir/modul-consent/StructureDefinition/mii-pr-consent-einwilligung";
    public static final String CDS_ENCOUNTER_PROFILE_URL = "https://www.medizininformatik-initiative.de/fhir/core/modul-fall/StructureDefinition/KontaktGesundheitseinrichtung";
    private final DataStore dataStore;
    private final ConsentCodeMapper mapper;
    private final JsonNode resourcetoField;
    private final FhirContext ctx;
    private final ConsentProcessor consentProcessor;

    /**
     * Constructs a new {@code ConsentHandler} with the specified dependencies.
     *
     * @param dataStore   The {@link DataStore} service for Server Calls.
     * @param mapper      The {@link ConsentCodeMapper} for mapping consent codes.
     * @param profilePath The file system path to the consent profile mapping configuration.
     * @throws IOException If an error occurs while reading the mapping profile file.
     */
    public ConsentHandler(DataStore dataStore, ConsentCodeMapper mapper, String profilePath, FhirContext ctx, ObjectMapper objectMapper) throws IOException {
        this.dataStore = dataStore;
        this.mapper = mapper;
        this.ctx = ctx;
        this.consentProcessor = new ConsentProcessor(ctx);
        resourcetoField = objectMapper.readTree(new File(profilePath).getAbsoluteFile());
    }


    /**
     * Returns a Mono which will emit a {@code ConsentInfo} for the given consent key and batch.
     * <p>
     * This method fetches Consent resources from a FHIR server and extracts provisions according to the consent key.
     *
     * @param consentKey Consent consentKey for which the ConsentInfo should be built.
     * @param batch      Batch of patient IDs.
     * @return {@link Mono<PatientBatchWithConsent>} containing all required provisions by patient with valid times.
     */
    public Mono<PatientBatchWithConsent> fetchAndBuildConsentInfo(String consentKey, PatientBatchWithConsent batch) {
        return buildingConsentInfo(consentKey, batch)
                .flatMap(this::updateConsentPeriodsByPatientEncounters);

    }


    /**
     * Builds consent information for a batch of patients based on the provided key and patient IDs.
     *
     * <p>This method retrieves relevant consent resources, processes them, and structures the consent
     * information in a map organized by patient ID and consent codes.
     *
     * @param key   A string key used to retrieve relevant consent codes from the {@link ConsentCodeMapper}.
     * @param batch A list of patient IDs to process in this batch.
     * @return A {@link Flux} emitting maps containing consent information structured by patient ID and consent codes.
     */
    public Mono<PatientBatchWithConsent> buildingConsentInfo(String key, PatientBatchWithConsent batch) {
        Objects.requireNonNull(batch, "Patient batch cannot be null");
        Set<String> codes = mapper.getRelevantCodes(key);

        logger.debug("Starting to build consent info for key: {} with batch size: {}", key, batch.bundles().size());
        String type = "Consent";
        Query query = Query.of(type, QueryParams.of("_profile", stringValue(CDS_CONSENT_PROFILE_URL))
                .appendParams(batch.compartmentSearchParam(type)));

        return dataStore.search(query)
                .cast(Consent.class)
                .doOnSubscribe(subscription -> logger.trace("Fetching resources for batch: {}", batch.bundles().keySet()))
                .doOnNext(resource -> logger.trace("Consent resource with id {} fetched for ConsentBuild", resource.getIdPart()))
                .flatMap(consent -> {
                    try {
                        String patientId = ResourceUtils.patientId(consent);
                        logger.trace("Processing consent for patient {}", patientId);

                        // Transform to provisions
                        Provisions newProvisions = consentProcessor.transformToConsentPeriodByCode(consent, codes);

                        // Collect multiple provisions per patient
                        return Mono.just(Map.entry(patientId, newProvisions));
                    } catch (ConsentViolatedException | PatientIdNotFoundException e) {
                        logger.warn("Skipping patient {} due to consent violation: {}", consent.getId(), e.getMessage());
                        return Mono.empty(); // Log and continue without throwing an error
                    }
                })
                .collectMultimap(Map.Entry::getKey, Map.Entry::getValue) // Collect multiple provisions per patient
                .map(provisionMap -> {
                    // Merge all provisions for each patient
                    Map<String, Provisions> mergedProvisions = provisionMap.entrySet().stream()
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    entry -> Provisions.merge(entry.getValue()) // Merge multiple provisions
                            ));

                    // Apply merged provisions to the batch
                    Map<String, PatientResourceBundle> updatedBundles = batch.bundles().entrySet().stream()
                            .map(entry -> {
                                String patientId = entry.getKey();
                                PatientResourceBundle patientBundle = entry.getValue();

                                // Apply merged provisions if available
                                if (mergedProvisions.containsKey(patientId)) {
                                    return Map.entry(patientId, patientBundle.updateConsent(mergedProvisions.get(patientId)));
                                }
                                return entry;
                            })
                            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue)); // Keep immutability

                    return new PatientBatchWithConsent(updatedBundles, batch.applyConsent());
                });
    }

    /**
     * Updates consent provisions based on patient encounters for a given batch.
     *
     * <p>This method retrieves all encounters associated with the patients in the batch and updates
     * their consent provisions accordingly. It ensures that consents are valid in the context of the
     * patient's encounters.
     *
     * @param patientBundles A {@link Flux} emitting maps of consent information structured by patient ID and consent codes.
     * @param batch          A list of patient IDs to process in this batch.
     * @return A {@link Flux} emitting updated maps of consent information.
     */
    public Mono<PatientBatchWithConsent> updateConsentPeriodsByPatientEncounters(
            PatientBatchWithConsent batch) {
        Objects.requireNonNull(batch, "Patient batch cannot be null");
        logger.debug("Starting to update consent info with batch size: {}", batch.keySet().size());
        String type = "Encounter";

        Map<String, PatientResourceBundle> filteredBundles = batch.bundles().entrySet().stream()
                .filter(entry -> entry.getValue().provisions() != null && !entry.getValue().provisions().periods().isEmpty())
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue)); // Ensures immutability

        PatientBatchWithConsent updateBatch = new PatientBatchWithConsent(filteredBundles, batch.applyConsent());


        Query query = Query.of(type, QueryParams.of("_profile", stringValue(CDS_ENCOUNTER_PROFILE_URL))
                .appendParams(updateBatch.compartmentSearchParam(type)));
        Flux<Encounter> allEncountersFlux = dataStore.search(query)
                .cast(Encounter.class)
                .doOnSubscribe(subscription -> logger.trace("Fetching encounters for batch: {}", updateBatch.keySet()))
                .doOnNext(encounter -> logger.trace("Encounter fetched: {}", encounter.getIdElement().getIdPart()));

        // Step 2: Group the encounters by patient ID
        Mono<Map<String, Collection<Encounter>>> encountersByPatientMono = groupEncounterByPatient(allEncountersFlux);

        // Step 3: Process each patient's consent info individually
        return encountersByPatientMono.map(encountersByPatientMap -> {
            updateBatch.bundles().forEach((patientId, patientBundle) -> {
                Collection<Encounter> patientEncounters = encountersByPatientMap.get(patientId);
                if (patientEncounters == null || patientEncounters.isEmpty()) {
                    logger.warn("No encounters found for patient {}", patientId);
                } else {
                    patientBundle.updateConsentPeriodsByPatientEncounters(patientEncounters);
                }
            });

            return updateBatch; // Return the updated batch with modified consent periods
        });
    }

    private static Mono<Map<String, Collection<Encounter>>> groupEncounterByPatient(Flux<Encounter> allEncountersFlux) {
        return allEncountersFlux
                .flatMap(encounter -> {
                    try {
                        String patientId = ResourceUtils.patientId(encounter);
                        return Mono.just(Tuples.of(patientId, encounter));
                    } catch (PatientIdNotFoundException e) {
                        return Mono.error(e);
                    }
                }).collectMultimap(Tuple2::getT1, Tuple2::getT2);
    }


}
