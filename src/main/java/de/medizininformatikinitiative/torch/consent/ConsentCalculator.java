package de.medizininformatikinitiative.torch.consent;

import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.model.consent.ConsentCodeConfig;
import de.medizininformatikinitiative.torch.model.consent.ConsentProvisions;
import de.medizininformatikinitiative.torch.model.consent.NonContinuousPeriod;
import de.medizininformatikinitiative.torch.model.consent.Period;
import de.medizininformatikinitiative.torch.model.consent.ProspectiveEntry;
import de.medizininformatikinitiative.torch.model.consent.Provision;
import de.medizininformatikinitiative.torch.model.management.TermCode;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

@Component
public class ConsentCalculator {

    private final ConsentCodeConfig consentCodeConfig;

    public ConsentCalculator(ConsentCodeConfig consentCodeConfig) {
        this.consentCodeConfig = requireNonNull(consentCodeConfig);
    }

    /**
     * Applies retrospective modifiers from {@link ConsentCodeConfig} to a flat list of provisions.
     * <p>
     * For each permitted retro modifier provision: any permitted prospective provision whose period
     * overlaps the retro period has its start shifted to {@link de.medizininformatikinitiative.torch.model.consent.RetroModifier#lookbackStart(java.time.LocalDate)}.
     * Deny provisions are never modified. Retro modifier provisions are dropped from the output.
     */
    private List<Provision> applyRetroModifiers(List<Provision> provisions) {
        Map<TermCode, ProspectiveEntry> retroMap = consentCodeConfig.retroToProspective();
        if (retroMap.isEmpty()) {
            return provisions;
        }

        // Group permitted retro periods by the prospective code they modify
        Map<TermCode, List<Period>> retroPermitsByProspective = provisions.stream()
                .filter(Provision::permit)
                .filter(p -> retroMap.containsKey(p.code()))
                .collect(Collectors.groupingBy(
                        p -> retroMap.get(p.code()).code(),
                        Collectors.mapping(Provision::period, Collectors.toList())
                ));

        return provisions.stream()
                .filter(p -> !retroMap.containsKey(p.code()))
                .map(p -> {
                    if (!p.permit()) {
                        return p;
                    }
                    ProspectiveEntry entry = consentCodeConfig.entries().stream()
                            .filter(e -> e.code().equals(p.code()))
                            .findFirst()
                            .orElse(null);
                    if (entry == null || !entry.hasRetroModifier()) {
                        return p;
                    }
                    List<Period> retroPeriods = retroPermitsByProspective.getOrDefault(p.code(), List.of());
                    boolean overlapsRetro = retroPeriods.stream().anyMatch(r -> p.period().intersect(r) != null);
                    if (overlapsRetro) {
                        return new Provision(p.code(), new Period(entry.retroModifier().lookbackStart(p.period().start()), p.period().end()), true);
                    }
                    return p;
                })
                .toList();
    }

    /**
     * Calculates the allowed consent periods per code for a single patient.
     * <p>
     * Retro modifier provisions are applied before merge/subtract: any permitted prospective provision
     * overlapping a permitted retro provision has its start shifted to the configured lookback date.
     * Only {@code consentCodes} (prospective codes) are required in the result.
     *
     * @param consentProvisions list of consent provisions for a patient
     * @param consentCodes      the prospective consent codes required for valid consent
     * @return a map from consent code to {@link NonContinuousPeriod} representing allowed periods,
     * or an empty map if any required code has no allowed period
     */
    Map<TermCode, NonContinuousPeriod> subtractAndMergeByCode(
            List<ConsentProvisions> consentProvisions,
            Set<TermCode> consentCodes
    ) {
        List<Provision> allProvisions = consentProvisions.stream()
                .flatMap(cp -> cp.provisions().stream())
                .toList();

        List<Provision> relevantProvisions = applyRetroModifiers(allProvisions).stream()
                .filter(p -> consentCodes.contains(p.code()))
                .toList();

        List<Provision> permits = relevantProvisions.stream()
                .filter(Provision::permit)
                .toList();

        List<Provision> denies = relevantProvisions.stream()
                .filter(p -> !p.permit())
                .toList();

        Map<TermCode, NonContinuousPeriod> result = new HashMap<>();

        for (Provision p : permits) {
            NonContinuousPeriod existing = result.getOrDefault(p.code(), NonContinuousPeriod.of());
            result.put(p.code(), existing.merge(NonContinuousPeriod.of(p.period())));
        }

        for (Provision p : denies) {
            NonContinuousPeriod existing = result.getOrDefault(p.code(), NonContinuousPeriod.of());
            result.put(p.code(), existing.substract(p.period()));
        }

        Map<TermCode, NonContinuousPeriod> filtered = result.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return filtered.keySet().equals(consentCodes) ? filtered : Map.of();
    }

    /**
     * Returns the intersection of all provided consent periods by code.
     *
     * @param consentsByCode map from consent code to {@link NonContinuousPeriod}
     * @return a {@link NonContinuousPeriod} representing the intersection of all provided periods
     * @throws ConsentViolatedException if there are no consent periods or if the intersection is empty
     */
    public NonContinuousPeriod intersectConsent(Map<TermCode, NonContinuousPeriod> consentsByCode) throws ConsentViolatedException {
        if (consentsByCode.isEmpty()) {
            throw new ConsentViolatedException("No consent periods found");
        }

        NonContinuousPeriod result = consentsByCode.values().stream()
                .reduce(NonContinuousPeriod::intersect)
                .orElseThrow(() -> new ConsentViolatedException("No consent periods found"));

        if (result.isEmpty()) {
            throw new ConsentViolatedException("Consent periods do not overlap");
        }

        return result;
    }

    /**
     * Calculates the effective consent periods for multiple patients.
     *
     * @param consentCodes      the prospective codes that must all be present for valid consent
     * @param consentsByPatient a map from patient ID to a list of their {@link ConsentProvisions}
     * @return a map from patient ID to {@link NonContinuousPeriod} representing their effective consent periods
     */
    public Map<String, NonContinuousPeriod> calculateConsent(
            Set<TermCode> consentCodes,
            Map<String, List<ConsentProvisions>> consentsByPatient
    ) {
        return consentsByPatient.entrySet().stream()
                .flatMap(entry -> {
                    String patientId = entry.getKey();
                    List<ConsentProvisions> provisions = entry.getValue();

                    try {
                        NonContinuousPeriod finalConsent =
                                intersectConsent(subtractAndMergeByCode(provisions, consentCodes));
                        return Stream.of(Map.entry(patientId, finalConsent));
                    } catch (ConsentViolatedException e) {
                        return Stream.empty();
                    }
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
