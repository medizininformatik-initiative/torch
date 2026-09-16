package de.medizininformatikinitiative.torch.consent;

import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.model.consent.ConsentCodeConfig;
import de.medizininformatikinitiative.torch.model.consent.ConsentProvisions;
import de.medizininformatikinitiative.torch.model.consent.NonContinuousPeriod;
import de.medizininformatikinitiative.torch.model.consent.Period;
import de.medizininformatikinitiative.torch.model.consent.ProspectiveEntry;
import de.medizininformatikinitiative.torch.model.consent.Provision;
import de.medizininformatikinitiative.torch.model.consent.RetroModifier;
import de.medizininformatikinitiative.torch.model.management.TermCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.Date;
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
     * Calculates the allowed consent periods per code for a single patient.
     * <p>
     * Resources are processed in ascending {@link ConsentProvisions#dateTime()} order (a missing
     * {@code dateTime} sorts first; ties broken by {@link ConsentProvisions#id()} for determinism),
     * one code at a time, folding each resource's
     * contribution into a running {@link NonContinuousPeriod} via {@link #applyResource}. This makes both
     * permits and denies order-sensitive: a later permit can reinstate a period an earlier deny removed.
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
        Map<TermCode, ProspectiveEntry> entriesByCode = consentCodeConfig.entries().stream()
                .collect(Collectors.toMap(ProspectiveEntry::code, e -> e));

        // Consent.dateTime is 0..1 in FHIR; a resource without one sorts first so a dated resource can
        // still override it, rather than throwing out of the comparator.
        List<ConsentProvisions> ordered = consentProvisions.stream()
                .sorted(Comparator.<ConsentProvisions, Date>comparing(cp -> cp.dateTime().getValue(),
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(ConsentProvisions::id))
                .toList();

        Map<TermCode, NonContinuousPeriod> state = new HashMap<>();

        for (ConsentProvisions cp : ordered) {
            List<Provision> provisions = cp.provisions();

            // Permits only come from resources that contain the full required package (.6 AND .8).
            // Denies are applied globally — a revocation document (deny-only) still counts.
            boolean fullPackage = provisions.stream()
                    .filter(Provision::permit)
                    .map(Provision::code)
                    .filter(consentCodes::contains)
                    .collect(Collectors.toSet())
                    .containsAll(consentCodes);

            for (TermCode code : consentCodes) {
                state.put(code, applyResource(state.getOrDefault(code, NonContinuousPeriod.of()),
                        provisions, code, entriesByCode.get(code), fullPackage));
            }
        }

        Map<TermCode, NonContinuousPeriod> result = state.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return result.keySet().equals(consentCodes) ? result : Map.of();
    }

    /**
     * Folds one resource's provisions into the running period for a single code.
     * <p>
     * The resource's own contribution is its permit period for {@code code} — only if the resource alone
     * carries the full required package of consent codes — plus a retro extension back to {@code entry}'s
     * {@code lookbackDate} when a same-resource retro modifier permit overlaps that permit; same-resource
     * retro modifier denies reduce that extension before it is added, exactly as before, and never touch
     * the plain permit period on their own. Finally, denies of {@code code} itself (from any resource) are
     * subtracted from the merged result.
     *
     * @param running     the code's accumulated period from resources processed so far
     * @param provisions  the current resource's provisions
     * @param code        the prospective code being folded
     * @param entry       {@code code}'s config entry, or {@code null} if it is not configured
     * @param fullPackage whether this resource alone permits every code in the requested set
     * @return the code's period after folding in this resource
     */
    private NonContinuousPeriod applyResource(
            NonContinuousPeriod running,
            List<Provision> provisions,
            TermCode code,
            ProspectiveEntry entry,
            boolean fullPackage
    ) {
        Set<TermCode> retroCodes = entry == null ? Set.of()
                : entry.retroModifiers().stream().map(RetroModifier::code).collect(Collectors.toSet());

        NonContinuousPeriod contribution = NonContinuousPeriod.of();
        if (fullPackage) {
            Provision ownPermit = provisions.stream()
                    .filter(Provision::permit)
                    .filter(p -> p.code().equals(code))
                    .findFirst()
                    .orElse(null);
            if (ownPermit != null) {
                contribution = contribution.merge(NonContinuousPeriod.of(ownPermit.period()));
                boolean retroPermitOverlaps = !retroCodes.isEmpty() && provisions.stream()
                        .filter(Provision::permit)
                        .filter(p -> retroCodes.contains(p.code()))
                        .anyMatch(p -> ownPermit.period().intersect(p.period()) != null);
                if (retroPermitOverlaps) {
                    NonContinuousPeriod extension = NonContinuousPeriod.of(
                            new Period(entry.lookbackDate(), ownPermit.period().end()));
                    for (Provision p : provisions) {
                        if (!p.permit() && retroCodes.contains(p.code())) {
                            extension = extension.substract(p.period());
                        }
                    }
                    contribution = contribution.merge(extension);
                }
            }
        }

        NonContinuousPeriod combined = running.merge(contribution);

        for (Provision p : provisions) {
            if (!p.permit() && p.code().equals(code)) {
                combined = combined.substract(p.period());
            }
        }

        return combined;
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
                        logger.debug("Patient {} excluded: required consent codes not fully permitted", patientId);
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
                        logger.debug("Patient {} excluded: {}", patientId, e.getMessage());
                        return Stream.empty();
                    }
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
