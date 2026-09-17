package de.medizininformatikinitiative.torch.diagnostics.consent;

import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import static java.util.Objects.requireNonNull;

/**
 * Collects the opt-in consent diagnostics (raw provisions, final consent periods, per-resource consent
 * decisions, and the set of patients whose consent was evaluated) recorded during processing of a single
 * batch.
 * <p>
 * Recording is a no-op when disabled, so that enabling this per job (via {@code consentDiagnostics} on
 * {@code POST /fhir/$extract-data}) is the only way to pay for its volume — a production run without it
 * incurs no extra allocation: {@link #disabled()} shares one set of empty backing collections across every
 * disabled instance rather than allocating fresh ones per batch.
 * <p>
 * Not a record to avoid mutations of the collections without the dedicated methods.
 */
public class ConsentDiagnostics {

    private static final Queue<RawProvisionEvent> DISABLED_RAW_PROVISIONS = new ConcurrentLinkedQueue<>();
    private static final Queue<FinalPeriodEvent> DISABLED_FINAL_PERIODS = new ConcurrentLinkedQueue<>();
    private static final Queue<ConsentConsideredResourceEvent> DISABLED_CONSIDERED_RESOURCES = new ConcurrentLinkedQueue<>();
    private static final Set<String> DISABLED_PATIENT_IDS = ConcurrentHashMap.newKeySet();

    private final boolean enabled;
    private final Queue<RawProvisionEvent> rawProvisions;
    private final Queue<FinalPeriodEvent> finalPeriods;
    private final Queue<ConsentConsideredResourceEvent> consideredResources;
    private final Set<String> patientIds;

    private ConsentDiagnostics(boolean enabled, Queue<RawProvisionEvent> rawProvisions,
                               Queue<FinalPeriodEvent> finalPeriods,
                               Queue<ConsentConsideredResourceEvent> consideredResources,
                               Set<String> patientIds) {
        this.enabled = enabled;
        this.rawProvisions = requireNonNull(rawProvisions);
        this.finalPeriods = requireNonNull(finalPeriods);
        this.consideredResources = requireNonNull(consideredResources);
        this.patientIds = requireNonNull(patientIds);
    }

    public static ConsentDiagnostics create(boolean enabled) {
        if (!enabled) {
            return disabled();
        }
        return new ConsentDiagnostics(true, new ConcurrentLinkedQueue<>(), new ConcurrentLinkedQueue<>(),
                new ConcurrentLinkedQueue<>(), ConcurrentHashMap.newKeySet());
    }

    public static ConsentDiagnostics disabled() {
        return new ConsentDiagnostics(false, DISABLED_RAW_PROVISIONS, DISABLED_FINAL_PERIODS,
                DISABLED_CONSIDERED_RESOURCES, DISABLED_PATIENT_IDS);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isEmpty() {
        return rawProvisions.isEmpty() && finalPeriods.isEmpty() && consideredResources.isEmpty() && patientIds.isEmpty();
    }

    /**
     * Compares by recorded content only; {@code enabled} governs whether future {@code addX}/{@code registerPatient}
     * calls are no-ops and is not itself part of the diagnostics.
     */
    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ConsentDiagnostics other)) {
            return false;
        }
        return this.getRawProvisions().equals(other.getRawProvisions()) &&
                this.getFinalPeriods().equals(other.getFinalPeriods()) &&
                this.getConsideredResources().equals(other.getConsideredResources()) &&
                this.patientIds.equals(other.patientIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getRawProvisions(), getFinalPeriods(), getConsideredResources(), patientIds);
    }

    public void addRawProvision(RawProvisionEvent event) {
        if (enabled) rawProvisions.add(event);
    }

    public void addFinalPeriod(FinalPeriodEvent event) {
        if (enabled) finalPeriods.add(event);
    }

    public void addConsideredResource(ConsentConsideredResourceEvent event) {
        if (enabled) consideredResources.add(event);
    }

    /**
     * Registers a patient as having had their consent evaluated in this batch, independent of whether that
     * evaluation produced any raw provisions, final periods or considered resources. Lets the per-patient
     * trail folder (see {@code DiagnosticsStore.writeConsentTrailPerPatient}) represent "this patient's
     * calculated consent is empty" rather than silently omitting the patient entirely.
     */
    public void registerPatient(String patientId) {
        if (enabled) patientIds.add(patientId);
    }

    public List<RawProvisionEvent> getRawProvisions() {
        return rawProvisions.stream().toList();
    }

    public List<FinalPeriodEvent> getFinalPeriods() {
        return finalPeriods.stream().toList();
    }

    public List<ConsentConsideredResourceEvent> getConsideredResources() {
        return consideredResources.stream().toList();
    }

    public List<String> getPatientIds() {
        return patientIds.stream().toList();
    }
}
