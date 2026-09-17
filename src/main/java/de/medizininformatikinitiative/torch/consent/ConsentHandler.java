package de.medizininformatikinitiative.torch.consent;

import de.medizininformatikinitiative.torch.diagnostics.consent.FinalPeriodEvent;
import de.medizininformatikinitiative.torch.diagnostics.consent.RawProvisionEvent;
import de.medizininformatikinitiative.torch.model.consent.ConsentCodeConfig;
import de.medizininformatikinitiative.torch.model.consent.ConsentProvisions;
import de.medizininformatikinitiative.torch.model.consent.NonContinuousPeriod;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.consent.Period;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import de.medizininformatikinitiative.torch.model.management.TermCode;
import de.medizininformatikinitiative.torch.service.DataStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
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
    private final boolean enableEncounterShift;

    /**
     * Constructs a new {@code ConsentHandler} with the specified dependencies.
     *
     * @param consentFetcher the {@link ConsentFetcher} for fetching and building consent provisions
     * @param consentAdjuster the {@link ConsentAdjuster} for adjusting consent periods by encounter periods
     * @param consentCalculator the {@link ConsentCalculator} for calculating effective consent periods
     * @param consentCodeConfig the {@link ConsentCodeConfig} describing supported codes and their roles
     * @param enableEncounterShift whether data-period provisions are shifted to overlapping encounter starts
     *                             ({@code torch.enableEncounterShift})
     */
    public ConsentHandler(ConsentFetcher consentFetcher, ConsentAdjuster consentAdjuster, ConsentCalculator consentCalculator, ConsentCodeConfig consentCodeConfig,
                           @Value("${torch.enableEncounterShift}") boolean enableEncounterShift) {
        this.consentFetcher = requireNonNull(consentFetcher);
        this.consentAdjuster = requireNonNull(consentAdjuster);
        this.consentCalculator = requireNonNull(consentCalculator);
        this.consentCodeConfig = requireNonNull(consentCodeConfig);
        this.enableEncounterShift = enableEncounterShift;
    }

    /**
     * Fetches and builds consent information for a batch of patients.
     * <p>
     * This method performs the following steps in a reactive pipeline:
     * <ol>
     *     <li>Fetches consent provisions from a FHIR server for the given {@code consentCodes} and patient batch.</li>
     *     <li>Adjusts the fetched consent periods based on patient encounters, unless disabled via
     *     {@code torch.enableEncounterShift}.</li>
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
        // registered eagerly, before any fetch/calculation, so a patient still appears in the consent-trail
        // folder (with an empty final period) even if the batch is later skipped entirely for lack of
        // consenting patients (ConsentViolatedException) — see ExtractDataService.processBatch
        batch.ids().forEach(id -> batch.diagnostics().consentDiagnostics().registerPatient(id));

        Set<TermCode> prospectiveCodes = consentCodeConfig.extractRequestedProspectiveCodes(consentCodes);
        Set<TermCode> codesToFetch = consentCodeConfig.withRetroModifiers(prospectiveCodes, consentCodes);
        Set<TermCode> encounterAdjustCodes = consentCodeConfig.nonGateCodes(prospectiveCodes);

        return consentFetcher.fetchConsentInfo(codesToFetch, batch)
                .doOnNext(rawProvisions -> recordRawProvisions(batch, rawProvisions))
                .flatMap(rawProvisions ->
                        enableEncounterShift
                                ? consentAdjuster.fetchEncounterAndAdjustByEncounter(batch, rawProvisions, encounterAdjustCodes)
                                : Mono.just(rawProvisions)
                )
                .map(consentProvisions -> consentCalculator.calculateConsent(prospectiveCodes, consentProvisions))
                .doOnNext(consentPeriodsMap -> recordFinalPeriods(batch, consentPeriodsMap))
                .flatMap(consentPeriodsMap ->
                        Mono.fromCallable(() -> PatientBatchWithConsent.fromBatchAndConsent(batch, consentPeriodsMap))
                );
    }

    /**
     * Records each fetched provision as a {@link RawProvisionEvent}, before any encounter-shift adjustment.
     * A no-op unless {@code consentDiagnostics} is enabled for this batch.
     */
    private void recordRawProvisions(PatientBatch batch, Map<String, List<ConsentProvisions>> rawProvisions) {
        if (!batch.diagnostics().consentDiagnostics().isEnabled()) {
            return;
        }
        rawProvisions.forEach((patientId, consents) -> consents.forEach(cp ->
                cp.provisions().forEach(provision -> batch.diagnostics().consentDiagnostics().addRawProvision(
                        new RawProvisionEvent(patientId, cp.id(), provision.code().code(), provision.permit(),
                                provision.period().start(), provision.period().end())))));
    }

    /**
     * Records each disjoint segment of a patient's final, intersected data-extraction period as a
     * {@link FinalPeriodEvent}. A no-op unless {@code consentDiagnostics} is enabled for this batch.
     */
    private void recordFinalPeriods(PatientBatch batch, Map<String, NonContinuousPeriod> consentPeriodsByPatient) {
        if (!batch.diagnostics().consentDiagnostics().isEnabled()) {
            return;
        }
        consentPeriodsByPatient.forEach((patientId, period) -> {
            for (int i = 0; i < period.size(); i++) {
                Period segment = period.get(i);
                batch.diagnostics().consentDiagnostics().addFinalPeriod(
                        new FinalPeriodEvent(patientId, segment.start(), segment.end()));
            }
        });
    }

}
