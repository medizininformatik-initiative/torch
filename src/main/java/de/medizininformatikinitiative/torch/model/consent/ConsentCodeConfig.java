package de.medizininformatikinitiative.torch.model.consent;

import de.medizininformatikinitiative.torch.exceptions.ConsentFormatException;
import de.medizininformatikinitiative.torch.model.management.TermCode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Defines the set of consent provision codes that TORCH supports and how they relate to each other.
 * <p>
 * Each entry describes one {@link ProspectiveEntry prospective code}. Entries declare:
 * <ul>
 *     <li>whether they act as a validity gate (today-in-period check) or provide the data-extraction window;</li>
 *     <li>which other codes must co-occur with them in the CRTDL;</li>
 *     <li>which retrospective modifier codes can extend their data window backwards in time.</li>
 * </ul>
 * <p>
 * The config is loaded at startup from {@code mappings/consent-code-config.json}.
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
     */
    public Set<TermCode> prospectiveCodes() {
        return entries.stream()
                .map(ProspectiveEntry::code)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Returns the subset of config prospective codes that appear in {@code requestedCodes}.
     *
     * @param requestedCodes the full set of codes from the CRTDL cohort definition
     * @return the intersection of config prospective codes and {@code requestedCodes}
     */
    public Set<TermCode> extractRequestedProspectiveCodes(Set<TermCode> requestedCodes) {
        return entries.stream()
                .map(ProspectiveEntry::code)
                .filter(requestedCodes::contains)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Returns {@code prospectiveCodes} plus any retrospective modifier codes that are both configured
     * for one of the prospective codes and were explicitly requested (present in {@code requestedCodes}).
     * <p>
     * Retro modifier codes are only added when the researcher included them in the CRTDL; this avoids
     * applying retrospective logic that was not requested.
     *
     * @param prospectiveCodes the prospective codes for the current request (after {@link #extractRequestedProspectiveCodes})
     * @param requestedCodes   the full set of codes from the CRTDL cohort definition
     * @return an unmodifiable set of codes to fetch from the FHIR server
     */
    public Set<TermCode> withRetroModifiers(Set<TermCode> prospectiveCodes, Set<TermCode> requestedCodes) {
        Set<TermCode> result = new HashSet<>(prospectiveCodes);
        entries.stream()
                .filter(e -> prospectiveCodes.contains(e.code()) && e.hasRetroModifiers())
                .forEach(e -> e.retroModifiers().stream()
                        .map(RetroModifier::code)
                        .filter(requestedCodes::contains)
                        .forEach(result::add));
        return Set.copyOf(result);
    }

    /**
     * Returns a map from each retrospective modifier {@link TermCode} to the {@link ProspectiveEntry} it modifies.
     *
     * @return an unmodifiable map; only entries with at least one retro modifier are included
     */
    public Map<TermCode, ProspectiveEntry> retroToProspective() {
        Map<TermCode, ProspectiveEntry> result = new HashMap<>();
        entries.stream()
                .filter(ProspectiveEntry::hasRetroModifiers)
                .forEach(e -> e.retroModifiers().forEach(retro -> result.put(retro.code(), e)));
        return Map.copyOf(result);
    }

    /**
     * Returns the subset of {@code codes} whose entries are marked as validity gates.
     *
     * @param codes the prospective codes to filter
     * @return codes in {@code codes} that correspond to validity-gate entries
     */
    public Set<TermCode> gateCodes(Set<TermCode> codes) {
        return entries.stream()
                .filter(ProspectiveEntry::validityGate)
                .map(ProspectiveEntry::code)
                .filter(codes::contains)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Returns the subset of {@code codes} whose entries are NOT validity gates (i.e. data-period codes).
     *
     * @param codes the prospective codes to filter
     * @return codes in {@code codes} that correspond to data-period (non-gate) entries
     */
    public Set<TermCode> nonGateCodes(Set<TermCode> codes) {
        return entries.stream()
                .filter(e -> !e.validityGate())
                .map(ProspectiveEntry::code)
                .filter(codes::contains)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Validates that all co-occurrence constraints declared by entries present in {@code codes} are satisfied.
     * <p>
     * For each entry whose code appears in {@code codes}, every code listed in that entry's {@code required}
     * field must also appear in {@code codes}. This enforces rules such as ".6 and .8 must appear together".
     *
     * @param codes the set of consent codes extracted from the CRTDL
     * @throws ConsentFormatException if any required co-occurrence constraint is violated
     */
    public void validateCodeCoOccurrence(Set<TermCode> codes) throws ConsentFormatException {
        for (ProspectiveEntry entry : entries) {
            if (!codes.contains(entry.code())) continue;
            for (TermCode required : entry.required()) {
                if (!codes.contains(required)) {
                    throw new ConsentFormatException(
                            "Consent code " + entry.code().code() + " requires " + required.code()
                                    + " to also be present in the CRTDL cohort definition.");
                }
            }
        }
    }
}
