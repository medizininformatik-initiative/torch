package de.medizininformatikinitiative.torch.model.consent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.medizininformatikinitiative.torch.model.management.TermCode;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Represents a supported prospective consent provision code, its role, its co-occurrence constraints,
 * and its optional retrospective modifiers.
 * <p>
 * A <em>validity-gate</em> entry (e.g. {@code ...3.8}) checks that today falls within the patient's
 * permitted period for this code — the patient is excluded if the check fails. A non-gate entry
 * (e.g. {@code ...3.6}) provides the actual data-extraction window.
 * <p>
 * {@code required} lists the codes that must appear alongside this one in the CRTDL cohort definition.
 * The constraint is validated symmetrically: if this code is present, all listed codes must be present too.
 * <p>
 * {@code retroModifiers} are the codes that trigger the retroactive extension. The {@code lookbackDate}
 * is how far back the grant reaches — it is a property of the prospective entry, not each modifier code.
 *
 * @param code           the FHIR provision code for this entry
 * @param validityGate   {@code true} if today must fall within the permitted period (gate check);
 *                       {@code false} if the period is used as the data-extraction window
 * @param required       codes that must co-occur with this entry in the CRTDL
 * @param retroModifiers optional retrospective modifier codes; empty list for standalone entries
 * @param lookbackDate   how far back the retroactive grant reaches; {@code null} for entries without modifiers
 * @see RetroModifier
 * @see ConsentCodeConfig
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProspectiveEntry(TermCode code, boolean validityGate, List<TermCode> required,
                               List<RetroModifier> retroModifiers, LocalDate lookbackDate) {

    public ProspectiveEntry {
        requireNonNull(code);
        required = required == null ? List.of() : List.copyOf(required);
        retroModifiers = retroModifiers == null ? List.of() : List.copyOf(retroModifiers);
    }

    /**
     * @param system         the OID/URI identifying the coding system (also used to build {@code required} term codes)
     * @param code           the provision code within that system
     * @param validityGate   {@code null} is treated as {@code false}
     * @param required       sibling code strings within the same system; {@code null} treated as empty
     * @param retroModifiers already-deserialized modifier entries; {@code null} treated as empty
     * @param lookbackDate   ISO-8601 date string ({@code "1900-01-01"}); {@code null} for entries without modifiers
     */
    @JsonCreator
    public static ProspectiveEntry fromJson(
            @JsonProperty("system") String system,
            @JsonProperty("code") String code,
            @JsonProperty("validityGate") Boolean validityGate,
            @JsonProperty("required") List<String> required,
            @JsonProperty("retroModifiers") List<RetroModifier> retroModifiers,
            @JsonProperty("lookbackDate") String lookbackDate) {
        String sys = requireNonNull(system, "system");
        List<TermCode> requiredCodes = required == null ? List.of()
                : required.stream().map(c -> new TermCode(sys, c)).collect(Collectors.toList());
        return new ProspectiveEntry(
                new TermCode(sys, code),
                validityGate != null && validityGate,
                requiredCodes,
                retroModifiers == null ? List.of() : retroModifiers,
                lookbackDate != null ? LocalDate.parse(lookbackDate) : null
        );
    }

    /** Returns {@code true} if this entry has at least one retrospective modifier configured. */
    public boolean hasRetroModifiers() {
        return !retroModifiers.isEmpty();
    }
}
