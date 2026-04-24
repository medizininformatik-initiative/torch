package de.medizininformatikinitiative.torch.model.consent;

import de.medizininformatikinitiative.torch.model.management.TermCode;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Defines the set of consent provision codes that TORCH supports and how they relate to each other.
 * <p>
 * Each entry describes one {@link ProspectiveEntry prospective code} — the code that must be permitted
 * in a patient's consent for extraction to proceed — and its optional {@link RetroModifier retrospective modifier},
 * which can extend the prospective code's valid period backwards in time.
 * <p>
 * The config is loaded at startup from {@code mappings/consent-code-config.json}, which can be edited manually.
 * The codes shipped with TORCH represent the recommended MII default. It is used in two places:
 * <ul>
 *     <li>{@code ConsentHandler} — to filter the codes expanded from a CRTDL combined key down to the
 *     supported prospective codes, and to determine which additional codes must be fetched from the FHIR
 *     server (i.e. the retro modifier codes).</li>
 *     <li>{@code ConsentCalculator} — to apply retrospective modifier logic before the standard
 *     permit/deny merge-and-subtract step.</li>
 * </ul>
 *
 * @param entries the list of supported prospective code entries
 * @see ProspectiveEntry
 * @see RetroModifier
 */
public record ConsentCodeConfig(List<ProspectiveEntry> entries) {

    public ConsentCodeConfig {
        requireNonNull(entries);
        entries = List.copyOf(entries);
    }

    /**
     * Returns the set of all prospective codes defined in this config.
     * <p>
     * These are the codes that must each have a non-empty allowed period for a patient's consent to be
     * considered valid.
     *
     * @return an unmodifiable set of prospective {@link TermCode}s
     */
    public Set<TermCode> prospectiveCodes() {
        return entries.stream()
                .map(ProspectiveEntry::code)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Filters the given set of codes to only those that are prospective codes in this config.
     * <p>
     * Used after expanding a CRTDL combined key to drop any individual codes that TORCH does not support
     * (e.g. Rekontaktierung codes in V1).
     *
     * @param codes the expanded set of codes from the CRTDL
     * @return an unmodifiable set containing only the codes that appear as prospective codes in this config
     */
    public Set<TermCode> filterToSupported(Set<TermCode> codes) {
        Set<TermCode> supported = prospectiveCodes();
        return codes.stream()
                .filter(supported::contains)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Returns the given prospective codes plus any retrospective modifier codes they require.
     * <p>
     * The returned set is passed to the FHIR fetcher so that both the prospective provisions and their
     * modifier provisions are available for the consent calculation.
     *
     * @param prospectiveCodes the supported prospective codes for the current request
     * @return an unmodifiable set of codes to fetch from the FHIR server
     */
    public Set<TermCode> withRetroModifiers(Set<TermCode> prospectiveCodes) {
        Set<TermCode> result = new HashSet<>(prospectiveCodes);
        entries.stream()
                .filter(e -> prospectiveCodes.contains(e.code()) && e.hasRetroModifier())
                .map(e -> e.retroModifier().code())
                .forEach(result::add);
        return Set.copyOf(result);
    }

    /**
     * Returns a map from each retrospective modifier {@link TermCode} to the {@link ProspectiveEntry} it modifies.
     * <p>
     * Used by {@code ConsentCalculator} to identify which provisions in the fetched consent data are retro
     * modifiers and which prospective code's period they should extend.
     *
     * @return an unmodifiable map keyed by retro modifier {@link TermCode}
     */
    public Map<TermCode, ProspectiveEntry> retroToProspective() {
        return entries.stream()
                .filter(ProspectiveEntry::hasRetroModifier)
                .collect(Collectors.toUnmodifiableMap(
                        e -> e.retroModifier().code(),
                        e -> e
                ));
    }
}
