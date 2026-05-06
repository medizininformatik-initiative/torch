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

import java.time.LocalDate;
import java.util.ArrayList;
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
     * Applies retrospective modifiers from {@link ConsentCodeConfig} to a flat list of provisions.
     * <p>
     * For each permitted prospective provision whose period overlaps a permitted retro modifier provision,
     * the start date is shifted to {@link ProspectiveEntry#lookbackDate()}.
     * Deny provisions for retro modifier codes subtract from the retroactive grant before it is emitted.
     * Retro modifier provisions themselves are dropped from the output.
     *
     * @param provisions the raw provisions from a single {@link ConsentProvisions} resource
     * @return the transformed provision list with retro grants applied and modifier provisions removed
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

        // Retro modifier denies (e.g. .45 deny) revoke the retroactive grant and must be applied
        // to the retro-extended period before it exits this method.
        Map<TermCode, List<Period>> retroDeniesByProspective = provisions.stream()
                .filter(p -> !p.permit())
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
                    if (entry == null || !entry.hasRetroModifiers()) {
                        return Stream.of(p);
                    }
                    List<Period> retroPeriods = retroPermitsByProspective.getOrDefault(p.code(), List.of());
                    if (retroPeriods.stream().noneMatch(r -> p.period().intersect(r) != null)) {
                        return Stream.of(p);
                    }
                    var newStart = entry.lookbackDate();
                    NonContinuousPeriod extended = NonContinuousPeriod.of(new Period(newStart, p.period().end()));
                    for (Period deny : retroDeniesByProspective.getOrDefault(p.code(), List.of())) {
                        extended = extended.substract(deny);
                    }
                    return extended.periods().stream()
                            .map(period -> new Provision(p.code(), period, true, true));
                })
                .toList();
    }

    /**
     * Calculates the allowed consent periods per code for a single patient.
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
        // Permits only come from resources that contain the full required package (.6 AND .8).
        // Denies are applied globally across all resources — a revocation document (deny-only) still counts.
        // Retro modifier denies (.45/.46) are consumed inside applyRetroModifiers per resource.
        List<Provision> validPermits = new ArrayList<>();
        List<Provision> allDenies = new ArrayList<>();

        for (ConsentProvisions cp : consentProvisions) {
            List<Provision> processed = applyRetroModifiers(cp.provisions());

            Set<TermCode> permittedCodes = processed.stream()
                    .filter(Provision::permit)
                    .map(Provision::code)
                    .filter(consentCodes::contains)
                    .collect(Collectors.toSet());

            // Only resources carrying all required codes contribute permits (full-package check).
            if (permittedCodes.containsAll(consentCodes)) {
                processed.stream()
                        .filter(Provision::permit)
                        .filter(p -> consentCodes.contains(p.code()))
                        .forEach(validPermits::add);
            }

            processed.stream()
                    .filter(p -> !p.permit())
                    .filter(p -> consentCodes.contains(p.code()))
                    .forEach(allDenies::add);
        }

        List<Provision> retroPermits = validPermits.stream()
                .filter(Provision::retroExtended)
                .toList();

        List<Provision> regularPermits = validPermits.stream()
                .filter(p -> !p.retroExtended())
                .toList();

        // Retro-extended permits are immune to prospective code denies (.6 deny).
        Map<TermCode, NonContinuousPeriod> result = new HashMap<>();
        for (Provision p : retroPermits) {
            result.merge(p.code(), NonContinuousPeriod.of(p.period()), NonContinuousPeriod::merge);
        }

        Map<TermCode, NonContinuousPeriod> regularResult = new HashMap<>();
        for (Provision p : regularPermits) {
            regularResult.merge(p.code(), NonContinuousPeriod.of(p.period()), NonContinuousPeriod::merge);
        }
        for (Provision p : allDenies) {
            regularResult.computeIfPresent(p.code(), (k, v) -> v.substract(p.period()));
        }

        regularResult.forEach((code, period) -> {
            if (!period.isEmpty()) {
                result.merge(code, period, NonContinuousPeriod::merge);
            }
        });

        return result.keySet().equals(consentCodes) ? result : Map.of();
    }

    /**
     * Returns the intersection of all provided consent periods by code.
     *
     * @param consentsByCode a non-empty map from consent code to its allowed {@link NonContinuousPeriod}
     * @return the intersection of all periods across all codes
     * @throws ConsentViolatedException if there are no periods or the intersection is empty
     */
    public NonContinuousPeriod intersectConsent(Map<TermCode, NonContinuousPeriod> consentsByCode) throws ConsentViolatedException {
        if (consentsByCode.isEmpty()) {
            throw new ConsentViolatedException("No consent periods found");
        }

        NonContinuousPeriod result = consentsByCode.values().stream()
                .reduce(NonContinuousPeriod::intersect)
                .get(); // non-empty is guaranteed by the isEmpty() check above

        if (result.isEmpty()) {
            throw new ConsentViolatedException("Consent periods do not overlap");
        }

        return result;
    }

    /**
     * Calculates the effective data-extraction consent period for multiple patients.
     * <p>
     * For each patient:
     * <ol>
     *     <li>Merges and subtracts permit/deny provisions per code.</li>
     *     <li>Checks that every validity-gate code's period contains today; excludes the patient if not.</li>
     *     <li>Intersects the non-gate (data-period) code periods and returns that as the extraction window.</li>
     * </ol>
     *
     * @param consentCodes      the prospective codes that must all be present for valid consent
     * @param consentsByPatient a map from patient ID to a list of their {@link ConsentProvisions}
     * @return a map from patient ID to {@link NonContinuousPeriod} representing their data-extraction window
     */
    public Map<String, NonContinuousPeriod> calculateConsent(
            Set<TermCode> consentCodes,
            Map<String, List<ConsentProvisions>> consentsByPatient
    ) {
        LocalDate today = LocalDate.now();
        Set<TermCode> gateCodes = consentCodeConfig.gateCodes(consentCodes);
        Set<TermCode> dataCodes = consentCodeConfig.nonGateCodes(consentCodes);

        return consentsByPatient.entrySet().stream()
                .flatMap(entry -> {
                    String patientId = entry.getKey();
                    List<ConsentProvisions> provisions = entry.getValue();

                    Map<TermCode, NonContinuousPeriod> byCode = subtractAndMergeByCode(provisions, consentCodes);
                    if (byCode.isEmpty()) {
                        return Stream.empty();
                    }

                    boolean gateValid = gateCodes.stream()
                            .allMatch(code -> byCode.get(code).containsDate(today));
                    if (!gateValid) {
                        logger.debug("Patient {} excluded: validity-gate period does not contain today ({})", patientId, today);
                        return Stream.empty();
                    }

                    Map<TermCode, NonContinuousPeriod> dataByCode = byCode.entrySet().stream()
                            .filter(e -> dataCodes.contains(e.getKey()))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

                    try {
                        NonContinuousPeriod dataPeriod = intersectConsent(dataByCode);
                        return Stream.of(Map.entry(patientId, dataPeriod));
                    } catch (ConsentViolatedException e) {
                        return Stream.empty();
                    }
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
