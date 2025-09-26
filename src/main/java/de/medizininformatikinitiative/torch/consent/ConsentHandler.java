package de.medizininformatikinitiative.torch.consent;

import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import de.medizininformatikinitiative.torch.service.DataStore;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import static java.util.Objects.requireNonNull;

/**
 * The {@code ConsentHandler} class is responsible for building patient consents
 * within the Torch application and adjusting the consent periods  by encounter period.
 *
 * @see DataStore
 * @see ConsentCodeMapper
 * @see ProvisionExtractor
 */
@Component
public class ConsentHandler {


    private final ConsentFetcher consentFetcher;
    private final ConsentAdjuster consentAdjuster;
    private final ConsentCalculator consentCalculator;

    /**
     * Constructs a new {@code ConsentHandler} with the specified dependencies.
     *
     * @param consentFetcher    The {@link ConsentFetcher} for fetching and building Consent consentPeriods
     * @param consentAdjuster   The {@link ConsentAdjuster} for adjusting consent periods by encounter periods
     * @param consentCalculator The {@link ConsentCalculator} for calculating effective consent periods
     */
    public ConsentHandler(ConsentFetcher consentFetcher, ConsentAdjuster consentAdjuster, ConsentCalculator consentCalculator) {
        this.consentFetcher = requireNonNull(consentFetcher);
        this.consentAdjuster = requireNonNull(consentAdjuster);
        this.consentCalculator = requireNonNull(consentCalculator);
    }

    /**
     * Fetches and builds consent information for a batch of patients.
     * <p>
     * This method performs the following steps in a reactive pipeline:
     * <ol>
     *     <li>Fetches consent provisions from a FHIR server for the given {@code consentKey} and patient batch.</li>
     *     <li>Adjusts the fetched consent periods based on patient encounters.</li>
     *     <li>Calculates the effective consent periods per patient.</li>
     *     <li>Filters the batch to include only patients with valid consent periods.</li>
     * </ol>
     * <p>
     * If no patients have valid consent periods, the resulting {@link Mono} will emit a
     * {@link de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException}.
     *
     * @param consentKey the consent key for which consent information should be built
     * @param batch      the batch of patient IDs to process
     * @return a {@link Mono} emitting a {@link PatientBatchWithConsent} containing patients with valid consent periods
     */
    public Mono<PatientBatchWithConsent> fetchAndBuildConsentInfo(String consentKey, PatientBatch batch) {
        return consentFetcher.fetchConsentInfo(consentKey, batch)
                .flatMap(consentProvisions ->
                        consentAdjuster.fetchEncounterAndAdjustByEncounter(batch, consentProvisions)
                )
                .map(consentProvisions -> consentCalculator.calculateConsent(consentKey, consentProvisions))
                .flatMap(consentPeriodsMap ->
                        Mono.fromCallable(() -> PatientBatchWithConsent.fromBatchAndConsent(batch, consentPeriodsMap))
                );
    }

}
