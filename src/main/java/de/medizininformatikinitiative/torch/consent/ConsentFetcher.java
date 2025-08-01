package de.medizininformatikinitiative.torch.consent;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.consent.Provisions;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.mapping.ConsentKey;
import de.medizininformatikinitiative.torch.service.DataStore;
import de.medizininformatikinitiative.torch.util.ResourceUtils;
import org.hl7.fhir.r4.model.Consent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.stringValue;
import static java.util.Objects.requireNonNull;

/**
 * The {@code ConsentFetcherBuilder} class is responsible for building patient consents from fetched Consent Resources
 * within the Torch application.
 *
 * @see DataStore
 * @see ConsentCodeMapper
 * @see ConsentProcessor
 */
public class ConsentFetcher {

    private static final Logger logger = LoggerFactory.getLogger(ConsentFetcher.class);
    private static final String CDS_CONSENT_PROFILE_URL = "https://www.medizininformatik-initiative.de/fhir/modul-consent/StructureDefinition/mii-pr-consent-einwilligung";
    private final DataStore dataStore;
    private final ConsentCodeMapper mapper;
    private final ConsentProcessor consentProcessor;

    /**
     * Constructs a new {@code ConsentHandler} with the specified dependencies.
     *
     * @param dataStore The {@link DataStore} service for Server Calls.
     * @param mapper    The {@link ConsentCodeMapper} for mapping consent codes.
     */
    public ConsentFetcher(DataStore dataStore, ConsentCodeMapper mapper, FhirContext ctx) {
        this.dataStore = requireNonNull(dataStore);
        this.mapper = requireNonNull(mapper);
        this.consentProcessor = new ConsentProcessor(ctx);
    }

    private static Query getConsentQuery(PatientBatch batch) {
        return Query.of("Consent", batch.compartmentSearchParam("Consent").appendParam("_profile:below", stringValue(CDS_CONSENT_PROFILE_URL)));
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
    public Mono<PatientBatchWithConsent> buildConsentInfo(ConsentKey key, PatientBatch batch) {
        logger.debug("Starting to build consent info for key {} and {} patients", key, batch.ids().size());

        Set<String> codes = mapper.getRelevantCodes(key);

        return dataStore.search(getConsentQuery(batch), Consent.class)
                .doOnSubscribe(subscription -> logger.trace("Fetching resources for batch: {}", batch.ids()))
                .doOnNext(resource -> logger.trace("Consent resource with id {} fetched for ConsentBuild", resource.getIdPart()))
                .concatMap(consent -> {
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
}
