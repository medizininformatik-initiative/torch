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
 * <p>
 * {@code dataPeriodOffsetYears} is subtracted from the provision's end date before the consent window is
 * evaluated. A value of 25 on a provision valid until 2050-12-31 yields an effective data-access end of
 * 2025-12-31. Use 0 (the default) to apply no offset.
 *
 * @param code                  the FHIR provision code for this prospective consent code
 * @param retroModifier         the optional retrospective modifier; {@code null} for standalone prospective codes
 * @param dataPeriodOffsetYears years to subtract from the provision end date (≥ 0)
 * @see RetroModifier
 * @see ConsentCodeConfig
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProspectiveEntry(TermCode code, RetroModifier retroModifier, int dataPeriodOffsetYears) {

    public ProspectiveEntry {
        requireNonNull(code);
        if (dataPeriodOffsetYears < 0) throw new IllegalArgumentException("dataPeriodOffsetYears must be non-negative");
    }

    public ProspectiveEntry(TermCode code, RetroModifier retroModifier) {
        this(code, retroModifier, 0);
    }

    @JsonCreator
    public static ProspectiveEntry fromJson(
            @JsonProperty("system") String system,
            @JsonProperty("code") String code,
            @JsonProperty("retroModifier") RetroModifier retroModifier,
            @JsonProperty("dataPeriodOffsetYears") Integer dataPeriodOffsetYears) {
        return new ProspectiveEntry(new TermCode(system, code), retroModifier, dataPeriodOffsetYears != null ? dataPeriodOffsetYears : 0);
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
