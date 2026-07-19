package de.medizininformatikinitiative.torch.consent.mii;

import de.medizininformatikinitiative.torch.consent.ConsentDataClient;
import de.medizininformatikinitiative.torch.consent.ConsentViolatedException;
import de.medizininformatikinitiative.torch.consent.mii.model.ConsentProvisions;
import de.medizininformatikinitiative.torch.consent.mii.model.TermCode;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.DateTimeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Responsible for building patient consents from fetched Consent resources.
 *
 * @see ConsentDataClient
 * @see ProvisionExtractor
 */
public class ConsentFetcher {

    private static final Logger logger = LoggerFactory.getLogger(ConsentFetcher.class);
    private static final String CDS_CONSENT_PROFILE_URL = "https://www.medizininformatik-initiative.de/fhir/modul-consent/StructureDefinition/mii-pr-consent-einwilligung";
    private final ConsentDataClient consentDataClient;
    private final ProvisionExtractor provisionExtractor;

    /**
     * Constructs a new {@code ConsentFetcher} with the specified dependencies.
     *
     * @param consentDataClient  the {@link ConsentDataClient} for FHIR server calls
     * @param provisionExtractor the {@link ProvisionExtractor} for extracting provisions from consent resources
     */
    public ConsentFetcher(ConsentDataClient consentDataClient, ProvisionExtractor provisionExtractor) {
        this.consentDataClient = requireNonNull(consentDataClient);
        this.provisionExtractor = provisionExtractor;
    }

    /**
     * Builds consent information for a batch of patients based on the provided codes and patient IDs.
     *
     * <p>This method retrieves relevant consent resources, processes them, and structures the consent
     * information in a map organized by patient ID and consent codes.
     *
     * @param codes      set of relevant consent codes
     * @param patientIds the patient IDs to process in this batch
     * @return a {@link Mono} emitting a map containing consent information structured by patient ID
     */
    public Mono<Map<String, List<ConsentProvisions>>> fetchConsentInfo(Set<TermCode> codes, List<String> patientIds) {
        logger.debug("Starting to build consent info for codes {} and {} patients", codes, patientIds.size());

        return consentDataClient.searchActiveConsentsByProfile(patientIds, CDS_CONSENT_PROFILE_URL)
                .doOnSubscribe(subscription -> logger.trace("Fetching resources for batch: {}", patientIds))
                .doOnNext(pr -> logger.trace("Consent resource with id {} fetched for ConsentBuild", pr.resource().getIdPart()))
                .filter(pr -> pr.resource().getStatus() == Consent.ConsentState.ACTIVE)
                .concatMap(pr -> {
                    Consent consent = pr.resource();
                    DateTimeType consentTime = consent.getDateTimeElement();
                    if (consentTime.isEmpty()) {
                        logger.warn("Skipping consent resource {} due to missing consent date", consent.getId());
                        return Mono.empty();
                    }
                    return Mono.just(Map.entry(pr.patientId(), consent));
                })
                .collectMultimap(Map.Entry::getKey, Map.Entry::getValue)
                .flatMap(consentsByPatient -> {
                    if (consentsByPatient.isEmpty()) {
                        return Mono.error(new ConsentViolatedException("No valid consentPeriods found for any patients in batch " + patientIds));
                    }

                    Map<String, List<ConsentProvisions>> provisionsByPatient =
                            consentsByPatient.entrySet().stream()
                                    .map(entry -> Map.entry(
                                            entry.getKey(),
                                            entry.getValue().stream()
                                                    .map(consent -> {
                                                        try {
                                                            return provisionExtractor.extractProvisionsPeriodByCode(entry.getKey(), consent, codes);
                                                        } catch (ConsentViolatedException e) {
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
                                "All patients in batch " + patientIds + " have no valid consentPeriods"));
                    }

                    return Mono.just(provisionsByPatient);
                });
    }
}
