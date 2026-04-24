package de.medizininformatikinitiative.torch.consent;

import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.model.consent.ConsentCodeConfig;
import de.medizininformatikinitiative.torch.model.consent.ConsentProvisions;
import de.medizininformatikinitiative.torch.model.consent.NonContinuousPeriod;
import de.medizininformatikinitiative.torch.model.consent.Period;
import de.medizininformatikinitiative.torch.model.consent.ProspectiveEntry;
import de.medizininformatikinitiative.torch.model.consent.Provision;
import de.medizininformatikinitiative.torch.model.management.TermCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(ConsentCalculator.class);

    private final ConsentCodeConfig consentCodeConfig;

    public ConsentCalculator(ConsentCodeConfig consentCodeConfig) {
        this.consentCodeConfig = requireNonNull(consentCodeConfig);
    }

    /**
     * Applies the {@code dataPeriodOffsetYears} from {@link ConsentCodeConfig} to all permitted prospective
     * provisions, regardless of whether a retrospective modifier is configured.
     * <p>
     * Provisions whose end date would precede the start date after subtraction are discarded with a warning.
     * Deny provisions and provisions without a matching config entry pass through unchanged.
     */
    private List<Provision> applyOffsets(List<Provision> provisions) {
        boolean hasOffsets = consentCodeConfig.entries().stream().anyMatch(e -> e.dataPeriodOffsetYears() > 0);
        if (!hasOffsets) {
            return provisions;
        }
        return provisions.stream()
                .flatMap(p -> {
                    if (!p.permit()) {
                        return Stream.of(p);
                    }
                    ProspectiveEntry entry = consentCodeConfig.entries().stream()
                            .filter(e -> e.code().equals(p.code()))
                            .findFirst()
                            .orElse(null);
                    if (entry == null || entry.dataPeriodOffsetYears() == 0) {
                        return Stream.of(p);
                    }
                    var newEnd = p.period().end().minusYears(entry.dataPeriodOffsetYears());
                    if (newEnd.isBefore(p.period().start())) {
                        logger.warn("Skipping provision for code {} — period [{}, {}] became negative after applying offset of {} years",
                                p.code(), p.period().start(), p.period().end(), entry.dataPeriodOffsetYears());
                        return Stream.empty();
                    }
                    return Stream.of(new Provision(p.code(), new Period(p.period().start(), newEnd), true));
                })
                .toList();
    }

    /**
     * Applies retrospective modifiers from {@link ConsentCodeConfig} to a flat list of provisions.
     * <p>
     * For each permitted prospective provision whose offset-adjusted period overlaps a permitted retro
     * modifier provision, the start date is shifted to
     * {@link de.medizininformatikinitiative.torch.model.consent.RetroModifier#lookbackStart(java.time.LocalDate)}.
     * Deny provisions and retro modifier provisions are dropped from the output.
     */
    private List<Provision> applyRetroModifiers(List<Provision> provisions) {
        Map<TermCode, ProspectiveEntry> retroMap = consentCodeConfig.retroToProspective();
        if (retroMap.isEmpty()) {
            return provisions;
        }

        Map<TermCode, List<Period>> retroPermitsByProspective = provisions.stream()
                .filter(Provision::permit)
                .filter(p -> retroMap.containsKey(p.code()))
                .collect(Collectors.groupingBy(
                        p -> retroMap.get(p.code()).code(),
                        Collectors.mapping(Provision::period, Collectors.toList())
                ));

        return provisions.stream()
                .filter(p -> !retroMap.containsKey(p.code()))
                .flatMap(p -> {
                    if (!p.permit()) {
                        return Stream.of(p);
                    }
                    ProspectiveEntry entry = consentCodeConfig.entries().stream()
                            .filter(e -> e.code().equals(p.code()))
                            .findFirst()
                            .orElse(null);
                    if (entry == null || !entry.hasRetroModifier()) {
                        return Stream.of(p);
                    }
                    List<Period> retroPeriods = retroPermitsByProspective.getOrDefault(p.code(), List.of());
                    if (retroPeriods.stream().noneMatch(r -> p.period().intersect(r) != null)) {
                        return Stream.of(p);
                    }
                    var newStart = entry.retroModifier().lookbackStart(p.period().start());
                    return Stream.of(new Provision(p.code(), new Period(newStart, p.period().end()), true, true));
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

        List<Provision> relevantProvisions = applyRetroModifiers(applyOffsets(allProvisions)).stream()
                .filter(p -> consentCodes.contains(p.code()))
                .toList();

        List<Provision> retroPermits = relevantProvisions.stream()
                .filter(Provision::permit)
                .filter(Provision::retroExtended)
                .toList();

        List<Provision> regularPermits = relevantProvisions.stream()
                .filter(Provision::permit)
                .filter(p -> !p.retroExtended())
                .toList();

        List<Provision> denies = relevantProvisions.stream()
                .filter(p -> !p.permit())
                .toList();

        // Retro-extended permits are immune to denies — the retro grant supersedes prior revocations
        Map<TermCode, NonContinuousPeriod> result = new HashMap<>();
        for (Provision p : retroPermits) {
            result.merge(p.code(), NonContinuousPeriod.of(p.period()), NonContinuousPeriod::merge);
        }

        // Regular permits have denies applied
        Map<TermCode, NonContinuousPeriod> regularResult = new HashMap<>();
        for (Provision p : regularPermits) {
            regularResult.merge(p.code(), NonContinuousPeriod.of(p.period()), NonContinuousPeriod::merge);
        }
        for (Provision p : denies) {
            regularResult.computeIfPresent(p.code(), (k, v) -> v.substract(p.period()));
        }

        regularResult.forEach((code, period) -> {
            if (!period.isEmpty()) {
                result.merge(code, period, NonContinuousPeriod::merge);
            }
        });

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
