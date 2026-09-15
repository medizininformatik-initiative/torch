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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
     * Resources sharing a {@link ConsentProvisions#dateTime()} form one signing event; events are processed
     * in ascending {@code dateTime} order (a missing {@code dateTime} sorts first), one code at a time,
     * folding each event into a running {@link NonContinuousPeriod} via {@link #applyEvent}. This makes both
     * permits and denies order-sensitive across events: a later permit can reinstate a period an earlier
     * deny removed, and an event denying any of a code's configured retro modifiers discards that code's
     * entire history so far rather than merely subtracting from it — a matching retro modifier permit does
     * not, since a revocation demands a conservative reset but a grant should never leave a patient worse
     * off. Within one event the outcome does not depend on resource order.
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

        Map<TermCode, NonContinuousPeriod> state = new HashMap<>();

        for (List<ConsentProvisions> event : groupByDateTime(consentProvisions)) {
            for (TermCode code : consentCodes) {
                state.put(code, applyEvent(state.getOrDefault(code, NonContinuousPeriod.of()),
                        event, code, entriesByCode.get(code), consentCodes));
            }
        }

        Map<TermCode, NonContinuousPeriod> result = state.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return result.keySet().equals(consentCodes) ? result : Map.of();
    }

    /**
     * Groups resources into signing events of equal {@code dateTime}, in ascending {@code dateTime} order.
     * <p>
     * Consent.dateTime is 0..1 in FHIR; resources without one form the first event so a dated resource can
     * still override them, rather than throwing out of the comparator.
     */
    private static List<List<ConsentProvisions>> groupByDateTime(List<ConsentProvisions> consentProvisions) {
        List<ConsentProvisions> ordered = consentProvisions.stream()
                .sorted(Comparator.comparing(cp -> cp.dateTime().getValue(),
                        Comparator.nullsFirst(Comparator.<Date>naturalOrder())))
                .toList();

        List<List<ConsentProvisions>> events = new ArrayList<>();
        Date current = null;
        for (ConsentProvisions cp : ordered) {
            Date dateTime = cp.dateTime().getValue();
            if (events.isEmpty() || !Objects.equals(current, dateTime)) {
                events.add(new ArrayList<>());
                current = dateTime;
            }
            events.getLast().add(cp);
        }
        return events;
    }

    /**
     * Folds one signing event into the running period for a single code.
     * <p>
     * If any resource of the event carries a <b>deny</b> of any of {@code entry}'s retro modifier codes —
     * regardless of overlap — {@code running} is discarded first. Then each resource's own contribution
     * (see {@link #ownContribution}) is merged in, and finally every deny of {@code code} itself within the
     * event is subtracted, so a same-event deny always wins over a same-event permit.
     *
     * @param running      the code's accumulated period from earlier events
     * @param event        the resources sharing one {@code dateTime}
     * @param code         the prospective code being folded
     * @param entry        {@code code}'s config entry, or {@code null} if it is not configured
     * @param consentCodes the requested codes a resource must permit in full to contribute permits
     * @return the code's period after folding in this event
     */
    private NonContinuousPeriod applyEvent(
            NonContinuousPeriod running,
            List<ConsentProvisions> event,
            TermCode code,
            ProspectiveEntry entry,
            Set<TermCode> consentCodes
    ) {
        Set<TermCode> retroCodes = entry == null ? Set.of()
                : entry.retroModifiers().stream().map(RetroModifier::code).collect(Collectors.toSet());

        List<Provision> eventProvisions = event.stream().flatMap(cp -> cp.provisions().stream()).toList();

        boolean retroTouched = eventProvisions.stream()
                .anyMatch(p -> !p.permit() && retroCodes.contains(p.code()));

        NonContinuousPeriod combined = retroTouched ? NonContinuousPeriod.of() : running;

        for (ConsentProvisions cp : event) {
            if (isFullPackage(cp.provisions(), consentCodes)) {
                combined = combined.merge(ownContribution(cp.provisions(), code, entry, retroCodes));
            }
        }

        for (Provision p : eventProvisions) {
            if (!p.permit() && p.code().equals(code)) {
                combined = combined.substract(p.period());
            }
        }

        return combined;
    }

    /**
     * Permits only come from resources that contain the full required package (.6 AND .8). Denies are
     * applied regardless — a revocation document (deny-only) still counts.
     */
    private static boolean isFullPackage(List<Provision> provisions, Set<TermCode> consentCodes) {
        return provisions.stream()
                .filter(Provision::permit)
                .map(Provision::code)
                .filter(consentCodes::contains)
                .collect(Collectors.toSet())
                .containsAll(consentCodes);
    }

    /**
     * Returns one resource's permit periods for {@code code}, each plus a retro extension back to
     * {@code entry}'s {@code lookbackDate} (up to that permit's end) when a same-resource retro modifier
     * permit overlaps that permit; same-resource retro modifier denies reduce that extension before it is
     * added and never touch the plain permit period on their own.
     */
    private static NonContinuousPeriod ownContribution(
            List<Provision> provisions,
            TermCode code,
            ProspectiveEntry entry,
            Set<TermCode> retroCodes
    ) {
        NonContinuousPeriod contribution = NonContinuousPeriod.of();
        List<Provision> ownPermits = provisions.stream()
                .filter(Provision::permit)
                .filter(p -> p.code().equals(code))
                .toList();
        for (Provision ownPermit : ownPermits) {
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
        return contribution;
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
