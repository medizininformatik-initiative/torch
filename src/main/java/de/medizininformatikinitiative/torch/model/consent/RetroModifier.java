package de.medizininformatikinitiative.torch.model.consent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.medizininformatikinitiative.torch.model.management.TermCode;

import static java.util.Objects.requireNonNull;

/**
 * Identifies a retrospective modifier provision code that can extend the data window of a paired
 * prospective consent code backwards in time.
 * <p>
 * The lookback date itself is a property of the prospective entry (how far back the grant reaches),
 * not of the individual modifier code.
 *
 * @param code the FHIR provision code identifying this retrospective modifier
 * @see ProspectiveEntry
 * @see ConsentCodeConfig
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RetroModifier(TermCode code) {

    public RetroModifier {
        requireNonNull(code);
    }

    /**
     * @param system the OID/URI identifying the coding system
     * @param code   the provision code within that system
     */
    @JsonCreator
    public static RetroModifier fromJson(
            @JsonProperty("system") String system,
            @JsonProperty("code") String code) {
        return new RetroModifier(new TermCode(system, code));
    }
}
