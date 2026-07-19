package de.medizininformatikinitiative.torch.consent.mii;

import de.medizininformatikinitiative.torch.consent.ConsentContext;
import de.medizininformatikinitiative.torch.consent.ConsentEvaluator;
import de.medizininformatikinitiative.torch.consent.ConsentFormatException;
import de.medizininformatikinitiative.torch.consent.PatientSet;
import de.medizininformatikinitiative.torch.consent.mii.model.ConsentCodeConfig;
import de.medizininformatikinitiative.torch.consent.mii.model.TermCode;
import de.medizininformatikinitiative.torch.model.consent.NonContinuousPeriod;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Default {@link ConsentEvaluator} implementation, applying the MII Broad Consent Module model:
 * consent criteria are recognized via the {@code "Einwilligung"} context code, consent/encounter
 * data is fetched from FHIR MII profiles, and the effective consent window is computed via the
 * gate/data-period/retro-modifier rules in {@link ConsentCodeConfig}.
 */
public class MiiConsentEvaluator implements ConsentEvaluator {

    private final CrtdlConsentValidator crtdlConsentValidator;
    private final ConsentCodeConfig consentCodeConfig;
    private final ConsentFetcher consentFetcher;
    private final ConsentAdjuster consentAdjuster;
    private final ConsentCalculator consentCalculator;

    public MiiConsentEvaluator(CrtdlConsentValidator crtdlConsentValidator,
                                ConsentCodeConfig consentCodeConfig,
                                ConsentFetcher consentFetcher,
                                ConsentAdjuster consentAdjuster,
                                ConsentCalculator consentCalculator) {
        this.crtdlConsentValidator = requireNonNull(crtdlConsentValidator);
        this.consentCodeConfig = requireNonNull(consentCodeConfig);
        this.consentFetcher = requireNonNull(consentFetcher);
        this.consentAdjuster = requireNonNull(consentAdjuster);
        this.consentCalculator = requireNonNull(consentCalculator);
    }

    @Override
    public boolean validate(ConsentContext crtdl) throws ConsentFormatException {
        Optional<Set<TermCode>> consentCodes = crtdlConsentValidator.extractConsentCodes(crtdl);
        if (consentCodes.isPresent()) {
            consentCodeConfig.validateCodeCoOccurrence(consentCodes.get());
        }
        return true;
    }

    @Override
    public Mono<Optional<Map<String, NonContinuousPeriod>>> evaluate(ConsentContext crtdl, PatientSet batch) {
        Optional<Set<TermCode>> maybeConsentCodes;
        try {
            maybeConsentCodes = crtdlConsentValidator.extractConsentCodes(crtdl);
        } catch (ConsentFormatException e) {
            return Mono.error(e);
        }
        if (maybeConsentCodes.isEmpty()) {
            return Mono.just(Optional.empty());
        }
        Set<TermCode> consentCodes = maybeConsentCodes.get();

        Set<TermCode> prospectiveCodes = consentCodeConfig.extractRequestedProspectiveCodes(consentCodes);
        Set<TermCode> codesToFetch = consentCodeConfig.withRetroModifiers(prospectiveCodes, consentCodes);
        Set<TermCode> encounterAdjustCodes = consentCodeConfig.nonGateCodes(prospectiveCodes);

        return consentFetcher.fetchConsentInfo(codesToFetch, batch.ids())
                .flatMap(consentProvisions ->
                        consentAdjuster.fetchEncounterAndAdjustByEncounter(batch.ids(), consentProvisions, encounterAdjustCodes)
                )
                .map(consentProvisions -> consentCalculator.calculateConsent(prospectiveCodes, consentProvisions))
                .map(Optional::of);
    }
}
