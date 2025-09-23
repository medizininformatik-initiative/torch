package de.medizininformatikinitiative.torch.consent;

import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.model.consent.ConsentProvisions;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import de.medizininformatikinitiative.torch.service.DataStore;
import de.medizininformatikinitiative.torch.util.ResourceUtils;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.DateTimeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.stringValue;
import static java.util.Objects.requireNonNull;

/**
 * The {@code ConsentFetcherBuilder} class is responsible for building patient consents from fetched Consent Resources
 * within the Torch application.
 *
 * @see DataStore
 * @see ConsentCodeMapper
 * @see ProvisionExtractor
 */
@Component
public class ConsentFetcher {

    private static final Logger logger = LoggerFactory.getLogger(ConsentFetcher.class);
    private static final String CDS_CONSENT_PROFILE_URL = "https://www.medizininformatik-initiative.de/fhir/modul-consent/StructureDefinition/mii-pr-consent-einwilligung";
    private final DataStore dataStore;
    private final ConsentCodeMapper mapper;
    private final ProvisionExtractor provisionExtractor;

    /**
     * Constructs a new {@code ConsentHandler} with the specified dependencies.
     *
     * @param dataStore          The {@link DataStore} service for Server Calls.
     * @param mapper             The {@link ConsentCodeMapper} for mapping consent codes.
     * @param provisionExtractor The {@link ProvisionExtractor} for extracting provisions from consent resources.
     */
    public ConsentFetcher(DataStore dataStore, ConsentCodeMapper mapper, ProvisionExtractor provisionExtractor) {
        this.dataStore = requireNonNull(dataStore);
        this.mapper = requireNonNull(mapper);
        this.provisionExtractor = provisionExtractor;
    }

    private static Query getConsentQuery(PatientBatch batch) {
        return Query.of("Consent", batch.compartmentSearchParam("Consent").appendParam("_profile:below", stringValue(CDS_CONSENT_PROFILE_URL)));
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
    public Mono<Map<String, List<ConsentProvisions>>> fetchConsentInfo(String key, PatientBatch batch) {
        logger.debug("Starting to build consent info for key {} and {} patients", key, batch.ids().size());

        Set<String> codes = mapper.getRelevantCodes(key);

        return dataStore.search(getConsentQuery(batch), Consent.class)
                .doOnSubscribe(subscription -> logger.trace("Fetching resources for batch: {}", batch.ids()))
                .doOnNext(resource -> logger.trace("Consent resource with id {} fetched for ConsentBuild", resource.getIdPart()))
                .filter(consent -> consent.getStatus() == Consent.ConsentState.ACTIVE)
                .concatMap(consent -> {
                    try {
                        String patientId = ResourceUtils.patientId(consent);
                        DateTimeType consentTime = consent.getDateTimeElement();
                        if (consentTime.isEmpty()) {
                            logger.warn("Skipping consent resource {} due to missing consent date", consent.getId());
                            return Mono.empty();
                        }
                        return Mono.just(Map.entry(patientId, consent));
                    } catch (PatientIdNotFoundException e) {
                        logger.warn("Skipping consent resource {} due to patient not found: {}", consent.getId(), e.getMessage());
                        return Mono.empty(); // Omit invalid consents
                    }
                })
                .collectMultimap(Map.Entry::getKey, Map.Entry::getValue)
                .flatMap(consentsByPatient -> {
                    if (consentsByPatient.isEmpty()) {
                        return Mono.error(new ConsentViolatedException("No valid consentPeriods found for any patients in batch " + batch.ids()));
                    }

                    Map<String, List<ConsentProvisions>> provisionsByPatient =
                            consentsByPatient.entrySet().stream()
                                    .map(entry -> Map.entry(
                                            entry.getKey(),
                                            entry.getValue().stream()
                                                    .map(consent -> {
                                                        try {
                                                            return provisionExtractor.extractProvisionsPeriodByCode(consent, codes);
                                                        } catch (ConsentViolatedException |
                                                                 PatientIdNotFoundException e) {
                                                            logger.warn("Skipping consent {} for patient {}: {}",
                                                                    consent.getId(), entry.getKey(), e.getMessage());
                                                            return null;
                                                        }
                                                    })
                                                    .filter(Objects::nonNull)
                                                    .toList()
                                    ))
                                    .filter(e -> {
                                        if (e.getValue().isEmpty()) {
                                            logger.info("Throwing away patient {}: no valid provisions", e.getKey());
                                            return false;
                                        }
                                        return true;
                                    })
                                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

                    if (provisionsByPatient.isEmpty()) {
                        return Mono.error(new ConsentViolatedException(
                                "All patients in batch " + batch.ids() + " have no valid consentPeriods"));
                    }

                    return Mono.just(provisionsByPatient);
                });
    }
}
