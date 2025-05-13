package de.medizininformatikinitiative.torch.consent;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.consent.Provisions;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
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

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.stringValue;
import static java.util.Objects.requireNonNull;

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
    private static final String CDS_CONSENT_PROFILE_URL = "https://www.medizininformatik-initiative.de/fhir/modul-consent/StructureDefinition/mii-pr-consent-einwilligung";
    private static final String CDS_ENCOUNTER_PROFILE_URL = "https://www.medizininformatik-initiative.de/fhir/core/modul-fall/StructureDefinition/KontaktGesundheitseinrichtung";

    private final DataStore dataStore;
    private final ConsentCodeMapper mapper;
    private final ConsentProcessor consentProcessor;

    /**
     * Constructs a new {@code ConsentHandler} with the specified dependencies.
     *
     * @param dataStore The {@link DataStore} service for Server Calls.
     * @param mapper    The {@link ConsentCodeMapper} for mapping consent codes.
     */
    public ConsentHandler(DataStore dataStore, ConsentCodeMapper mapper, FhirContext ctx) {
        this.dataStore = requireNonNull(dataStore);
        this.mapper = requireNonNull(mapper);
        this.consentProcessor = new ConsentProcessor(ctx);
    }

    private static Query getConsentQuery(PatientBatch batch) {
        return Query.of("Consent", batch.compartmentSearchParam("Consent").appendParam("_profile", stringValue(CDS_CONSENT_PROFILE_URL)));
    }

    private static Query getEncounterQuery(PatientBatch batch) {
        return Query.of("Encounter", batch.compartmentSearchParam("Encounter").appendParam("_profile", stringValue(CDS_ENCOUNTER_PROFILE_URL)));
    }

    /**
     * Creates out of merged Provisions a map of PatientResourceBundles.
     *
     * <p> Patients without provisions are filtered out, since no consent info was found for them.
     *
     * @param batch            batch of patientIds to be processed
     * @param mergedProvisions provisions grouped by patientId
     * @return map of PatientResourceBundle grouped by patientId
     */
    private static Map<String, PatientResourceBundle> createBundles(PatientBatch batch, Map<String, Provisions> mergedProvisions) {
        return batch.ids().stream()
                .filter(mergedProvisions::containsKey)
                .map(patientId -> Map.entry(patientId, new PatientResourceBundle(patientId, mergedProvisions.get(patientId))))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Takes all provisions grouped by patientId and merges them to a single one per patientId.
     *
     * @param provisions map of list of provisions grouped by patientId to be merged
     * @return map of nonempty merged provisions by patientId
     */
    private static Map<String, Provisions> mergeAllProvisions(Map<String, Collection<Provisions>> provisions) {
        return provisions.entrySet().stream()
                .flatMap(entry -> {
                    Provisions mergedProvisions = Provisions.merge(entry.getValue());
                    return mergedProvisions.isEmpty() ? Stream.empty() : Stream.of(Map.entry(entry.getKey(), mergedProvisions));
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
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
     * This method fetches Consent resources from a FHIR server and extracts provisions according to the consent key.
     *
     * @param consentKey Consent consentKey for which the ConsentInfo should be built.
     * @param batch      Batch of patient IDs.
     * @return {@link Mono<PatientBatchWithConsent>} containing all required provisions by patient with valid times.
     */
    public Mono<PatientBatchWithConsent> fetchAndBuildConsentInfo(String consentKey, PatientBatch batch) {
        return buildingConsentInfo(consentKey, batch)
                .flatMap(this::adjustConsentPeriodsByPatientEncounters);
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
    public Mono<PatientBatchWithConsent> buildingConsentInfo(String key, PatientBatch batch) {
        logger.debug("Starting to build consent info for key {} and {} patients", key, batch.ids().size());

        Set<String> codes = mapper.getRelevantCodes(key);

        return dataStore.search(getConsentQuery(batch), Consent.class)
                .doOnSubscribe(subscription -> logger.trace("Fetching resources for batch: {}", batch.ids()))
                .doOnNext(resource -> logger.trace("Consent resource with id {} fetched for ConsentBuild", resource.getIdPart()))
                .flatMap(consent -> {
                    try {
                        String patientId = ResourceUtils.patientId(consent);
                        logger.trace("Processing consent for patient {}", patientId);

                        Provisions provisions = consentProcessor.transformToConsentPeriodByCode(consent, codes);

                        return Mono.just(Map.entry(patientId, provisions));
                    } catch (ConsentViolatedException e) {
                        logger.warn("Skipping consent resource {} due to consent violation: {}", consent.getId(), e.getMessage());
                        return Mono.empty(); // Omit invalid patients
                    } catch (PatientIdNotFoundException e) {
                        logger.warn("Skipping consent resource {} due to patient not found: {}", consent.getId(), e.getMessage());
                        return Mono.empty(); // Omit invalid patients

                    }
                })
                .collectMultimap(Map.Entry::getKey, Map.Entry::getValue)
                .flatMap(provisions -> {
                    if (provisions.isEmpty()) {
                        return Mono.error(new ConsentViolatedException("No valid provisions found for any patients in batch " + batch.ids()));
                    }

                    Map<String, Provisions> mergedProvisions = mergeAllProvisions(provisions);
                    Map<String, PatientResourceBundle> bundles = createBundles(batch, mergedProvisions);
                    return Mono.just(new PatientBatchWithConsent(bundles, true));
                });
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
