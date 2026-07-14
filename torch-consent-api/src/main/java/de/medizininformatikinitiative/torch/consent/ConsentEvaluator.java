package de.medizininformatikinitiative.torch.consent;

import de.medizininformatikinitiative.torch.model.consent.NonContinuousPeriod;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;

/**
 * Extension point for consent handling. Torch's default implementation applies the MII Broad Consent
 * Module model ({@code torch-consent-mii}); a replacement implementation can be dropped onto the
 * classpath (see the project README for the plugin mechanism) to apply a different consent model
 * entirely, without rebuilding Torch itself.
 */
public interface ConsentEvaluator {

    /**
     * Validates that this CRTDL's consent-related criteria are well-formed. Called once per CRTDL,
     * at submission time, before any batch runs. A trivial implementation that doesn't need
     * CRTDL-level validation can simply return {@code true}.
     *
     * @param crtdl the CRTDL being submitted
     * @return {@code true} if the CRTDL passes consent-related validation
     * @throws ConsentFormatException when validation fails and there is something specific to say about why
     */
    boolean validate(ConsentContext crtdl) throws ConsentFormatException;

    /**
     * Computes the effective consent window per patient. Called once per patient batch.
     *
     * @param crtdl the CRTDL the batch is being processed for
     * @param batch the batch of patient IDs to evaluate
     * @return a {@link Mono} emitting {@link Optional#empty()} if this CRTDL has no consent
     * requirement at all (no patient should be restricted), or {@link Optional#of} a map from
     * patient ID to their allowed {@link NonContinuousPeriod} otherwise. An empty map means a
     * consent requirement exists but no patient in the batch currently qualifies.
     */
    Mono<Optional<Map<String, NonContinuousPeriod>>> evaluate(ConsentContext crtdl, PatientSet batch);
}
