package de.medizininformatikinitiative.torch.consent;

import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.model.consent.ConsentCode;
import de.medizininformatikinitiative.torch.model.consent.ConsentProvisions;
import de.medizininformatikinitiative.torch.model.consent.NonContinuousPeriod;
import de.medizininformatikinitiative.torch.model.consent.Provision;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class ConsentCalculator {

    ConsentCalculator() {

    }


    /**
     * Calculates the allowed consent periods per code for a single patient.
     * <p>
     * This method processes a list of {@link ConsentProvisions} sorted by date. For each provision:
     * <ul>
     *     <li>If the provision is a permit, its period is merged into the existing allowed periods for the code.</li>
     *     <li>If the provision is a denial, its period is subtracted from the existing allowed periods for the code.</li>
     * </ul>
     * Only codes relevant {@code consentCodes} (as determined by {@link ConsentCodeMapper} in the pipeline) are considered.
     *
     * @param consentProvisions list of consent provisions for a patient
     * @param consentCodes      the consentcodes relevant to type of consent to calculate
     * @return a map from consent code to {@link NonContinuousPeriod} representing allowed periods for that code
     */
    Map<ConsentCode, NonContinuousPeriod> subtractAndMergeByCode(
            List<ConsentProvisions> consentProvisions,
            Set<ConsentCode> consentCodes
    ) {
        // Flatten all provisions, filter relevant codes
        List<Provision> relevantProvisions = consentProvisions.stream()
                .flatMap(cp -> cp.provisions().stream())
                .filter(p -> consentCodes.contains(p.code()))
                .toList();

        // Separate permits and denies
        List<Provision> permits = relevantProvisions.stream()
                .filter(Provision::permit)
                .toList();

        List<Provision> denies = relevantProvisions.stream()
                .filter(p -> !p.permit())
                .toList();

        Map<ConsentCode, NonContinuousPeriod> result = new HashMap<>();

        // Apply permits first
        for (Provision p : permits) {
            NonContinuousPeriod existing = result.getOrDefault(p.code(), NonContinuousPeriod.of());
            result.put(p.code(), existing.merge(NonContinuousPeriod.of(p.period())));
        }

        // Apply denials later
        for (Provision p : denies) {
            NonContinuousPeriod existing = result.getOrDefault(p.code(), NonContinuousPeriod.of());
            result.put(p.code(), existing.substract(p.period()));
        }

        Map<ConsentCode, NonContinuousPeriod> filtered = result.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return filtered.keySet().equals(consentCodes) ? filtered : Map.of();
    }

    /**
     * Returns the intersection of all provided consent periods by code.
     * <p>
     * This method calculates the overlapping periods across all consent codes.
     * <ul>
     *     <li>If {@code consentsByCode} is empty, a {@link ConsentViolatedException} is thrown.</li>
     *     <li>If the intersection of all periods is empty, a {@link ConsentViolatedException} is thrown.</li>
     * </ul>
     *
     * @param consentsByCode map from consent code to {@link NonContinuousPeriod}
     * @return a {@link NonContinuousPeriod} representing the intersection of all provided periods
     * @throws ConsentViolatedException if there are no consent periods or if the intersection is empty
     */
    public NonContinuousPeriod intersectConsent(Map<ConsentCode, NonContinuousPeriod> consentsByCode) throws ConsentViolatedException {
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
     * <p>
     * For each patient in {@code consentsByPatient}:
     * <ul>
     *     <li>Calculates allowed periods per code using {@link #subtractAndMergeByCode(List, Set)}</li>
     *     <li>Computes the intersection of all consent periods for that patient using {@link #intersectConsent(Map)}</li>
     *     <li>If a patient has no overlapping consent periods, they are skipped in the result</li>
     * </ul>
     *
     * @param consentCodes      the key identifying the type of consent to calculate
     * @param consentsByPatient a map from patient ID to a list of their {@link ConsentProvisions}
     * @return a map from patient ID to {@link NonContinuousPeriod} representing their effective consent periods
     */
    public Map<String, NonContinuousPeriod> calculateConsent(
            Set<ConsentCode> consentCodes,
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
                        // skip patient with empty consent
                        return Stream.empty();
                    }
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

}
