package de.medizininformatikinitiative.torch.model.consent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.medizininformatikinitiative.torch.model.management.TermCode;

import static java.util.Objects.requireNonNull;

/**
 * Represents a supported prospective consent provision code and its optional retrospective modifier.
 * <p>
 * A prospective code (e.g. {@code 2.16.840.1.113883.3.1937.777.24.5.3.8}) is a code that must be present
 * with a permit in the patient's consent for extraction to proceed. Its period is taken directly from the
 * FHIR Consent resource.
 * <p>
 * If a {@link RetroModifier} is configured, any permitted prospective provision whose period overlaps a
 * permitted modifier provision will have its start date shifted backwards to the modifier's
 * {@link RetroModifier#lookbackStart(java.time.LocalDate)}.
 *
 * @param code          the FHIR provision code for this prospective consent code
 * @param retroModifier the optional retrospective modifier; {@code null} for standalone prospective codes
 * @see RetroModifier
 * @see ConsentCodeConfig
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProspectiveEntry(TermCode code, RetroModifier retroModifier) {

    public ProspectiveEntry {
        requireNonNull(code);
        // retroModifier is nullable — absence means standalone prospective code
    }

    @JsonCreator
    public static ProspectiveEntry fromJson(
            @JsonProperty("system") String system,
            @JsonProperty("code") String code,
            @JsonProperty("retroModifier") RetroModifier retroModifier) {
        return new ProspectiveEntry(new TermCode(system, code), retroModifier);
    }

    /**
     * Returns {@code true} if this entry has a configured {@link RetroModifier}.
     *
     * @return {@code true} when a retrospective modifier is present
     */
    public boolean hasRetroModifier() {
        return retroModifier != null;
    }
}
