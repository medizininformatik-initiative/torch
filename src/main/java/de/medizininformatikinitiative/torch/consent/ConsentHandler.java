package de.medizininformatikinitiative.torch.consent;

import de.medizininformatikinitiative.torch.model.consent.ConsentCodeConfig;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import de.medizininformatikinitiative.torch.model.management.TermCode;
import de.medizininformatikinitiative.torch.service.DataStore;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * The {@code ConsentHandler} class is responsible for building patient consents
 * within the Torch application and adjusting the consent periods by encounter period.
 *
 * @see DataStore
 * @see ProvisionExtractor
 */
@Component
public class ConsentHandler {


    private final ConsentFetcher consentFetcher;
    private final ConsentAdjuster consentAdjuster;
    private final ConsentCalculator consentCalculator;
    private final ConsentCodeConfig consentCodeConfig;

    /**
     * Constructs a new {@code ConsentHandler} with the specified dependencies.
     *
     * @param consentFetcher    the {@link ConsentFetcher} for fetching and building consent provisions
     * @param consentAdjuster   the {@link ConsentAdjuster} for adjusting consent periods by encounter periods
     * @param consentCalculator the {@link ConsentCalculator} for calculating effective consent periods
     * @param consentCodeConfig the {@link ConsentCodeConfig} describing supported codes and their roles
     */
    public ConsentHandler(ConsentFetcher consentFetcher, ConsentAdjuster consentAdjuster, ConsentCalculator consentCalculator, ConsentCodeConfig consentCodeConfig) {
        this.consentFetcher = requireNonNull(consentFetcher);
        this.consentAdjuster = requireNonNull(consentAdjuster);
        this.consentCalculator = requireNonNull(consentCalculator);
        this.consentCodeConfig = requireNonNull(consentCodeConfig);
    }

    /**
     * Fetches and builds consent information for a batch of patients.
     * <p>
     * This method performs the following steps in a reactive pipeline:
     * <ol>
     *     <li>Fetches consent provisions from a FHIR server for the given {@code consentCodes} and patient batch.</li>
     *     <li>Adjusts the fetched consent periods based on patient encounters.</li>
     *     <li>Calculates the effective consent periods per patient.</li>
     *     <li>Filters the batch to include only patients with valid consent periods.</li>
     * </ol>
     * <p>
     * If no patients have valid consent periods, the resulting {@link Mono} will emit a
     * {@link de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException}.
     *
     * @param consentCodes the set of consent codes from the CRTDL for which consent information should be built
     * @param batch        the batch of patient IDs to process
     * @return a {@link Mono} emitting a {@link PatientBatchWithConsent} containing patients with valid consent periods
     */
    public Mono<PatientBatchWithConsent> fetchAndBuildConsentInfo(Set<TermCode> consentCodes, PatientBatch batch) {
        Set<TermCode> prospectiveCodes = consentCodeConfig.extractRequestedProspectiveCodes(consentCodes);
        Set<TermCode> codesToFetch = consentCodeConfig.withRetroModifiers(prospectiveCodes, consentCodes);
        Set<TermCode> encounterAdjustCodes = consentCodeConfig.nonGateCodes(prospectiveCodes);

        return consentFetcher.fetchConsentInfo(codesToFetch, batch)
                .flatMap(consentProvisions ->
                        consentAdjuster.fetchEncounterAndAdjustByEncounter(batch, consentProvisions, encounterAdjustCodes)
                )
                .map(consentProvisions -> consentCalculator.calculateConsent(prospectiveCodes, consentProvisions))
                .flatMap(consentPeriodsMap ->
                        Mono.fromCallable(() -> PatientBatchWithConsent.fromBatchAndConsent(batch, consentPeriodsMap))
                );
    }

}
